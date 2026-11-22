#!/usr/bin/env python3
"""Omnix 图标：纯色底 + 细纹路（同心环 + 斜纹 + 高光），大写 O，跟随 8 套主题。"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "..", "app", "res")
PREV = os.path.join(ROOT, "..", "preview")
FONT = os.path.join(ROOT, "fonts", "MiSansLatinVF.ttf")
FALLBACK = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# 主题主色（纯色底）
SOLID = {
    "mist":     "#006699",
    "celadon":  "#2F7966",
    "violet":   "#6750A4",
    "sakura":   "#B5546E",
    "amber":    "#B0703A",
    "mocha":    "#8B5E3C",
    "graphite": "#3C4854",
    "ocean":    "#0C6E8C",
}
ORDER = ["mist", "celadon", "violet", "sakura", "amber", "mocha", "graphite", "ocean"]
DENS = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
N = 4.0


def load_font(px):
    for path, names, wght in ((FONT, ["Heavy", "Black", "Bold"], 700), (FALLBACK, [], 0)):
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


def squircle(size, inset=0.004):
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    a = size / 2 * (1 - inset); c = size / 2
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        ct, st = math.cos(t), math.sin(t)
        pts.append((c + a * (abs(ct) ** (2 / N)) * (1 if ct >= 0 else -1),
                    c + a * (abs(st) ** (2 / N)) * (1 if st >= 0 else -1)))
    d.polygon(pts, fill=255)
    return img


def glass_base(S, hexcolor):
    """纯色底 + 玻璃质感：顶部柔光、左上大高光、边缘高光、底部微暗"""
    base = Image.new("RGBA", (S, S), hexcolor + "ff")
    # 1) 竖向柔光（上亮下暗），用 1px 渐变放大
    grad = Image.new("L", (1, 256), 0)
    gp = grad.load()
    for y in range(256):
        t = y / 255.0
        gp[0, y] = int(max(0.0, 1.0 - t / 0.62) ** 1.5 * 46)
    grad = grad.resize((S, S), Image.BICUBIC)
    base.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0), grad)

    # 2) 左上大面积柔光（玻璃反光）
    hi = Image.new("L", (128, 128), 0)
    hp = hi.load()
    for y in range(128):
        for x in range(128):
            dx = (x - 34) / 128.0
            dy = (y - 26) / 128.0
            v = max(0.0, 1.0 - math.hypot(dx, dy) * 2.05)
            hp[x, y] = int((v ** 1.9) * 120)
    hi = hi.resize((S, S), Image.BICUBIC).filter(ImageFilter.GaussianBlur(S * 0.02))
    base.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0), hi)

    # 3) 底部微暗，增加体积
    sh = Image.new("L", (1, 256), 0)
    sp = sh.load()
    for y in range(256):
        t = max(0.0, (y / 255.0 - 0.52) / 0.48)
        sp[0, y] = int((t ** 1.8) * 62)
    sh = sh.resize((S, S), Image.BICUBIC)
    base.paste(Image.new("RGBA", (S, S), (0, 0, 0, 255)), (0, 0), sh)

    # 4) 玻璃边缘高光：沿 squircle 内侧一圈白色细边
    edge = squircle(S, 0.004)
    inner = squircle(S, 0.030)
    rim = Image.new("L", (S, S), 0)
    rim.paste(edge, (0, 0))
    rim.paste(Image.new("L", (S, S), 0), (0, 0), inner)
    rim = rim.filter(ImageFilter.GaussianBlur(S * 0.004))
    base.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0),
               rim.point(lambda v: int(v * 0.55)))
    return base


def o_layer(S, em=0.62):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = load_font(int(S * em))
    d.text((S / 2, S / 2), "O", font=font, fill=(255, 255, 255, 255), anchor="ls")
    bb = layer.getbbox()
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(layer, (int(S / 2 - (bb[2] - bb[0]) / 2 - bb[0]),
                      int(S / 2 - (bb[3] - bb[1]) / 2 - 0.004 * S - bb[1])), layer)
    return out


def icon(size, theme, mode="full"):
    SS = 4; S = size * SS
    if mode == "bg":
        return glass_base(S, SOLID[theme]).convert("RGBA")
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if mode == "full":
        canvas.paste(glass_base(S, SOLID[theme]).convert("RGBA"), (0, 0), squircle(S))
    canvas.alpha_composite(o_layer(S))
    return canvas.resize((size, size), Image.LANCZOS) if size != S else canvas


if __name__ == "__main__":
    os.makedirs(PREV, exist_ok=True)
    # 自适应前景：白色 O 缩到 62% 安全区
    inner = o_layer(432).resize((int(432 * 0.62),) * 2, Image.LANCZOS)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fg.paste(inner, ((432 - inner.size[0]) // 2,) * 2, inner)
    fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"), "PNG")

    for t in ORDER:
        icon(256, t).save(os.path.join(PREV, "icon-%s-256.png" % t), "PNG")
        for name, px in DENS:
            p = os.path.join(RES, "mipmap-%s" % name, "ic_%s.png" % t)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            icon(px, t).save(p, "PNG", optimize=True)
        bg = glass_base(432, SOLID[t]).convert("RGB")
        bg.save(os.path.join(RES, "drawable", "ic_%s_bg.png" % t), "PNG")
        x = os.path.join(RES, "mipmap-anydpi-v26", "ic_%s.xml" % t)
        os.makedirs(os.path.dirname(x), exist_ok=True)
        open(x, "w").write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_%s_bg"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n' % t)
        print("theme %-9s ok" % t)
    icon(192, "mist").save(os.path.join(RES, "drawable", "icon.png"), "PNG")
    print("done")
