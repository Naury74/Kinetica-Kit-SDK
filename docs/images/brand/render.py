#!/usr/bin/env python3
"""Kinetica 브랜드 이미지를 다시 만든다.

만드는 파일
- docs/images/brand/mark.svg, logo-light.svg, logo-dark.svg (원본, 글자는 윤곽선으로 바꿔 글꼴 없이 열린다)
- docs/images/logo-light.png, logo-dark.png (884x300), docs/images/icon.png (512x512)
- sample-showcase 런처 아이콘 벡터 (ic_launcher_foreground.xml, ic_launcher_background.xml)

필요한 것
- Python 패키지 fonttools
- Pretendard Bold·Regular OTF (OFL). 없으면 jsdelivr에서 ~/.cache/kinetica-brand 로 받는다.
  KINETICA_FONT_DIR 로 다른 폴더를 줄 수 있다.
- PNG 변환에 Chrome (headless). CHROME 환경 변수로 경로를 바꿀 수 있다.

실행: python3 docs/images/brand/render.py
"""

import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

BRAND_DIR = Path(__file__).resolve().parent
IMAGES_DIR = BRAND_DIR.parent
ROOT = IMAGES_DIR.parent.parent
RES_DIR = ROOT / "sample-showcase/src/main/res"

# 색은 docs/brand.md 와 맞춘다.
GRADIENT_START = "#FF8A5B"
GRADIENT_END = "#D6336C"
THEMES = {
    "light": {"title": "#21141C", "subtitle": "#6E5A66"},
    "dark": {"title": "#FFFFFF", "subtitle": "#E2C3D1"},
}
TITLE = "Kinetica"
SUBTITLE = "Android 모션·인터랙티브 UI SDK"

# 심벌은 220x220 타일 좌표로 그린다. 모서리 반지름 55(25%)와 왼쪽 위→오른쪽 아래 그라디언트는 FrameKit·PULSE와 같다.
TILE = 220
TILE_RADIUS = 55
STROKE = 20
# 뒤에 비치는 글래스 판, K의 줄기, 이징 곡선으로 휘는 위쪽 팔(끝은 키프레임 점), 아래쪽 다리.
PANE = (81, 42, 88, 88, 22, 0.28)  # x, y, w, h, 반지름, 불투명도
STEM = "M61,54 L61,170"
ARM = "M61,128 C107,128 101,66 131,60"
LEG = "M91,120 L137,170"
DOT = (141, 60, 16)

# 로고 배치. FrameKit 로고(884x300)에서 잰 값이다.
CANVAS_W, CANVAS_H = 884, 300
ICON_X, ICON_Y, ICON_SIZE = 20, 40, 220
TEXT_X = 288
TITLE_CAP_TOP, TITLE_BASELINE = 72, 177
SUBTITLE_TOP, SUBTITLE_BOTTOM = 216, 250
TEXT_RIGHT = CANVAS_W - 20

FONT_URL = "https://cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/public/static/Pretendard-{}.otf"


def rounded_rect_path(x, y, w, h, r):
    return (
        f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} V{y + h - r} "
        f"A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0 1 {x},{y + h - r} "
        f"V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z"
    )


def circle_path(cx, cy, r):
    return f"M{cx - r},{cy} A{r},{r} 0 1 0 {cx + r},{cy} A{r},{r} 0 1 0 {cx - r},{cy} Z"


def mark_shapes():
    """심벌을 (path, 채움 여부, 불투명도) 목록으로 돌려준다. SVG와 VectorDrawable이 같은 목록을 쓴다."""
    x, y, w, h, r, alpha = PANE
    return [
        (rounded_rect_path(x, y, w, h, r), True, alpha),
        (STEM, False, 1.0),
        (ARM, False, 1.0),
        (LEG, False, 1.0),
        (circle_path(*DOT), True, 1.0),
    ]


def mark_svg_body():
    parts = []
    for d, filled, alpha in mark_shapes():
        opacity = f' fill-opacity="{alpha}"' if alpha < 1 else ""
        if filled:
            parts.append(f'<path d="{d}" fill="#fff"{opacity}/>')
        else:
            parts.append(
                f'<path d="{d}" fill="none" stroke="#fff" stroke-width="{STROKE}" stroke-linecap="round"/>'
            )
    return "\n    ".join(parts)


