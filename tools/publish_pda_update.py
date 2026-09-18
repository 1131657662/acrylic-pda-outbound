#!/usr/bin/env python3
"""Create an immutable, signed PDA update manifest and copy its APK."""
import argparse
import base64
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

CHANNELS = {"debug", "staging", "production"}
PACKAGE_NAMES = {
    "debug": "com.acrylic.outbound.pda.debug",
    "staging": "com.acrylic.outbound.pda.staging",
    "production": "com.acrylic.outbound.pda",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--channel", choices=sorted(CHANNELS), required=True)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--certificate-sha256", required=True)
    parser.add_argument("--min-supported-version-code", type=int, required=True)
    parser.add_argument("--max-supported-version-code", type=int)
    parser.add_argument("--server-compatibility", required=True)
    parser.add_argument("--release-notes", required=True)
    parser.add_argument("--signing-key", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate(args: argparse.Namespace) -> None:
    if not args.apk.is_file() or args.apk.stat().st_size <= 0:
        raise SystemExit("APK 不存在或为空")
    if not args.release_id.replace("-", "").replace("_", "").replace(".", "").isalnum():
        raise SystemExit("发布标识包含不安全字符")
    if args.version_code <= 0 or args.min_supported_version_code <= 0:
        raise SystemExit("版本号必须为正数")
    if args.max_supported_version_code is not None and args.max_supported_version_code < args.min_supported_version_code:
        raise SystemExit("可升级版本范围无效")
    cert = args.certificate_sha256.lower()
    if len(cert) != 64 or any(char not in "0123456789abcdef" for char in cert):
        raise SystemExit("签名证书摘要必须为64位十六进制")
    if not args.signing_key.is_file():
        raise SystemExit("签名私钥不存在")


def sign(payload: bytes, key: Path) -> bytes:
    with tempfile.TemporaryDirectory() as directory:
        source = Path(directory) / "payload.json"
        source.write_bytes(payload)
        try:
            result = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key), "-in", str(source)], check=True, capture_output=True)
            return result.stdout
        except FileNotFoundError:
            try:
                from cryptography.hazmat.primitives import hashes, serialization
                private = serialization.load_pem_private_key(key.read_bytes(), password=None)
                return private.sign(payload, __import__('cryptography.hazmat.primitives.asymmetric.ec', fromlist=['ec']).ECDSA(hashes.SHA256()))
            except ImportError as error:
                raise SystemExit("需要 openssl 或 Python cryptography 才能签名") from error


def public_key(key: Path) -> str:
    try:
        result = subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-outform", "DER"], check=True, capture_output=True)
        return base64.b64encode(result.stdout).decode("ascii")
    except FileNotFoundError:
        try:
            from cryptography.hazmat.primitives import serialization
            private = serialization.load_pem_private_key(key.read_bytes(), password=None)
            return base64.b64encode(private.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode("ascii")
        except ImportError as error:
            raise SystemExit("需要 openssl 或 Python cryptography 才能导出公钥") from error


def main() -> None:
    args = parse_args()
    validate(args)
    apk_hash = sha256(args.apk)
    payload = {
        "apkPath": f"updates/{args.channel}/{args.release_id}.apk",
        "certificateSha256": args.certificate_sha256.lower(),
        "channel": args.channel,
        "maxSupportedVersionCode": args.max_supported_version_code,
        "minAndroidApi": 28,
        "minSupportedVersionCode": args.min_supported_version_code,
        "packageName": PACKAGE_NAMES[args.channel],
        "publishedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "releaseId": args.release_id,
        "releaseNotes": args.release_notes,
        "serverCompatibility": args.server_compatibility,
        "sha256": apk_hash,
        "sizeBytes": args.apk.stat().st_size,
        "versionCode": args.version_code,
        "versionName": args.version_name,
    }
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    manifest = {"payload": base64.b64encode(raw).decode("ascii"), "signature": base64.b64encode(sign(raw, args.signing_key)).decode("ascii")}
    destination = args.output_dir / "updates" / args.channel
    destination.mkdir(parents=True, exist_ok=True)
    apk_target = destination / f"{args.release_id}.apk"
    manifest_target = destination / "manifest.json"
    for target, content in ((apk_target, args.apk),):
        temporary = target.with_suffix(target.suffix + ".tmp")
        shutil.copyfile(content, temporary)
        temporary.replace(target)
    temporary_manifest = manifest_target.with_suffix(".json.tmp")
    temporary_manifest.write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    temporary_manifest.replace(manifest_target)
    (args.output_dir / "update-public-key.base64").write_text(public_key(args.signing_key), encoding="ascii")
    print(json.dumps({"manifest": str(manifest_target), "apk": str(apk_target), "sha256": apk_hash, "sizeBytes": args.apk.stat().st_size}, ensure_ascii=False))


if __name__ == "__main__":
    main()
