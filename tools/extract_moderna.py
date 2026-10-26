"""Rebuild ArcQ's small Moderna atlas from Jorge Avila's original CC BY 3.0 PSD.

Usage: python tools/extract_moderna.py /path/to/640x480_0.psd
Build-time dependencies only: Pillow, psd-tools. No network access or game dependency.
Source and attribution: src/main/resources/META-INF/licenses/moderna/NOTICE.txt
"""
from pathlib import Path
import sys

from PIL import Image, ImageEnhance, ImageOps
from psd_tools import PSDImage


def extract(source: Path, target: Path) -> None:
    psd = PSDImage.open(source)

    def layer(*indices):
        item = psd
        for index in indices:
            item = item[index]
        return item.topil().convert("RGBA")

    atlas = Image.new("RGBA", (128, 128))
    frame = ImageEnhance.Brightness(layer(3, 3)).enhance(1.7)
    assert frame.size == (274, 387), "Unexpected Moderna PSD revision"
    # Preserve only eight border patches; the interior remains completely transparent.
    bounds_x, bounds_y = (0, 18, 256, 274), (0, 18, 369, 387)
    positions, sizes = (0, 8, 40), (8, 32, 8)
    for row in range(3):
        for col in range(3):
            if row == col == 1:
                continue
            tile = frame.crop((bounds_x[col], bounds_y[row], bounds_x[col + 1], bounds_y[row + 1]))
            atlas.alpha_composite(tile.resize((sizes[col], sizes[row]), Image.Resampling.LANCZOS),
                                  (positions[col], positions[row]))
    corner = ImageEnhance.Brightness(layer(2, 31, 0)).enhance(1.7)
    corner = corner.resize((24, 24), Image.Resampling.LANCZOS)
    atlas.alpha_composite(corner, (48, 0))
    atlas.alpha_composite(ImageOps.mirror(corner), (72, 0))
    atlas.alpha_composite(ImageOps.flip(corner), (48, 24))
    atlas.alpha_composite(ImageOps.flip(ImageOps.mirror(corner)), (72, 24))
    crystal = layer(3, 2, 2)
    neutral_crystal = ImageOps.grayscale(crystal).convert("RGBA")
    neutral_crystal.putalpha(crystal.getchannel("A"))
    atlas.alpha_composite(neutral_crystal, (0, 64))
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target, optimize=True)


if __name__ == "__main__":
    destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/arc_quest/textures/gui/style/moderna.png"
    extract(Path(sys.argv[1]), destination)
    print(destination)
