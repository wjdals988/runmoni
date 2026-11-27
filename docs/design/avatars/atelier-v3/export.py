"""Author, save and verify the pose-bound RunMoni Aseprite artwork."""
from argparse import ArgumentParser
from hashlib import sha256
import json
from pathlib import Path
from struct import pack, unpack_from
from subprocess import run
from zlib import compress

from PIL import Image, ImageDraw, ImageFont

from paint import ASSET, merged
from production import ANCHORS, ITEMS, POSES, ROLES, asset, sprite_source, tint_cel

SOURCE_SHA = 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
GENDERS = ('male', 'female')
SIZE = (256, 384)
RUN_SETS = (
    (('레이스','Race'), ('top-10', 'bottom-9', 'shoes-8', 'headwear-7', 'waist-7'), '#eddcd0'),
    (('야간 도심','Night'), ('top-11', 'bottom-10', 'shoes-9', 'headwear-8', 'eyewear-7'), '#dce9e7'),
    (('트레일','Trail'), ('top-12', 'bottom-11', 'shoes-10', 'headwear-9', 'hydration-7'), '#ebe6d2'),
)


def sha(path):
    return sha256(path.read_bytes()).hexdigest()


def files_in(directory):
    return {path.relative_to(directory).as_posix(): sha(path) for path in sorted(directory.glob('*/*.png'))}


def pack_chunk(kind, data):
    return pack('<IH', len(data)+6, kind) + data


def ase_string(value):
    text = value.encode('utf-8')
    return pack('<H', len(text)) + text


def ase_layer(name):
    return pack_chunk(0x2004, pack('<HHHHHHB3x',3,0,0,0,0,0,255) + ase_string(name))


def ase_cel(index, image):
    box = image.getbbox()
    if box is None:
        return b''
    x, y, w, h = box
    head = pack('<HhhBHh5xHH', index, x, y, 255, 2, 0, w-x, h-y)
    return pack_chunk(0x2005, head + compress(image.crop(box).tobytes(), 9))


def ase_tags():
    record = pack('<H8x', 2)
    names = ('Male fit', 'Female fit')
    colors = ((68,147,126),(190,123,117))
    for gender, name in enumerate(names):
        record += pack('<HHBH6xBBB1x', gender*8, gender*8+7, 0, 0, *colors[gender]) + ase_string(name)
    return pack_chunk(0x2018, record)


def ase_project(layers):
    output = []
    used = [i for i in range(len(layers[0])) if any(row[i][1].getbbox() for row in layers)]
    for frame, row in enumerate(layers):
        chunks = []
        if frame == 0:
            chunks.extend(ase_layer(row[i][0]) for i in used)
            chunks.append(ase_tags())
        chunks.extend(c for index, i in enumerate(used) if (c := ase_cel(index, row[i][1])))
        payload = b''.join(chunks)
        output.append(pack('<IHHH2xI',len(payload)+16,0xF1FA,len(chunks),90,0) + payload)
    data = b''.join(output)
    header = pack('<IHHHHHIHII4xHBBhhHH84x',len(data)+128,0xA5E0,len(layers),256,384,32,1,90,0,0,0,1,1,0,0,0,0)
    return header + data


def filename(pose, tint=False):
    gender, frame = pose
    return f'{GENDERS[gender]}-{frame}{"-tint" if tint else ""}.png'


def details_from_sheet(path, metadata):
    image = Image.open(path).convert('RGBA')
    frames = list(json.loads(metadata.read_text())['frames'].values())
    if len(frames) != len(POSES):
        raise ValueError(f'{path.name} has {len(frames)} frames')
    for frame in frames:
        bounds = frame['frame']
        x, y, w, h = (bounds[key] for key in ('x','y','w','h'))
        if (w,h) != SIZE or frame['trimmed'] or frame['rotated']:
            raise ValueError(f'Unexpected native sprite dimensions for {path.name}')
        yield image.crop((x,y,x+w,y+h))


def same_visible(first, second):
    return all(f == s or f[3] == s[3] == 0 for f,s in zip(first.get_flattened_data(),second.get_flattened_data(),strict=True))