def tile_svg_group(gradient_id):
    return (
        f'<rect width="{TILE}" height="{TILE}" rx="{TILE_RADIUS}" fill="url(#{gradient_id})"/>\n    '
        + mark_svg_body()
    )


def gradient_def(gradient_id):
    return (
        f'<linearGradient id="{gradient_id}" x1="0" y1="0" x2="{TILE}" y2="{TILE}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{GRADIENT_START}"/><stop offset="1" stop-color="{GRADIENT_END}"/>'
        "</linearGradient>"
    )


def font_path(weight):
    font_dir = Path(os.environ.get("KINETICA_FONT_DIR", Path.home() / ".cache/kinetica-brand"))
    path = font_dir / f"Pretendard-{weight}.otf"
    if not path.exists():
        font_dir.mkdir(parents=True, exist_ok=True)
        print(f"글꼴 내려받기: {path.name}")
        urllib.request.urlretrieve(FONT_URL.format(weight), path)
    return path


class TextOutline:
    """글자를 SVG path로 바꾼다. 커닝은 적용하지 않고 tracking(em 비율)만 더한다."""

    def __init__(self, path):
        self.font = TTFont(path)
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.upem = self.font["head"].unitsPerEm
        self.cap = self.font["OS/2"].sCapHeight / self.upem

    def _layout(self, text, size, tracking):
        scale = size / self.upem
        x = 0.0
        for ch in text:
            name = self.cmap.get(ord(ch))
            if name is None:
                raise ValueError(f"글꼴에 없는 글자: {ch!r}")
            yield name, x, scale
            x += self.glyphs[name].width * scale + tracking * size

    def bounds(self, text, size, tracking=0.0):
        pen = BoundsPen(self.glyphs)
        for name, x, scale in self._layout(text, size, tracking):
            self.glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, 0)))
        return pen.bounds  # (xMin, yMin, xMax, yMax), 기준선 0, 아래가 +

    def path(self, text, size, x0, baseline, tracking=0.0):
        pen = SVGPathPen(self.glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        for name, x, scale in self._layout(text, size, tracking):
            self.glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x0 + x, baseline)))
        return pen.getCommands()


def fit_text(outline, text, top, bottom, tracking, max_width):
    """잉크 높이가 top~bottom 에 맞는 크기와 기준선을 구한다. 폭이 넘치면 줄인다."""
    xmin, ymin, xmax, ymax = outline.bounds(text, 100, tracking)
    size = 100 * (bottom - top) / (ymax - ymin)
    size = min(size, 100 * max_width / (xmax - xmin))
    xmin, ymin, xmax, ymax = outline.bounds(text, size, tracking)
    baseline = bottom - ymax
    return size, baseline, xmin


def logo_svg(theme):
    colors = THEMES[theme]
    bold = TextOutline(font_path("Bold"))
    regular = TextOutline(font_path("Regular"))
    title_tracking = -0.02
    max_w = TEXT_RIGHT - TEXT_X
    t_size, t_base, t_xmin = fit_text(bold, TITLE, TITLE_CAP_TOP, TITLE_BASELINE, title_tracking, max_w)
    s_size, s_base, s_xmin = fit_text(regular, SUBTITLE, SUBTITLE_TOP, SUBTITLE_BOTTOM, 0.0, max_w)
    title_d = bold.path(TITLE, t_size, TEXT_X - t_xmin, t_base, title_tracking)
    subtitle_d = regular.path(SUBTITLE, s_size, TEXT_X - s_xmin, s_base)
    scale = ICON_SIZE / TILE
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{CANVAS_W}" height="{CANVAS_H}" viewBox="0 0 {CANVAS_W} {CANVAS_H}">
  <title>Kinetica</title>
  <defs>{gradient_def("kinetica-gradient")}</defs>
  <g transform="translate({ICON_X} {ICON_Y}) scale({scale:g})">
    {tile_svg_group("kinetica-gradient")}
  </g>
  <path d="{title_d}" fill="{colors["title"]}"/>
  <path d="{subtitle_d}" fill="{colors["subtitle"]}"/>
</svg>
"""


def mark_svg():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{TILE}" height="{TILE}" viewBox="0 0 {TILE} {TILE}">
  <title>Kinetica</title>
  <defs>{gradient_def("kinetica-gradient")}</defs>
    {tile_svg_group("kinetica-gradient")}
</svg>
"""


