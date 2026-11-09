import { imageStyles, imageAngle } from './image-geometry.js';
import { TouchResize } from './resize.js';
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
    rotation:{default:0,parseHTML:e=>imageAngle(e.getAttribute('data-rotation'))},
    stretchRatio:{default:null,parseHTML:e=>Number(e.getAttribute('data-stretch-ratio'))||null},
    aspectLocked:{default:true,parseHTML:e=>e.getAttribute('data-aspect-locked')!=='false'},
    align:{default:'left',parseHTML:e=>e.getAttribute('data-align')||'left'},
  };},
  parseHTML(){return [{tag:'div[data-image-frame]'}, {tag:'img[data-asset-id]'}];},
  renderHTML({node}){
    const {assetId,width,align,alt,rotation,stretchRatio,aspectLocked}=node.attrs;
    const safe=assetPattern.test(assetId||''),styles=imageStyles(node.attrs);
    return ['div',{'data-image-frame':'true','data-asset-id':safe?assetId:'','data-width':Math.max(10,Math.min(100,Number(width)||50)),
      'data-align':['left','center','right'].includes(align)?align:'left','data-rotation':styles.g.angle,
      'data-stretch-ratio':stretchRatio==null?'':styles.g.ratio,'data-aspect-locked':aspectLocked!==false?'true':'false',
      style:styles.frame,contenteditable:'false'},
      ['img',{src:safe?assetUrl(assetId):'/missing-image',alt:alt||'Document image','data-asset-id':safe?assetId:'',style:styles.image}]];
  },
}).configure({allowBase64:false});
export const LekhakTableRow = TableRow.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      height: {
        default: null,
        parseHTML: element => {
          const val = element.style.height || element.getAttribute('data-height');
          const num = parseInt(val, 10);
          return num && num >= 24 ? num : null;
        },
        renderHTML: attributes => {
          if (!attributes.height) return {};
          return {
            'data-height': attributes.height,
            style: `height:${attributes.height}px`,
          };
        },
      },
    };
  },
});
const CellStyle=Extension.create({name:'cellStyle',addGlobalAttributes(){return [{types:['tableCell','tableHeader'],attributes:{
  borderColor:{default:'#64748b',parseHTML:e=>e.style.borderColor,renderHTML:a=>({style:`border-color:${/^#[0-9a-f]{6}$/i.test(a.borderColor)?a.borderColor:'#64748b'}`})},
  borderWidth:{default:1,parseHTML:e=>parseInt(e.style.borderWidth)||0,renderHTML:a=>({style:`border-width:${[0,1,2].includes(a.borderWidth)?a.borderWidth:1}px`})},
  backgroundColor:{default:null,parseHTML:e=>e.getAttribute('data-cell-fill'),renderHTML:a=>/^#[0-9a-f]{6}$/i.test(a.backgroundColor||'')?{'data-cell-fill':a.backgroundColor,style:`background-color:${a.backgroundColor}`} : {}},
}}];}});
export const mediaExtensions=[Table.configure({resizable:true,cellMinWidth:36,allowTableNodeSelection:true}),LekhakTableRow,TableCell,TableHeader,CellStyle,PrivateImage,TouchResize];
