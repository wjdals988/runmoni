"""Validate actual opaque UI tokens against text/control contrast thresholds."""
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/runmoni/app/ui/RunmoniTheme.kt").read_text(encoding="utf-8")
colors = dict(re.findall(r"val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", source))

def luminance(value):
    rgb = [int(value[i:i+2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))

checks = [
    ("Navy", "Cream", 4.5), ("Muted", "Paper", 4.5), ("Muted", "Mint", 4.5),
    ("OnDark", "Primary", 4.5), ("OnDark", "Navy", 4.5),
    ("OnDarkMuted", "Navy", 4.5), ("Lime", "Navy", 4.5),
    ("Primary", "Mint", 3), ("Outline", "Paper", 3),
]
results = []
for foreground, background, minimum in checks:
    high, low = sorted((luminance(colors[foreground]), luminance(colors[background])), reverse=True)
    contrast = (high + 0.05) / (low + 0.05)
    results.append(dict(foreground=foreground, background=background, contrast=round(contrast, 3), minimum=minimum, passed=contrast >= minimum))
print(json.dumps({"checks": results, "passed": sum(r["passed"] for r in results), "total": len(results)}))
raise SystemExit(0 if all(r["passed"] for r in results) else 1)
