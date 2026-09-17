"""Require every planned fault case and compare original context before/after recovery."""
import json
from pathlib import Path
from device_snapshot import verify_saved, verify_context_unchanged
from run_crash_checks import STAGES, check_killed


def verify(root):
    counts = {}
    issues = []
    for gate in STAGES:
        counts[gate] = 0
        for number in range(1 if gate == "D0" else 20):
            folder = root / f"{gate}-{number+1:02}"
            result = json.loads((folder / "verification.json").read_text())
            intent = json.loads((folder / "intent.json").read_text())
            assert result["processRecoveryPassed"] and not result["physicalPowerLossPassed"]
            check_killed(result["killed"], gate, intent["captureId"])
            verify_saved(result["recovered"], intent["captureId"], 0 if gate == "D0" else 1)
            verify_context_unchanged(result["killed"], result["recovered"])
            counts[gate] += 1
            if result.get("harnessIssue"):
                issues.append({"case": folder.name, "issue": result["harnessIssue"]})
    report = {"completeProcessCampaignPassed": True, "physicalPowerLossPassed": False,
              "originalContextPreserved": True, "cases": counts, "harnessIssuesPreserved": issues}
    (root / "campaign-verification.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    import sys
    verify(Path(sys.argv[1]))
