import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).parents[1] / "build_beta_delta_chains.py"
SPEC = importlib.util.spec_from_file_location("build_beta_delta_chains", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class BetaArchiveTest(unittest.TestCase):
    def test_archive_full_only_uploads_versioned_apk_without_patch_tools(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / "qr-code-simple-beta.apk"
            metadata = root / "version.json"
            apk.write_bytes(b"signed beta apk")
            metadata.write_text(
                json.dumps({"versionCode": 123, "versionName": "0.3.2"}),
                encoding="utf-8",
            )
            uploads = []

            def capture_upload(path):
                uploads.append((path.name, path.read_bytes()))

            arguments = [
                str(SCRIPT),
                "--metadata",
                str(metadata),
                "--apk",
                str(apk),
                "--archive-full-only",
            ]
            with mock.patch.object(sys, "argv", arguments), \
                    mock.patch.object(MODULE.shutil, "which", return_value="/usr/bin/gh"), \
                    mock.patch.object(MODULE, "ensure_archive"), \
                    mock.patch.object(MODULE, "archive_assets", return_value=set()), \
                    mock.patch.object(MODULE, "upload_asset", side_effect=capture_upload):
                MODULE.main()

            self.assertEqual([("beta-123.apk", b"signed beta apk")], uploads)
            written = json.loads(metadata.read_text(encoding="utf-8"))
            self.assertEqual("qr-code-simple-beta.apk", written["apkFile"])
            self.assertEqual(len(b"signed beta apk"), written["apkSize"])
            self.assertEqual({}, written["patches"])
            self.assertEqual({}, written["chains"])

    def test_invalid_metadata_is_rejected_before_remote_operations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / "qr-code-simple-beta.apk"
            metadata = root / "version.json"
            apk.write_bytes(b"apk")
            metadata.write_text(
                json.dumps({"versionCode": 0, "versionName": "beta"}),
                encoding="utf-8",
            )
            arguments = [
                str(SCRIPT),
                "--metadata",
                str(metadata),
                "--apk",
                str(apk),
                "--archive-full-only",
            ]

            with mock.patch.object(sys, "argv", arguments), \
                    mock.patch.object(MODULE, "ensure_archive") as ensure_archive:
                with self.assertRaisesRegex(RuntimeError, "positive versionCode"):
                    MODULE.main()
                ensure_archive.assert_not_called()


if __name__ == "__main__":
    unittest.main()
