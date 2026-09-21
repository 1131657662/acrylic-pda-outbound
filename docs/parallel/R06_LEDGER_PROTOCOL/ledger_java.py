"""Offline direct-source compiler; refuses to execute before a fixed production manifest."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from ledger_primitives import ROOT
from ledger_sources import validate_sources

DEPENDENCIES = (
    ("com.fasterxml.jackson.core", "jackson-core", "2.21.4"),
    ("com.fasterxml.jackson.core", "jackson-databind", "2.21.4"),
    ("com.fasterxml.jackson.core", "jackson-annotations", "2.21"),
    ("org.springframework", "spring-context", "6.2.19"),
    ("org.springframework", "spring-beans", "6.2.19"),
    ("org.springframework", "spring-core", "6.2.19"),
)


def dependencies():
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    cache = cache / "caches/modules-2/files-2.1"
    jars = []
    for group, artifact, version in DEPENDENCIES:
        matches = list((cache / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
        if len(matches) != 1:
            raise ValueError(f"Required cached dependency: {artifact}:{version}")
        jars.append(str(matches[0]))
    return os.pathsep.join(jars)


class LedgerDriver:
    def __init__(self):
        self.repository = Path(os.environ.get("LEDGER_SOURCE_ROOT", str(ROOT.parents[2])))
        self.sources = validate_sources(self.repository)
        self.jdk = Path(os.environ["JAVA_HOME"])
        cached_dependencies = dependencies()
        self.directory = tempfile.TemporaryDirectory(prefix="acrylic-ledger-consumer-")
        self.output = Path(self.directory.name)
        self.classpath = str(self.output) + os.pathsep + cached_dependencies

    def compile(self):
        adapters = [ROOT / name for name in (
            "LedgerProtocolDriver.java", "LedgerFixtureSigner.java", "LedgerWireOutput.java")]
        command = [str(self.jdk / "bin/javac"), "-encoding", "UTF-8", "-cp", self.classpath,
                   "-d", str(self.output), *map(str, self.sources + adapters)]
        result = subprocess.run(command, capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))

    def invoke(self, payload, mode="generate"):
        path = self.output / "input.json"
        path.write_text(json.dumps(payload, ensure_ascii=True), encoding="utf-8")
        command = [str(self.jdk / "bin/java"), "-cp", self.classpath,
                   "LedgerProtocolDriver", mode, str(path)]
        result = subprocess.run(command, capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        return json.loads(result.stdout.decode("utf-8"))

    def close(self):
        self.directory.cleanup()
