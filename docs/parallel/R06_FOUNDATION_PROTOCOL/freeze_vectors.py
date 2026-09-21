"""Explicit maintainer-only fixture generation; normal tests never rewrite fixtures."""
import argparse
import json
import subprocess
from canonical import canonical
from fixtures import ROOT, command
from java_driver import ProductionDriver
from scenarios import evidence_command
from signing_cases import receipt_fixture, signing_samples


def control_fixture():
    body = command()
    selected = ("warehouseId", "source", "submissionId", "workRound", "authorizationId",
                "freezeDeviceId", "sourceRecoveryEpoch")
    return dict(controlVersion=1, **{key: body[key] for key in selected},
                assignmentId=None, continuationId=None)


def hash_inputs():
    normal = command()
    nullable = command()
    nullable["expected"].update(canonicalOrderId=None, mappingVersion="0", slotVersion=None,
                                targetRevision=None, receivedPayloadSha256=None)
    values = [("normal-empty-reason", "command", normal), ("nullable", "command", nullable),
              ("physical", "command", evidence_command("PHYSICAL_DISPOSITION")),
              ("reopen", "command", evidence_command("REOPEN_WORK")),
              ("receipt-synthetic", "receipt", receipt_fixture()),
              ("control-derived", "control", control_fixture())]
    return [{"id": name, "domain": domain, "canonical": canonical(value).decode("utf-8")}
            for name, domain, value in values]


def node_hashes(rows):
    script = """const fs=require('node:fs'),crypto=require('node:crypto');
const rows=JSON.parse(fs.readFileSync(0,'utf8'));
const kinds={command:'COMMAND',receipt:'RECEIPT',control:'DERIVED-CONTROL'};
for (const r of rows) {
 r.sha256=crypto.createHash('sha256').update('ACRYLIC-R06/'+kinds[r.domain]+'/1\\n','utf8')
 .update(r.canonical,'utf8').digest('hex');
}
process.stdout.write(JSON.stringify(rows));"""
    result = subprocess.run(["node", "-e", script], input=json.dumps(rows).encode(),
                            capture_output=True, check=True, timeout=30)
    return json.loads(result.stdout)


def write(name, value):
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--regenerate-synthetic-fixtures", action="store_true", required=True)
    parser.parse_args()
    driver = ProductionDriver()
    try:
        driver.compile()
        signed = driver.invoke(signing_samples(), "sign")
    finally:
        driver.close()
    write("hash_vectors.json", {"syntheticOnly": True, "vectors": node_hashes(hash_inputs())})
    write("signed_vectors.json", signed)
    print("Generated public-only synthetic fixtures; independent review is required.")


if __name__ == "__main__":
    main()
