"""
Fixtures for running each Python snippet as a subprocess against a throwaway SQLite repository.

Senzing install locations use the same environment variables as the Java SnippetRunner:
SENZING_PATH, SENZING_DIR, SENZING_CONFIG_DIR, SENZING_SUPPORT_DIR and SENZING_RESOURCE_DIR.
PYTHONPATH and LD_LIBRARY_PATH are inherited from the calling shell.
"""

import importlib.util
import json
import os
import shutil
import signal
import subprocess  # nosec B404
import sys
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import pytest

TESTS_DIR = Path(__file__).resolve().parent
SNIPPETS_DIR = TESTS_DIR.parent
REPO_ROOT = SNIPPETS_DIR.parent
DATA_DIR = REPO_ROOT / "resources" / "data"
SIGINT_GRACE_SECONDS = 30


@dataclass(frozen=True)
class Repo:
    """Template repository: data sources to register and data files to load."""

    sources: tuple[str, ...] = ()
    loads: tuple[str, ...] = ()


@dataclass(frozen=True)
class Snippet:
    """How to run a snippet; mirrors the Java runner's .properties files."""

    repo: Repo = Repo()
    stdin: str | None = None
    sigint_after: float | None = None
    timeout: float = 300
    skip: str | None = None
    returncode: int = 0
    expect_stderr: str | None = None


@dataclass
class Result:
    """Outcome of running a snippet."""

    returncode: int
    stdout: str
    stderr: str


def senzing_paths() -> dict[str, str]:
    senzing_path = Path(os.getenv("SENZING_PATH", "/opt/senzing"))
    install_dir = Path(os.getenv("SENZING_DIR", senzing_path / "er"))
    return {
        "CONFIGPATH": os.getenv("SENZING_CONFIG_DIR", "/etc/opt/senzing"),
        "RESOURCEPATH": os.getenv("SENZING_RESOURCE_DIR", str(install_dir / "resources")),
        "SUPPORTPATH": os.getenv("SENZING_SUPPORT_DIR", str(senzing_path / "data")),
    }


def settings_for(db_file: Path) -> str:
    return json.dumps({"PIPELINE": senzing_paths(), "SQL": {"CONNECTION": f"sqlite3://na:na@{db_file}"}})


def snippet_env(db_file: Path) -> dict[str, str]:
    return {**os.environ, "SENZING_ENGINE_CONFIGURATION_JSON": settings_for(db_file)}


@pytest.fixture(scope="session", autouse=True)
def senzing_available() -> None:
    if importlib.util.find_spec("senzing_core") is None:
        pytest.exit("senzing_core is not importable; set PYTHONPATH to the Senzing Python SDK", returncode=1)


@pytest.fixture(name="template_repo", scope="session")
def fixture_template_repo(tmp_path_factory: pytest.TempPathFactory) -> Callable[[Repo], Path]:
    """Build each distinct template repository once per session; returns its database file."""
    built: dict[Repo, Path] = {}

    def _template(repo: Repo) -> Path:
        if repo not in built:
            db_file = tmp_path_factory.mktemp("template") / "G2C.db"
            cmd = [sys.executable, str(TESTS_DIR / "build_repo.py"), str(db_file), senzing_paths()["RESOURCEPATH"]]
            cmd += [arg for source in repo.sources for arg in ("--source", source)]
            cmd += [arg for load in repo.loads for arg in ("--load", str(DATA_DIR / load))]
            result = subprocess.run(
                cmd, env=snippet_env(db_file), capture_output=True, encoding="utf-8", check=False
            )  # nosec B603
            assert result.returncode == 0, f"Failed to build template repository {repo}:\n{result.stderr}"
            built[repo] = db_file
        return built[repo]

    return _template


@pytest.fixture(name="run_snippet")
def fixture_run_snippet(template_repo: Callable[[Repo], Path], tmp_path: Path) -> Callable[[Path, Snippet], Result]:
    """Run a snippet from its own directory against a private copy of its template repository."""

    def _run(path: Path, snippet: Snippet) -> Result:
        db_file = tmp_path / "G2C.db"
        shutil.copy(template_repo(snippet.repo), db_file)
        # Bandit subprocess checks suppressed: only ever runs sys.executable on files in this repository
        with subprocess.Popen(  # nosec B603
            [sys.executable, path.name],
            cwd=path.parent,
            env=snippet_env(db_file),
            stdin=subprocess.DEVNULL if snippet.stdin is None else subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding="utf-8",
        ) as proc:
            try:
                if snippet.sigint_after is None:
                    stdout, stderr = proc.communicate(snippet.stdin, timeout=snippet.timeout)
                else:
                    stdout, stderr = interrupt_after(proc, snippet.sigint_after)
            except subprocess.TimeoutExpired:
                proc.kill()
                stdout, stderr = proc.communicate()
                pytest.fail(f"{path.name} timed out\nstdout:\n{stdout}\nstderr:\n{stderr}")
        return Result(proc.returncode, stdout, stderr)

    return _run


def interrupt_after(proc: subprocess.Popen[str], seconds: float) -> tuple[str, str]:
    """Let a long-running snippet work for a while, then ctrl-c it like a user would."""
    try:
        return proc.communicate(timeout=seconds)
    except subprocess.TimeoutExpired:
        proc.send_signal(signal.SIGINT)
        return proc.communicate(timeout=SIGINT_GRACE_SECONDS)
