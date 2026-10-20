"""Offline TEMP original-file consumer; no network or business writer."""
import argparse
import json
from pathlib import Path

from .integrity import parse
from .temporary_receipt import MAX_PAYLOAD, MAX_RESPONSE, digest, verify_temporary_receipt


def original(path, limit):
    if path.stat().st_size > limit:
        raise ValueError('Original file exceeds resource limit')
    with path.open('rb') as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise ValueError('Original file grew beyond resource limit')
    return raw


def main():
    parser = argparse.ArgumentParser(description='离线核验首轮TEMP历史成功原文件；不确认现时结果或放行')
    parser.add_argument('payload', type=Path, help='未改写的原冻结正文文件')
    parser.add_argument('response', type=Path, help='含code/data的原HTTP成功响应文件')
    parser.add_argument('--context', required=True, type=Path, help='包外预先核验的原批次、人机仓、映射目标JSON')
    parser.add_argument('--receipt-key', required=True, type=Path, help='包外预先核验的服务端RS256公钥JSON')
    args = parser.parse_args()
    try:
        paths = [args.payload, args.response, args.context, args.receipt_key]
        limits = [MAX_PAYLOAD, MAX_RESPONSE, 1024 * 1024, 1024 * 1024]
        inputs = [original(path, limit) for path, limit in zip(paths, limits)]
        context, key = [parse(raw.removeprefix(b'\xef\xbb\xbf')) for raw in inputs[2:]]
        report = verify_temporary_receipt(*inputs[:2], context, key)
        if [original(path, limit) for path, limit in zip(paths, limits)] != inputs:
            raise ValueError('Original files or external trust changed during verification')
        report['contextMaterialSha256'] = digest(inputs[2])
        report['receiptKeyMaterialSha256'] = digest(inputs[3])
        print(json.dumps(report, ensure_ascii=False))
    except Exception as error:
        parser.exit(1, f'TEMP历史原回执核验未完成（{type(error).__name__}）；原件保留，不改变业务状态。\n')


if __name__ == '__main__':
    main()
