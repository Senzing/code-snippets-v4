#! /usr/bin/env python3

import concurrent.futures
import json
import os
import sys
from multiprocessing import Process, Queue
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INPUT_FILE = Path("../../resources/data/load-500.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def add_record(engine, record_to_add):
    record_dict = json.loads(record_to_add)
    data_source = record_dict.get("DATA_SOURCE", "")
    record_id = record_dict.get("RECORD_ID", "")
    engine.add_record(data_source, record_id, record_to_add)


def producer(input_file, queue):
    with open(input_file, "r", encoding="utf-8") as file:
        for record in file:
            queue.put(record, block=True)


def consumer(engine, queue):
    success_recs = 0
    error_recs = 0

    with concurrent.futures.ThreadPoolExecutor() as executor:
        futures = {executor.submit(add_record, engine, queue.get()): _ for _ in range(executor._max_workers)}

        while futures:
            done, _ = concurrent.futures.wait(futures, return_when=concurrent.futures.FIRST_COMPLETED)
            for f in done:
                try:
                    f.result()
                except (SzBadInputError, json.JSONDecodeError) as err:
                    mock_logger("ERROR", err, futures[f])
                    error_recs += 1
                except SzRetryableError as err:
                    mock_logger("WARN", err, futures[f])
                    error_recs += 1
                except (SzUnrecoverableError, SzError) as err:
                    mock_logger("CRITICAL", err, futures[f])
                    raise err
                else:
                    if not queue.empty():
                        record = queue.get()
                        futures[executor.submit(add_record, engine, record)] = record

                    success_recs += 1
                    if success_recs % 100 == 0:
                        print(f"Processed {success_recs:,} adds, with {error_recs:,} errors", flush=True)
                finally:
                    del futures[f]

        print(f"\nSuccessfully loaded {success_recs:,} records, with {error_recs:,} errors")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()

    input_queue = Queue(maxsize=200)  # type: ignore
    producer_proc = Process(target=producer, args=(INPUT_FILE, input_queue))
    producer_proc.start()
    consumer_proc = Process(target=consumer, args=(sz_engine, input_queue))
    consumer_proc.start()
    producer_proc.join()
    consumer_proc.join()

except SzError as err:
    mock_logger("CRITICAL", err)
