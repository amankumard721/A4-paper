import json,unicodedata
from pathlib import Path
import pypdf,pypdfium2,fitz
root=Path(__file__).resolve().parents[2]/'validation/audit'
samples=json.loads((root/'hindi-input.json').read_text(encoding='utf8'))
result={}
for name in ['chromium-comparator','unicode-samples']:
    file=root/(name+'.pdf');py=pypdf.PdfReader(file);mu=fitz.open(file);ium=pypdfium2.PdfDocument(file)
    fonts=[f.get_object() for f in py.pages[0]['/Resources']['/Font'].values()]
    text={'pypdf':py.pages[0].extract_text(),'PyMuPDF':mu[0].get_text(),'PDFium':ium[0].get_textpage().get_text_range()}
    result[name]={'producer':py.metadata.producer,'fontTypes':[f.get('/Subtype') for f in fonts],'toUnicodeCount':sum('/ToUnicode' in f for f in fonts),'actualTextOccurrences':py.pages[0].get_contents().get_data().count(b'/ActualText'),'readers':{}}
    for engine,raw in text.items():
        lines=[unicodedata.normalize('NFC',s.strip()) for s in raw.splitlines() if s.strip()]
        result[name]['readers'][engine]={'original':samples,'extracted':raw,'exactLines':lines==samples,'mismatches':[{'line':i+1,'expected':s,'actual':lines[i] if i<len(lines) else '<missing>'} for i,s in enumerate(samples) if i>=len(lines) or lines[i]!=s]}
(root/'baseline-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print({n:{e:v['exactLines'] for e,v in r['readers'].items()} for n,r in result.items()})
