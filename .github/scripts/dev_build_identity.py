import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def identity():
    branch = os.environ["SOURCE_REF"]
    commit = git("rev-parse", "HEAD")
    short = git("rev-parse", "--short=8", "HEAD")
    tag = git("describe", "--tags", "--abbrev=0", "--match", "[0-9]*", "--match", "v[0-9]*")
    base = re.match(r"v?(\d+(?:\.\d+)+)", tag).group(1)
    name = re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("-")
    version = f"{base}-dev.{name}.{os.environ['GITHUB_RUN_NUMBER']}+{short}"
    values = {
        "VERSION": version,
        "RELEASE_VERSION": version,
        "COMMIT_SHORT": short,
        "BRANCH_KEY": hashlib.sha256(branch.encode()).hexdigest()[:12],
        "GTNL_BUILD_REPOSITORY": os.environ["SOURCE_REPOSITORY"],
        "GTNL_BUILD_BRANCH": branch,
        "GTNL_BUILD_COMMIT": commit,
        "GTNL_BUILD_CHANNEL": "development",
    }
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as output:
        for key, value in values.items():
            if "\n" in value or "\r" in value:
                raise ValueError("Invalid build identity")
            output.write(f"{key}={value}\n")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"commit_short={short}\n")


def assets():
    output = Path("build/release-assets")
    output.mkdir(parents=True, exist_ok=True)
    key = os.environ["BRANCH_KEY"]
    jars = list(Path("build/libs").glob("*.jar"))
    if not jars:
        raise ValueError("No build jars")
    names = []
    main = None
    for jar in jars:
        name = f"{jar.stem}-{key}.jar"
        (output / name).write_bytes(jar.read_bytes())
        names.append(name)
        if not jar.name.endswith(("-dev.jar", "-sources.jar", "-predowngrade.jar")):
            if main is not None:
                raise ValueError("More than one install jar")
            main = name
            with zipfile.ZipFile(jar) as archive:
                data = archive.read("gtnl-build.properties").decode("iso-8859-1")
                properties = {}
                for line in data.splitlines():
                    if not line or line.startswith(("#", "!")):
                        continue
                    field, separator, value = line.partition("=")
                    if separator:
                        value = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), value)
                        properties[field] = re.sub(r"\\(.)", r"\1", value)
                expected = {
                    "channel": "development",
                    "repository": os.environ["GTNL_BUILD_REPOSITORY"],
                    "branch": os.environ["GTNL_BUILD_BRANCH"],
                    "commit": os.environ["GTNL_BUILD_COMMIT"],
                    "version": os.environ["VERSION"],
                }
                if any(properties.get(field) != value for field, value in expected.items()):
                    raise ValueError("Install jar identity does not match this CI build")
    if main is None:
        raise ValueError("No install jar")
    manifest = {
        "schema": 1,
        "channel": "development",
        "repository": os.environ["GTNL_BUILD_REPOSITORY"],
        "branch": os.environ["GTNL_BUILD_BRANCH"],
        "commit": os.environ["GTNL_BUILD_COMMIT"],
        "version": os.environ["VERSION"],
        "artifact": main,
    }
    (output / f"gtnl-update-{key}.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "asset-names.txt").write_text("\n".join(names) + "\n", encoding="utf-8")


if __name__ == "__main__":
    {"identity": identity, "assets": assets}[sys.argv[1]]()
