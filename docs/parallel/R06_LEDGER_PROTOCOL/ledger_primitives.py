"""Reuse the independently reviewed byte contract without importing production code."""
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PRIMITIVE_PATH = ROOT.parent / "R06_FOUNDATION_PROTOCOL" / "canonical.py"
SPEC = importlib.util.spec_from_file_location("foundation_canonical", PRIMITIVE_PATH)
PRIMITIVES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PRIMITIVES)
canonical = PRIMITIVES.canonical
digest = PRIMITIVES.digest
parse = PRIMITIVES.parse
raw_digest = PRIMITIVES.raw_digest


def require(valid, code):
    if not valid:
        raise ValueError(code)


def identifier(number):
    return f"60000000-0000-4000-8000-{number:012d}"
