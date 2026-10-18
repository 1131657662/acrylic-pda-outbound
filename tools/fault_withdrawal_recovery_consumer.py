"""Consume preserved five-checkpoint recovery evidence without database/network access."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from fault_recovery_accounting import check_business
from fault_recovery_evidence import (check_facts, check_files, digest, local_file, manifest,
                                     read_json, require, table_rows, verify_receipt)


CHECKPOINTS = ("before-withdrawal", "after-withdrawal", "after-republish", "before-success", "after-success")
BASE_INPUTS = ("final-facts.json", "private/scenario.json", "private-signing/server-public.der",
               "original-binlog.bin", "original-binlog.sha256", "connected-identity.tsv",
               "shutdown-identity.tsv", "test-completed.marker", "integration-result.xml")


def checkpoint_names(source: Path, name: str) -> list[str]:
    prefix = "checkpoints/" + name
    names = [prefix + "/" + file for file in ("before.json", "rebuilt.json", "http-result.json",
             "metadata.json", "database.sql", "config/server-public.der")]
    before = read_json(local_file(source, prefix + "/before.json"))
    final = read_json(local_file(source, "final-facts.json"))
    names.extend(check_files((source, prefix + "/storage"), before["files"]))
    names.extend(check_files((source, prefix + "/final-storage"), final["files"]))
    return names


def input_names(source: Path) -> list[str]:
    names = list(BASE_INPUTS)
    final = read_json(local_file(source, "final-facts.json"))
    names.extend(check_files((source, "final-storage"), final["files"]))
    for name in CHECKPOINTS:
        names.extend(checkpoint_names(source, name))
    return names


def check_execution(source: Path) -> None:
    identity = local_file(source, "connected-identity.tsv").read_bytes().strip()
    require(bool(identity) and identity == local_file(source, "shutdown-identity.tsv").read_bytes().strip(),
            "execution.shutdown_identity")
    require(bool(local_file(source, "test-completed.marker").read_bytes().strip()), "execution.completed")
    suite = ET.fromstring(local_file(source, "integration-result.xml").read_bytes())
    require(suite.tag == "testsuite" and suite.get("tests") == "1", "execution.test_count")
    require(all(suite.get(k) == "0" for k in ("failures", "errors", "skipped")), "execution.test_result")
    require(digest(local_file(source, "original-binlog.bin")) ==
            local_file(source, "original-binlog.sha256").read_text(encoding="ascii").strip(), "execution.binlog_pin")


def check_before(before: dict, index: int) -> dict:
    counts = check_facts(before)
    assignments = table_rows(before, "order_assignment")
    statuses = [row["status"] for row in assignments]
    expected = ([b"ACTIVE"], [b"REVOKED"], [b"REVOKED"], [b"REVOKED", b"ACTIVE"], [b"REVOKED", b"CLOSED"])
    require(sorted(statuses) == sorted(expected[index]), "checkpoint.before_assignments")
    require(len(table_rows(before, "submission")) == (1 if index == 4 else 0), "checkpoint.before_submission")
    require(len(table_rows(before, "order_publication")) == (1 if index < 2 else 2), "checkpoint.before_publication")
    return counts


def check_http(http: dict, final: dict, scenario: dict) -> None:
    for key in ("oldSubmissionStatus", "oldPlanStatus", "changedWithdrawalStatus"):
        require(http[key] == 409, "http." + key)
    require(http["sameReceipt"] is True and http["successReplay"] is True, "http.replay")
    require(http["withdrawalReceipt"] == scenario["withdrawalReceipt"], "http.withdrawal_receipt")
    require(http["success"] == scenario["success"], "http.success_receipt")
    tables = ("dispatch_record", "inventory_ledger", "bale_commitment")
    require(http["accounting"] == {name: final["tables"][name] for name in tables}, "http.accounting")


def check_checkpoint(options: tuple[Path, str], final: dict, scenario: dict) -> dict:
    source, name = options
    prefix = "checkpoints/" + name
    before = read_json(local_file(source, prefix + "/before.json"))
    rebuilt = read_json(local_file(source, prefix + "/rebuilt.json"))
    require(rebuilt == final, "checkpoint.exact_rebuild")
    check_http(read_json(local_file(source, prefix + "/http-result.json")), final, scenario)
    metadata = read_json(local_file(source, prefix + "/metadata.json"))
    require(digest(local_file(source, prefix + "/database.sql")) == metadata["dumpSha256"], "checkpoint.dump_pin")
    public_key = local_file(source, "private-signing/server-public.der").read_bytes()
    require(local_file(source, prefix + "/config/server-public.der").read_bytes() == public_key, "checkpoint.key")
    return {"name": name, "before": check_before(before, CHECKPOINTS.index(name)),
            "binlogFile": metadata["binlogFile"], "position": metadata["position"], "exactRebuild": True}


def check_payload(source: Path, final: dict, scenario: dict) -> None:
    row = table_rows(final, "submission_payload")[0]
    key = row["file_key"].decode("utf-8")
    path = local_file(source / "final-storage", key)
    require(key in final["files"], "payload.manifest_entry")
    require(digest(path) == scenario["success"]["payloadSha256"], "payload.success_hash")
    require(path.stat().st_size == int(row["byte_count"]), "payload.byte_count")
    require(read_json(path) == scenario["submission"], "payload.original_body")


def validate(source: Path) -> dict:
    check_execution(source)
    final = read_json(local_file(source, "final-facts.json"))
    scenario = read_json(local_file(source, "private/scenario.json"))
    counts = check_facts(final)
    check_business(final, scenario)
    check_payload(source, final, scenario)
    key = local_file(source, "private-signing/server-public.der").read_bytes()
    verify_receipt(scenario["withdrawalReceipt"], key, "acrylic-pda-fault-withdrawal")
    verify_receipt(scenario["success"], key, "acrylic-pda-receipt")
    checkpoints = [check_checkpoint((source, name), final, scenario) for name in CHECKPOINTS]
    positions = [row["position"] for row in checkpoints]
    require(all(isinstance(p, int) and p > 0 for p in positions), "checkpoint.position_type")
    require(positions == sorted(set(positions)), "checkpoint.positions")
    require(len({row["binlogFile"] for row in checkpoints}) == 1, "checkpoint.binlog_file")
    return {"facts": counts, "checkpoints": checkpoints, "accountingOnce": True,
            "labSignaturesVerified": 2, "originalIdentityOrCustodyProven": False}


def consume(options: tuple[Path, Path], output: Path) -> dict:
    source, bindings = (p.resolve() for p in options)
    output = output.resolve()
    require(not output.is_relative_to(source) and not source.is_relative_to(output), "output.overlaps_source")
    require(not output.exists(), "output.exists")
    names = input_names(source)
    before = manifest(source, names)
    require(before == read_json(bindings), "input.bindings")
    result = validate(source)
    after = manifest(source, names)
    require(before == after, "input.changed")
    result.update({"passed": True, "readOnly": True, "inputUnchanged": True, "inputManifest": before,
                   "bindingsSha256": digest(bindings), "consumerSha256": digest(Path(__file__))})
    output.mkdir(parents=True)
    (output / "consumer-result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--bindings", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = consume((args.source, args.bindings), args.output)
    except Exception as error:
        print(json.dumps({"passed": False, "errorType": type(error).__name__}))
        return 1
    print(json.dumps({"passed": True, "checkpoints": len(result["checkpoints"]), "facts": result["facts"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
