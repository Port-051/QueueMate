#!/usr/bin/env python3
"""
raw/ 의 Codex 생성 원본을 frontend 배포 규격으로 변환해 final/ 에 저장한다.

    python3 postprocess.py                # 전체
    python3 postprocess.py --only avatar  # 이름에 avatar가 든 것만
    python3 postprocess.py --list         # 목록만 보고 변환 안 함

원본은 건드리지 않는다. raw/ 에 없는 파일은 조용히 건너뛰고 마지막에 요약한다.
"""
import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow가 없다:  pip install --user Pillow")

ROOT = Path(__file__).resolve().parent
RAW, FINAL = ROOT / "raw", ROOT / "final"

# (raw 파일명, 최종 파일명, 폭, 높이, 알파유지, 좌측페이드)
JOBS = [
    ("gen1-mascot-banner.png",  "hero-mascot-banner.webp",     1600, 420,  False, False),
    ("gen2-landing-backdrop.png","landing-hero-backdrop.webp", 1920, 1080, False, False),
    ("gen3-lol.png",            "game-art-lol.webp",            800, 320,  False, True),
    ("gen3-valorant.png",       "game-art-valorant.webp",       800, 320,  False, True),
    ("gen3-pubg.png",           "game-art-pubg.webp",           800, 320,  False, True),
    *[(f"gen4-avatar-{i:02d}.png", f"avatar-{i:02d}.webp",       256, 256,  True,  False)
      for i in range(1, 9)],
    ("gen5-no-match.png",       "empty-no-match.webp",          480, 360,  True,  False),
    ("gen5-no-social.png",      "empty-no-social.webp",         480, 360,  True,  False),
    ("gen5-no-reservation.png", "empty-no-reservation.webp",    480, 360,  True,  False),
    ("gen6-og-backdrop.png",    "og-backdrop.png",             1200, 630,  False, False),
]

PANEL = (7, 14, 29)   # #070E1D — 게임 카드 좌측이 수렴할 색
BG    = (2, 7, 21)    # #020715 — 배너 여백을 채울 배경색


