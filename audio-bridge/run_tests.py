"""Run local bridge tests with optional project-local PyAV wheels; no network or cloud credentials."""
import sys
from pathlib import Path

directory = Path(__file__).resolve().parent
sys.path.insert(0, str(directory / ".test-deps"))
import pytest

raise SystemExit(pytest.main(["-q", "-p", "no:cacheprovider", str(directory / "tests")]))
