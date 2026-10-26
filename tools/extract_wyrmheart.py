"""Extract Wyrmheart's CC0 RPG Game UI into ArcQ's native HUD atlas.

Usage: python tools/extract_wyrmheart.py [path/to/interface.psd]
Build-time dependencies only: Pillow, psd-tools. No downloads or runtime dependency.
Original PSD and provenance: art_sources/hud/wyrmheart-rpg-game-ui/.
"""
from hashlib import sha256
from pathlib import Path
import sys

from PIL import Image, ImageChops, ImageEnhance
from psd_tools import PSDImage


ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA256 = "d58e29bd74cfe46401910dfe05784ed72f67b5f4880eff210d9026f60854a184"


def extract(source: Path, target: Path) -> None:
    if sha256(source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError("Unexpected Wyrmheart PSD revision")
    psd = PSDImage.open(source)
    atlas = Image.new("RGBA", (256, 256))

    def node(*indices):
        item = psd
        for index in indices:
            item = item[index]
        return item

    def pixels(*indices):
        item = node(*indices)
        return (item.composite() if item.is_group() else item.topil()).convert("RGBA")

    def metal(im):
        return ImageEnhance.Brightness(im).enhance(1.8)

    def neutral(im):
        # The reference's red must never leak into caller-owned theme/rarity colors.
        r, g, b, a = im.split()
        gray = ImageChops.lighter(r, ImageChops.lighter(g, b))
        return Image.merge("RGBA", (gray, gray, gray, a))

    def place(im, xy, size):
        atlas.alpha_composite(im.resize(size, Image.Resampling.LANCZOS), xy)

    # Narrow edge strips retain the engraved metal but never cover the panel center.
    place(metal(neutral(pixels(1, 0, 3, 2))), (0, 0), (64, 8))
    place(metal(neutral(pixels(1, 0, 3, 4))), (0, 8), (64, 8))
    place(metal(pixels(1, 0, 3, 0)), (64, 0), (8, 64))
    place(metal(pixels(1, 0, 3, 1)), (72, 0), (8, 64))
    for i in range(4):
        place(metal(pixels(1, 0, 5, i)), (i * 48, 80), (48, 48))

    # Recessed input: eight border patches, deliberately no center texture.
    field = metal(pixels(13))
    xs, ys = (0, 25, 195, 220), (0, 9, 23, 30)
    pos, sizes = (0, 8, 40), (8, 32, 8)
    for row in range(3):
        for col in range(3):
            if row == col == 1:
                continue
            patch = field.crop((xs[col], ys[row], xs[col + 1], ys[row + 1]))
            place(patch, (128 + pos[col], pos[row]), (sizes[col], sizes[row]))

    # Circular frame only; no baked highlight, inventory item or text.
    ring = Image.new("RGBA", (74, 74))
    for i in (2, 4):
        item = node(10, i)
        ring.alpha_composite(metal(pixels(10, i)), (item.left - 36, item.top - 121))
    place(ring, (192, 0), (64, 64))

    button = node(12)
    button[10].visible = False  # Login text belongs to Minecraft, never the texture.
    # Only modifies the in-memory layer tree; source file bytes stay untouched.
    face = neutral(pixels(12))
    # Normalize the dark red paint into a subtle relief map. Runtime tint then
    # retains the theme's saturation without brightening beyond the text-safe face.
    relief = face.getchannel("R").point(lambda value: 160 + value * 95 // 255)
    face = Image.merge("RGBA", (relief, relief, relief, face.getchannel("A")))
    place(face, (0, 144), (168, 28))
    rim = Image.new("RGBA", (168, 28))
    for i in (3, 5, 7, 9):
        item = node(12, i)
        rim.alpha_composite(metal(pixels(12, i)), (item.left - 15, item.top - 52))
    place(rim, (0, 176), (168, 28))
    # Inventory rim, reserved for compact square cards.
    place(metal(pixels(15, 0, 1)), (192, 80), (40, 40))
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target, optimize=True)


if __name__ == "__main__":
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "art_sources/hud/wyrmheart-rpg-game-ui/interface.psd"
    target = ROOT / "src/main/resources/assets/arc_quest/textures/gui/style/wyrmheart.png"
    extract(source, target)
    print(target)
