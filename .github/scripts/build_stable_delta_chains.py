#!/usr/bin/env python3
"""Build verified stable APK delta patches (ApkDiffPatch) and single-hop upgrade chains.

Only releases with both version.json and the canonical qr-code-simple-<version>.apk
asset participate. This deliberately ignores the historical app-release.apk-only
releases so a legacy asset can never be mistaken for a compatible delta base.

ApkDiffPatch (sisong/ApkDiffPatch v1.8.1, MIT) server-side generation:
- the published APK is ApkNormalized(new APK) + apksigner 34.0.0 re-sign (done by the
  workflow before this script runs)
- for each of the most recent MAX_KEEP historical released APKs, generate a direct patch
  with ZipDiff, then ZipPatch it back and compare byte-for-byte with the published APK
- only generate for from-versions whose APK contains libapkpatch.so; older clients cannot
  apply ZiPat1 patches and safely fall back to the full download
- drop patches not smaller than half the target APK
- single-hop chains only; any failure drops only that entry and never blocks publishing

Environment:
  APKDIFF_BIN  directory containing ZipDiff/ZipPatch (default ".")
"""

import argparse
import filecmp
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


RELEASE_TAG = re.compile(r"^v\d+\.\d+\.\d+$")
VERSION_NAME = re.compile(r"^\d+\.\d+\.\d+(?:\+\d+)?$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
APKDIFF_BIN = os.environ.get("APKDIFF_BIN", ".")
ZIPDIFF = os.path.join(APKDIFF_BIN, "ZipDiff")
ZIPPATCH = os.path.join(APKDIFF_BIN, "ZipPatch")
MAX_KEEP = 8
MIN_PATCH_RATIO = 0.5


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(command: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(command, check=False, capture_output=True, text=True)
    except FileNotFoundError as error:
        raise RuntimeError(f"Required command is unavailable: {command[0]}") from error
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"Command failed ({' '.join(command)}): {detail}")
    return result


def git(*arguments: str) -> str:
    return run(["git", *arguments]).stdout.strip()


def repository() -> str:
    value = os.environ.get("GITHUB_REPOSITORY")
    if not value:
        raise RuntimeError("GITHUB_REPOSITORY is required for release asset URLs and gh commands")
    return value


def gh(*arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["gh", "release", *arguments, "--repo", repository()], check=check)


def positive_int(value: object) -> int | None:
    return value if type(value) is int and value > 0 else None


def valid_sha(value: object) -> str | None:
    return value if isinstance(value, str) and SHA256.fullmatch(value) else None


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError(f"{path} must contain a JSON object")
    return value


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", delete=False
    ) as output:
        json.dump(value, output, ensure_ascii=False, indent=2, sort_keys=True)
        output.write("\n")
        temporary_path = Path(output.name)
    os.replace(temporary_path, path)


def apk_has_native_lib(apk: Path) -> bool:
    """Only from-versions bundling libapkpatch.so can apply ZiPat1 patches; older
    clients fall back to the full download, keeping the update button always usable."""
    try:
        with zipfile.ZipFile(apk) as archive:
            return any(name.endswith("libapkpatch.so") for name in archive.namelist())
    except Exception:
        return False


def release_assets(tag: str) -> set[str] | None:
    result = gh("view", tag, "--json", "assets", check=False)
    if result.returncode != 0:
        return None
    try:
        assets = json.loads(result.stdout).get("assets", [])
        return {asset["name"] for asset in assets if isinstance(asset, dict) and isinstance(asset.get("name"), str)}
    except (AttributeError, json.JSONDecodeError):
        return None


def download_release_asset(tag: str, asset: str, destination: Path) -> Path | None:
    destination.mkdir(parents=True, exist_ok=True)
    result = gh(
        "download", tag, "--pattern", asset, "--dir", str(destination), "--clobber", check=False
    )
    candidate = destination / asset
    return candidate if result.returncode == 0 and candidate.is_file() else None


def create_patch(old_apk: Path, new_apk: Path, output_path: Path, new_hash: str, new_size: int) -> dict | None:
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-stable-patch-") as temporary:
        temporary_dir = Path(temporary)
        candidate = temporary_dir / output_path.name
        verified = temporary_dir / "verified.apk"
        try:
            run([ZIPDIFF, str(old_apk), str(new_apk), str(candidate)])
            run([ZIPPATCH, str(old_apk), str(candidate), str(verified)])
            if not filecmp.cmp(new_apk, verified, shallow=False):
                print(f"drop {output_path.name}: ZipPatch verification bytes differ")
                return None
            if candidate.stat().st_size >= new_size * MIN_PATCH_RATIO:
                print(f"drop {output_path.name}: patch is not smaller than half the target APK")
                return None
            shutil.move(str(candidate), str(output_path))
            return {
                "file": output_path.name,
                "size": output_path.stat().st_size,
                "patchSha256": sha256(output_path),
            }
        except Exception as error:
            print(f"drop {output_path.name}: {error}")
            return None


