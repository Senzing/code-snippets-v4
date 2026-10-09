#! /usr/bin/env python3

import concurrent.futures
import json
import os
import queue
import sys
import threading
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INPUT_FILE = Path("../../resources/data/load-500.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem
MAX_WORKERS = 8
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


def producer(in_file, record_queue, errors):
    try:
        for record in in_file:
            record_queue.put(record, block=True)
    except Exception as err:
        # Errors in a thread don't reach the exit code, so hand them to the main thread. Ignore the file being
        # closed by the main thread after a fatal error, there is nothing more to read
        if not in_file.closed:
            errors.append(err)
    finally:
        # None tells the consumer there are no more records
        record_queue.put(None)


def submit_next(executor, engine, record_queue, futures):
    """Submit the next record from the queue, returns False once the producer has finished"""
    if (record := record_queue.get()) is None:
        return False
    futures[executor.submit(add_record, engine, record)] = record
    return True


def consumer(engine, record_queue):
    error_recs = 0
    more_records = True
    shutdown = False
    success_recs = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {}
        while more_records and len(futures) < MAX_WORKERS:
            more_records = submit_next(executor, engine, record_queue, futures)

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
                    shutdown = True
                    raise err
                else:
                    success_recs += 1
                    if success_recs % 100 == 0:
                        print(f"Processed {success_recs:,} adds, with {error_recs:,} errors", flush=True)
                finally:
                    if not shutdown and more_records:
                        more_records = submit_next(executor, engine, record_queue, futures)

                    del futures[f]

        print(f"\nSuccessfully loaded {success_recs:,} records, with {error_recs:,} errors")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()

    input_queue = queue.Queue(maxsize=200)
    producer_errors = []
    with open(INPUT_FILE, "r", encoding="utf-8") as input_file:
        producer_thread = threading.Thread(
            target=producer, args=(input_file, input_queue, producer_errors), daemon=True
        )
        producer_thread.start()
        consumer(sz_engine, input_queue)
        producer_thread.join()
    if producer_errors:
        raise producer_errors[0]
except SzError as err:
    mock_logger("CRITICAL", err)
    sys.exit(1)
