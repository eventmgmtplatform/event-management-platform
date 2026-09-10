"""Prevent certification from mutating an incompatible runtime or mounted build."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ess_certification', ROOT/'testing/certifications/event-state-certification.py')
ess = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ess)


class EssPreflightTest(unittest.TestCase):
    def test_empty_uncommitted_partition_is_drained(self):
        output = 'group topic 0 - 0 - consumer host client\ngroup topic 1 5 5 0 consumer host client'
        self.assertTrue(ess.offsets_drained(output, 'topic', 'group'))

    def test_nonempty_uncommitted_or_positive_lag_never_passes(self):
        for output in ['', 'group topic 0 - 5 -', 'group topic 0 4 5 1',
                       'other topic 0 5 5 0', 'group other 0 5 5 0']:
            with self.subTest(output=output):
                self.assertFalse(ess.offsets_drained(output, 'topic', 'group'))

    def test_missing_schema_stops_before_runtime_commands(self):
        with patch.object(ess, 'sql', return_value='1'), patch.object(ess, 'run') as run:
            with self.assertRaisesRegex(AssertionError, 'MIGRATIONS_REQUIRED'):
                ess.preflight(True)
            run.assert_not_called()

    def test_missing_consumer_stops_before_build_inspection(self):
        with patch.object(ess, 'sql', return_value='3'), patch.object(ess, 'run', return_value='event-state-service') as run:
            with self.assertRaisesRegex(AssertionError, 'CONSUMER_REQUIRED'):
                ess.preflight(True)
            self.assertEqual(run.call_count, 1)

    def test_mounted_build_is_rejected_but_unrelated_mount_is_allowed(self):
        for source, blocked in [(ROOT/'services/event-state-service/target/quarkus-app', True),
                                (ROOT/'services/event-state-service', True),
                                (ROOT/'services/integration-worker/target/quarkus-app', False)]:
            responses = ['event-state-service-lifecycle', 'container-id',
                         json.dumps([{'Mounts': [{'Source': str(source)}]}])]
            with self.subTest(source=source), patch.object(ess, 'sql', return_value='3'), patch.object(ess, 'run', side_effect=responses):
                if blocked:
                    with self.assertRaisesRegex(AssertionError, 'BUILD_MOUNTED'):
                        ess.preflight(True)
                else:
                    ess.preflight(True)

    def test_service_only_does_not_inspect_or_rebuild_mounted_output(self):
        with patch.object(ess, 'sql', return_value='3'), patch.object(ess, 'run', return_value='event-state-service-lifecycle') as run:
            ess.preflight(False)
            self.assertEqual(run.call_count, 1)


if __name__ == '__main__':
    unittest.main()
