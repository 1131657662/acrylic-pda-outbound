"""Opt-in offline FORMAL original receipt consumer; no network or business writer."""
import argparse
import getpass
import hashlib
import json
from pathlib import Path

from .envelope import decrypt
from .integrity import parse
from .receipt import verify_formal_receipt


def public_material(path):
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('Trust material exceeds resource limit')
    data = path.read_bytes()
    return parse(data.removeprefix(b'\xef\xbb\xbf')), data


def main():
    parser = argparse.ArgumentParser(description='离线核验FORMAL历史成功原回执；不确认现时结果或放行')
    parser.add_argument('package', type=Path)
    parser.add_argument('--trust', required=True, type=Path, help='包外预先核验的原设备登记范围')
    parser.add_argument('--receipt-key', required=True, type=Path, help='包外预先核验的服务端RS256公钥JSON')
    args = parser.parse_args()
    try:
        registration, registration_bytes = public_material(args.trust)
        key, key_bytes = public_material(args.receipt_key)
        from .envelope import MAX_BYTES, HEADER_SIZE
        if args.package.stat().st_size > MAX_BYTES + HEADER_SIZE + 16:
            raise ValueError('Encrypted package exceeds resource limit')
        encrypted = args.package.read_bytes()
        password = getpass.getpass('应急包口令（不写入报告）：')
        report = verify_formal_receipt(decrypt(args.package, password), registration, key)
        if (args.package.read_bytes(), args.trust.read_bytes(), args.receipt_key.read_bytes()) != (
                encrypted, registration_bytes, key_bytes):
            raise ValueError('Original package or external trust material changed during verification')
        report['encryptedPackageSha256'] = hashlib.sha256(encrypted).hexdigest()
        report['registrationTrustSha256'] = hashlib.sha256(registration_bytes).hexdigest()
        report['receiptKeyMaterialSha256'] = hashlib.sha256(key_bytes).hexdigest()
        print(json.dumps(report, ensure_ascii=False))
    except Exception as error:
        parser.exit(1, f'历史原回执核验未完成（{type(error).__name__}）；原件保留，不改变业务状态。\n')


if __name__ == '__main__':
    main()
