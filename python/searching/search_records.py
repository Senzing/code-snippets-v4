#! /usr/bin/env python3

import json
import os
import sys
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

search_records = [
    {
        "NAME_FULL": "Susan Moony",
        "DATE_OF_BIRTH": "15/6/1998",
        "SSN_NUMBER": "521212123",
    },
    {
        "NAME_FIRST": "Robert",
        "NAME_LAST": "Smith",
        "ADDR_FULL": "123 Main Street Las Vegas NV 89132",
    },
    {
        "NAME_FIRST": "Makio",
        "NAME_LAST": "Yamanaka",
        "ADDR_FULL": "787 Rotary Drive Rotorville FL 78720",
    },
]


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def searcher(engine):
    for search_record in search_records:
        try:
            record_str = json.dumps(search_record)
            response = engine.search_by_attributes(record_str)
        except (SzBadInputError, json.JSONDecodeError) as err:
            mock_logger("ERROR", err, record_str)
        except SzRetryableError as err:
            mock_logger("WARN", err, record_str)
        except (SzUnrecoverableError, SzError) as err:
            mock_logger("CRITICAL", err, record_str)
            raise err

        print(f"\n------ Searched: {record_str}", flush=True)
        print(f"\n{response}", flush=True)


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    searcher(sz_engine)
except SzError as err:
    mock_logger("CRITICAL", err)
