"""Run the existing artifact runtime only after package validation, before publishing the directory."""
import os
from pathlib import Path
import subprocess


def convert(directory: Path):
    node = os.environ.get("PDA_RESCUE_NODE", "node")
    worker = Path(__file__).with_name("xlsx.mjs")
    try:
        completed = subprocess.run([node, "--max-old-space-size=1536", str(worker), str(directory / "verified.json"), str(directory / "rescue.xlsx")],
                                   capture_output=True, check=False, timeout=600)
    except subprocess.TimeoutExpired as error:
        (directory / "conversion.log").write_bytes((error.stdout or b"") + (error.stderr or b""))
        raise ValueError("Excel conversion exceeded resource limit; partial evidence retained") from error
    (directory / "conversion.log").write_bytes(completed.stdout + completed.stderr)
    if completed.returncode:
        raise ValueError("Excel conversion failed; no complete directory published")
    from .xlsx_text import repair_and_verify
    from .render import render
    repair_and_verify(directory / "rescue.xlsx")
    render(directory / "rescue.xlsx")
