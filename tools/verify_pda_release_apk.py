"""Check APK metadata and its Android signing certificate before first-install publication."""
import re
import subprocess
from pathlib import Path


def inspect_apk(apk: Path, build_tools: Path) -> dict:
    aapt = build_tools / ("aapt.exe" if (build_tools / "aapt.exe").exists() else "aapt")
    signer = build_tools / ("apksigner.bat" if (build_tools / "apksigner.bat").exists() else "apksigner")
    metadata = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                              check=True, capture_output=True, encoding="utf-8").stdout
    signature = subprocess.run([str(signer), "verify", "--print-certs", str(apk)],
                               check=True, capture_output=True, encoding="utf-8").stdout
    return parse_metadata(metadata, signature)


def parse_metadata(metadata: str, signature: str) -> dict:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", metadata, re.M)
    minimum = re.search(r"^sdkVersion:'(\d+)'", metadata, re.M)
    certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$", signature, re.M)
    if not package or not minimum or len(certificates) != 1:
        raise ValueError("APK元数据或唯一签名证书无法核对")
    return {"packageName": package[1], "versionCode": int(package[2]), "versionName": package[3],
            "minAndroidApi": int(minimum[1]), "certificateSha256": certificates[0].lower()}


def verify_first_install(args, package_name: str) -> None:
    if args.android_build_tools is None:
        raise SystemExit("允许首次安装须提供 --android-build-tools 并核验真实APK")
    expected = {"packageName": package_name, "versionCode": args.version_code, "versionName": args.version_name,
                "minAndroidApi": 28, "certificateSha256": args.certificate_sha256.lower()}
    try:
        actual = inspect_apk(args.apk, args.android_build_tools)
        if actual != expected:
            raise ValueError("APK包名、版本、最低Android或签名证书与发布参数不一致")
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        raise SystemExit(f"首次安装APK校验失败：{type(error).__name__}") from error
