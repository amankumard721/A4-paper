const bounded=(value,min,max,fallback)=>Number.isFinite(Number(value))?Math.max(min,Math.min(max,Number(value))):fallback;
export const imageAngle=value=>((bounded(value,-100000,100000,0)%360)+360)%360;
export function imageGeometry(rotation,ratio=1){
 const angle=imageAngle(rotation),r=bounded(ratio,.05,20,1),t=angle*Math.PI/180;
 return {angle,ratio:r,x:Math.abs(Math.cos(t))+r*Math.abs(Math.sin(t)),y:Math.abs(Math.sin(t))+r*Math.abs(Math.cos(t))};
}
export function imageStyles(attrs,naturalRatio=1){
 const g=imageGeometry(attrs.rotation,attrs.stretchRatio??naturalRatio),w=bounded(attrs.width,10,100,50);
 return {g,frame:`width:${w}%;aspect-ratio:${g.x} / ${g.y}`,image:`width:${100/g.x}%;height:${100*g.ratio/g.y}%;transform:translate(-50%,-50%) rotate(${g.angle}deg)`};
}
export function layoutImage(frame){
 if(!frame?.matches?.('[data-image-frame]'))return;
 const image=frame.querySelector('img');if(!image?.naturalWidth)return;
 const styles=imageStyles({width:frame.dataset.width,rotation:frame.dataset.rotation,stretchRatio:frame.dataset.stretchRatio||null},image.naturalHeight/image.naturalWidth);
 frame.style.cssText=styles.frame;image.style.cssText=styles.image;
}
