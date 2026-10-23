"""Explicit Java/Python acceptance runner; missing environment fails, never skips."""
from pathlib import Path
import argparse
import json
import os
import platform
import sys
import unittest
from control_java_process import classpath
from control_java_cases import JavaControlCase, JavaOriginalTests
from control_java_locks import JavaLockTests
from control_java_writers import JavaWriterTests
from control_java_intents import JavaIntentTests
from control_java_successor import JavaSuccessorTests, JavaSuccessorCrashTests


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', required=True)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--evidence-root', required=True, type=Path)
    args = parser.parse_args()
    args.evidence_root.mkdir(parents=True, exist_ok=False)
    paths = classpath(args.classpath)
    JavaControlCase.config = (args.java, paths, args.evidence_root)
    suite = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(cls)
                               for cls in (JavaOriginalTests, JavaLockTests, JavaWriterTests, JavaIntentTests, JavaSuccessorTests))
    suite.addTest(JavaSuccessorCrashTests('test_process_death_at_each_successor_boundary_resumes_only_original'))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    summary = {'platform': platform.platform(), 'python': sys.version, 'pid': os.getpid(),
               'java': args.java, 'classpath': paths, 'tests': result.testsRun,
               'failures': len(result.failures), 'errors': len(result.errors),
               'skipped': len(result.skipped), 'scope': 'LOCAL_JAVA_PYTHON_CONTROL_ONLY',
               'sharedGateIntegrated': False, 'localDeletionAllowed': False}
    (args.evidence_root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    return 0 if result.wasSuccessful() and not result.skipped else 1


if __name__ == '__main__':
    sys.exit(main())