def fit_crop(img, tw, th):
    """비율을 맞춰 중앙 크롭한 뒤 목표 크기로 리샘플. 늘리지 않고 잘라낸다."""
    target = tw / th
    w, h = img.size
    if w / h > target:                       # 원본이 더 넓다 → 좌우를 자른다
        nw = round(h * target)
        box = ((w - nw) // 2, 0, (w - nw) // 2 + nw, h)
    else:                                    # 원본이 더 높다 → 위아래를 자른다
        nh = round(w / target)
        box = (0, (h - nh) // 2, w, (h - nh) // 2 + nh)
    return img.resize((tw, th), Image.LANCZOS, box=box)


def fit_contain_right(img, tw, th):
    """세로를 다 살려 축소한 뒤 우측에 붙이고, 좌측 여백은 원본 왼쪽 끝 색으로 늘려 채운다.
    가로로 아주 긴 배너에서 중앙 크롭을 쓰면 피사체 머리가 잘리기 때문."""
    w, h = img.size
    nw = max(1, round(w * th / h))
    scaled = img.resize((nw, th), Image.LANCZOS)
    if nw >= tw:
        return scaled.crop((nw - tw, 0, nw, th))      # 우측 정렬로 잘라낸다
    canvas = Image.new(img.mode, (tw, th), BG if img.mode == "RGB" else BG + (255,))
    pad = tw - nw
    edge = scaled.crop((0, 0, 1, th)).resize((pad, th), Image.LANCZOS)  # 좌측 끝 열을 늘림
    canvas.paste(edge, (0, 0))
    canvas.paste(scaled, (pad, 0))
    return canvas


def fit_crop_head_centered(img, tw, th):
    """알파 있는 인물 이미지를 '머리 중심'으로 잘라낸다.

    캔버스 정중앙으로 자르면 어깨까지 포함한 실루엣이 한쪽으로 쏠렸을 때 얼굴이
    원형 프레임에서 밀려 보인다. 상단 45% 구간(대부분 머리)의 알파 bbox 중심을
    가로 기준점으로 삼는다."""
    a = img.getchannel("A")
    head = a.crop((0, 0, a.width, max(1, round(a.height * 0.45))))
    bb = head.getbbox() or a.getbbox()
    if bb is None:
        return fit_crop(img, tw, th)

    focus_x = (bb[0] + bb[2]) / 2
    w, h = img.size
    target = tw / th
    if w / h > target:                       # 좌우를 자른다 — 얼굴 중심을 유지
        nw = round(h * target)
        left = min(max(round(focus_x - nw / 2), 0), w - nw)
        box = (left, 0, left + nw, h)
    else:                                    # 위아래를 자른다 — 머리를 살린다
        nh = round(w / target)
        box = (0, 0, w, nh)
    return img.resize((tw, th), Image.LANCZOS, box=box)


def recenter_head(img):
    """자른 뒤에도 남은 가로 쏠림을 평행이동으로 없앤다.

    원본이 세로로 길면 정사각 크롭이 가로 폭을 다 쓰므로 크롭 단계에서는
    좌우를 옮길 여유가 없다. 원형 마스크가 어차피 모서리를 잘라내니
    가장자리 몇 px을 투명으로 비우고 얼굴을 중앙에 놓는 편이 낫다."""
    a = img.getchannel("A")
    head = a.crop((0, 0, a.width, max(1, round(a.height * 0.45))))
    bb = head.getbbox() or a.getbbox()
    if bb is None:
        return img
    dx = round(img.width / 2 - (bb[0] + bb[2]) / 2)
    if dx == 0:
        return img
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (dx, 0))
    return out


def apply_left_fade(img, to_color=PANEL, stop=0.42):
    """좌측을 패널색으로 수렴시킨다. 카드 텍스트가 왼쪽에 얹히기 때문."""
    w, h = img.size
    overlay = Image.new("RGB", (w, h), to_color)
    mask = Image.new("L", (w, 1))
    px = mask.load()
    edge = max(1, int(w * stop))
    for x in range(w):
        px[x, 0] = 255 if x == 0 else (max(0, int(255 * (1 - x / edge))) if x < edge else 0)
    return Image.composite(overlay, img, mask.resize((w, h)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="파일명에 이 문자열이 든 항목만 처리")
    ap.add_argument("--list", action="store_true", help="목록만 출력")
    args = ap.parse_args()

    jobs = [j for j in JOBS if not args.only or args.only in j[0] or args.only in j[1]]
    if args.list:
        for src, dst, w, h, alpha, fade in jobs:
            mark = "O" if (RAW / src).exists() else "-"
            print(f"  [{mark}] {src:30s} -> {dst:28s} {w}x{h}"
                  f"{' alpha' if alpha else ''}{' fade' if fade else ''}")
        print("\n  O = raw/에 있음, - = 아직 없음")
        return

    FINAL.mkdir(exist_ok=True)
    done, missing = [], []

    for src, dst, w, h, alpha, fade in jobs:
        path = RAW / src
        if not path.exists():
            missing.append(src)
            continue

        img = Image.open(path)
        img = img.convert("RGBA" if alpha else "RGB")
        if src.startswith("gen1-"):
            img = fit_contain_right(img, w, h)
        elif src.startswith("gen4-"):        # 아바타는 얼굴 기준으로 맞춘다
            img = recenter_head(fit_crop_head_centered(img, w, h))
        else:
            img = fit_crop(img, w, h)

        if fade:
            img = apply_left_fade(img)

        out = FINAL / dst
        if out.suffix == ".webp":
            img.save(out, "WEBP", quality=80, method=6)
        else:
            img.save(out, "PNG", optimize=True)

        kb = out.stat().st_size / 1024
        done.append(f"  {dst:30s} {w}x{h}  {kb:6.1f} KB")

    if done:
        print(f"변환 완료 {len(done)}개 -> {FINAL}")
        print("\n".join(done))
    if missing:
        print(f"\nraw/에 없어서 건너뜀 {len(missing)}개:")
        for m in missing:
            print(f"  {m}")
    if not done and not missing:
        print("처리할 항목이 없다. --only 값을 확인하라.")


if __name__ == "__main__":
    main()