def current_tag_from_git() -> str:
    result = run(["git", "describe", "--tags", "--exact-match", "--match", "v*", "HEAD"], check=False)
    if result.returncode != 0:
        raise RuntimeError("HEAD must be an exact vMAJOR.MINOR.PATCH tag for a stable release")
    return result.stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=Path("version.json"))
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME"))
    arguments = parser.parse_args()

    metadata = read_json(arguments.metadata)
    version_code = positive_int(metadata.get("versionCode"))
    version_name = metadata.get("versionName")
    if version_code is None or not isinstance(version_name, str) or not VERSION_NAME.fullmatch(version_name):
        raise RuntimeError("version.json must contain a positive versionCode and a numeric versionName")

    tag = arguments.tag or current_tag_from_git()
    if not RELEASE_TAG.fullmatch(tag) or tag != f"v{version_name}":
        raise RuntimeError(f"Stable tag '{tag}' does not exactly match versionName '{version_name}'")

    expected_apk_name = f"qr-code-simple-{version_name}.apk"
    new_apk = arguments.apk or Path(expected_apk_name)
    if new_apk.name != expected_apk_name or not new_apk.is_file():
        raise RuntimeError(f"Expected canonical release APK '{expected_apk_name}'")

    new_hash = sha256(new_apk)
    new_size = new_apk.stat().st_size
    metadata["apkFile"] = expected_apk_name
    metadata["apkSha256"] = new_hash
    metadata["apkSize"] = new_size
    metadata.setdefault("patches", {})
    metadata.setdefault("chains", {})
    # Preserve a complete full-download manifest even if GitHub or the toolchain later fails.
    write_json(arguments.metadata, metadata)

    if not Path(ZIPDIFF).is_file() or not Path(ZIPPATCH).is_file():
        print(f"ZipDiff/ZipPatch unavailable (APKDIFF_BIN='{APKDIFF_BIN}'); publishing full APK metadata without patches")
        return

    if shutil.which("gh") is None:
        raise RuntimeError("gh is required to inspect historical stable releases")

    history: list[dict] = []
    seen_codes: set[int] = set()
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-stable-history-") as temporary:
        history_dir = Path(temporary)
        for historical_tag in git("tag", "--merged", "HEAD", "-l", "v*").splitlines():
            if historical_tag == tag:
                continue
            assets = release_assets(historical_tag)
            if not assets or "version.json" not in assets:
                print(f"skip {historical_tag}: no version.json release asset")
                continue

            historical_metadata_path = download_release_asset(
                historical_tag, "version.json", history_dir / historical_tag
            )
            if historical_metadata_path is None:
                print(f"skip {historical_tag}: version.json could not be downloaded")
                continue
            try:
                historical_metadata = read_json(historical_metadata_path)
            except Exception as error:
                print(f"skip {historical_tag}: invalid version.json ({error})")
                continue

            historical_code = positive_int(historical_metadata.get("versionCode"))
            historical_name = historical_metadata.get("versionName")
            if historical_code is None or not isinstance(historical_name, str) or not VERSION_NAME.fullmatch(historical_name):
                print(f"skip {historical_tag}: invalid version metadata")
                continue
            canonical_apk = f"qr-code-simple-{historical_name}.apk"
            if canonical_apk not in assets:
                print(f"skip {historical_tag}: no canonical APK ({canonical_apk})")
                continue
            if historical_code >= version_code:
                print(f"skip {historical_tag}: versionCode is not older than the target")
                continue
            if historical_code in seen_codes:
                print(f"skip {historical_tag}: duplicate versionCode {historical_code}")
                continue

            seen_codes.add(historical_code)
            history.append({
                "versionCode": historical_code,
                "tag": historical_tag,
                "apk": canonical_apk,
                "apkSha256": valid_sha(historical_metadata.get("apkSha256")),
            })

        history.sort(key=lambda item: item["versionCode"])
        history = history[-MAX_KEEP:]
        patches: dict[str, dict] = {}
        for source in history:
            source_code = source["versionCode"]
            old_apk = download_release_asset(
                source["tag"], source["apk"], history_dir / f"apk-{source_code}"
            )
            if old_apk is None:
                print(f"skip {source['tag']}: canonical APK could not be downloaded")
                continue
            old_hash = sha256(old_apk)
            if source["apkSha256"] is not None and source["apkSha256"] != old_hash:
                print(f"skip {source['tag']}: APK hash disagrees with version.json")
                continue
            if not apk_has_native_lib(old_apk):
                print(f"skip {source['tag']}({source_code}): old APK has no libapkpatch.so, full download only")
                continue

            patch_path = Path(f"patch-{source_code}-to-{version_code}.patch")
            patch = create_patch(old_apk, new_apk, patch_path, new_hash, new_size)
            if patch is not None:
                patches[str(source_code)] = patch
                print(f"created {patch_path.name} ({patch['size']} bytes)")

    chains: dict[str, dict] = {}
    for source in history:
        source_hash = source.get("apkSha256")
        if valid_sha(source_hash) is None:
            continue
        patch = patches.get(str(source["versionCode"]))
        if patch is None:
            continue
        chains[str(source["versionCode"])] = {
            "fromApkSha256": source_hash,
            "totalSize": patch["size"],
            "hops": [
                {
                    "toVersionCode": version_code,
                    "url": f"https://github.com/{repository()}/releases/download/{tag}/{patch['file']}",
                    "size": patch["size"],
                    "patchSha256": patch["patchSha256"],
                    "resultSha256": new_hash,
                }
            ],
        }

    metadata["patches"] = patches
    metadata["chains"] = chains
    metadata["apkSha256"] = new_hash
    metadata["apkSize"] = new_size
    write_json(arguments.metadata, metadata)
    print(f"stable: {len(patches)} direct patch(es), {len(chains)} upgrade chain(s)")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Stable delta generation failed: {error}", file=sys.stderr)
        sys.exit(1)
