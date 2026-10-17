"""Independent structural and business-marker consumer for a PDF export."""
import shutil
import subprocess
import tempfile
from pathlib import Path


def info(path):
    executable = shutil.which('pdfinfo')
    assert executable, 'pdfinfo is required for independent PDF parsing'
    result = subprocess.run([executable, str(path)], check=True, capture_output=True,
                            text=True, encoding='utf-8', errors='replace')
    fields = {}
    for line in result.stdout.splitlines():
        if ':' in line:
            key, value = line.split(':', 1)
            fields[key.strip()] = value.strip()
    assert int(fields['Pages']) > 0
    assert int(fields['File size'].split()[0]) == path.stat().st_size
    return fields


def consume(path: Path, snapshot):
    raw = path.read_bytes()
    assert raw.startswith(b'%PDF-') and raw.rstrip().endswith(b'%%EOF')
    fields = info(path)
    metadata = snapshot['metadata']
    assert metadata['basis'] == 'ORIGINAL_CONFIRMED'
    subject = fields['Subject']
    assert metadata['snapshotId'] in subject and '原确认V1' in subject
    assert b'/Annots' not in raw
    rasterizer = shutil.which('pdftoppm')
    assert rasterizer, 'pdftoppm is required for independent PDF rendering'
    with tempfile.TemporaryDirectory(prefix='d15-pdf-consumer-') as directory:
        prefix = str(Path(directory) / 'page')
        subprocess.run([rasterizer, '-f', '1', '-singlefile', '-png', str(path), prefix],
                       check=True, capture_output=True)
        preview = Path(prefix + '.png')
        assert preview.exists() and preview.stat().st_size > 1000
    return dict(format='PDF', pages=int(fields['Pages']), bytes=len(raw), annotations=0,
                subject=subject, basis=metadata['basis'], rasterized=True)
