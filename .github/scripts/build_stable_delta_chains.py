#!/usr/bin/env python3
"""Build verified stable APK delta patches and flattened upgrade chains.

Only releases with both version.json and the canonical qr-code-simple-<version>.apk
asset participate. This deliberately ignores the historical app-release.apk-only
releases so a legacy asset can never be mistaken for a compatible delta base.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


RELEASE_TAG = re.compile(r"^v\d+\.\d+\.\d+$")
VERSION_NAME = re.compile(r"^\d+\.\d+\.\d+(?:\+\d+)?$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


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


def patch_metadata(value: object, from_code: int, to_code: int) -> dict | None:
    if not isinstance(value, dict):
        return None
    expected_name = f"patch-{from_code}-to-{to_code}.bspatch"
    size = positive_int(value.get("size"))
    patch_hash = valid_sha(value.get("patchSha256"))
    if value.get("file") != expected_name or size is None or patch_hash is None:
        return None
    return {"file": expected_name, "size": size, "patchSha256": patch_hash}


def create_patch(old_apk: Path, new_apk: Path, output_path: Path, new_hash: str, new_size: int) -> dict | None:
    with tempfile.TemporaryDirectory(prefix="qr-code-simple-stable-patch-") as temporary:
        temporary_dir = Path(temporary)
        candidate = temporary_dir / output_path.name
        verified = temporary_dir / "verified.apk"
        try:
            run(["bsdiff", str(old_apk), str(new_apk), str(candidate)])
            run(["bspatch", str(old_apk), str(verified), str(candidate)])
            if sha256(verified) != new_hash:
                print(f"drop {output_path.name}: bspatch verification hash mismatch")
                return None
            if candidate.stat().st_size >= new_size:
                print(f"drop {output_path.name}: patch is not smaller than the target APK")
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
    # Preserve a complete full-download manifest even if GitHub or bsdiff later fails.
    write_json(arguments.metadata, metadata)

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
                "patches": historical_metadata.get("patches")
                if isinstance(historical_metadata.get("patches"), dict) else {},
            })

        history.sort(key=lambda item: item["versionCode"])
        patches: dict[str, dict] = {}
        if shutil.which("bsdiff") is None or shutil.which("bspatch") is None:
            print("bsdiff/bspatch unavailable; publishing full APK metadata without new stable patches")
        else:
            backoff = 1
            while backoff <= len(history):
                source = history[-backoff]
                source_code = source["versionCode"]
                old_apk = download_release_asset(
                    source["tag"], source["apk"], history_dir / f"apk-{source_code}"
                )
                if old_apk is None:
                    print(f"skip {source['tag']}: canonical APK could not be downloaded")
                    backoff *= 2
                    continue
                old_hash = sha256(old_apk)
                if source["apkSha256"] is not None and source["apkSha256"] != old_hash:
                    print(f"skip {source['tag']}: APK hash disagrees with version.json")
                    backoff *= 2
                    continue
                source["apkSha256"] = old_hash

                patch_path = Path(f"patch-{source_code}-to-{version_code}.bspatch")
                patch = create_patch(old_apk, new_apk, patch_path, new_hash, new_size)
                if patch is not None:
                    patches[str(source_code)] = patch
                    print(f"created {patch_path.name} ({patch['size']} bytes)")
                backoff *= 2

    nodes = history + [{
        "versionCode": version_code,
        "tag": tag,
        "apkSha256": new_hash,
        "patches": patches,
    }]
    chains: dict[str, dict] = {}
    for start_index, source in enumerate(nodes[:-1]):
        source_hash = source.get("apkSha256")
        if valid_sha(source_hash) is None:
            continue
        current_index = start_index
        hops: list[dict] = []
        complete = True
        while current_index < len(nodes) - 1:
            selected: tuple[int, dict] | None = None
            step = 1
            while current_index + step < len(nodes):
                target_index = current_index + step
                candidate = patch_metadata(
                    nodes[target_index].get("patches", {}).get(str(nodes[current_index]["versionCode"])),
                    nodes[current_index]["versionCode"],
                    nodes[target_index]["versionCode"],
                )
                if candidate is not None:
                    selected = (target_index, candidate)
                step *= 2
            if selected is None:
                complete = False
                break
            target_index, patch = selected
            target = nodes[target_index]
            target_hash = valid_sha(target.get("apkSha256"))
            if target_hash is None:
                complete = False
                break
            hops.append({
                "toVersionCode": target["versionCode"],
                "url": f"https://github.com/{repository()}/releases/download/{target['tag']}/{patch['file']}",
                "size": patch["size"],
                "patchSha256": patch["patchSha256"],
                "resultSha256": target_hash,
            })
            current_index = target_index
        if complete and hops:
            chains[str(source["versionCode"])] = {
                "fromApkSha256": source_hash,
                "totalSize": sum(hop["size"] for hop in hops),
                "hops": hops,
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
