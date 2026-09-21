"""Explicit public-vector refresh; never invoked by ordinary tests."""
import argparse
import json
import subprocess
import tempfile
from pathlib import Path
from ledger_cases import negative_cases
from ledger_java import LedgerDriver
from ledger_primitives import ROOT, canonical, digest, require
from receipt_consumer import verify_response
from receipt_expectations import expected_receipt
from ledger_vectors import trusted
from trusted_facts import fixtures


def node_expected(samples):
    payload = [{"id": sample["id"], "unsigned": expected_receipt(sample)} for sample in samples]
    with tempfile.TemporaryDirectory(prefix="acrylic-ledger-node-") as directory:
        path = Path(directory) / "input.json"
        path.write_text(json.dumps(payload, ensure_ascii=True), encoding="utf-8")
        result = subprocess.run(["node", str(ROOT / "hash_oracle.js"), str(path)],
                                capture_output=True, check=True, timeout=30)
    return json.loads(result.stdout.decode("utf-8"))


def check_positive(output, samples, oracle):
    require(len(output["codec"]) == len(samples) == len(oracle), "VECTOR_COUNT")
    for row, facts, expected in zip(output["codec"], samples, oracle):
        unsigned = expected_receipt(facts)
        require(row["id"] == facts["id"] == expected["id"], "VECTOR_ID")
        require(row["unsignedReceiptUtf8"].encode() == canonical(unsigned), "UNSIGNED_BYTES")
        require(row["unsignedReceiptUtf8"] == expected["unsignedReceiptUtf8"], "NODE_BYTES")
        require(row["unsignedReceiptSha256"] == expected["unsignedReceiptSha256"], "NODE_HASH")
        require(row["unsignedReceiptSha256"] == digest(unsigned, "receipt"), "PYTHON_HASH")
        verify_response(row["originalResponseUtf8"].encode(), trusted(row, facts, output["publicKey"]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--freeze-fixed-production-vectors", action="store_true", required=True)
    parser.parse_args()
    samples = fixtures()
    negatives = negative_cases()
    driver = LedgerDriver()
    try:
        driver.compile()
        output = driver.invoke({"codec": samples, "forged": negatives})
    finally:
        driver.close()
    oracle = node_expected(samples)
    check_positive(output, samples, oracle)
    for name, value in (("signed_vectors.json", output), ("wire_vectors.json", oracle)):
        (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"codec": len(samples), "forged": len(negatives), "node": len(oracle)}))


if __name__ == "__main__":
    main()
