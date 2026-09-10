import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'release.py'
spec = importlib.util.spec_from_file_location('release', SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def publication(self, root):
        pom = root / 'dev/sajidali/example/library/1.0/library-1.0.pom'
        pom.parent.mkdir(parents=True)
        pom.write_text('<project><groupId>dev.sajidali.example</groupId><artifactId>library</artifactId><version>1.0</version></project>')
        return pom

    def test_missing_publications_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, 'no Maven publications'):
                release.validate(Path(tmp), {'dev.sajidali.example': '1.0'})

    def test_wrong_version_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.publication(Path(tmp))
            with self.assertRaisesRegex(ValueError, 'Unexpected publication'):
                release.validate(Path(tmp), {'dev.sajidali.example': '2.0'})

    def test_missing_metadata_payload_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pom = self.publication(Path(tmp))
            pom.with_suffix('.module').write_text(json.dumps({'component': {'version': '1.0'}, 'variants': [{'files': [{'url': 'missing.klib'}]}]}))
            with self.assertRaisesRegex(ValueError, 'Missing/unsafe'):
                release.validate(Path(tmp), {'dev.sajidali.example': '1.0'})

    def test_collision_prevents_any_upload(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.publication(Path(tmp))
            with patch.dict(os.environ, REPOSILITE_URL='https://example.test/releases', REPOSILITE_USER='user', REPOSILITE_TOKEN='secret'):
                with patch.object(release.urllib.request, 'urlopen') as request:
                    with self.assertRaisesRegex(ValueError, 'already exists'):
                        release.upload_reposilite(Path(tmp))
                    self.assertEqual([call.args[0].method for call in request.call_args_list], ['HEAD'])

    def test_releases_refuse_ci(self):
        for destination in ('central', 'reposilite'):
            for variable in ('CI', 'GITHUB_ACTIONS'):
                result = subprocess.run(['python3', str(SCRIPT), destination, '--version', '1.0', '--plugin-version', '1.0', '--dry-run'],
                                        env=dict(os.environ, **{variable: 'true'}), capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('manual only', result.stderr)



if __name__ == '__main__':
    unittest.main()
