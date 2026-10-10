from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

root = Path(__file__).resolve().parents[2]
directory = root / 'app/src/main/assets/editor/fonts'
families = {'Noto Sans':'NotoSans', 'Noto Devanagari':'NotoSansDevanagari', 'Noto Serif':'NotoSerif'}
faces = []
for label, family in families.items():
    for weight, style in [(400, 'Regular'), (700, 'Bold')]:
        font = TTFont(directory / f'{family}.ttf')
        instantiateVariableFont(font, {'wght':weight, 'wdth':100}, inplace=True).save(directory / f'{family}-{style}.ttf')
        faces.append(f"@font-face{{font-family:'{label}';src:url('fonts/{family}-{style}.ttf') format('truetype');font-weight:{weight};font-display:block}}")
css = root / 'editor/src/editor.css'
lines = [line for line in css.read_text().splitlines() if not line.startswith('@font-face')]
css.write_text('\n'.join(faces + lines), encoding='utf-8')
