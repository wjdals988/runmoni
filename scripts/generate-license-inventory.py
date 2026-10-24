"""Build a bundled inventory from resolved release artifacts and cached POMs only."""
from __future__ import annotations
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "work/gradle-home/caches/modules-2/files-2.1"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def metadata(group: str, name: str, version: str, seen: frozenset = frozenset()):
    key = (group, name, version)
    if key in seen:
        return [], "", ""
    paths = sorted((CACHE / group / name / version).glob("*/*.pom"))
    if not paths:
        return [], "", ""
    root = ET.parse(paths[0]).getroot()
    # Maven POMs normally use this namespace; a few older publishers omit it.
    if not root.tag.startswith("{"):
        for element in root.iter():
            element.tag = "{http://maven.apache.org/POM/4.0.0}" + element.tag
    value = lambda element, tag: (element.findtext(f"m:{tag}", default="", namespaces=NS) or "").strip()
    licenses = [(value(item, "name"), value(item, "url")) for item in root.findall("m:licenses/m:license", NS)]
    homepage = value(root, "url") or value(root.find("m:scm", NS), "url") if root.find("m:scm", NS) is not None else value(root, "url")
    developers = ", ".join(value(item, "name") for item in root.findall("m:developers/m:developer", NS) if value(item, "name"))
    parent = root.find("m:parent", NS)
    if not licenses and parent is not None:
        inherited, parent_url, parent_developers = metadata(value(parent, "groupId"), value(parent, "artifactId"), value(parent, "version"), seen | {key})
        licenses = inherited
        homepage = homepage or parent_url
        developers = developers or parent_developers
    return licenses, homepage, developers


def notices(path: Path):
    found = []
    def scan(archive, prefix=""):
        for item in archive.infolist():
            if re.search(r"(?:^|/)(?:LICENSE|NOTICE|COPYING)(?:[._-].*)?$", item.filename, re.I) and item.file_size <= 128 * 1024:
                text = archive.read(item).decode("utf-8", errors="replace").strip()
                if text:
                    found.append(f"{prefix}{item.filename}\n{text}")
    with zipfile.ZipFile(path) as artifact:
        scan(artifact)
        if path.suffix == ".aar" and "classes.jar" in artifact.namelist():
            with zipfile.ZipFile(io.BytesIO(artifact.read("classes.jar"))) as classes:
                scan(classes, "classes.jar/")
    return "\n\n".join(dict.fromkeys(found))


def main():
    coordinates = json.loads((ROOT / "work/license-coordinates.json").read_text(encoding="utf-8"))
    entries = []
    unknown = []
    for item in coordinates:
        group, name, version = (item[key] for key in ("group", "name", "version"))
        licenses, homepage, developers = metadata(group, name, version)
        license_name = "; ".join(name for name, _ in licenses)
        url = next((url for _, url in licenses if url.startswith(("https://", "http://"))), homepage)
        extra = notices(Path(item["file"]))
        if group == "com.naver.maps":
            license_name = "NAVER Maps SDK 별도 이용약관"
            url = "https://navermaps.github.io/android-map-sdk/guide-ko/0.html"
        if not license_name:
            license_name = "제공자 라이선스 안내"
            unknown.append(f"{group}:{name}:{version}")
        if url.startswith("http://"):
            url = "https://" + url.removeprefix("http://")
        note = f"{group}:{name}:{version}\n"
        if developers:
            note += f"제공자: {developers}\n"
        note += f"POM 배포 메타데이터: {license_name}\n"
        note += "\n" + extra if extra else "\n이 아티팩트 안에 독립 LICENSE/NOTICE 텍스트가 없어 배포 메타데이터와 제공자 원문을 안내합니다."
        entries.append({"name": f"{group}:{name} {version}", "license": license_name, "url": url, "notice": note})
    assets = ROOT / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    (assets / "open-source-licenses.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = ["# Third-party libraries and SDK notices", "", "Generated offline from the resolved releaseRuntimeClasspath, cached POM metadata and artifact LICENSE/NOTICE entries.", "NAVER Maps and Google SDK terms may be separate from open-source licenses. This inventory is not a legal compliance certification.", "", "| Runtime artifact | Published license / terms | Source |", "|---|---|---|"]
    for entry in entries:
        link = f"[Provider notice]({entry['url']})" if entry["url"] else "Artifact notice bundled in app"
        report.append(f"| {entry['name']} | {entry['license']} | {link} |")
    report += ["", "Full artifact notices found in the cache are bundled in app/src/main/assets/open-source-licenses.json and shown offline in the app.", "", f"Artifacts: {len(entries)}; without a declared license name in the available cached POM chain: {len(unknown)}."]
    if unknown:
        report += ["", "## Metadata gaps", ""] + [f"- {item}" for item in unknown]
    (ROOT / "THIRD_PARTY_NOTICES.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print(json.dumps({"artifacts": len(entries), "metadata_gaps": unknown, "bundled_bytes": (assets / "open-source-licenses.json").stat().st_size}, ensure_ascii=False))


if __name__ == "__main__":
    main()
