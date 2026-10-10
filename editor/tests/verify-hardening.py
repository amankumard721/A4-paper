"""Save raw extraction and mismatches; only NFC + layout whitespace are normalized."""
import json, unicodedata, re
from pathlib import Path
import pypdf, pypdfium2, fitz
root=Path(__file__).resolve().parents[2]/'validation/audit'
report=json.loads((root/'hardening-browser.json').read_text(encoding='utf8'))
samples=json.loads((root/'hindi-input.json').read_text(encoding='utf8'))
normalize=lambda s:re.sub(r'\s+',' ',unicodedata.normalize('NFC',s)).strip()
results=[]
for case in report['results']:
    name=case['name'];file=root/(name+'.pdf');mu=fitz.open(file);py=pypdf.PdfReader(file);ium=pypdfium2.PdfDocument(file)
    texts={'pypdf':'\n'.join(p.extract_text() for p in py.pages),'PyMuPDF':'\n'.join(p.get_text() for p in mu),'PDFium':'\n'.join(p.get_textpage().get_text_range() for p in ium)}
    expected=normalize(case['text'])
    row={'name':name,'pages':len(mu),'a4':all(abs(min(p.rect.width,p.rect.height)-595.276)<1 and abs(max(p.rect.width,p.rect.height)-841.89)<1 for p in mu),'extractors':{}}
    for engine,text in texts.items():
        actual=normalize(text)
        match=actual==expected
        idx=next((i for i,(a,b) in enumerate(zip(expected,actual)) if a!=b),min(len(expected),len(actual)))
        row['extractors'][engine]={'match':match,'expected':expected,'actual':actual,'firstMismatch':None if match else {'index':idx,'expected':expected[max(0,idx-30):idx+100],'actual':actual[max(0,idx-30):idx+100]}}
        (root/(name+'-'+engine+'.txt')).write_text(text,encoding='utf8')
    if name in ('unicode-samples','serif'):
        row['search']={s:any(p.search_for(s) for p in mu) for s in samples}
    if name=='image-signature':row['embeddedImages']=sum(len(p.get_images()) for p in mu)
    if name=='styles':
        row['meaningfulDoubleSpaces']={engine:'Two  spaces remain.' in raw for engine,raw in texts.items()}
    for i in sorted(set([0,len(mu)-1])):mu[i].get_pixmap(matrix=fitz.Matrix(1.25,1.25)).save(root/f'{name}-page-{i+1}.png')
    results.append(row)
    print(name,row['pages'],{e:r['match'] for e,r in row['extractors'].items()})
(root/'pdf-regression.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
assert all(r['a4'] for r in results)
assert all(v['match'] for r in results for v in r['extractors'].values())
assert all(all(r.get('search',{}).values()) for r in results)
assert all(all(r.get('meaningfulDoubleSpaces',{}).values()) for r in results), 'Known compatibility failure: PDFium collapses consecutive spaces; raw results retained in pdf-regression.json'
