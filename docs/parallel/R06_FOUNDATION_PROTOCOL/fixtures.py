"""Synthetic fixtures for the fifth-wave production primitive consumer."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def load(name):
    return json.loads((ROOT / name).read_text(encoding="utf-8"))


def identifier(number):
    return "00000000-0000-4000-8000-%012d" % number


def command(kind="NORMAL_CONFIRM"):
    value = load("command_vectors.json")["normalCommand"]
    value["commandType"] = kind
    if kind != "NORMAL_CONFIRM":
        value["reason"] = "合成测试理由"
    return value


def replacement(value, path, changed):
    result = copy.deepcopy(value)
    parts = path.split(".")
    target = result
    for part in parts[:-1]:
        target = target[part]
    target[parts[-1]] = changed
    return result


def omission(value, path):
    result = copy.deepcopy(value)
    parts = path.split(".")
    target = result
    for part in parts[:-1]:
        target = target[part]
    del target[parts[-1]]
    return result
