"""Regression checks for test discovery, fixture migration and honest E2E reporting."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


class TestingBaseTest(unittest.TestCase):
    def test_maven_discovers_central_sources_and_resources(self):
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for service in ('event-gateway', 'event-processor', 'integration-worker', 'event-state-service'):
            basedir = ROOT/'services'/service
            pom = ET.parse(basedir/'pom.xml')
            source = pom.find('m:build/m:testSourceDirectory', ns).text
            source = Path(source.replace('${project.basedir}', str(basedir))).resolve()
            self.assertEqual(source, ROOT/'testing/services'/service/'java')
            self.assertTrue(list(source.rglob('*Test.java')))
            for item in pom.findall('m:build/m:testResources/m:testResource/m:directory', ns):
                path = Path(item.text.replace('${project.basedir}', str(basedir))).resolve()
                # Gateway has shared fixtures instead of classpath resources.
                self.assertTrue(path.is_dir() or service == 'event-gateway')

    def test_happy_path_cannot_pass_when_scenario_fails(self):
        runner = load(ROOT/'testing/run.py')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'testing/cases').mkdir(parents=True)
            (root/'testing/certifications').mkdir()
            (root/'testing/cases/catalog.json').write_text((ROOT/'testing/cases/catalog.json').read_text())
            with patch.object(runner, 'ROOT', root), patch.object(runner, 'module', side_effect=AssertionError('Missing provider confirmation')), patch.object(sys, 'argv', ['run.py', 'happy-path']):
                self.assertEqual(runner.main(), 1)
            report = json.loads(next(root.rglob('report.json')).read_text())
            self.assertEqual(report['status'], 'FAIL')
            self.assertEqual(report['error'], 'Missing provider confirmation')
            self.assertTrue(list(root.rglob('SHA256SUMS')))

    def test_blackout_rejects_false_observations_even_under_optimized_python(self):
        result = subprocess.run([sys.executable, '-O', '-c',
            "import sys; sys.path.insert(0, 'testing/e2e'); from blackout import require; require(False, 'missing suppression')"],
            cwd=ROOT, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing suppression', result.stderr)

    def test_blackout_polling_is_bounded(self):
        blackout = load(ROOT/'testing/e2e/blackout.py')
        with self.assertRaisesRegex(AssertionError, 'Timeout: missing decision'):
            blackout.wait(lambda: False, 'missing decision', timeout=0)

    def test_catalog_has_unique_cases_and_no_execution_results(self):
        cases = json.loads((ROOT/'testing/cases/catalog.json').read_text())['cases']
        self.assertEqual(len(cases), len({c['id'] for c in cases}))
        for case in cases:
            self.assertNotIn('result', case)
            self.assertNotIn('runId', case)
            if 'specification' in case:
                self.assertTrue((ROOT/'testing/cases'/case['specification']).is_file())


if __name__ == '__main__':
    unittest.main()
