import {build} from 'esbuild';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const text=(await readFile('src/editor.js','utf8')).replace("'./pagination.js'",JSON.stringify(resolve('tests/fixtures/pagination-baseline.js')));
await build({stdin:{contents:text,resolveDir:resolve('src'),sourcefile:'baseline-editor.js'},bundle:true,outfile:'../work/editor-baseline.js',format:'iife',target:'chrome90',minify:true,external:['fs','path']});
