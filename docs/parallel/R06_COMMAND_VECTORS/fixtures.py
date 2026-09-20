"""Read immutable synthetic fixtures; each test receives its own decoded objects."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def load(name):
    return json.loads((ROOT / name).read_text(encoding='utf-8'))


def command():
    return load('command_vectors.json')['command']


def context():
    return load('command_vectors.json')['trustedContext']


def receipt():
    return load('receipt_vectors.json')['originalCommandReceipt']


def replace_path(value, path, replacement):
    parts = path.split('.')
    parent = value
    for key in parts[:-1]:
        parent = parent[key]
    parent[parts[-1]] = replacement
    return value
