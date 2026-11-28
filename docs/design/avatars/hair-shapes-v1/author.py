"""Export three hairstyles against the approved motion cells with Aseprite."""
from argparse import ArgumentParser
from hashlib import sha256
import json
from pathlib import Path
from subprocess import run
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "atelier-v3"))
from export import SOURCE_SHA, ase_project, details_from_sheet, files_in, same_visible
from production import ANCHORS, POSES

ROOT = HERE.parents[3]
SOURCE = ROOT / "app/src/main/assets/avatars/v0.3.0/running-motion.png"
SIZE = (256, 384)
GENDERS = ("male", "female")
STYLES = {4: "Slim short", 5: "Side part", 6: "Low bun"}
FACES = (
    ((-17, -44), (22, -55), (39, -55), (65, -43), (79, -20), (79, 27),
     (46, 49), (4, 50), (-25, 28), (-34, 12), (-33, -12)),
    ((-16, -33), (18, -45), (38, -48), (64, -35), (74, -16), (74, 29),
     (39, 48), (2, 47), (-26, 26), (-34, 9), (-32, -10)),
)
HAIR_LIMITS = (
    ((-95, -115), (-50, -132), (22, -132), (78, -111), (97, -69), (86, -17),
     (56, -24), (51, -39), (25, -48), (12, -27), (-21, -9), (-36, -12),
     (-51, -16), (-67, -9), (-82, -24), (-95, -54)),
    ((-150, -104), (-112, -123), (-49, -122), (9, -110), (58, -94), (83, -60),
     (82, -17), (66, -17), (54, -39), (37, -49), (17, -38), (-22, -7),
     (-34, -13), (-54, -13), (-67, -3), (-81, 23), (-127, 25), (-150, -1)),
)
PALETTES = {
    4: ("#151a2b", "#232838", "#313746", "#444b5a", "#59606e"),
    5: ("#1d1827", "#37272b", "#52363a", "#6a4747", "#87615b"),
    6: ("#191828", "#2d2431", "#43313d", "#594152", "#725969"),
}
SLIM = ((-76,-50),(-74,-65),(-63,-76),(-46,-85),(-27,-89),(-6,-89),(14,-86),
        (36,-79),(51,-68),(59,-58),(59,-43),(50,-47),(41,-57),(29,-53),(12,-43),
        (-2,-31),(-23,-25),(-31,-17),(-35,-22),(-50,-22),(-58,-14),(-62,-14),(-71,-25))
PART = ((-79,-52),(-77,-73),(-67,-87),(-50,-97),(-27,-104),(-10,-104),(-2,-102),(4,-110),
        (23,-111),(44,-105),(62,-94),(73,-78),(79,-58),(77,-45),(68,-43),(62,-50),
        (55,-61),(45,-65),(33,-64),(20,-54),(5,-40),(-13,-28),(-28,-22),(-34,-15),
        (-42,-22),(-52,-22),(-59,-14),(-67,-16),(-76,-29))
BUN_CROWN = ((-78,-57),(-72,-73),(-60,-85),(-43,-93),(-19,-98),(6,-95),(31,-88),
             (48,-78),(62,-63),(66,-45),(60,-40),(53,-54),(44,-59),(30,-56),(13,-46),
             (-6,-34),(-26,-26),(-33,-15),(-41,-21),(-50,-20),(-61,-13),(-71,-20),(-77,-33))
BUN_NAPE = ((-78,-46),(-89,-48),(-103,-43),(-112,-33),(-113,-19),(-107,-9),(-96,-3),
            (-85,-4),(-76,-10),(-67,-17),(-65,-27),(-69,-36))


def source_frames():
    if sha256(SOURCE.read_bytes()).hexdigest() != SOURCE_SHA:
        raise ValueError("The approved sprite source changed")
    grid = Image.open(SOURCE).convert("RGBA")
    return [grid.crop((frame * 256, gender * 384, (frame + 1) * 256, (gender + 1) * 384))
            for gender, frame in POSES]


def polygon(mask, origin, points, fill):
    ImageDraw.Draw(mask).polygon([(origin[0] + x, origin[1] + y) for x, y in points], fill=fill)


def head_masks(base, pose):
    gender, frame = pose
    eye = ANCHORS[gender][frame][:2]
    area, face = Image.new("L", SIZE), Image.new("L", SIZE)
    polygon(area, eye, HAIR_LIMITS[gender], 255)
    polygon(face, eye, FACES[gender], 255)
    # Keep the rim, shadow, and visible skin of the original ear.
    ImageDraw.Draw(face).rectangle((eye[0] - 61, eye[1] - 8, eye[0] - 27, eye[1] + 31), fill=255)
    erase = Image.new("L", SIZE)
    guard = Image.new("L", SIZE)
    for y in range(14 if gender else 8, eye[1] + 26):
        for x in range(8, 249):
            r, g, b, alpha = base.getpixel((x, y))
            skin = r >= 147 and g >= 52 and r - b >= 49
            if alpha and (skin or face.getpixel((x, y))):
                guard.putpixel((x,y), 255)
            elif alpha and area.getpixel((x, y)):
                erase.putpixel((x,y), 255)
    return erase, guard


