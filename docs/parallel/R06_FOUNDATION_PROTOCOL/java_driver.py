"""Compile the actual selected production primitive sources, with no Gradle/network."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from fixtures import ROOT

BASE_SOURCES = (
    "common/BusinessException.java", "common/JsonText.java", "devices/DeviceCrypto.java",
    "inventory/ImportFiles.java", "pda/OfflineSigner.java", "submissions/SubmissionJson.java",
)
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
            raise ValueError(f"Required offline dependency: {artifact}:{version}")
        jars.append(str(matches[0]))
    return os.pathsep.join(jars)


class ProductionDriver:
    def __init__(self):
        self.directory = tempfile.TemporaryDirectory(prefix="acrylic-foundation-consumer-")
        self.output = Path(self.directory.name)
        self.jdk = Path(os.environ["JAVA_HOME"])
        self.repository = Path(os.environ.get("FOUNDATION_SOURCE_ROOT", str(ROOT.parents[2])))
        self.classpath = str(self.output) + os.pathsep + dependencies()

    def compile(self):
        root = self.repository / "server/src/main/java/com/acrylic/outbound"
        sources = [root / name for name in BASE_SOURCES]
        sources += sorted((root / "temporary/protocol").glob("*.java"))
        if not (root / "temporary/protocol/TemporaryProtocol.java").is_file():
            raise ValueError("Production TemporaryProtocol source is required")
        sources += [ROOT / "FoundationProtocolDriver.java", ROOT / "SyntheticReceiptDriver.java"]
        command = [str(self.jdk / "bin/javac"), "-encoding", "UTF-8", "-cp", self.classpath,
                   "-d", str(self.output), *map(str, sources)]
        result = subprocess.run(command, capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))

    def invoke(self, samples, mode="primitive"):
        path = self.output / "input.json"
        path.write_text(json.dumps(samples, ensure_ascii=True), encoding="utf-8")
        command = [str(self.jdk / "bin/java"), "-cp", self.classpath,
                   "FoundationProtocolDriver", mode, str(path)]
        result = subprocess.run(command, capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        return json.loads(result.stdout.decode("utf-8"))

    def close(self):
        self.directory.cleanup()
