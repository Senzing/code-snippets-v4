#! /usr/bin/env python3
"""
Build a Senzing SQLite repository for the snippet tests.

Run as a subprocess by conftest.py so the pytest process never initializes Senzing:

    build_repo.py <db_file> <resource_dir> [--source CODE ...] [--load FILE ...]

SENZING_ENGINE_CONFIGURATION_JSON must point at <db_file>.
"""

import argparse
import json
import os
import sqlite3
import sys
from pathlib import Path

from senzing import SzAbstractFactory, SzError
from senzing_core import SzAbstractFactoryCore


def create_schema(db_file: str, resource_dir: str) -> None:
    schema = Path(resource_dir, "schema", "szcore-schema-sqlite-create.sql").read_text(encoding="utf-8")
    with sqlite3.connect(db_file) as conn:
        conn.executescript(schema)


def configure(sz_factory: SzAbstractFactory, sources: list[str]) -> None:
    sz_configmanager = sz_factory.create_configmanager()
    sz_config = sz_configmanager.create_config_from_template()
    for source in sources:
        sz_config.register_data_source(source)
    sz_configmanager.set_default_config(sz_config.export(), "Snippet tests template")


def load(sz_factory: SzAbstractFactory, files: list[str]) -> None:
    sz_engine = sz_factory.create_engine()
    for file in files:
        with open(file, "r", encoding="utf-8") as in_file:
            for line in in_file:
                if not (line := line.strip()) or line.startswith("#"):
                    continue
                record = json.loads(line)
                sz_engine.add_record(record.get("DATA_SOURCE", "TEST"), record["RECORD_ID"], line)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("db_file")
    parser.add_argument("resource_dir")
    parser.add_argument("--source", action="append", default=[])
    parser.add_argument("--load", action="append", default=[])
    args = parser.parse_args()

    create_schema(args.db_file, args.resource_dir)
    try:
        sz_factory = SzAbstractFactoryCore("build_repo", os.environ["SENZING_ENGINE_CONFIGURATION_JSON"])
        configure(sz_factory, args.source)
        load(sz_factory, args.load)
    except SzError as err:
        print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
