#!/usr/bin/env python3
"""为 8 套主题各生成一套图标：超椭圆底色 + MiSans Heavy 的 MR。
产出：mipmap-*/ic_<theme>.png、drawable/ic_<theme>_bg.png、
      共享 drawable/ic_launcher_foreground.png、mipmap-anydpi-v26/ic_<theme>.xml、预览图。"""
import math, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "..", "app", "res")
PREV = os.path.join(ROOT, "..", "preview")
FONT = os.path.join(ROOT, "fonts", "MiSansLatinVF.ttf")
FALLBACK = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# 每套主题的图标渐变（上亮 → 下深），与 themes.css 的 acc1 同源
GRADS = {
    "mist":     ((0x1D, 0x8C, 0xBE), (0x03, 0x33, 0x4A)),
    "celadon":  ((0x3E, 0x9E, 0x80), (0x0B, 0x3D, 0x31)),
    "violet":   ((0x84, 0x67, 0xC8), (0x2C, 0x1B, 0x4D)),
    "sakura":   ((0xD4, 0x73, 0x8C), (0x57, 0x22, 0x2F)),
    "amber":    ((0xD9, 0x92, 0x4A), (0x4A, 0x2A, 0x0C)),
    "mocha":    ((0xB1, 0x7B, 0x52), (0x3A, 0x24, 0x17)),
    "graphite": ((0x5C, 0x6B, 0x78), (0x14, 0x19, 0x1D)),
    "ocean":    ((0x16, 0x96, 0xB8), (0x04, 0x30, 0x3E)),
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
    pts = [(c + a * (abs(math.cos(2*math.pi*i/720)) ** (2/N)) * (1 if math.cos(2*math.pi*i/720) >= 0 else -1),
            c + a * (abs(math.sin(2*math.pi*i/720)) ** (2/N)) * (1 if math.sin(2*math.pi*i/720) >= 0 else -1))
           for i in range(720)]
    d.polygon(pts, fill=255)
    return img

def gradient(size, c1, c2):
    small = Image.new("RGB", (48, 48)); sp = small.load()
    for y in range(48):
        for x in range(48):
            t = max(0.0, min(1.0, (y + x * 0.42) / (47 * 1.42)))
            sp[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return small.resize((size, size), Image.BICUBIC)

def mr_layer(S, em=0.42, tracking=-0.028):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = load_font(int(S * em))
    tr = tracking * font.size
    adv = sum(d.textlength(c, font=font) for c in "MR") + tr
    x = S / 2 - adv / 2
    for ch in "MR":
        d.text((x, S / 2), ch, font=font, fill=(255, 255, 255, 255), anchor="ls")
        x += d.textlength(ch, font=font) + tr
    bb = layer.getbbox()
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(layer, (int(S/2 - (bb[2]-bb[0])/2 - bb[0]), int(S/2 - (bb[3]-bb[1])/2 - 0.006*S - bb[1])), layer)
    return out

def icon(size, theme, with_bg=True):
    SS = 4; S = size * SS
    c1, c2 = GRADS[theme]
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if with_bg:
        canvas.paste(gradient(S, c1, c2).convert("RGBA"), (0, 0), squircle(S))
    canvas.alpha_composite(mr_layer(S))
    if canvas.size[0] != size:
        canvas = canvas.resize((size, size), Image.LANCZOS)
    return canvas

if __name__ == "__main__":
    os.makedirs(PREV, exist_ok=True)
    # 共享前景（白色 MR，62% 安全区）
    fg = mr_layer(512)
    fg = fg.resize((int(432*0.62/ (512/432)),)*2, Image.LANCZOS) if False else fg
    inner = mr_layer(432).resize((int(432*0.62),)*2, Image.LANCZOS)
    fgc = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fgc.paste(inner, ((432-inner.size[0])//2,)*2, inner)
    fgc.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"), "PNG")

    for t in ORDER:
        # 预览
        icon(256, t).save(os.path.join(PREV, "icon-%s-256.png" % t), "PNG")
        # 各密度
        for name, px in DENS:
            p = os.path.join(RES, "mipmap-%s" % name, "ic_%s.png" % t)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            icon(px, t).save(p, "PNG", optimize=True)
        # 自适应背景
        c1, c2 = GRADS[t]
        gradient(432, c1, c2).save(os.path.join(RES, "drawable", "ic_%s_bg.png" % t), "PNG")
        # 自适应 xml
        x = os.path.join(RES, "mipmap-anydpi-v26", "ic_%s.xml" % t)
        os.makedirs(os.path.dirname(x), exist_ok=True)
        open(x, "w").write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_%s_bg"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n' % t)
        print("theme %-9s ok" % t)

    # 兼容旧引用
    icon(192, "mist").save(os.path.join(RES, "drawable", "icon.png"), "PNG")
    print("done: %d themes × %d densities" % (len(ORDER), len(DENS)))
