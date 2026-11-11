/** Adjustment queue presentation; financial decisions remain in the backend service. */
const tuitionAdjustments=(()=>{
 const $=id=>document.getElementById(id);
 const labels={PENDING:'Chờ duyệt',APPROVED:'Đã duyệt',REJECTED:'Đã từ chối'};
 const tones={PENDING:'warning',APPROVED:'success',REJECTED:'neutral'};
 const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
 const normalized=value=>String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLocaleLowerCase('vi').trim();
 const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
 let items=[],options,request=0;
 function info(label,value) {const el=node('div');el.append(node('dt',label),node('dd',value||'—'));return el;}
 function card(item) {
  const el=node('article',null,'adjustment-card');
  const head=node('header',null,'adjustment-card-heading'),identity=node('div');
  identity.append(node('p',item.code,'adjustment-code'),node('h3',item.student));
  head.append(identity,node('span',labels[item.status]||item.status,'ledger-badge tone-'+(tones[item.status]||'neutral')));
  el.append(head);
  const body=node('div',null,'adjustment-body'),amount=node('div',null,'adjustment-amount');
  amount.append(node('span','Khoản tiền mặt đề nghị điều chỉnh'),node('strong',money(item.amount)));
  const details=node('dl',null,'adjustment-details');
  details.append(info('Lý do đề nghị',item.reason),info('Người lập đề nghị',item.requestedBy));
  if(item.reviewedBy)details.append(info('Người xử lý',item.reviewedBy),info('Kết quả kiểm tra',item.reviewNote));
  body.append(amount,details);el.append(body);
  const footer=node('footer',null,'adjustment-card-footer');
  if(item.status==='PENDING') {
   footer.append(node('p','Chỉ duyệt sau khi kiểm tra khoản thu. Người lập không được tự duyệt.'));
   const actions=node('div',null,'adjustment-buttons');
   for(const approve of [true,false]) {
    const button=node('button',approve?'Duyệt điều chỉnh':'Từ chối',approve?'btn btn-success btn-sm':'btn btn-outline-secondary btn-sm');
    button.type='button';button.addEventListener('click',()=>options.review(item,approve));actions.append(button);
   }
   footer.append(actions);
  } else footer.append(node('p',item.status==='APPROVED'?'Đã điều chỉnh khoản thu; biên lai gốc vẫn được giữ để tra cứu.':'Đề nghị bị từ chối; khoản thu không thay đổi.'));
  el.append(footer);return el;
 }
 function render() {
  const keyword=normalized($('correction-keyword').value),state=$('correction-state').value;
  const filtered=items.filter(item=>(!state||item.status===state)&&normalized([item.code,item.student,item.reason,item.requestedBy,item.reviewedBy,item.reviewNote].join(' ')).includes(keyword));
  tuitionPagination.render('điều chỉnh khoản thu',filtered,$('correction-records'),card);
  if(!filtered.length) {
   const empty=node('div',null,'adjustment-empty');empty.append(node('i',null,'bi bi-inbox'),node('h3',items.length?'Không có đề nghị phù hợp':'Chưa có đề nghị điều chỉnh'),node('p',items.length?'Thử thay từ khóa hoặc trạng thái để tìm lại.':'Mở biên lai của khoản thu tiền mặt nhập sai để lập đề nghị.'));
   $('correction-records').append(empty);
  }
  $('correction-count').textContent=String(items.filter(item=>item.status==='PENDING').length);
  $('correction-count').setAttribute('aria-label',items.filter(item=>item.status==='PENDING').length+' đề nghị chờ duyệt');
  $('correction-summary').replaceChildren(...['PENDING','APPROVED','REJECTED'].map(state=>{
   const tile=node('div',null,'adjustment-stat');tile.append(node('span',labels[state]),node('strong',items.filter(item=>item.status===state).length));return tile;
  }));
 }
 async function load() {
  const id=++request;$('correction-refresh').disabled=true;$('correction-error').hidden=true;
  $('correction-records').setAttribute('aria-busy','true');
  try {const data=await options.api.reversals();if(id!==request)return;items=data||[];render();}
  catch(error){if(id===request){$('correction-error').textContent=error.message;$('correction-error').hidden=false;}}
  finally{if(id===request){$('correction-refresh').disabled=false;$('correction-records').setAttribute('aria-busy','false');}}
 }
 function init(config) {
  options=config;
  for(const id of ['correction-keyword','correction-state'])$(id).addEventListener(id==='correction-keyword'?'input':'change',()=>{tuitionPagination.reset('điều chỉnh khoản thu');render();});
  $('correction-refresh').addEventListener('click',load);
 }
 return {init,load};
})();
