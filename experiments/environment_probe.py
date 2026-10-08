"""Report S02 prerequisites without installing or modifying the environment."""
from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys


def command_version(command: list[str]) -> str:
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=5)
        return (result.stdout + result.stderr).strip().splitlines()[0]
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        return f"unavailable:{type(exc).__name__}"


def main() -> None:
    print({
        "python": sys.version.split()[0],
        "java": command_version(["java", "-version"]),
        "javac": command_version(["javac", "-version"]),
        "maven": command_version(["mvn", "-version"]),
        "maven_wrapper": "experiments/java-compatibility/mvnw.cmd",
        "pypdf": bool(importlib.util.find_spec("pypdf")),
        "python_docx": bool(importlib.util.find_spec("docx")),
        "project_backend": "not_created_by_S02_scope",
    })


if __name__ == "__main__":
    main()
