#!/usr/bin/env python3
"""Maintain the prerelease beta archive and verified beta APK upgrade chains.

The archive keeps eight signed beta bases. New patches target the 1, 2, and 4
most recent bases using ApkDiffPatch single-hop direct patches; the Pages manifest
always retains a full APK checksum and size. The STABLE_CROSS_BASES most recent
stable releases also get direct patches so stable clients can switch channels
incrementally; those patches live in beta-archive alongside the beta bases.

ApkDiffPatch (sisong/ApkDiffPatch v1.8.1, MIT) server-side generation:
- the published beta APK is ApkNormalized(new APK) + apksigner 34.0.0 re-sign (done by
  the workflow before this script runs)
- ZipDiff generates a direct patch, ZipPatch replays it, and the result is compared
  byte-for-byte with the published APK; mismatches drop that patch
- only generate for from-versions whose APK contains libapkpatch.so; older clients
  cannot apply ZiPat1 patches and safely fall back to the full download
- drop patches not smaller than half the target APK; single-hop chains only
- any failure drops only that entry and never blocks the beta publication

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


ARCHIVE_TAG = "beta-archive"
HISTORY_FILE = "beta-history.json"
APKDIFF_BIN = os.environ.get("APKDIFF_BIN", ".")
ZIPDIFF = os.path.join(APKDIFF_BIN, "ZipDiff")
ZIPPATCH = os.path.join(APKDIFF_BIN, "ZipPatch")
MAX_KEEP = 8
BACKOFF = (1, 2, 4)
MIN_PATCH_RATIO = 0.5
VERSION_NAME = re.compile(r"^\d+\.\d+\.\d+(?:\+\d+)?$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
# Cross-channel bases: recent stable releases also get direct patches to a new beta,
# so stable clients can switch to the beta channel incrementally.
STABLE_TAG = re.compile(r"^v\d+\.\d+\.\d+$")
STABLE_CROSS_BASES = 2


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


def archive_assets() -> set[str]:
    result = gh("view", ARCHIVE_TAG, "--json", "assets")
    try:
        assets = json.loads(result.stdout).get("assets", [])
        return {asset["name"] for asset in assets if isinstance(asset, dict) and isinstance(asset.get("name"), str)}
    except (AttributeError, json.JSONDecodeError) as error:
        raise RuntimeError("Could not parse beta-archive release assets") from error


def upload_asset(path: Path) -> None:
    gh("upload", ARCHIVE_TAG, str(path), "--clobber")
    print(f"uploaded {path.name}")


def download_release_asset(tag: str, asset: str, destination: Path) -> Path | None:
    destination.mkdir(parents=True, exist_ok=True)
    result = gh(
        "download", tag, "--pattern", asset, "--dir", str(destination), "--clobber", check=False
    )
    candidate = destination / asset
    return candidate if result.returncode == 0 and candidate.is_file() else None


def download_archive_asset(asset: str, destination: Path) -> Path | None:
    return download_release_asset(ARCHIVE_TAG, asset, destination)


def stable_cross_sources(target_code: int, workdir: Path) -> list[dict]:
    """Return the most recent stable releases eligible for a direct patch to this beta,
    newest releases first as returned by the GitHub API."""
    result = run(["gh", "api", f"repos/{repository()}/releases?per_page=10"], check=False)
    if result.returncode != 0:
        print("skip stable cross bases: could not list releases")
        return []
    try:
        releases = json.loads(result.stdout)
    except json.JSONDecodeError:
        print("skip stable cross bases: could not parse the release list")
        return []
    if not isinstance(releases, list):
        print("skip stable cross bases: unexpected release list payload")
        return []

    sources: list[dict] = []
    seen_codes: set[int] = set()
    for release in releases:
        if len(sources) >= STABLE_CROSS_BASES:
            break
        if not isinstance(release, dict) or release.get("prerelease") is not False or release.get("draft") is not False:
            continue
        tag = release.get("tag_name")
        if not isinstance(tag, str) or not STABLE_TAG.fullmatch(tag):
            continue
        assets = {
            asset["name"]
            for asset in release.get("assets", [])
            if isinstance(asset, dict) and isinstance(asset.get("name"), str)
        }
        if "version.json" not in assets:
            print(f"skip {tag}: no version.json release asset")
            continue

        metadata_path = download_release_asset(tag, "version.json", workdir / f"stable-{tag}")
        if metadata_path is None:
            print(f"skip {tag}: version.json could not be downloaded")
            continue
        try:
            stable_metadata = read_json(metadata_path)
        except Exception as error:
            print(f"skip {tag}: invalid version.json ({error})")
            continue

        code = positive_int(stable_metadata.get("versionCode"))
        name = stable_metadata.get("versionName")
        if code is None or not isinstance(name, str) or not VERSION_NAME.fullmatch(name):
            print(f"skip {tag}: invalid version metadata")
            continue
        if tag != f"v{name}":
            print(f"skip {tag}: tag does not match versionName {name}")
            continue
        if code >= target_code:
            print(f"skip {tag}: versionCode is not older than the target")
            continue
        if code in seen_codes:
            continue
        apk_hash = valid_sha(stable_metadata.get("apkSha256"))
        canonical_apk = f"qr-code-simple-{name}.apk"
        if apk_hash is None or canonical_apk not in assets:
            print(f"skip {tag}: no canonical APK or checksum ({canonical_apk})")
            continue

        seen_codes.add(code)
        sources.append({
            "versionCode": code,
            "tag": tag,
            "apk": canonical_apk,
            "apkSha256": apk_hash,
        })
    return sources


def patch_metadata(value: object, from_code: int, to_code: int) -> dict | None:
    if not isinstance(value, dict):
        return None
    expected_name = f"patch-beta-{from_code}-to-{to_code}.patch"
    size = positive_int(value.get("size"))
    patch_hash = valid_sha(value.get("patchSha256"))
    if value.get("file") != expected_name or size is None or patch_hash is None:
        return None
    return {"file": expected_name, "size": size, "patchSha256": patch_hash}


def create_patch(old_apk: Path, new_apk: Path, output_path: Path, new_hash: str, new_size: int) -> dict | None:
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-beta-patch-") as temporary:
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


def ensure_archive() -> None:
    root = git("rev-list", "--max-parents=0", "HEAD").splitlines()[0]
    local_tag = run(["git", "rev-parse", f"{ARCHIVE_TAG}^{{}}"], check=False)
    if local_tag.returncode == 0 and local_tag.stdout.strip() != root:
        raise RuntimeError(
            "beta-archive already points away from the repository root; refusing to move an existing tag"
        )

    release = gh("view", ARCHIVE_TAG, check=False)
    if release.returncode != 0:
        # Creating the release with --target creates beta-archive at the root commit.
        gh(
            "create", ARCHIVE_TAG, "--target", root,
            "--title", "QR Code Simple beta archive",
            "--notes", "Rolling signed beta APK bases and verified delta patches.",
            "--prerelease",
        )
        print("created prerelease beta-archive at the repository root")
    else:
        # An existing archive must stay prerelease so GitHub's /releases/latest remains stable-only.
        gh("edit", ARCHIVE_TAG, "--prerelease")


def sanitize_history(raw_history: object, assets: set[str]) -> dict[int, dict]:
    if not isinstance(raw_history, dict):
        return {}
    history: dict[int, dict] = {}
    for raw_code, raw_entry in raw_history.items():
        try:
            code = int(raw_code)
        except (TypeError, ValueError):
            continue
        if code <= 0 or not isinstance(raw_entry, dict):
            continue
        apk_name = f"beta-{code}.apk"
        apk_hash = valid_sha(raw_entry.get("apkSha256") or raw_entry.get("sha256"))
        apk_size = positive_int(raw_entry.get("apkSize") or raw_entry.get("size"))
        if raw_entry.get("apk") != apk_name or apk_hash is None or apk_size is None or apk_name not in assets:
            continue

        patches: dict[str, dict] = {}
        raw_patches = raw_entry.get("patches")
        if isinstance(raw_patches, dict):
            for raw_from, raw_patch in raw_patches.items():
                try:
                    from_code = int(raw_from)
                except (TypeError, ValueError):
                    continue
                patch = patch_metadata(raw_patch, from_code, code)
                if patch is not None and patch["file"] in assets:
                    patches[str(from_code)] = patch
        history[code] = {
            "apk": apk_name,
            "apkSha256": apk_hash,
            "apkSize": apk_size,
            "patches": patches,
        }
    return history


def managed_assets(entry: dict) -> set[str]:
    assets = {entry["apk"]}
    for patch in entry.get("patches", {}).values():
        if isinstance(patch, dict) and isinstance(patch.get("file"), str):
            assets.add(patch["file"])
    return assets


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=Path("version.json"))
    parser.add_argument("--apk", type=Path, default=Path("qr-code-simple-beta.apk"))
    parser.add_argument(
        "--archive-full-only",
        action="store_true",
        help="upload the full beta APK to beta-archive without generating patches",
    )
    arguments = parser.parse_args()

    metadata = read_json(arguments.metadata)
    version_code = positive_int(metadata.get("versionCode"))
    version_name = metadata.get("versionName")
    if version_code is None or not isinstance(version_name, str) or not VERSION_NAME.fullmatch(version_name):
        raise RuntimeError("version.json must contain a positive versionCode and a numeric versionName")
    if arguments.apk.name != "qr-code-simple-beta.apk" or not arguments.apk.is_file():
        raise RuntimeError("Expected beta APK named qr-code-simple-beta.apk")

    new_hash = sha256(arguments.apk)
    new_size = arguments.apk.stat().st_size
    metadata["channel"] = "beta"
    metadata["apkFile"] = arguments.apk.name
    metadata["apkSha256"] = new_hash
    metadata["apkSize"] = new_size
    metadata.setdefault("patches", {})
    metadata.setdefault("chains", {})
    # This is deliberately written before any remote operation so full beta metadata survives failures.
    write_json(arguments.metadata, metadata)

    if shutil.which("gh") is None:
        raise RuntimeError("gh is required to maintain beta-archive")
    ensure_archive()
    assets = archive_assets()

    archive_apk = f"beta-{version_code}.apk"
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-beta-full-") as temporary:
        staged_apk = Path(temporary) / archive_apk
        shutil.copyfile(arguments.apk, staged_apk)
        upload_asset(staged_apk)

    if arguments.archive_full_only:
        return

    if not Path(ZIPDIFF).is_file() or not Path(ZIPPATCH).is_file():
        print(f"ZipDiff/ZipPatch unavailable (APKDIFF_BIN='{APKDIFF_BIN}'); publishing full beta metadata without patches")
        return

    history: dict[int, dict] = {}
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-beta-history-") as temporary:
        working_dir = Path(temporary)
        history_path = download_archive_asset(HISTORY_FILE, working_dir / "metadata")
        if history_path is not None:
            try:
                history = sanitize_history(read_json(history_path), assets)
            except Exception as error:
                print(f"ignoring invalid beta history: {error}")

        patches: dict[str, dict] = {}
        previous_codes = sorted(code for code in history if code < version_code)
        for backoff in BACKOFF:
            if backoff > len(previous_codes):
                continue
            source_code = previous_codes[-backoff]
            source = history[source_code]
            old_apk = download_archive_asset(source["apk"], working_dir / f"apk-{source_code}")
            if old_apk is None:
                print(f"skip beta {source_code}: archived APK could not be downloaded")
                continue
            if sha256(old_apk) != source["apkSha256"]:
                print(f"skip beta {source_code}: archived APK hash disagrees with beta history")
                continue
            if not apk_has_native_lib(old_apk):
                print(f"skip beta {source_code}: old APK has no libapkpatch.so, full download only")
                continue
            patch_path = Path(f"patch-beta-{source_code}-to-{version_code}.patch")
            patch = create_patch(old_apk, arguments.apk, patch_path, new_hash, new_size)
            if patch is not None:
                patches[str(source_code)] = patch
                print(f"created {patch_path.name} ({patch['size']} bytes)")

        cross_hashes: dict[int, str] = {}
        for source in stable_cross_sources(version_code, working_dir):
            source_code = source["versionCode"]
            old_apk = download_release_asset(
                source["tag"], source["apk"], working_dir / f"apk-{source_code}"
            )
            if old_apk is None:
                print(f"skip {source['tag']}: canonical APK could not be downloaded")
                continue
            if sha256(old_apk) != source["apkSha256"]:
                print(f"skip {source['tag']}: APK hash disagrees with version.json")
                continue
            if not apk_has_native_lib(old_apk):
                print(f"skip {source['tag']}({source_code}): old APK has no libapkpatch.so, full download only")
                continue
            patch_path = Path(f"patch-beta-{source_code}-to-{version_code}.patch")
            patch = create_patch(old_apk, arguments.apk, patch_path, new_hash, new_size)
            if patch is not None:
                patches[str(source_code)] = patch
                cross_hashes[source_code] = source["apkSha256"]
                print(f"created {patch_path.name} ({patch['size']} bytes)")

        for patch in patches.values():
            upload_asset(Path(patch["file"]))

        history[version_code] = {
            "apk": archive_apk,
            "apkSha256": new_hash,
            "apkSize": new_size,
            "patches": patches,
        }
        other_codes = sorted(code for code in history if code != version_code)
        keep_codes = set(other_codes[-(MAX_KEEP - 1):])
        keep_codes.add(version_code)
        kept_history = {code: history[code] for code in sorted(keep_codes)}
        pruned_history = {code: entry for code, entry in history.items() if code not in keep_codes}

        # Single-hop direct chains: from version -> current version.
        source_hashes: dict[int, str] = {}
        for code, entry in history.items():
            entry_hash = valid_sha(entry.get("apkSha256"))
            if entry_hash is not None:
                source_hashes[code] = entry_hash
        source_hashes.update(cross_hashes)

        chains: dict[str, dict] = {}
        for from_code, patch in patches.items():
            source_hash = source_hashes.get(int(from_code))
            if source_hash is None:
                continue
            chains[from_code] = {
                "fromApkSha256": source_hash,
                "totalSize": patch["size"],
                "hops": [
                    {
                        "toVersionCode": version_code,
                        "url": f"https://github.com/{repository()}/releases/download/{ARCHIVE_TAG}/{patch['file']}",
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

        local_history = Path(HISTORY_FILE)
        write_json(local_history, {str(code): entry for code, entry in kept_history.items()})
        upload_asset(local_history)

        protected_assets = {HISTORY_FILE, archive_apk}
        for entry in kept_history.values():
            protected_assets.update(managed_assets(entry))
        for code, entry in pruned_history.items():
            for asset in managed_assets(entry):
                if asset in protected_assets:
                    print(f"keep protected beta asset {asset}")
                    continue
                result = gh("delete-asset", ARCHIVE_TAG, asset, "--yes", check=False)
                if result.returncode == 0:
                    print(f"pruned beta {code} asset {asset}")
                else:
                    print(f"could not prune beta {code} asset {asset}")

    print(f"beta: {len(patches)} direct patch(es), {len(chains)} upgrade chain(s)")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Beta delta generation failed: {error}", file=sys.stderr)
        sys.exit(1)
