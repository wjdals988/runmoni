#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"
output="${1:-work/avatar-reference-review}"
mkdir -p "$output/classes"
python3 - <<'PY'
import hashlib, json
from pathlib import Path
assets = Path('app/src/main/assets/avatars/reference-review')
manifest = assets / 'motion-manifest.json'
if manifest.exists():
    for name, digest in json.loads(manifest.read_text())['assets'].items():
        assert hashlib.sha256((assets/name).read_bytes()).hexdigest() == digest, name
    print('Motion source/mask hashes verified')
PY
if command -v javac >/dev/null 2>&1; then
  compiler=(javac --release 17)
elif [ -n "${ECJ_JAR:-}" ] && [ -f "$ECJ_JAR" ]; then
  compiler=(java -jar "$ECJ_JAR" -17)
else
  echo 'Requires a JDK (javac) or ECJ_JAR pointing to a verified Eclipse Java compiler.' >&2
  exit 1
fi
"${compiler[@]}" -d "$output/classes" \
  app/src/main/java/com/runmoni/app/avatar/GarmentPalette.java \
  scripts/avatar/host-preview/android/graphics/Bitmap.java \
  scripts/avatar/host-preview/PreviewImages.java \
  scripts/avatar/host-preview/GarmentPaletteCheck.java \
  scripts/avatar/host-preview/MotionLayerCheck.java
java -Xmx512m -Djava.awt.headless=true -cp "$output/classes" \
  com.runmoni.hostpreview.GarmentPaletteCheck "$repo_root" "$output"
if [ -f app/src/main/assets/avatars/reference-review/motion-manifest.json ]; then
  java -Xmx512m -Djava.awt.headless=true -cp "$output/classes" \
    com.runmoni.hostpreview.MotionLayerCheck "$repo_root/app/src/main/assets/avatars/reference-review" "$output"
fi