def paint(pose, variant):
    gender, frame = pose
    x, y = ANCHORS[gender][frame][:2]
    # Two-pixel clusters align to each eye reference, not to an animated canvas grid.
    reference = (74, 64)
    work = Image.new("RGBA", (148, 144))
    ink, base, mid, light, shine = PALETTES[variant]
    nape, crown, volume, accents = (Image.new("RGBA", work.size) for _ in range(4))

    def draw(layer, points, color):
        polygon(layer, reference, ((px // 2, py // 2) for px, py in points), color)

    if variant == 4:
        draw(crown, SLIM, ink)
        draw(crown, ((-70,-52),(-67,-65),(-56,-74),(-42,-82),(-23,-85),(13,-82),
               (34,-75),(49,-65),(54,-54),(43,-63),(29,-59),(10,-49),(-10,-34),(-27,-30),
               (-37,-26),(-53,-26),(-62,-20),(-68,-28)), base)
        draw(volume, ((-65,-66),(-49,-76),(-28,-82),(-7,-82),(17,-77),(29,-72),
               (7,-73),(-16,-67),(-40,-57),(-60,-47),(-68,-49)), mid)
        draw(volume, ((-26,-70),(-5,-75),(18,-73),(31,-69),(15,-66),(-5,-62),(-27,-53),(-43,-54)), light)
        draw(accents, ((-26,-69),(-9,-73),(6,-73),(13,-71),(-5,-68),(-17,-65)), shine)
        draw(volume, ((-65,-44),(-58,-48),(-34,-49),(-22,-46),(-45,-40),(-61,-35),(-66,-36)), mid)
        draw(accents, ((-65,-33),(-56,-37),(-48,-37),(-53,-31),(-61,-29)), mid)
    elif variant == 5:
        draw(crown, PART, ink)
        draw(crown, ((-75,-57),(-70,-75),(-60,-85),(-46,-92),(-25,-98),(-9,-99),(-3,-94),
               (6,-104),(25,-106),(43,-100),(60,-90),(69,-77),(74,-59),(72,-49),(65,-58),
               (55,-66),(44,-71),(30,-69),(13,-57),(-6,-39),(-26,-29),(-40,-27),(-54,-26),(-65,-21),(-73,-32)), base)
        draw(volume, ((-71,-58),(-64,-76),(-51,-87),(-33,-94),(-13,-97),(-4,-94),(-11,-85),
               (-27,-78),(-43,-68),(-59,-56),(-72,-50)), mid)
        draw(volume, ((-61,-76),(-49,-87),(-28,-93),(-16,-91),(-25,-84),(-43,-78),(-56,-70)), light)
        draw(volume, ((1,-95),(11,-104),(28,-105),(44,-99),(59,-90),(69,-76),(70,-66),
               (58,-73),(40,-76),(26,-72),(10,-61),(-9,-42),(-29,-32),(-36,-34),(-22,-47),(-4,-66)), mid)
        draw(volume, ((5,-94),(16,-101),(29,-101),(44,-96),(57,-87),(62,-80),(46,-84),
               (29,-85),(12,-81),(-4,-72)), light)
        draw(accents, ((11,-96),(22,-100),(38,-96),(46,-92),(35,-93),(22,-92),(11,-90)), shine)
        draw(accents, ((-8,-93),(-1,-95),(1,-91),(-10,-81),(-18,-71),(-21,-72),(-18,-80)), "#ae7b64")
    else:
        # A compact knot settles alternately toward the nape or back during the stride.
        nape_bob = (0, 2, 2, 0, -2, 0, 2, 0)[frame]
        draw(nape, ((a, b+nape_bob) for a, b in BUN_NAPE), ink)
        draw(nape, ((a,b+nape_bob) for a,b in ((-80,-42),(-90,-43),(-102,-39),(-107,-31),
               (-108,-21),(-103,-12),(-95,-8),(-85,-10),(-76,-15),(-71,-24),(-72,-33))), base)
        draw(nape, ((a,b+nape_bob) for a,b in ((-105,-32),(-97,-39),(-85,-40),(-79,-35),
               (-91,-32),(-99,-26),(-104,-21),(-107,-24))), mid)
        draw(nape, ((a,b+nape_bob) for a,b in ((-102,-34),(-94,-38),(-85,-38),(-82,-35),
               (-91,-33),(-97,-29),(-102,-28))), light)
        draw(crown, BUN_CROWN, ink)
        draw(crown, ((-74,-55),(-68,-71),(-55,-82),(-39,-89),(-19,-93),(6,-90),(29,-84),
               (45,-75),(57,-63),(60,-51),(51,-59),(41,-63),(28,-60),(10,-51),(-9,-40),
               (-29,-31),(-36,-26),(-51,-26),(-63,-22),(-70,-25)), base)
        draw(volume, ((-69,-61),(-59,-74),(-43,-83),(-22,-89),(-3,-88),(17,-84),(33,-78),
               (28,-74),(12,-77),(-5,-76),(-23,-69),(-46,-56),(-65,-43),(-72,-43)), mid)
        draw(volume, ((-53,-73),(-36,-82),(-16,-87),(2,-86),(12,-83),(2,-80),(-22,-77),(-39,-70),(-52,-66)), light)
        draw(accents, ((-42,-78),(-25,-84),(-10,-85),(-2,-83),(-20,-81),(-34,-76)), shine)
        draw(accents, ((-79,-40),(-76,-41),(-73,-37),(-76,-29),(-75,-23),(-80,-20),(-82,-23)), "#295a63")
        draw(accents, ((-79,-40),(-75,-39),(-76,-35),(-79,-32)), "#56868b")

    layers = []
    for name, image in (("Nape and knot", nape), ("Cut silhouette", crown), ("Hair volume", volume), ("Part and light", accents)):
        expanded = image.resize((296, 288), Image.Resampling.NEAREST)
        framed = Image.new("RGBA", SIZE)
        framed.paste(expanded, (x - 2 * reference[0], y - 2 * reference[1]))
        layers.append((name, framed))
    return layers


def visible(layers):
    result = Image.new("RGBA", SIZE)
    for _, layer in layers:
        result.alpha_composite(layer)
    return result


def layered_frames(base, pose, variant):
    erase, guard = head_masks(base, pose)
    isolated = base.copy()
    isolated.paste((0,0,0,0), (0,0,*SIZE), erase)
    blocked = ImageChops.lighter(isolated.getchannel("A").point(lambda a: 255 if a else 0), guard)
    # Render only in transparent background or inside the explicitly removable hair.
    art = paint(pose, variant)
    for name, layer in art:
        layer.putalpha(ImageChops.subtract(layer.getchannel("A"), blocked))
    near_skin = ImageChops.multiply(erase, guard.filter(ImageFilter.MaxFilter(27)))
    color = PALETTES[variant]
    seam = Image.new("RGBA", SIZE, color[1])
    seam.putalpha(near_skin)
    rim = Image.new("RGBA", SIZE, color[0])
    rim.putalpha(ImageChops.multiply(erase, guard.filter(ImageFilter.MaxFilter(7))))
    seam.alpha_composite(rim)
    art.insert(0, ("Hairline contact shadow", seam))
    removal = Image.new("RGBA", SIZE, "#ece3ac")
    removal.putalpha(erase)
    # Preserve skin/tail/body and make the cut mask editable in the native project.
    return [("Approved reference", base), ("Approved face and body", isolated), *art,
            ("Approved hair removal", removal)]


def compose(base, ink, cut=None):
    result = base.copy()
    if cut is not None:
        result.paste((0, 0, 0, 0), (0, 0, *SIZE), cut.getchannel("A"))
    result.alpha_composite(ink)
    return result


def old_ink(asset_root, pose, variant):
    gender, frame = pose
    path = asset_root / "atelier-v2" / f"hair-{variant}" / f"{GENDERS[gender]}-{frame}.png"
    return Image.open(path).convert("RGBA")


def board(bases, directory, output):
    asset_root = SOURCE.parents[1]
    background = "#f5f2e9"
    fonts = "/usr/share/fonts/truetype/dejavu/DejaVuSans"
    font = ImageFont.truetype(fonts + ".ttf", 14)
    title = ImageFont.truetype(fonts + "-Bold.ttf", 25)
    canvas = Image.new("RGB", (896, 596), background)
    d = ImageDraw.Draw(canvas)
    d.text((24, 17), "RunMoni · Hair silhouettes", fill="#1e2d39", font=title)
    d.text((25, 52), "Approved face, same clothes and poses. Tiles rendered at 64 px.", fill="#455663", font=font)
    for gender in range(2):
        x0 = 24 + gender * 433
        d.text((x0, 83), GENDERS[gender].capitalize(), fill="#1e2d39", font=font)
        for i, variant in enumerate((0, 4, 5, 6)):
            x = x0 + i * 104
            for row, frame in enumerate((0, 0, 3, 5)):
                pose = (gender, frame)
                base = bases[gender * 8 + frame]
                if variant:
                    stroke = old_ink(asset_root, pose, variant) if row == 0 else directory[variant, pose][0]
                    shape = compose(base, stroke, None if row == 0 else directory[variant, pose][1])
                else:
                    shape = base
                scene = "#e4e8e4" if row in (0, 1) else "#23343c"
                y = 110 + row * 108
                d.rounded_rectangle((x,y,x+96,y+84), radius=7, fill=scene)
                # Use the actual closet wide head crop for these 64px previews.
                cropped = shape.crop((10, 14, 246, 194))
                cropped.thumbnail((64,64),Image.Resampling.NEAREST)
                canvas.paste(cropped,(x+(96-cropped.width)//2,y+(84-cropped.height)//2),cropped)
            label = STYLES.get(variant, "Approved")
            d.text((x + 3, 557), label, fill="#24343e", font=font)
    for index,line in enumerate(("Before · frame 0","Updated · frame 0","Updated · frame 3","Updated · frame 5")):
        d.text((25,196 + index*108),line,fill="#526c6f",font=font)
    canvas.save(output)


def preview(bases, output):
    cels = {}
    for variant in STYLES:
        for pose, base in zip(POSES, bases, strict=True):
            layers = layered_frames(base, pose, variant)
            cels[variant, pose] = (visible(layers[2:-1]), layers[-1][1])
    board(bases, cels, output)
    return cels


def verify(bases, cels):
    minimum_changed = SIZE[0] * SIZE[1]
    minimum_outline = minimum_changed
    for pose, source in zip(POSES, bases, strict=True):
        gender, frame = pose
        ex, ey = ANCHORS[gender][frame][:2]
        before = list(source.getdata())
        silhouettes = [source.getchannel("A").point(lambda a: 255 if a >= 128 else 0)]
        for variant in STYLES:
            ink, cut = cels[variant, pose]
            after = list(compose(source, ink, cut).getdata())
            changed = 0
            for i, (a, b) in enumerate(zip(before, after, strict=True)):
                if a == b or a[3] == b[3] == 0:
                    continue
                x, y = i % SIZE[0], i // SIZE[0]
                r, g, blue, alpha = a
                if not 8 <= x < 255 or not 14 <= y < ey + 26 or alpha and r >= 147 and g >= 52 and r - blue >= 49:
                    raise ValueError(f"Hair changed source face or body for {variant}/{pose} at {x},{y}")
                changed += 1
            minimum_changed = min(minimum_changed, changed)
            silhouettes.append(compose(source, ink, cut).getchannel("A").point(lambda a: 255 if a >= 128 else 0))
        for i, first in enumerate(silhouettes):
            for second in silhouettes[:i]:
                pixels = ImageChops.difference(first, second).histogram()[255]
                minimum_outline = min(minimum_outline, pixels)
    if minimum_changed < 500 or minimum_outline < 340:
        raise ValueError("Insufficient coverage or pairwise silhouette separation.")
    print(f"48 poses; minimum changed {minimum_changed}px; minimum pairwise outline difference {minimum_outline}px")


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--aseprite", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    bases = source_frames()
    cels = preview(bases, args.output/"hair-wardrobe.png")
    verify(bases, cels)
    if args.aseprite is None:
        return
    projects = args.output/"projects"
    assets = args.output/"assets"
    staging = args.output/"staging"
    for path in (projects, assets, staging):
        path.mkdir(exist_ok=True)
    for variant in STYLES:
        name = f"hair-{variant}"
        item = assets/name
        item.mkdir(exist_ok=True)
        layers = [layered_frames(base, pose, variant) for pose, base in zip(POSES,bases,strict=True)]
        native, draft = projects/f"{name}.aseprite", staging/f"{name}.aseprite"
        sheet, metadata = staging/f"{name}.png", staging/f"{name}.json"
        draft.write_bytes(ase_project(layers,hidden_layers=("Approved reference","Approved hair removal")))
        run([args.aseprite,"-b",draft,"--save-as",native],check=True)
        run([args.aseprite,"-b",native,"--sheet",sheet,"--data",metadata],check=True)
        for pose, native_frame, base in zip(POSES,details_from_sheet(sheet,metadata),bases,strict=True):
            detail, cut = cels[variant,pose]
            if not same_visible(native_frame,compose(base,detail,cut)):
                raise ValueError(f"Aseprite changed visible ink for {name} {pose}")
            gender, frame = pose
            stem = f"{GENDERS[gender]}-{frame}"
            detail.save(item/f"{stem}.png",optimize=True)
            cut.save(item/f"{stem}-cut.png",optimize=True)
    manifest = {"schemaVersion":1,"width":256,"height":384,"rows":2,"frames":8,
                "items":len(STYLES),"sourceSha256":SOURCE_SHA,"files":files_in(assets)}
    (assets/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")


if __name__ == "__main__":
    main()
