#! /usr/bin/env python3

import os
import signal
import sys
import time
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
OUTPUT_FILE = Path("../../resources/output/redo_with_info_continuous.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem


def signal_handler(signum, frame):
    print(f"\nWith info responses written to {OUTPUT_FILE}")
    sys.exit()


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def redo_pause(success):
    print("No redo records to process, pausing for 30 seconds. Total processed:" f" {success:,} (CTRL-C to exit)...")
    time.sleep(30)


def process_redo(engine, output_file):
    success_recs = 0
    error_recs = 0

    with open(output_file, "w", encoding="utf-8") as out_file:
        try:
            while 1:
                redo_record = engine.get_redo_record()

                if not redo_record:
                    redo_pause(success_recs)
                    continue

                response = engine.process_redo_record(redo_record, flags=SzEngineFlags.SZ_WITH_INFO)
                success_recs += 1
                out_file.write(f"{response}\n")

                if success_recs % 100 == 0:
                    print(f"Processed {success_recs:,} redo records, with" f" {error_recs:,} errors")
        except SzBadInputError as err:
            mock_logger("ERROR", err, redo_record)
            error_recs += 1
        except SzRetryableError as err:
            mock_logger("WARN", err, redo_record)
            error_recs += 1
        except (SzUnrecoverableError, SzError) as err:
            mock_logger("CRITICAL", err, redo_record)
            raise err


signal.signal(signal.SIGINT, signal_handler)

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    process_redo(sz_engine, OUTPUT_FILE)
except SzError as err:
    mock_logger("CRITICAL", err)
