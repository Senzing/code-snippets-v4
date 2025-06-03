#! /usr/bin/env python3

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


def process_redo(engine):
    success_recs = 0
    error_recs = 0

    while True:
        try:
            if not (response := engine.get_redo_record()):
                print(
                    "No redo records to process, pausing for 30 seconds. Total"
                    f" processed {success_recs:,} . (CTRL-C to exit)..."
                )
                time.sleep(30)
                continue

            engine.process_redo_record(response)

            success_recs += 1
            if success_recs % 100 == 0:
                print(f"Processed {success_recs:,} redo records, with" f" {error_recs:,} errors")
        except SzBadInputError as err:
            mock_logger("ERROR", err)
            error_recs += 1
        except SzRetryableError as err:
            mock_logger("WARN", err)
            error_recs += 1
        except (SzUnrecoverableError, SzError) as err:
            mock_logger("CRITICAL", err)
            raise err


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    process_redo(sz_engine)
except SzError as err:
    mock_logger("CRITICAL", err)