def export(binary, output):
    if sha(ASSET/'reference-review/approved-running.png') != SOURCE_SHA:
        raise ValueError('The approved RunMoni source has changed')
    art = output/'projects'
    art.mkdir(parents=True, exist_ok=True)
    work = output/'staging'
    work.mkdir(exist_ok=True)
    dest = output/'assets'
    dest.mkdir(exist_ok=True)
    for key, (_, slot) in ITEMS.items():
        rows = [asset(key, pose) for pose in POSES]
        draft = work/f'{key}.aseprite'
        final = art/f'{key}.aseprite'
        sheet = work/f'{key}.png'
        metadata = work/f'{key}.json'
        draft.write_bytes(ase_project(rows))
        run([binary, '-b', str(draft), '--save-as', str(final)], check=True)
        run([binary, '-b', str(final), '--sheet', str(sheet), '--data', str(metadata)], check=True)
        folder = dest/key
        folder.mkdir(exist_ok=True)
        for pose, detail, row in zip(POSES, details_from_sheet(sheet,metadata), rows, strict=True):
            if not same_visible(detail, merged(row)):
                raise ValueError(f'Aseprite did not preserve authored ink for {key} {pose}')
            detail.save(folder/filename(pose), optimize=True)
            if slot in ROLES:
                tint_cel(key, detail).save(folder/filename(pose,True), optimize=True)
    manifest = {
        'schemaVersion':1, 'width':256, 'height':384, 'frames':8, 'rows':2,
        'items':len(ITEMS), 'sourceSha256':SOURCE_SHA, 'files':files_in(dest),
    }
    (dest/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    verify(dest, art)
    reviews(dest, output)


def read_rgba(path):
    with Image.open(path) as im:
        if im.mode != 'RGBA' or im.size != SIZE:
            raise ValueError(f'Unexpected cel mode or size {path}')
        return im.copy()


def verify(directory, projects=None):
    if sha(ASSET/'reference-review/approved-running.png') != SOURCE_SHA:
        raise ValueError('The approved RunMoni source has changed')
    manifest = json.loads((directory/'manifest.json').read_text())
    expected_keys = {
        f'{item}/{filename(pose, tint)}'
        for item, (_, slot) in ITEMS.items()
        for pose in POSES for tint in ((False,True) if slot in ROLES else (False,))
    }
    if set(manifest['files']) != expected_keys or files_in(directory) != manifest['files']:
        raise ValueError('Art file list or hashes do not match the runtime manifest')
    if (manifest['schemaVersion'], manifest['width'], manifest['height'],manifest['frames'],manifest['rows'],manifest['items'],manifest['sourceSha256']) != (1,256,384,8,2,len(ITEMS),SOURCE_SHA):
        raise ValueError('Unsupported runtime manifest dimensions or source')
    min_ink, min_tint = {}, {}
    for key, (_, slot) in ITEMS.items():
        minimum_ink, minimum_tint = None, None
        for pose in POSES:
            source, _, hand = sprite_source(pose)
            detail = read_rgba(directory/key/filename(pose))
            visible = detail.getchannel('A').histogram()
            if sum(visible[1:255]) or visible[255] < 100:
                raise ValueError(f'Insufficient opaque ink for {key} {pose}')
            minimum_ink = min(minimum_ink or visible[255], visible[255])
            if slot in ROLES:
                tint = read_rgba(directory/key/filename(pose,True))
                amount = 0
                for (role, value, reserved, a), pixel in zip(tint.get_flattened_data(), detail.get_flattened_data(),strict=True):
                    if a:
                        if a!=255 or role!=ROLES[slot] or reserved or pixel[3]!=255:
                            raise ValueError(f'Invalid material role for {key} {pose}')
                        amount += 1
                if amount < 100:
                    raise ValueError(f'Insufficient material coverage for {key} {pose}')
                minimum_tint = min(minimum_tint or amount, amount)
            face_bound = 120 if pose[0]==0 else 121
            bounds = detail.getbbox()
            mask = detail.load(); original = source.load(); hands = hand.load()
            for y in range(bounds[1],bounds[3]):
                for x in range(bounds[0],bounds[2]):
                    if not mask[x,y][3]:
                        continue
                    if hands[x,y][3]:
                        raise ValueError(f'Foreground hand covered by {key} {pose} {(x,y)}')
                    if original[x,y][3]<20 and slot not in ('headwear','hydration'):
                        raise ValueError(f'Unapproved silhouette extension for {key} {pose} {(x,y)}')
                    if slot=='headwear' and y>=face_bound:
                        raise ValueError(f'Headwear reached the protected face for {key} {pose}')
                    if slot=='eyewear':
                        ex, ey, ear_x, ear_y, _, _ = ANCHORS[pose[0]][pose[1]]
                        if not (ear_x <= x <= ex+60 and min(ey-14, ear_y-10) <= y <= ey+12):
                            raise ValueError(f'Eyewear escaped the lens and temple area for {pose} {(x,y)}')
                    if slot not in ('headwear','eyewear') and y<160:
                        raise ValueError(f'Garment covered a protected face for {key} {pose}')
        min_ink[key] = minimum_ink
        if minimum_tint is not None:
            min_tint[key] = minimum_tint
        if projects is not None:
            data = (projects/f'{key}.aseprite').read_bytes()
            head = unpack_from('<I5H',data)
            if head != (len(data),0xA5E0,16,256,384,32):
                raise ValueError(f'Invalid saved Aseprite project for {key}')
    print(f'{len(ITEMS)} items, {len(ITEMS)*len(POSES)} detail cels, {len(min_tint)*len(POSES)} tint cels')
    print('Minimum opaque pixels per item', min_ink)
    print('Minimum authored material pixels per tintable item', min_tint)


def preview(directory, pose, keys):
    source, _, _ = sprite_source(pose)
    out = source.copy()
    pixels = out.load()
    for key in keys:
        detail = read_rgba(directory/key/filename(pose))
        if ITEMS[key][1] in ('headwear', 'hydration'):
            out.alpha_composite(detail)
            continue
        ink = detail.load()
        x0, y0, x1, y1 = detail.getbbox()
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, alpha = ink[x,y]
                if alpha:
                    pixels[x,y] = (r,g,b,pixels[x,y][3])
    return out


def reviews(directory, output):
    # This system font is for the host review only. It is never an app dependency.
    try:
        font=ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc',18,index=1)
        language=0
    except OSError:
        font=ImageFont.load_default(size=18)
        language=1
    nearest = Image.Resampling.NEAREST
    genders=(('남자','Male'),('여자','Female'))
    themes=(('#eae9de',('밝게','Light')),('#253531',('어둡게','Dark')))
    for gender in range(2):
        out = Image.new('RGB',(1184,683),'#f5f3e9')
        d = ImageDraw.Draw(out)
        for row, (name, keys, color) in enumerate(RUN_SETS):
            y = row*227
            d.rectangle((0,y,1184,y+217),fill=color)
            d.text((12,y+6),f'{name[language]} · {genders[gender][language]}',font=font,fill='#273a36')
            for f in range(8):
                sprite = preview(directory, (gender,f), keys).resize((126,189),nearest)
                out.paste(sprite,(f*148+10,y+26),sprite)
        out.save(output/f'production-fit-{GENDERS[gender]}.png',optimize=True)
    board=Image.new('RGB',(1056,467),'#f7f5e9')
    d=ImageDraw.Draw(board)
    for row,(theme, text) in enumerate(themes):
        for col,(name,keys,_color) in enumerate(RUN_SETS):
            x,y=col*352,row*231
            d.rectangle((x,y+8,x+345,y+224),fill=theme)
            fg='#233631' if row==0 else '#e7eee3'
            d.text((x+12,y+19),f'{name[language]} · {text[language]}',font=font,fill=fg)
            d.text((x+18,y+177),'96 dp',font=font,fill=fg)
            d.text((x+188,y+177),'64 dp',font=font,fill=fg)
            for j,g in enumerate(range(2)):
                sprite=preview(directory,(g,0),keys)
                stage=sprite.resize((64,96),nearest)
                tile=sprite.resize((43,64),nearest)
                board.paste(stage,(x+13+j*68,y+59),stage)
                board.paste(tile,(x+184+j*60,y+82),tile)
    board.save(output/'production-wardrobe.png',optimize=True)


if __name__ == '__main__':
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--aseprite', help='Full Aseprite executable (batch mode)')
    parser.add_argument('--output', type=Path, help='Empty staging directory for generated files')
    parser.add_argument('--verify', type=Path, help='Verify the production asset directory')
    parser.add_argument('--projects', type=Path, help='Also verify the corresponding saved projects')
    args = parser.parse_args()
    if args.verify:
        verify(args.verify, args.projects)
    elif args.aseprite and args.output:
        export(args.aseprite,args.output.resolve())
    else:
        parser.error('Supply --verify or both --aseprite and --output')
