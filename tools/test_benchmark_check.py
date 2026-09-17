"""Evidence checks must reject omitted, reordered or substituted input."""
import unittest
from run_benchmark_check import chain


class ChainTest(unittest.TestCase):
    def fixture(self):
        captures = [{"captureId": f"id-{i}", "rawInput": f"003A000000{i}", "receivedElapsedNs": i * 250_000_000} for i in range(1, 4)]
        events = []
        for row in captures:
            events += [{"type": "BROADCAST_CALLBACK", "source": "TEST_INJECTION", "captureId": row["captureId"], "rawInput": row["rawInput"]},
                       {"type": "SAVE_RESULT", "status": "SAVED", "captureId": row["captureId"]},
                       {"type": "SOUND_REQUEST", "sound": "SAVED", "streamId": 1, "captureId": row["captureId"]}]
        return events, captures

    def test_complete_chain_preserves_characters_and_order(self):
        events, captures = self.fixture()
        self.assertTrue(chain(events, captures, 3)["passed"])

    def test_missing_saved_input_fails(self):
        events, captures = self.fixture()
        events.pop(1)
        self.assertFalse(chain(events, captures, 3)["passed"])

    def test_reordered_broadcasts_fail(self):
        events, captures = self.fixture()
        events[0], events[3] = events[3], events[0]
        self.assertFalse(chain(events, captures, 3)["passed"])

    def test_changed_character_fails(self):
        events, captures = self.fixture()
        events[0]["rawInput"] = "003A0000011"
        self.assertFalse(chain(events, captures, 3)["passed"])

    def test_zero_sound_stream_is_not_reported_as_success(self):
        events, captures = self.fixture()
        events[2]["streamId"] = 0
        self.assertFalse(chain(events, captures, 3)["passed"])


if __name__ == "__main__":
    unittest.main()