# 적응형 아이콘: 108dp 캔버스 가운데 60dp에 220 타일 좌표를 맞춘다. 보이는 72dp를 다 쓰면 원형 마스크에서
# 심벌이 안전 영역(지름 66dp) 가장자리에 붙어 답답해 보인다. 배경 그라디언트는 보이는 72dp에 맞춘다.
ADAPTIVE_SCALE = 60 / TILE
ADAPTIVE_OFFSET = (108 - 60) / 2
VISIBLE_OFFSET = 18


def foreground_xml():
    paths = []
    for d, filled, alpha in mark_shapes():
        if filled:
            attrs = 'android:fillColor="#FFFFFF"'
            if alpha < 1:
                attrs += f'\n            android:fillAlpha="{alpha}"'
        else:
            attrs = (
                'android:strokeColor="#FFFFFF"\n'
                f'            android:strokeWidth="{STROKE}"\n'
                '            android:strokeLineCap="round"'
            )
        paths.append(f'        <path\n            {attrs}\n            android:pathData="{d}" />')
    body = "\n".join(paths)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- docs/images/brand/render.py 가 만든 파일이다. 직접 고치지 말고 스크립트를 고친다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{ADAPTIVE_SCALE:.6f}"
        android:scaleY="{ADAPTIVE_SCALE:.6f}"
        android:translateX="{ADAPTIVE_OFFSET:g}"
        android:translateY="{ADAPTIVE_OFFSET:g}">
{body}
    </group>
</vector>
"""


def background_xml():
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- docs/images/brand/render.py 가 만든 파일이다. 직접 고치지 말고 스크립트를 고친다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{VISIBLE_OFFSET}"
                android:startY="{VISIBLE_OFFSET}"
                android:endX="{108 - VISIBLE_OFFSET}"
                android:endY="{108 - VISIBLE_OFFSET}">
                <item android:offset="0" android:color="{GRADIENT_START}" />
                <item android:offset="1" android:color="{GRADIENT_END}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""


def find_chrome():
    candidates = [
        os.environ.get("CHROME"),
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        shutil.which("google-chrome"),
        shutil.which("chromium"),
    ]
    for c in candidates:
        if c and Path(c).exists():
            return c
    sys.exit("Chrome을 찾지 못했다. CHROME 환경 변수로 경로를 준다.")


def render_png(svg_path, png_path, width, height):
    """Chrome headless로 투명 배경 PNG를 만든다."""
    chrome = find_chrome()
    with tempfile.TemporaryDirectory() as tmp:
        html = Path(tmp) / "page.html"
        html.write_text(
            "<!doctype html><html><body style='margin:0;background:transparent'>"
            f"<img src='{svg_path.as_uri()}' width='{width}' height='{height}' style='display:block'>"
            "</body></html>",
            encoding="utf-8",
        )
        # headless Chrome이 가끔 화면을 못 찍고 끝나서 몇 번 다시 시도한다.
        for attempt in range(3):
            done = subprocess.run(
                [
                    chrome,
                    "--headless=new",
                    "--disable-gpu",
                    "--hide-scrollbars",
                    "--force-device-scale-factor=1",
                    "--default-background-color=00000000",
                    f"--user-data-dir={tmp}/profile-{attempt}",
                    f"--window-size={width},{height}",
                    f"--screenshot={png_path}",
                    html.as_uri(),
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if done.returncode == 0:
                break
        else:
            sys.exit(f"Chrome이 {png_path.name}을 만들지 못했다.")
    print(f"만듦: {png_path.relative_to(ROOT)}")


def write(path, text):
    path.write_text(text, encoding="utf-8")
    print(f"만듦: {path.relative_to(ROOT)}")


def main():
    write(BRAND_DIR / "mark.svg", mark_svg())
    for theme in THEMES:
        write(BRAND_DIR / f"logo-{theme}.svg", logo_svg(theme))
        render_png(BRAND_DIR / f"logo-{theme}.svg", IMAGES_DIR / f"logo-{theme}.png", CANVAS_W, CANVAS_H)
    render_png(BRAND_DIR / "mark.svg", IMAGES_DIR / "icon.png", 512, 512)
    write(RES_DIR / "drawable/ic_launcher_foreground.xml", foreground_xml())
    write(RES_DIR / "drawable/ic_launcher_background.xml", background_xml())


if __name__ == "__main__":
    main()
