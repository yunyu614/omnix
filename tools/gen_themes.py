#!/usr/bin/env python3
"""生成 themes.css：8 套主题 × 日/夜，供 app 与图标共用同一套颜色。"""

THEMES = [
    # id, 名称, 日间(bg,txt,sub,mut,line,glass,glass2,glow1,glow2,acc1,acc2,shadow,hi), 夜间(...)
    ("mist", "雾泉",
     ("#E7EFF2","#153142","#486574","#6B828D","rgba(0,102,153,.16)","rgba(255,255,255,.58)","rgba(217,230,235,.55)","#8FC0D2","#BCE2EA","#006699","#167F94","48,84,103","rgba(255,255,255,.6)",
      "#081720","#E9E7E2","#B0C1C9","#8299A5","rgba(138,178,209,.18)","rgba(28,50,64,.55)","rgba(33,58,71,.5)","#0D3247","#125E6B","#8AB2D1","#65DCE2","0,7,14","rgba(255,255,255,.07)")),
    ("celadon", "青瓷",
     ("#EEF0E9","#22302B","#5F6F66","#627268","rgba(44,73,61,.16)","rgba(255,255,255,.58)","rgba(226,233,224,.55)","#AFCBAF","#CFE4CE","#2F7966","#487565","40,64,52","rgba(255,255,255,.6)",
      "#101B18","#E9E7E2","#B0BDB4","#82958A","rgba(176,203,188,.18)","rgba(26,44,37,.55)","rgba(36,60,52,.5)","#16382D","#2A6650","#8AC1A0","#9BD4B4","3,10,7","rgba(255,255,255,.07)")),
    ("violet", "暮云",
     ("#F1EDF5","#2A2136","#5D4E6E","#7A6B8A","rgba(103,80,164,.18)","rgba(255,255,255,.58)","rgba(232,224,240,.55)","#C6ADD9","#E0CFEF","#6750A4","#8A6B91","60,42,80","rgba(255,255,255,.6)",
      "#140F1C","#ECE6F2","#BCAECC","#8E80A0","rgba(198,161,206,.18)","rgba(36,28,48,.55)","rgba(48,36,63,.5)","#33244A","#553770","#C6A1CE","#D0BCFF","8,4,14","rgba(255,255,255,.07)")),
    ("sakura", "樱粉",
     ("#F8EDEF","#3A2026","#75505A","#94707A","rgba(198,102,124,.18)","rgba(255,255,255,.58)","rgba(243,224,229,.55)","#EDC2CD","#F7D9E0","#B5546E","#C97F92","90,52,62","rgba(255,255,255,.6)",
      "#1C1014","#F3E3E7","#C9A7B0","#9C818A","rgba(226,166,181,.18)","rgba(50,31,37,.55)","rgba(63,39,46,.5)","#4A2230","#7C3B49","#E2A6B5","#F2C1CC","14,6,9","rgba(255,255,255,.07)")),
    ("amber", "琥珀",
     ("#F6EFE6","#35251A","#6E5443","#8C7261","rgba(176,116,54,.2)","rgba(255,255,255,.58)","rgba(240,229,214,.55)","#E6C49A","#F3DCB9","#B0703A","#C98A45","80,58,36","rgba(255,255,255,.6)",
      "#1A1209","#F1E7DA","#C4AE94","#9A8570","rgba(224,181,124,.18)","rgba(46,33,20,.55)","rgba(58,43,27,.5)","#4A3319","#764E24","#E0B57C","#F2CE93","12,7,2","rgba(255,255,255,.07)")),
    ("mocha", "摩卡",
     ("#F4EDE9","#31241D","#6A5347","#8A7264","rgba(139,94,60,.18)","rgba(255,255,255,.58)","rgba(236,226,219,.55)","#D6B69B","#E8D2BE","#8B5E3C","#A67C52","74,52,36","rgba(255,255,255,.6)",
      "#17100B","#F0E6DC","#C7B3A3","#9C8577","rgba(210,166,126,.18)","rgba(38,28,21,.55)","rgba(52,39,30,.5)","#3A2A1E","#5E432C","#D2A67E","#E8C39A","12,7,3","rgba(255,255,255,.07)")),
    ("graphite", "石墨",
     ("#EDEFF1","#1D2227","#4C555E","#6E7781","rgba(60,72,84,.16)","rgba(255,255,255,.58)","rgba(226,231,235,.55)","#BCC6CF","#D6DEE5","#3C4854","#55636F","44,52,60","rgba(255,255,255,.6)",
      "#0E1114","#E6E9EC","#A9B1B8","#7C858D","rgba(150,163,175,.16)","rgba(26,31,35,.55)","rgba(36,42,47,.5)","#1D252B","#36424B","#9FAAB3","#C2CCD4","2,4,6","rgba(255,255,255,.06)")),
    ("ocean", "深海",
     ("#E6F1F4","#0F2E38","#3F6470","#63838D","rgba(12,110,140,.18)","rgba(255,255,255,.58)","rgba(214,233,239,.55)","#94CBDB","#C0E4ED","#0C6E8C","#1D8FA6","30,70,86","rgba(255,255,255,.6)",
      "#05161C","#E4EFF2","#A6C2CB","#7C979F","rgba(140,190,205,.18)","rgba(14,36,45,.55)","rgba(22,52,63,.5)","#0A2E3C","#125366","#7FC2D4","#8FE0EC","0,6,10","rgba(255,255,255,.07)")),
]

KEYS = ["bg","txt","sub","mut","line","glass","glass2","glow1","glow2","acc1","acc2","shadow","hi"]

def split_rgba(v):
    """'rgba(255,255,255,.58)' -> ('255,255,255', '.58')"""
    try:
        inner = v[v.index("(")+1:v.rindex(")")]
        parts = [x.strip() for x in inner.split(",")]
        return ",".join(parts[:3]), parts[3]
    except Exception:
        return "255,255,255", "0.55"

def build_css():
    out = ["/* ===== 主题：8 套 × 日/夜（整页联动，图标同源） ===== */",
           "/* 主题色板见 tools/gen_themes.py，图标见 tools/gen_icons_all.py */"]
    for tid, name, vals in THEMES:
        day, night = vals[:13], vals[13:]
        for mode, arr in (("day", day), ("night", night)):
            vars_ = "\n".join("  --%s:%s;" % (k, v) for k, v in zip(KEYS, arr))
            d = dict(zip(KEYS, arr))
            g_rgb, g_a = split_rgba(d["glass"])
            g2_rgb, g2_a = split_rgba(d["glass2"])
            extra = ("  --glass-rgb:%s;\n  --glass-a:%s;\n  --glass2-rgb:%s;\n  --glass2-a:%s;\n"
                     "  --panel-bg:rgba(var(--glass-rgb),calc(var(--glass-a) * var(--pa,1)));\n"
                     "  --nav-bg:rgba(var(--glass-rgb),calc(var(--glass-a) * var(--na,1)));\n"
                     % (g_rgb, g_a, g2_rgb, g2_a))
            out.append('html[data-theme="%s"][data-mode="%s"]{\n%s\n%s  --glowop:%s;\n}'
                       % (tid, mode, vars_, extra, ".55" if mode == "day" else ".7"))
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    import os
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "assets", "www", "themes.css")
    css = build_css()
    open(p, "w").write(css)
    print("themes.css: %d themes, %d bytes" % (len(THEMES), len(css)))
