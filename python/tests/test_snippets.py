"""
Run every Python snippet end to end and check it exits cleanly.

Snippets are discovered automatically; SNIPPETS only lists those needing something other than an
empty repository with the default configuration (Java equivalent: java/runner/resources/*.properties).
"""

from collections.abc import Callable
from pathlib import Path

import pytest
from conftest import SNIPPETS_DIR, TESTS_DIR, Repo, Result, Snippet

TRUTHSET_SOURCES = ("CUSTOMERS", "REFERENCE", "WATCHLIST")
TRUTHSET_FILES = ("truthset/customers.jsonl", "truthset/reference.jsonl", "truthset/watchlist.jsonl")

LOAD_500 = Repo(loads=("load-500.jsonl",))
TRUTHSET = Repo(sources=TRUTHSET_SOURCES, loads=TRUTHSET_FILES)
TRUTHSET_SOURCES_ONLY = Repo(sources=TRUTHSET_SOURCES)
CONFIRM_PURGE = "YESPURGESENZING\n"

SNIPPETS = {
    "configuration/init_default_config.py": Snippet(stdin="y\n"),
    "deleting/delete_futures.py": Snippet(repo=LOAD_500),
    "deleting/delete_loop.py": Snippet(repo=LOAD_500),
    "deleting/delete_with_info_futures.py": Snippet(repo=LOAD_500),
    "initialization/abstract_factory_parameters.py": Snippet(
        skip="Hardcodes its own settings (sqlite3://na:na@/tmp/sqlite/G2C.db) so can't use a test repository"
    ),
    # Uses a made-up config ID to demonstrate the error
    "initialization/abstract_factory_with_config_id.py": Snippet(returncode=1, expect_stderr="SENZ7221"),
    # Verbose logging goes to stderr
    "initialization/abstract_factory_with_debug.py": Snippet(expect_stderr="INFO:"),
    "initialization/purge_repository.py": Snippet(repo=LOAD_500, stdin=CONFIRM_PURGE),
    "initialization/signal_handler.py": Snippet(sigint_after=5),
    "loading/add_truthset_loop.py": Snippet(repo=TRUTHSET_SOURCES_ONLY),
    # load-500-with-errors.jsonl contains a record for an unregistered data source and a malformed line
    "loading/add_with_info_futures.py": Snippet(expect_stderr="SENZ2207"),
    "redo/add_with_redo.py": Snippet(repo=TRUTHSET_SOURCES_ONLY),
    "redo/redo_continuous.py": Snippet(repo=TRUTHSET, sigint_after=30),
    "redo/redo_continuous_futures.py": Snippet(repo=TRUTHSET, sigint_after=30),
    "redo/redo_with_info_continuous.py": Snippet(repo=TRUTHSET, sigint_after=30),
    "searching/search_futures.py": Snippet(repo=LOAD_500),
    "searching/search_records.py": Snippet(repo=TRUTHSET),
    "stewardship/force_resolve.py": Snippet(stdin=CONFIRM_PURGE),
    "stewardship/force_unresolve.py": Snippet(stdin=CONFIRM_PURGE),
}

ALL_SNIPPETS = sorted(
    path.relative_to(SNIPPETS_DIR).as_posix()
    for path in SNIPPETS_DIR.rglob("*.py")
    if TESTS_DIR not in path.parents and ".mypy_cache" not in path.parts
)


def test_snippet_table_is_current() -> None:
    assert not set(SNIPPETS) - set(ALL_SNIPPETS), "SNIPPETS lists snippets that no longer exist"


@pytest.mark.parametrize("name", ALL_SNIPPETS)
def test_snippet(name: str, run_snippet: Callable[[Path, Snippet], Result]) -> None:
    snippet = SNIPPETS.get(name, Snippet())
    if snippet.skip:
        pytest.skip(snippet.skip)

    result = run_snippet(SNIPPETS_DIR / name, snippet)

    output = f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
    if not result.killed:
        assert result.returncode == snippet.returncode, output
    # Per-record errors are logged to stderr without changing the exit code, so stderr must be clean
    # unless the snippet is expected to report errors
    if snippet.expect_stderr:
        assert snippet.expect_stderr in result.stderr, output
        assert "Traceback" not in result.stderr, output
    else:
        assert not result.stderr.strip(), output
