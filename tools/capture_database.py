"""Stop only this Demo; preserve DB/WAL/SHM, preferences and private files including key profiles."""
import argparse
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("output", type=Path)
    parser.add_argument("--adb", required=True, type=Path)
    args = parser.parse_args()
    prefix = [str(args.adb), "-s", args.serial]
    package = "com.acrylic.m71demo.debug"
    subprocess.run(prefix + ["shell", "am", "force-stop", package], check=True)
    members = ["databases", "shared_prefs"]
    check_files = subprocess.run(prefix + ["shell", "run-as", package, "sh", "-c", "'test -d files'"])
    if check_files.returncode not in (0, 1):
        raise RuntimeError("Could not inspect private files; stop before creating an incomplete backup.")
    if check_files.returncode == 0:
        members.append("files")
    with args.output.open("xb") as target:
        subprocess.run(prefix + ["exec-out", "run-as", package, "tar", "-cf", "-", *members],
                       stdout=target, check=True)
    print("Captured stopped Demo database including WAL/SHM. This is not a hard-power-loss test.")


if __name__ == "__main__":
    main()
