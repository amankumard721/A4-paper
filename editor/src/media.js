import { Extension, mergeAttributes } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import Table from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableCell from '@tiptap/extension-table-cell';
import TableHeader from '@tiptap/extension-table-header';

export const assetPattern=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(?:png|jpg)$/;
export const assetUrl=id=>`/document-assets/${id}`;
export const PrivateImage=Image.extend({
  addAttributes(){return {
    ...this.parent?.(),
    assetId:{default:null,parseHTML:e=>e.getAttribute('data-asset-id')},
    width:{default:50,parseHTML:e=>Number(e.getAttribute('data-width'))||50},
    align:{default:'left',parseHTML:e=>e.getAttribute('data-align')||'left'},
  };},
  renderHTML({node,HTMLAttributes}){
    const {assetId,width,align,alt}=node.attrs;
    const safe=assetPattern.test(assetId||'');
    return ['img',mergeAttributes(HTMLAttributes,{
      src:safe?assetUrl(assetId):'/missing-image',alt:alt||'Document image',
      'data-asset-id':safe?assetId:'','data-width':Math.max(10,Math.min(100,Number(width)||50)),
      'data-align':['left','center','right'].includes(align)?align:'left',
      style:`width:${Math.max(10,Math.min(100,Number(width)||50))}%;height:auto`,
      width:null,height:null,align:null,assetId:null,
    })];
  },
}).configure({allowBase64:false});
const CellStyle=Extension.create({name:'cellStyle',addGlobalAttributes(){return [{types:['tableCell','tableHeader'],attributes:{
  borderColor:{default:'#64748b',parseHTML:e=>e.style.borderColor,renderHTML:a=>({style:`border-color:${/^#[0-9a-f]{6}$/i.test(a.borderColor)?a.borderColor:'#64748b'}`})},
  borderWidth:{default:1,parseHTML:e=>parseInt(e.style.borderWidth)||0,renderHTML:a=>({style:`border-width:${[0,1,2].includes(a.borderWidth)?a.borderWidth:1}px`})},
}}];}});
export const mediaExtensions=[Table.configure({resizable:true,cellMinWidth:36,allowTableNodeSelection:true}),TableRow,TableCell,TableHeader,CellStyle,PrivateImage];
