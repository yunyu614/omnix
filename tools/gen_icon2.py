#!/usr/bin/env python3
"""ModRepo 图标 v3 · 小米风格：超椭圆底色 + 粗体 MR（MiSans Heavy，光学校正居中）"""
import math, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "..", "app", "res")
PREV = os.path.join(ROOT, "..", "..", "preview")

FONTS = [
    (os.path.join(ROOT, "fonts", "MiSansLatinVF.ttf"), ["Heavy", "Black", "Bold"], 700),
    ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", [], 0),
]

PALETTES = {
    # 主色：雾泉（深青蓝渐变）
    "blue":   ((0x1D, 0x8C, 0xBE), (0x03, 0x33, 0x4A)),
    # 备选：小米橙
    "orange": ((0xFF, 0x8C, 0x24), (0xDE, 0x48, 0x00)),
    # 备选：青瓷绿
    "green":  ((0x35, 0x9E, 0x82), (0x09, 0x45, 0x37)),
}
N = 4.0   # 超椭圆指数

def load_font(px):
    for path, names, wght in FONTS:
        if not os.path.exists(path):
            continue
        try:
            f = ImageFont.truetype(path, px)
        except Exception:
            continue
        for n in names:
            try:
                f.set_variation_by_name(n); return f
            except Exception:
                pass
        if wght:
            try:
                f.set_variation_by_axes([wght]); return f
            except Exception:
                pass
        return f
    raise RuntimeError("no font")

def squircle(size, inset=0.0):
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    a = size / 2 * (1 - inset); c = size / 2
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        ct, st = math.cos(t), math.sin(t)
        x = c + a * (abs(ct) ** (2 / N)) * (1 if ct >= 0 else -1)
        y = c + a * (abs(st) ** (2 / N)) * (1 if st >= 0 else -1)
        pts.append((x, y))
    d.polygon(pts, fill=255)
    return img

def gradient(size, top, bottom):
    small = Image.new("RGB", (48, 48))
    sp = small.load()
    for y in range(48):
        for x in range(48):
            t = max(0.0, min(1.0, (y * 1.0 + x * 0.42) / (47 * 1.42)))
            sp[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return small.resize((size, size), Image.BICUBIC)

def text_layer(S, em_ratio=0.42, tracking=-0.028):
    """在透明层上写 MR，返回 (layer, ink_bbox)"""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = load_font(int(S * em_ratio))
    tr = tracking * font.size
    adv = sum(d.textlength(c, font=font) for c in "MR") + tr
    x = S / 2 - adv / 2
    for ch in "MR":
        d.text((x, S / 2), ch, font=font, fill=(255, 255, 255, 255), anchor="ls")
        x += d.textlength(ch, font=font) + tr
    bb = layer.getbbox()
    return layer, bb

def optically_center(layer, bb, S, dy=-0.006):
    """按墨迹包围盒居中，抵消字体左右不对称"""
    bw, bh = bb[2] - bb[0], bb[3] - bb[1]
    target_x = S / 2 - bw / 2
    target_y = S / 2 - bh / 2 + dy * S
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(layer, (int(round(target_x - bb[0])), int(round(target_y - bb[1]))), layer)
    return out

def render(size, palette="blue", mode="full", em_ratio=0.42):
    SS = 4
    S = size * SS
    top, bottom = PALETTES[palette]

    if mode == "bg":
        return gradient(S, top, bottom).convert("RGBA")

    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if mode == "full":
        canvas.paste(gradient(S, top, bottom).convert("RGBA"), (0, 0), squircle(S, 0.004))

    layer, bb = text_layer(S, em_ratio)
    layer = optically_center(layer, bb, S)
    canvas.alpha_composite(layer)
    return canvas

DENS = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]

def save(img, path, size):
    out = img.resize((size, size), Image.LANCZOS) if img.size[0] != size else img
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path, "PNG", optimize=True)
    return os.path.getsize(path)

def coverage(img):
    im = img.convert("RGBA"); w = h = im.size[0]; n = 0; tot = 0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            r, g, b, a = im.getpixel((x, y)); tot += 1
            if a > 200 and r > 232 and g > 232 and b > 232: n += 1
    return 100.0 * n / tot

if __name__ == "__main__":
    os.makedirs(PREV, exist_ok=True)
    for pal in ("blue", "orange", "green"):
        render(256, pal).save(os.path.join(PREV, "icon-%s-256.png" % pal), "PNG")
    print("previews ok")

    for name, px in DENS:
        p = os.path.join(RES, "mipmap-" + name, "ic_launcher.png")
        print("%-11s %3dpx %6dB" % (name, px, save(render(px, "blue"), p, px)))

    # 自适应：背景满幅，前景字母进 62% 安全区
    render(432, "blue", "bg").save(os.path.join(RES, "drawable", "ic_launcher_background.png"), "PNG")
    fgsrc = render(432, "blue", "fg")
    inner = fgsrc.resize((int(432 * 0.62),) * 2, Image.LANCZOS)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fg.paste(inner, ((432 - inner.size[0]) // 2,) * 2, inner)
    fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"), "PNG")
    print("adaptive bg/fg 432px")

    render(192, "blue").resize((192, 192), Image.LANCZOS).save(
        os.path.join(RES, "drawable", "icon.png"), "PNG")

    print("glyph coverage %.1f%%" % coverage(render(192, "blue")))
