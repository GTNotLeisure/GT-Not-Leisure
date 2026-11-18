import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location("build_identity", Path(__file__).with_name("dev_build_identity.py"))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class BuildIdentityTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.previous)
        self.environment = {
            "SOURCE_REF": "dev-290",
            "SOURCE_REPOSITORY": "example/GTNL",
            "GITHUB_RUN_NUMBER": "123",
            "GITHUB_ENV": str(self.root / "env"),
            "GITHUB_OUTPUT": str(self.root / "output"),
        }

    def identity(self, branch):
        self.environment["SOURCE_REF"] = branch
        def git(*args):
            if args[0] == "describe":
                return "v0.2.7"
            return "a" * (8 if "--short=8" in args else 40)
        with patch.dict(os.environ, self.environment), patch.object(builder, "git", git):
            builder.identity()
        return dict(line.split("=", 1) for line in (self.root / "env").read_text().splitlines())

    def test_version_and_full_identity(self):
        values = self.identity("dev-290")
        self.assertEqual(values["VERSION"], "0.2.7-dev.dev-290.123+aaaaaaaa")
        self.assertEqual(values["GTNL_BUILD_COMMIT"], "a" * 40)
        self.assertEqual(values["GTNL_BUILD_REPOSITORY"], "example/GTNL")
        self.assertEqual(values["GTNL_BUILD_CHANNEL"], "development")

    def test_sanitized_branches_keep_distinct_identity(self):
        first = self.identity("dev/a")
        second = self.identity("dev-a")
        self.assertNotEqual(first["BRANCH_KEY"], second["BRANCH_KEY"])
        self.assertEqual(first["GTNL_BUILD_BRANCH"], "dev/a")

    def prepare_assets(self, wrong_version=False):
        values = self.identity("dev-290")
        self.environment.update(values)
        jars = self.root / "build/libs"
        jars.mkdir(parents=True)
        properties = {
            "channel": "development",
            "repository": values["GTNL_BUILD_REPOSITORY"],
            "branch": values["GTNL_BUILD_BRANCH"],
            "commit": values["GTNL_BUILD_COMMIT"],
            "version": "wrong" if wrong_version else values["VERSION"],
        }
        for suffix in ("", "-dev", "-sources", "-predowngrade"):
            with zipfile.ZipFile(jars / f"sciencenotleisure-{values['VERSION']}{suffix}.jar", "w") as archive:
                archive.writestr("gtnl-build.properties", "\n".join(f"{key}={value}" for key, value in properties.items()))
        return values

    def test_manifest_selects_install_jar(self):
        values = self.prepare_assets()
        with patch.dict(os.environ, self.environment):
            builder.assets()
        output = self.root / "build/release-assets"
        manifest = json.loads((output / f"gtnl-update-{values['BRANCH_KEY']}.json").read_text())
        self.assertEqual(manifest["commit"], "a" * 40)
        self.assertEqual(manifest["artifact"], f"sciencenotleisure-{values['VERSION']}-{values['BRANCH_KEY']}.jar")
        self.assertTrue((output / manifest["artifact"]).is_file())

    def test_mismatched_jar_identity_rejected(self):
        self.prepare_assets(wrong_version=True)
        with patch.dict(os.environ, self.environment), self.assertRaises(ValueError):
            builder.assets()


if __name__ == "__main__":
    unittest.main()
