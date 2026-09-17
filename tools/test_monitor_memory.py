"""Android version-specific TOTAL layouts and missing process evidence."""
import unittest
from monitor_memory import parse_memory


class MemoryParserTest(unittest.TestCase):
    def test_android9_uses_total_with_graphics_included(self):
        raw = " Native Heap 17564 17504\n TOTAL 55614 37300 9200 0\n Graphics: 7280\n TOTAL: 55614 TOTAL SWAP PSS: 0\n"
        result = parse_memory(raw)
        self.assertEqual(55614 * 1024, result["totalPssBytes"])
        self.assertEqual(7280 * 1024, result["graphicsPssBytes"])

    def test_newer_layout_uses_total_pss(self):
        result = parse_memory(" TOTAL PSS: 63123 TOTAL RSS: 99000\n Graphics: 1024\n")
        self.assertEqual(63123 * 1024, result["totalPssBytes"])

    def test_missing_process_cannot_pass_as_zero(self):
        with self.assertRaisesRegex(ValueError, "No process TOTAL PSS"):
            parse_memory("No process found for: com.acrylic.m71demo.debug")


if __name__ == "__main__":
    unittest.main()
