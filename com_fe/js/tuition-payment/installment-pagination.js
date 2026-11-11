/** Shared five-item pagination for invoice cards, receipts and review queues. */
const tuitionPagination=(()=>{
 const pages=new Map(),size=5;
 const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
 function render(key,items,container,renderItem) {
  const count=Math.max(1,Math.ceil(items.length/size));
  const page=Math.min(count,Math.max(1,pages.get(key)||1));pages.set(key,page);
  container.replaceChildren(...items.slice((page-1)*size,page*size).map(renderItem));
  if(!items.length)return;
  const nav=node('nav',null,'ledger-pagination');nav.setAttribute('aria-label','Phân trang '+key);
  nav.append(node('span',((page-1)*size+1)+'–'+Math.min(page*size,items.length)+' / '+items.length+' mục · 5 mục/trang'));
  const controls=node('div',null,'pagination-controls');
  function control(label,target,disabled) {
   const b=node('button',label,'btn btn-outline-success btn-sm');b.type='button';b.disabled=disabled;
   b.addEventListener('click',()=>{pages.set(key,target);render(key,items,container,renderItem);});return b;
  }
  controls.append(control('Trước',page-1,page===1),node('strong',page+' / '+count),control('Sau',page+1,page===count));
  nav.append(controls);container.append(nav);
 }
 return {render,reset:key=>pages.set(key,1),select:(key,index)=>pages.set(key,Math.floor(Math.max(0,index)/size)+1)};
})();
