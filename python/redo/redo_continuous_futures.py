#! /usr/bin/env python3

import concurrent.futures
import os
import sys
import time
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def get_redo_record(engine):
    try:
        return engine.get_redo_record()
    except SzError as err:
        mock_logger("CRITICAL", err)
        raise err


def prime_redo_records(engine, quantity):
    redo_records = []
    for _ in range(quantity):
        if redo_record := get_redo_record(engine):
            redo_records.append(redo_record)
    return redo_records


def process_redo_record(engine, redo_record):
    engine.process_redo_record(redo_record)


def redo_count(engine):
    try:
        return engine.count_redo_records()
    except SzRetryableError as err:
        mock_logger("WARN", err)
    except SzError as err:
        mock_logger("CRITICAL", err)
        raise err


def redo_pause(success):
    print("No redo records to process, pausing for 30 seconds. Total processed:" f" {success:,} (CTRL-C to exit)...")
    time.sleep(30)


def futures_redo(engine):
    success_recs = 0
    error_recs = 0
    redo_paused = False

    with concurrent.futures.ThreadPoolExecutor() as executor:
        while True:
            futures = {
                executor.submit(process_redo_record, engine, record): record
                for record in prime_redo_records(engine, executor._max_workers)
            }
            if not futures:
                redo_pause(success_recs)
            else:
                break

        while True:
            done, _ = concurrent.futures.wait(futures, return_when=concurrent.futures.FIRST_COMPLETED)
            for f in done:
                try:
                    _ = f.result()
                except SzBadInputError as err:
                    mock_logger("ERROR", err, futures[f])
                    error_recs += 1
                except SzRetryableError as err:
                    mock_logger("WARN", err, futures[f])
                    error_recs += 1
                except (SzUnrecoverableError, SzError) as err:
                    mock_logger("CRITICAL", err, futures[f])
                    raise err
                else:
                    success_recs += 1
                    if success_recs % 100 == 0:
                        print(f"Processed {success_recs:,} redo records, with" f" {error_recs:,} errors")
                finally:
                    if record := get_redo_record(engine):
                        futures[executor.submit(process_redo_record, engine, record)] = record
                    else:
                        redo_paused = True

                    del futures[f]

            if redo_paused:
                while not redo_count(engine):
                    redo_pause(success_recs)
                redo_paused = False
                while len(futures) < executor._max_workers:
                    if record := get_redo_record(engine):
                        futures[executor.submit(process_redo_record, engine, record)] = record


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    futures_redo(sz_engine)
except SzError as err:
    mock_logger("CRITICAL", err)
