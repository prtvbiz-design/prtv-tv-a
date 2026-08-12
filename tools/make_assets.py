#!/usr/bin/env python3
"""
Генерация иконок и TV-баннера.
Бинарники в репозиторий не кладутся — картинки собираются на CI.
Тот же приём, что в «Фабрике»: репозиторий остаётся создаваемым и
правимым через браузер, без загрузки бинарных файлов.
Баннер 320x180 обязателен для лаунчера Android TV: без него приложение
в списке приложений не появится.
"""
import os
from PIL import Image, ImageDraw, ImageFont
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
BG = (16, 16, 20)
FG = (255, 255, 255)
ACCENT = (110, 190, 255)
MIPMAPS = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
def font(size):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()
def centered(draw, box, text, f, fill):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=f)
    w, h = right - left, bottom - top
    draw.text(
        (box[0] + (box[2] - w) / 2 - left, box[1] + (box[3] - h) / 2 - top),
        text, font=f, fill=fill,
    )
def make_icon(size, path):
    img = Image.new("RGBA", (size, size), BG + (255,))
    d = ImageDraw.Draw(img)
    pad = max(2, size // 12)
    d.rounded_rectangle(
        [pad, pad, size - pad, size - pad],
        radius=size // 6, outline=ACCENT, width=max(2, size // 24),
    )
    centered(d, (0, 0, size, size), "A", font(int(size * 0.5)), FG)
    img.save(path)
def make_banner(path):
    w, h = 320, 180
    img = Image.new("RGBA", (w, h), BG + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, h - 6, w, h], fill=ACCENT)
    centered(d, (0, 0, w, h - 30), "PRTV TV A", font(38), FG)
    centered(d, (0, h - 66, w, h - 6), "тестовое приложение", font(17), (138, 138, 150))
    img.save(path)
DESIGN = {
    # имя -> (ширина, высота). Размеры взяты из макета приложения.
    # Это ЗАГЛУШКИ: настоящие файлы кладутся в app/src/main/res/drawable/
    # и перекрывают сгенерированные.
    "start_photo": (1024, 973),
    "prtv_logo_header": (1024, 164),
}
def make_placeholder(name, size, path):
    w, h = size
    img = Image.new("RGBA", (w, h), BG + (255,))
    d = ImageDraw.Draw(img)
    step = max(40, w // 16)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=(30, 32, 38), width=1)
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=(30, 32, 38), width=1)
    centered(d, (0, 0, w, h), name, font(max(14, h // 8)), (90, 95, 105))
    img.save(path)
def main():
    drawable_dir = os.path.join(RES, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    for name, size in DESIGN.items():
        target = os.path.join(drawable_dir, name + ".png")
        if os.path.exists(target):
            print("skip (настоящий файл на месте)", name)
            continue
        make_placeholder(name, size, target)
        print("placeholder", name, size)
    for folder, size in MIPMAPS.items():
        target = os.path.join(RES, folder)
        os.makedirs(target, exist_ok=True)
        make_icon(size, os.path.join(target, "ic_launcher.png"))
        print("icon", folder, size)
    make_banner(os.path.join(drawable_dir, "banner.png"))
    print("banner 320x180")
if __name__ == "__main__":
    main()
