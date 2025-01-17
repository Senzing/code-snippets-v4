#! /usr/bin/env python3

import concurrent.futures
import itertools
import json
import os
import sys
from pathlib import Path

from senzing import (
    SzBadInputError,
    SzEngineFlags,
    SzError,
    SzRetryableError,
    SzUnrecoverableError,
)
from senzing_core import SzAbstractFactoryCore

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INPUT_FILE = Path("../../resources/data/load-500-with-errors.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem
OUTPUT_FILE = Path("../../resources/output/add_file_with_info.jsonl").resolve()


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def add_record(engine, record_to_add):
    record_dict = json.loads(record_to_add)
    data_source = record_dict.get("DATA_SOURCE", "")
    record_id = record_dict.get("RECORD_ID", "")
    return engine.add_record(data_source, record_id, record_to_add, flags=SzEngineFlags.SZ_WITH_INFO)


def engine_stats(engine):
    try:
        print(f"\n{engine.get_stats()}\n")
    except SzRetryableError as err:
        mock_logger("WARN", err)
    except SzError as err:
        mock_logger("CRITICAL", err)
        raise err


def futures_add(engine, input_file, output_file):
    success_recs = 0
    error_recs = 0

    with open(output_file, "w", encoding="utf-8") as out_file:
        with open(input_file, "r", encoding="utf-8") as in_file:
            with concurrent.futures.ThreadPoolExecutor() as executor:
                futures = {
                    executor.submit(add_record, engine, record): record
                    for record in itertools.islice(in_file, executor._max_workers)
                }

                while futures:
                    done, _ = concurrent.futures.wait(futures, return_when=concurrent.futures.FIRST_COMPLETED)
                    for f in done:
                        try:
                            result = f.result()
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
                            record = in_file.readline()
                            if record:
                                futures[executor.submit(add_record, engine, record)] = record

                            out_file.write(f"{result}\n")

                            success_recs += 1
                            if success_recs % 100 == 0:
                                print(f"Processed {success_recs:,} adds, with {error_recs:,} errors", flush=True)

                            if success_recs % 200 == 0:
                                engine_stats(engine)
                        finally:
                            del futures[f]

                print(f"\nSuccessfully loaded {success_recs:,} records, with" f" {error_recs:,} errors")
                print(f"\nWith info responses written to {output_file}")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    futures_add(sz_engine, INPUT_FILE, OUTPUT_FILE)
except SzError as err:
    mock_logger("CRITICAL", err)
