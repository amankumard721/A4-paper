import {readdir,readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
const seen=new Set(),inventory=[],notices=[];
for(const entry of await readdir('node_modules/.pnpm')){
 const modules=join('node_modules/.pnpm',entry,'node_modules');let names;try{names=await readdir(modules);}catch{continue;}
 const paths=[];for(const name of names){if(name.startsWith('@')){for(const child of await readdir(join(modules,name)))paths.push(join(modules,name,child));}else paths.push(join(modules,name));}
 for(const path of paths){try{
  const pkg=JSON.parse(await readFile(join(path,'package.json'),'utf8')),key=pkg.name+'@'+pkg.version;if(seen.has(key))continue;seen.add(key);
  const files=(await readdir(path)).filter(n=>/^(license|copying|notice)(\.|$)/i.test(n));const item={name:pkg.name,version:pkg.version,license:pkg.license,files};inventory.push(item);
  for(const file of files)notices.push(`\n===== ${key} — ${file} =====\n`+await readFile(join(path,file),'utf8'));
 }catch{}}
}
await writeFile('../app/src/main/assets/licenses/DEPENDENCY-NOTICES.txt',notices.join('\n'));
await writeFile('../validation/audit/license-inventory.json',JSON.stringify(inventory,null,2));
console.log(`Collected license notices for ${inventory.length} installed packages; includes build/test dependencies.`,inventory.filter(x=>!x.files.length).map(x=>x.name));
