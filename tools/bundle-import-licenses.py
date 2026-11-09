"""Bundle license notices for the offline spreadsheet/PDF readers."""
from pathlib import Path
from urllib.request import urlopen

target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/licenses'
target.mkdir(parents=True, exist_ok=True)
for filename, url in {
    'JXL-LGPL-2.1.txt': 'https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt',
    'PDFBOX-APACHE-2.0.txt': 'https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/LICENSE.txt',
    'PDFBOX-NOTICE.txt': 'https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/NOTICE.txt',
}.items():
    with urlopen(url, timeout=30) as response:
        data = response.read(200000)
    assert data and b'<html' not in data[:100].lower()
    (target / filename).write_bytes(data)
    print(filename, len(data))
