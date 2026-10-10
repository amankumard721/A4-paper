import json,unicodedata,sys
from pathlib import Path
import pypdf,pypdfium2
import fitz
root=Path(__file__).resolve().parents[2]/'validation/audit'
expected=json.loads((root/'hindi-input.json').read_text(encoding='utf8'))
name=sys.argv[1] if len(sys.argv)>1 else 'word-font-poc'
file=root/(name+'.pdf')
pdf=fitz.open(file)
actuals={'pypdf':pypdf.PdfReader(file).pages[0].extract_text(),'PyMuPDF':pdf[0].get_text(),'PDFium':pypdfium2.PdfDocument(file)[0].get_textpage().get_text_range()}
results={}
for engine,text in actuals.items():
    lines=[unicodedata.normalize('NFC',s.strip()) for s in text.splitlines() if s.strip()]
    results[engine]={'expected':expected,'extracted':text,'exactLines':lines==[unicodedata.normalize('NFC',s) for s in expected]}
    print(engine,results[engine]['exactLines'])
    (root/(engine+'-poc.txt')).write_text(text,encoding='utf8')
results['search']={text:bool(pdf[0].search_for(text)) for text in expected}
(root/(name+'-results.json')).write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
pdf[0].get_pixmap(matrix=fitz.Matrix(1.6,1.6)).save(root/(name+'-render.png'))
assert all(v['exactLines'] for k,v in results.items() if k!='search')
assert all(results['search'].values())
