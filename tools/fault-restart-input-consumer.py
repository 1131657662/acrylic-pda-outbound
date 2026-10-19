"""Read-only Python consumer for the isolated fault-restart input contract."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    return parser.parse_args()


def read_contract(path: Path) -> tuple[dict, str]:
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest().upper()
    value = json.loads(data)
    if value.get("purpose") != "SYNTHETIC_FAULT_RESTART_CONSUMERS":
        raise ValueError("unexpected fixture purpose")
    for name in ("web", "storage", "android"):
        if name not in value:
            raise ValueError(f"missing consumer: {name}")
    return value, digest


def summary(value: dict, digest: str) -> dict:
    android = value["android"]
    old = android["oldBody"]["scans"]
    revocation = android.get("revocation", {})
    return {
        "purpose": value["purpose"],
        "inputSha256": digest,
        "consumerCount": 3,
        "scanCount": len(old),
        "oldAssignmentId": android["oldAssignment"]["id"],
        "oldDeviceId": android["oldAssignment"]["deviceId"],
        "revocationOrderId": revocation.get("orderId"),
        "readOnly": True,
    }


def main() -> None:
    args = parse_args()
    value, digest = read_contract(args.input)
    args.output.write_text(json.dumps(summary(value, digest), ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    if hashlib.sha256(args.input.read_bytes()).hexdigest().upper() != digest:
        raise RuntimeError("input changed during read")


if __name__ == "__main__":
    main()
