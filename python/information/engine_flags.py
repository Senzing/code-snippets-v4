#! /usr/bin/env python3

import os
from pathlib import Path

# TODO class method?ƒ
from senzing import SzEngineFlags, SzError

# from senzing import combine_flags2
# from senzing import combine_flags2 as senzing_combine_flags2
from senzing_core import SzAbstractFactoryCore

# TODO
# import senzing


SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INSTANCE_NAME = Path(__file__).stem

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_product = sz_factory.create_engine()

    COMBINED_WITH_ENUM = SzEngineFlags.combine_flags(
        [
            SzEngineFlags.SZ_ENTITY_CORE_FLAGS,
            SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS,
            SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_ENTITY_NAME,
            SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_RECORD_SUMMARY,
            SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO,
            SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_FEATURES,
        ]
    )

    COMBINED_WITH_STR = SzEngineFlags.combine_flags(
        # [
        "SZ_ENTITY_CORE_FLAGS",
        "SZ_ENTITY_INCLUDE_ALL_RELATIONS",
        "SZ_ENTITY_INCLUDE_RELATED_ENTITY_NAME",
        "SZ_ENTITY_INCLUDE_RELATED_RECORD_SUMMARY",
        "SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO",
        "SZ_ENTITY_INCLUDE_ALL_FEATURES",
        # ]
    )

    print(f"\n{SzEngineFlags.SZ_ENTITY_DEFAULT_FLAGS.value = }", flush=True)
    print(f"\n{COMBINED_WITH_ENUM.value = }")
    print(f"\n{COMBINED_WITH_STR.value = }")

except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")


# COMBINED_WITH_NEW = senzing.combine_flags2(
#     SzEngineFlags.SZ_ENTITY_CORE_FLAGS,
#     SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS,
#     SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_ENTITY_NAME,
#     SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_RECORD_SUMMARY,
#     SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO,
#     SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_FEATURES,
# )

# print(f"\n{COMBINED_WITH_NEW = }", flush=True)
# print(f"\n{COMBINED_WITH_NEW.value = }", flush=True)
