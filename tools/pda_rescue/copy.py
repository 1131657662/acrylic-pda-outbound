"""Create or retrieve a verified encrypted snapshot copy; no network or business writes."""
import argparse
import getpass
import json
import os
from pathlib import Path
import tempfile

from .copy_record import checked_record, digest, record, regular, verified


def write_new(path: Path, data: bytes):
    path = path.absolute()
    if any(part.is_symlink() or part.is_junction() for part in (path, *path.parents)):
        raise ValueError('Copy outputs cannot traverse links')
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def encode(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')


def create(source: tuple[Path, str, Path], destination: Path, labels: dict) -> dict:
    encrypted, snapshot = verified(*source)
    value = record(snapshot, labels)
    destination = destination.absolute()
    if destination.exists() or not destination.parent.is_dir():
        raise ValueError('Copy destination must be a new directory under an existing parent')
    if any(part.is_symlink() or part.is_junction() for part in (destination, *destination.parents)):
        raise ValueError('Copy destination cannot traverse links')
    partial = Path(tempfile.mkdtemp(prefix='pda-copy-partial-', dir=destination.parent))
    write_new(partial / 'original.pdarescue', encrypted)
    if (partial / 'original.pdarescue').read_bytes() != encrypted:
        raise ValueError(f'Copy readback failed; partial retained: {partial}')
    write_new(partial / 'copy.json', encode(value))
    if checked_record((partial / 'copy.json').read_bytes()) != value:
        raise ValueError(f'Copy record readback failed; partial retained: {partial}')
    partial.rename(destination)
    return value


def retrieve(directory: Path, credentials: tuple[str, Path, str]) -> dict:
    password, trust, expected = credentials
    path = regular(directory / 'copy.json')
    if path.stat().st_size > 65536:
        raise ValueError('Copy record exceeds resource limit')
    data = path.read_bytes()
    if len(expected) != 64 or any(c not in '0123456789abcdef' for c in expected) or digest(data) != expected:
        raise ValueError('Copy record differs from the separately recorded SHA256')
    value = checked_record(data)
    _, snapshot = verified(directory / 'original.pdarescue', password, trust)
    if snapshot != value['snapshot']:
        raise ValueError('Retrieved copy differs from recorded snapshot or external registration')
    return {'retrievedAt': record(snapshot, value['custody'])['createdAt'], 'copy': value, 'copyRecordSha256': expected,
        'integrityVerified': True, 'sourceTrusted': True, 'externalStorageVerified': False,
        'protectsExportedSnapshotOnly': True, 'businessReceiptVerified': False, 'localDeletionAllowed': False}


def arguments():
    parser = argparse.ArgumentParser(description='加密副本清单与取回核验；只保护导出截点，不能据此删除或放行')
    commands = parser.add_subparsers(dest='command', required=True)
    create_parser = commands.add_parser('create', help='验证原包并复制到新目录')
    create_parser.add_argument('package', type=Path)
    create_parser.add_argument('--out', type=Path, required=True)
    create_parser.add_argument('--custodian', required=True, help='人工登记保管人，不填口令或敏感凭据')
    create_parser.add_argument('--location', required=True, help='人工登记位置，不等于工具已核验外部介质')
    retrieve_parser = commands.add_parser('retrieve', help='重新解密验签并核对副本清单')
    retrieve_parser.add_argument('directory', type=Path)
    retrieve_parser.add_argument('--report', type=Path, required=True, help='新建取回报告路径')
    retrieve_parser.add_argument('--expected-copy-sha256', required=True, help='创建后在副本目录之外另行保管的清单SHA256')
    for command in (create_parser, retrieve_parser):
        command.add_argument('--trust', type=Path, required=True, help='包外预先登记的可信公钥清单')
    return parser, parser.parse_args()


def main():
    parser, args = arguments()
    try:
        password = getpass.getpass('应急包口令（不存入副本清单，与介质分开保管）：')
        if args.command == 'create':
            value = create((args.package, password, args.trust), args.out,
                {'custodian': args.custodian, 'location': args.location})
            copy_hash = digest((args.out / 'copy.json').read_bytes())
        else:
            if args.report.exists() or not args.report.parent.is_dir():
                raise ValueError('Retrieval report must use a new file in an existing directory')
            value = retrieve(args.directory, (password, args.trust, args.expected_copy_sha256))
            write_new(args.report, encode(value))
            copy_hash = args.expected_copy_sha256
        print(json.dumps({'passed': True, 'sourceTrusted': True, 'externalStorageVerified': False,
            'protectsExportedSnapshotOnly': True, 'localDeletionAllowed': False, 'copyRecordSha256': copy_hash}, ensure_ascii=False))
    except Exception as error:
        parser.exit(1, f'副本/取回未完成（{type(error).__name__}）；原件和partial保留，不改变业务状态。\n')


if __name__ == '__main__':
    main()
