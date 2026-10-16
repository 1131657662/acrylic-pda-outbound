"""Generate an unrelated guarded order without altering the target fixture."""
from contextlib import closing

from ..codec import parse
from ..store import connect
from .factory import insert, mutate
from .support import RetentionCase


class UnrelatedGuardCase(RetentionCase):
    def setUp(self):
        super().setUp()
        original = self.fixture['body']['captures'][0]['id']
        with closing(connect(self.source, True)) as db:
            capture = dict(db.execute('SELECT * FROM work_capture WHERE id=?', (original,)).fetchone())
            scan = dict(db.execute('SELECT * FROM work_scan WHERE captureId=?', (original,)).fetchone())
            capture.update(id='unrelated-capture', assignmentId='unrelated-assignment',
                           originAssignmentId='unrelated-assignment', orderId='unrelated-order',
                           barcode='UNRELATED01', rawInput='UNRELATED01', deviceCaptureSeq=1001)
            scan.update(captureId=capture['id'], assignmentId=capture['assignmentId'], barcode=capture['barcode'])
            insert(db, 'work_capture', capture)
            insert(db, 'work_scan', scan)
            insert(db, 'work_guard', dict(barcode=capture['barcode'], captureId=capture['id'],
                   assignmentId=capture['assignmentId'], dispatchCycle=scan['expectedDispatchCycle']))
        mutate(self.spec, 'SELECT 1')
        self.spec_data = parse(self.spec.read_bytes())
