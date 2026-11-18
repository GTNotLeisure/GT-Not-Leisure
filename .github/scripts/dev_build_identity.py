import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def git(*args):
    return subprocess.check_output(["git", *args], encoding="utf-8").strip()


def github(*args):
    return json.loads(subprocess.check_output(["gh", "api", *args], encoding="utf-8", stderr=subprocess.PIPE))


def published_assets(repository):
    try:
        release = github(f"repos/{repository}/releases/tags/dev-build")
    except subprocess.CalledProcessError as error:
        if "(HTTP 404)" in error.stderr:
            return {}
        raise
    pages = github("--paginate", "--slurp", f"repos/{repository}/releases/{release['id']}/assets?per_page=100")
    return {asset["name"]: asset for page in pages for asset in page}


def newer_publication(published, key):
    current = int(os.environ["GITHUB_RUN_NUMBER"]), int(os.environ.get("GITHUB_RUN_ATTEMPT", "1"))
    manifest_name = re.compile(rf"gtnl-update-{key}-([0-9]+)-([0-9]+)\.json")
    for name, asset in published.items():
        match = manifest_name.fullmatch(name)
        if match and asset["state"] == "uploaded" and (int(match[1]), int(match[2])) > current:
            return True
    return False


def publication():
    published = published_assets(os.environ["GITHUB_REPOSITORY"])
    eligible = not newer_publication(published, os.environ["BRANCH_KEY"])
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"eligible={str(eligible).lower()}\n")
    if not eligible:
        print("Skipping obsolete publication: a newer build for this branch is already published")


def identity():
    branch = os.environ["SOURCE_REF"]
    commit = git("rev-parse", "HEAD")
    short = git("rev-parse", "--short=8", "HEAD")
    tag = git("describe", "--tags", "--abbrev=0", "--match", "[0-9]*", "--match", "v[0-9]*")
    base = re.match(r"v?(\d+(?:\.\d+)+)", tag).group(1)
    name = re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("-")
    version = f"{base}-dev.{name}.{os.environ['GITHUB_RUN_NUMBER']}.{os.environ.get('GITHUB_RUN_ATTEMPT', '1')}+{short}"
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
                data = archive.read("gtnl-build.properties").decode("utf-8")
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
    manifest_name = f"gtnl-update-{key}-{os.environ['GITHUB_RUN_NUMBER']}-{os.environ.get('GITHUB_RUN_ATTEMPT', '1')}.json"
    (output / manifest_name).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    names.append(manifest_name)
    (output / "asset-names.txt").write_text("\n".join(names) + "\n", encoding="utf-8")


def cleanup(dry_run=False):
    repository = os.environ["GITHUB_REPOSITORY"]
    key = os.environ["BRANCH_KEY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid release repository")
    if not re.fullmatch(r"[0-9a-f]{12}", key):
        raise ValueError("Invalid branch key")

    current = set(Path("build/release-assets/asset-names.txt").read_text(encoding="utf-8").splitlines())
    if not current or not any(name.startswith(f"gtnl-update-{key}-") for name in current):
        raise ValueError("Current build manifest is missing")

    published = published_assets(repository)
    if newer_publication(published, key):
        print("Skipping obsolete cleanup: a newer build for this branch is already published")
        return
    if any(name not in published or published[name]["state"] != "uploaded" for name in current):
        if not dry_run:
            raise ValueError("Current build assets are not completely uploaded; keeping previous builds")
        print("Preview only: current build assets are not completely uploaded")

    branch_asset = re.compile(
        rf".+-{key}(?:-[0-9a-fA-F]{{7,40}})?\.jar|gtnl-update-{key}(?:-[0-9]+-[0-9]+)?\.json"
    )
    for name, asset in published.items():
        if name in current or not branch_asset.fullmatch(name):
            continue
        print(f"{'Would remove' if dry_run else 'Removing'} obsolete branch asset: {name}")
        if not dry_run:
            subprocess.run(
                ["gh", "api", "--method", "DELETE", f"repos/{repository}/releases/assets/{asset['id']}"],
                check=True,
                encoding="utf-8",
            )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("identity", "assets", "publication", "cleanup"))
    parser.add_argument("--dry-run", action="store_true", help="Preview cleanup without deleting release assets")
    arguments = parser.parse_args()
    if arguments.dry_run and arguments.command != "cleanup":
        parser.error("--dry-run is only available for cleanup")
    if arguments.command == "cleanup":
        cleanup(arguments.dry_run)
    else:
        {"identity": identity, "assets": assets, "publication": publication}[arguments.command]()
