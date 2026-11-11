(() => {
 if(!checkAuth()) return;
 const role=getCurrentUser().role;
 const parent=document.body.dataset.tuitionView==='parent';
 const manager=['ADMIN','BRANCH_MANAGER'].includes(role);
 if(parent ? role!=='PARENT' : !['STAFF','ADMIN','BRANCH_MANAGER'].includes(role)) {
  window.location.replace(appPath('/index.html')); return;
 }
 const $=id=>document.getElementById(id);
 const api=installmentApi;
 const pagination=tuitionPagination;
 let initialInvoiceId=new URLSearchParams(location.search).get('invoiceId');
 const money=n=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(n);
 const date=d=>d?d.split('T')[0].split('-').reverse().join('/'):'—';
 const dateTime=d=>d?date(d)+' · '+d.split('T')[1].slice(0,5):'—';
 const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
 const status={UNPAID:'Chưa đóng',PARTIALLY_PAID:'Đã đóng một phần',PAID:'Đã đóng đủ',CANCELLED:'Đã hủy',
  PENDING:'Chờ duyệt',APPROVED:'Đã duyệt',REJECTED:'Từ chối',CREATING:'Đang tạo QR',UNCERTAIN:'Cần kiểm tra với PayOS',
  RECONCILE:'Cần kiểm tra ngân hàng',UPCOMING:'Sắp đến hạn',DUE_TODAY:'Đến hạn hôm nay',OVERDUE:'Đã quá hạn'};
 function badge(text,tone='neutral') {return node('span',text,'ledger-badge tone-'+tone);}
 function sectionHeading(el,title,description,count) {
  const head=node('div',null,'ledger-section-heading');
  head.append(node('h2',title),badge(count+' hồ sơ'));
  el.replaceChildren(head,node('p',description,'ledger-help'));
 }
 let invoices=[];
 let action;
 let busy=false;
 let loadId=0;
 let qrTimer;
 function selectTab(adjustments,updateUrl=true) {
  if(!manager||!$('tuition-tabs'))return;
  $('tuition-panel').hidden=adjustments;$('reversals').hidden=!adjustments;
  for(const [id,selected] of [['tuition-tab',!adjustments],['corrections-tab',adjustments]]) {
   $(id).classList.toggle('active',selected);$(id).setAttribute('aria-selected',String(selected));$(id).tabIndex=selected?0:-1;
  }
  if(updateUrl){const url=new URL(location.href);if(adjustments){url.searchParams.set('tab','adjustments');url.searchParams.delete('invoiceId');url.searchParams.delete('invoiceCode');}else url.searchParams.delete('tab');history.replaceState(null,'',url);}
 }
 if(manager && $('tuition-tabs')) {
  $('tuition-tabs').hidden=false;
  tuitionAdjustments.init({api,review:(item,approve)=>{
   open(approve?'Duyệt điều chỉnh khoản thu':'Từ chối đề nghị điều chỉnh',async data=>{
    await api.reviewReversal(item.id,approve,data.get('note'));
    return {message:approve?'Đã duyệt điều chỉnh và cập nhật tiền đã thu.':'Đã từ chối đề nghị; khoản thu không thay đổi.'};
   });
   $('action-content').append(node('p',item.student+' · '+item.code+' · '+money(item.amount),'payment-context'),node('p','Lý do đề nghị: '+item.reason,'ledger-help'));
   field('Ghi chú xác minh và quyết định','note',{multiline:true,maxLength:500});
  }});
  for(const [id,adjustments] of [['tuition-tab',false],['corrections-tab',true]]) {
   $(id).addEventListener('click',()=>selectTab(adjustments));
   $(id).addEventListener('keydown',event=>{
    if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();
    const next=event.key==='Home'?false:event.key==='End'?true:!adjustments;
    selectTab(next);$(next?'corrections-tab':'tuition-tab').focus();
   });
  }
  selectTab(!initialInvoiceId && new URLSearchParams(location.search).get('tab')==='adjustments',false);
 }
 function node(tag,text,cls) {
  const n=document.createElement(tag);
  if(text!=null)n.textContent=text;
  if(cls)n.className=cls;
  return n;
 }
 function feedback(text,error=false) {
  $('feedback').hidden=!text;
  $('feedback').className='alert '+(error?'alert-danger':'alert-success');
  $('feedback').textContent=text;
 }
 function button(text,fn,cls='btn btn-outline-success btn-sm') {
  const b=node('button',text,cls); b.type='button';
  b.addEventListener('click',async()=>{try{await fn();}catch(e){feedback(e.message,true);}});
  return b;
 }
 function open(title,fn) {
  clearTimeout(qrTimer);
  $('action-dialog').removeAttribute('data-mode');
  $('action-title').textContent=title;
  $('action-content').replaceChildren();
  $('action-error').textContent='';
  $('submit-action').hidden=!fn;
  $('submit-action').disabled=false;
  $('submit-action').textContent='Xác nhận';
  action=fn;
  if(!$('action-dialog').open)$('action-dialog').showModal();
 }
 function field(label,name,options={}) {
  const wrap=node('label',label);
  const input=node(options.multiline?'textarea':'input',null,'form-control');
  const {multiline,...props}=options;
  Object.assign(input,{name,required:true},props);
  wrap.append(input);$('action-content').append(wrap);
  return input;
 }
 function integer(value) {
  const raw=String(value||'').trim();
  if(!/^\d+$/.test(raw)) throw new Error('Nhập tiền VND bằng các chữ số, ví dụ 1500000.');
  return Number(raw);
 }
 function table(headers,rows) {
  const scroll=node('div',null,'ledger-scroll'),t=node('table',null,'ledger-table');
  const head=node('thead'),tr=node('tr');
  headers.forEach(h=>tr.append(node('th',h)));head.append(tr);
  const body=node('tbody');
  rows.forEach(row=>{const tr=node('tr'); row.forEach(value=>{const td=node('td');if(value instanceof Node)td.append(value);else td.textContent=value;tr.append(td);}); body.append(tr);});
  t.append(head,body);scroll.append(t);return scroll;
 }
 function ask(title,text,fn) {open(title,fn);$('action-content').append(node('p',text));}
 function previewReceipt(invoice,receipt) {
  open('Biên lai '+receipt.code,null);
  $('action-dialog').dataset.mode='receipt';
  $('action-content').append(tuitionReceipt.render(invoice,receipt),
   button('In biên lai',()=>window.print(),'btn btn-success receipt-print'));
 }
 function configure(invoice) {
  open('Chốt lịch đóng · '+invoice.code, async data=>{
   const dues=[];
   for(let i=0;i<Number(data.get('count'));i++)dues.push({amount:integer(data.get('amount'+i)),date:data.get('date'+i)});
   await api.plan(invoice.id,{installments:dues,discountMethod:data.get('discountMethod'),discountValue:integer(data.get('discountValue')),discountReason:data.get('discountReason')});
  });
  const base=Number(invoice.originalAmount??invoice.total);
  const discountLabel=node('label','Kiểu ưu đãi'),method=node('select',null,'form-select');
  method.name='discountMethod';
  method.add(new Option('Giảm theo số tiền','FIXED_AMOUNT'));method.add(new Option('Giảm theo phần trăm','PERCENTAGE'));
  discountLabel.append(method);$('action-content').append(discountLabel);
  const value=field('Mức giảm (đ hoặc %)','discountValue',{inputMode:'numeric',value:invoice.discountAmount||0});
  const reason=field('Lý do ưu đãi','discountReason',{maxLength:255,required:false,value:invoice.discountReason||''});
  const totalLabel=node('p',null,'alert alert-light');$('action-content').append(totalLabel);
  let total=base;
  const label=node('label','Phương án đóng'),select=node('select',null,'form-select');
  select.name='count';
  [1,2,3].forEach(n=>select.add(new Option(n===1?'Đóng một lần':n+' kỳ',n)));
  label.append(select);$('action-content').append(label);
  const rows=node('div');$('action-content').append(rows);
  function render() {
   rows.replaceChildren();
   const reduction=method.value==='PERCENTAGE'?Math.round(base*Number(value.value||0)/100):Number(value.value||0);
   total=Math.max(0,base-reduction);
   reason.required=reduction>0;
   totalLabel.textContent='Học phí gốc '+money(base)+' · Giảm '+money(reduction)+' · Còn phải đóng '+money(total);
   const count=Number(select.value),first=count===1?total:Math.ceil(total/2);
   const amounts=count===1?[first]:count===2?[first,total-first]:[first,Math.floor((total-first)/2),total-first-Math.floor((total-first)/2)];
   for(let i=0;i<count;i++) {
    const group=node('div',null,'ledger-card');
    const amountLabel=node('label','Kỳ '+(i+1)+' · Số tiền (đ)'),amount=node('input',null,'form-control');
    Object.assign(amount,{name:'amount'+i,required:true,inputMode:'numeric',value:amounts[i]});
    const dateLabel=node('label','Hạn đóng'),due=node('input',null,'form-control');
    Object.assign(due,{type:'date',name:'date'+i,required:true,min:today,value:i===0?today:''});
    amountLabel.append(amount);dateLabel.append(due);group.append(amountLabel,dateLabel);rows.append(group);
   }
  }
  select.addEventListener('change',render);method.addEventListener('change',render);value.addEventListener('input',render);render();
 }
 function payment(invoice) {
  const next=invoice.installments.find(d=>Number(d.remaining)>0);
  const key=crypto.randomUUID();
  open('Thanh toán đợt '+(next?.sequence||1)+' · '+invoice.code,async data=>{
   const amount=integer(data.get('amount'));
   if(data.get('method')==='CASH') {
    await api.collect(invoice.id,{amount,cashGiven:integer(data.get('given')),idempotencyKey:key});
   } else {
    const intent=await api.intent(invoice.id,amount,parent);
    showIntent(intent,invoice);return {keepOpen:true};
   }
  });
  const context=node('div',null,'payment-context');
  context.append(node('strong',invoice.student),node('span',invoice.className),node('p','Còn phải đóng '+money(invoice.balance)+'. Khoản thu được phân bổ vào đợt còn thiếu sớm nhất.'));
  $('action-content').append(context);
  field('Số tiền thanh toán (đ)','amount',{inputMode:'numeric',value:next?next.remaining:invoice.balance});
  const label=node('label','Phương thức thanh toán'),method=node('select',null,'form-select');
  method.name='method';
  if(!parent)method.add(new Option('Tiền mặt tại quầy','CASH'));
  if(Number(invoice.balance)>0)method.add(new Option('Chuyển khoản QR / PayOS','QR'));
  label.append(method);$('action-content').append(label);
  const given=field('Tiền khách đưa (đ)','given',{inputMode:'numeric',value:next?next.remaining:0});
  function toggle() {
   given.parentElement.hidden=method.value!=='CASH';given.required=method.value==='CASH';
   $('submit-action').textContent=method.value==='CASH'?'Xác nhận đã thu tiền':'Tạo mã QR';
  }
  method.addEventListener('change',toggle);toggle();
 }
 function qrView(intent,width=256) {
  const qr=node('div',null,'payment-qr');
  if(/^https:\/\//.test(intent.qrCode)) {
   const img=node('img',null,'qr-image');img.src=intent.qrCode;img.alt='Mã QR thanh toán';qr.append(img);
  } else if(intent.qrCode && typeof QRCode!=='undefined') {
   const canvas=node('canvas',null,'qr-image');qr.append(canvas);
   QRCode.toCanvas(canvas,intent.qrCode,{width,margin:2},error=>{
    if(error)qr.replaceChildren(node('p','Không dựng được QR. Vui lòng mở trang PayOS.'));
   });
  }
  return qr;
 }
 function confirmBank(intent,invoice) {
  open('Xác nhận đã thu tiền chuyển khoản',async data=>{
   await api.confirmBank(intent.id,{amount:integer(data.get('amount')),reference:data.get('reference').trim(),reason:data.get('reason').trim(),bankVerified:data.get('bankVerified')==='on'});
  });
  $('action-content').append(node('p',invoice.student+' · '+invoice.code+' · '+money(intent.amount),'payment-context'),
   node('p','Dùng khi ngân hàng đã ghi có nhưng hệ thống chưa nhận được xác nhận tự động. Kiểm tra tài khoản ngân hàng của trung tâm, đúng người chuyển, số tiền và nội dung. Không chỉ dựa vào ảnh thông báo chuyển tiền của khách.','ledger-help'));
  field('Mã giao dịch ngân hàng','reference',{maxLength:120});
  field('Số tiền ngân hàng thực nhận (đ)','amount',{inputMode:'numeric',value:intent.amount});
  field('Lý do xác nhận thủ công và kết quả kiểm tra','reason',{multiline:true,maxLength:400});
  const label=node('label',null,'bank-verification'),check=node('input',null,'form-check-input');
  Object.assign(check,{name:'bankVerified',type:'checkbox',required:true});
  label.append(check,node('span','Tôi đã kiểm tra ngân hàng trung tâm thực nhận đúng khoản tiền này.'));$('action-content').append(label);
  $('submit-action').textContent='Xác nhận đã thu tiền';
 }
 function showIntent(intent,invoice) {
  open('Thanh toán bằng mã QR',null);
  const content=$('action-content');
  content.append(node('p',invoice.student+' · '+invoice.code,'ledger-help'),node('p',money(intent.amount),'qr-amount'));
  const state=node('p',intent.status==='PENDING'?'Đang chờ chuyển khoản':(status[intent.status]||intent.status),'fw-semibold');content.append(state);
  if(intent.simulated) content.append(node('p','MÔ PHỎNG LOCAL · Không chuyển tiền thật. Quản trị viên dùng nút mô phỏng để kiểm thử.','alert alert-warning'));
  else {
   if(intent.qrCode)content.append(qrView(intent));
   if(intent.checkoutUrl && /^https:\/\//.test(intent.checkoutUrl)) {
    const link=node('a','Mở trang thanh toán PayOS','btn btn-success');
    link.href=intent.checkoutUrl;link.target='_blank';link.rel='noopener noreferrer';content.append(link);
   }
   content.append(node('p','Sau khi thanh toán, giữ trang này để hệ thống kiểm tra xác nhận từ cổng.'));
  }
  const actions=node('div',null,'ledger-actions');content.append(actions);
  actions.append(button('Hủy yêu cầu QR',async()=>{
   await api.cancelIntent(intent.id,parent);$('action-dialog').close();await load();
  }));
  if(!parent && !intent.simulated && (['PENDING','UNCERTAIN'].includes(intent.status) || role==='ADMIN' && intent.status==='RECONCILE'))
   actions.append(button('Xác nhận đã thu tiền',()=>confirmBank(intent,invoice),'btn btn-success'));
  if(intent.simulated && role==='ADMIN')actions.append(button('Mô phỏng nhận tiền',async()=>{await api.simulate(intent.id);await poll();}));
  async function poll() {
   clearTimeout(qrTimer);
   if(!$('action-dialog').open)return;
   try {
    const current=await api.intentStatus(intent.id,parent);
    state.textContent=current.status==='PENDING'?'Đang chờ chuyển khoản':(status[current.status]||current.status);
    if(current.status==='PAID') {
     state.textContent='Đã nhận tiền. Biên lai đã được cập nhật.';
     $('action-dialog').close();
     feedback('Đã nhận tiền chuyển khoản. Giao dịch và biên lai đã hoàn tất.');
     await load();return;
    }
    if(['RECONCILE','CANCELLED'].includes(current.status))return;
   } catch(e) {state.textContent=e.message;}
   if($('action-dialog').open)qrTimer=setTimeout(poll,4000);
  }
  qrTimer=setTimeout(poll,4000);
 }
 function requestChange(invoice,due) {
  open('Đề nghị gia hạn kỳ '+due.sequence,async data=>{
   await api.change(invoice.id,{installmentId:due.id,proposedDate:data.get('date'),reason:data.get('reason')});
  });
  $('action-content').append(node('p','Hạn hiện tại '+date(due.date)+' · Còn thiếu '+money(due.remaining)));
  field('Hạn mới','date',{type:'date',min:today});
  field('Lý do theo trao đổi với phụ huynh','reason',{multiline:true,maxLength:500});
 }
 function reservation(invoice) {
  open('Hủy hồ sơ chờ thanh toán',data=>api.reservation(invoice.id,{action:'CANCEL',reason:data.get('reason'),contactConfirmed:data.get('contactConfirmed')==='on'}));
  $('action-content').append(node('p',invoice.student+' · Phụ huynh '+invoice.parent+' · '+(invoice.phone||'Chưa có số điện thoại'),'payment-context'),
   node('p','Admin liên hệ phụ huynh và kiểm tra chưa nhận tiền trước khi hủy. QR chưa trả sẽ được đóng trước khi hủy; hóa đơn và ghi danh giữ trạng thái đã hủy để tra cứu.','ledger-help'));
  field('Kết quả liên hệ và lý do hủy','reason',{multiline:true,maxLength:500});
  const label=node('label',null,'bank-verification'),check=node('input',null,'form-check-input');Object.assign(check,{name:'contactConfirmed',type:'checkbox',required:true});
  label.append(check,node('span','Tôi đã liên hệ phụ huynh và kiểm tra trung tâm chưa nhận tiền.'));$('action-content').append(label);
 }
 function card(invoice) {
  const el=node('article',null,'ledger-card');el.id='invoice-'+invoice.id;
  const head=node('div',null,'invoice-heading'),identity=node('div');
  identity.append(node('p',invoice.code,'invoice-code'),node('h2',invoice.student),node('p',invoice.className,'invoice-class'));
  head.append(identity,badge(status[invoice.status]||invoice.status,invoice.status==='PAID'?'success':invoice.status==='CANCELLED'?'neutral':'warning'));el.append(head);
  if(!parent)el.append(node('p','Phụ huynh: '+invoice.parent+(invoice.phone?' · '+invoice.phone:''),'invoice-parent'));
  const amounts=node('div',null,'invoice-amounts');
  [['Học phí sau ưu đãi',invoice.total],['Đã thu',invoice.paid],['Còn phải đóng',invoice.balance]].forEach(([label,value])=>{const item=node('div');item.append(node('span',label),node('strong',money(value)));amounts.append(item);});
  el.append(amounts);
  const progress=node('div',null,'invoice-progress');
  progress.setAttribute('role','progressbar');progress.setAttribute('aria-label','Tiến độ đóng học phí');
  const percent=Number(invoice.total)>0?Math.min(100,Math.round(Number(invoice.paid)/Number(invoice.total)*100)):invoice.status==='PAID'?100:0;
  progress.setAttribute('aria-valuemin','0');progress.setAttribute('aria-valuemax','100');progress.setAttribute('aria-valuenow',String(percent));
  const fill=node('span');fill.style.width=percent+'%';progress.append(fill);el.append(progress);
  if(invoice.reviewRequired)el.append(node('p',invoice.reviewReason,'alert alert-warning'));
  if(invoice.reservationPending)el.append(node('p','Chờ đóng đủ đợt đầu để ghi danh chính thức. Quá hạn thanh toán không tự hủy hồ sơ.','reservation-info'));
  if(invoice.escalation)el.append(node('p','Quá hạn trên 7 ngày — cần quản lý liên hệ xử lý.','alert alert-warning'));
  el.append(table(['Kỳ','Phải đóng','Đã đóng','Còn thiếu','Hạn','Tình trạng'],invoice.installments.map(d=>
   ['Đợt '+d.sequence,money(d.amount),money(d.paid),money(d.remaining),date(d.date),badge(Number(d.remaining)===0?'Đã đủ':d.overdue?'Quá hạn':d.date===today?'Đến hạn hôm nay':'Chưa đến hạn',Number(d.remaining)===0?'success':d.overdue?'danger':'neutral')])));
  const actions=node('div',null,'ledger-actions');
  (invoice.intents||[]).forEach(intent=>actions.append(button('Mở QR đang chờ',()=>showIntent(intent,invoice))));
  if(role==='ADMIN' && invoice.reservationPending && Number(invoice.paid)===0 && invoice.status!=='CANCELLED')
   actions.append(button('Hủy giữ chỗ',()=>reservation(invoice),'btn btn-outline-danger btn-sm'));
  if(!invoice.reviewRequired && invoice.status!=='CANCELLED') {
   if(!invoice.confirmed) {
    if(!parent)actions.append(button('Chốt lịch đóng',()=>configure(invoice),'btn btn-success'));
    else el.append(node('p','Trung tâm đang chốt lịch đóng học phí.','text-secondary'));
   } else {
    if(Number(invoice.balance)>0 || (Number(invoice.total)===0 && invoice.status!=='PAID')) {
     const next=invoice.installments.find(d=>Number(d.remaining)>0);
     actions.append(button('Thanh toán đợt '+(next?.sequence||1),()=>payment(invoice),'btn btn-success'));
    }
    if(!parent)invoice.installments.filter(d=>Number(d.remaining)>0).forEach(d=>actions.append(button('Gia hạn kỳ '+d.sequence,()=>requestChange(invoice,d))));
   }
  }
  el.append(actions);
  if(invoice.receipts.length) {
   const history=node('details',null,'receipt-history'),summary=node('summary','Biên lai ('+invoice.receipts.length+')');
   history.append(summary);
   const receiptList=node('div');history.append(receiptList);
   receiptList.replaceChildren(...invoice.receipts.map(r=>{
    const row=node('div',null,'receipt-history-row'),info=node('div');
    info.append(node('strong',r.code),node('small',date(r.at)+' · '+(r.method==='CASH_AT_DESK'?'Tiền mặt':'Chuyển khoản PayOS')));
    row.append(info,node('strong',money(r.amount)),badge(r.reversed?'Đã đảo':'Thành công',r.reversed?'danger':'success'),button('Xem biên lai',()=>previewReceipt(invoice,r)));
    if(manager && !r.reversed && r.method==='CASH_AT_DESK')row.append(button('Điều chỉnh thu nhầm',()=>{
     open('Đề nghị điều chỉnh khoản thu nhầm',async data=>{await api.reversal(r.id,data.get('reason'));return {message:'Đã gửi đề nghị điều chỉnh; chờ quản lý khác kiểm tra và duyệt.'};});
     $('action-content').append(node('p','Phiếu '+r.code+' · '+money(r.amount),'payment-context'),node('p','Đề nghị này chỉ điều chỉnh bản ghi tiền mặt nhập sai, không chuyển tiền cho phụ huynh. Khi quản lý khác người đề nghị duyệt, khoản thu này bị loại khỏi số đã đóng và công nợ tăng tương ứng. Biên lai gốc vẫn được giữ.','ledger-help'));
     field('Lý do và kết quả kiểm tra','reason',{multiline:true,maxLength:500});
    }));
    return row;
   }));
   el.append(history);
  }
  if(invoice.reservationHistory?.length) {
   const history=node('details',null,'receipt-history');history.append(node('summary','Lịch sử xử lý hồ sơ ('+invoice.reservationHistory.length+')'));
   invoice.reservationHistory.forEach(a=>history.append(node('p',(a.action==='EXTEND'?'Gia hạn đến '+dateTime(a.newDeadline):'Hủy giữ chỗ')+' · '+a.actor+' · '+dateTime(a.at)+' · '+a.reason,'ledger-help')));el.append(history);
  }
  return el;
 }
 function render() {
  const keyword=$('keyword').value.trim().toLocaleLowerCase('vi');
  const state=$('state').value;
  const items=invoices.filter(i=>([i.student,i.parent,i.phone,i.code,i.className].join(' ').toLocaleLowerCase('vi').includes(keyword))
   && (!state || state==='paid'&&i.status==='PAID' || state==='outstanding'&&Number(i.balance)>0
    || state==='overdue'&&i.installments.some(d=>d.overdue) || state==='review'&&i.reviewRequired));
  pagination.render('hóa đơn',items,$('records'),card);
  if(!items.length)$('records').append(node('p','Chưa có hóa đơn phù hợp.','ledger-card'));
  const totals=[['Tổng học phí',invoices.filter(i=>i.status!=='CANCELLED').reduce((n,i)=>n+Number(i.total),0)],
   ['Đã thu',invoices.reduce((n,i)=>n+Number(i.paid),0)],
   ['Còn phải đóng',invoices.filter(i=>i.status!=='CANCELLED').reduce((n,i)=>n+Number(i.balance),0)]];
  $('summary').replaceChildren(...totals.map(([label,total],index)=>{const el=node('div',null,'summary-tile summary-'+index),icon=node('i',null,'bi '+['bi-receipt','bi-check2-circle','bi-wallet2'][index]);icon.setAttribute('aria-hidden','true');const info=node('div');info.append(node('span',label),node('strong',money(total)));el.append(icon,info);return el;}));
 }
 async function renderNotices() {
  const list=await api.notices();const el=$('notices');el.hidden=!list.length;
  el.replaceChildren(node('h2','Thông báo học phí'));
  const noticeList=node('div');el.append(noticeList);
  pagination.render('thông báo',list,noticeList,n=>{
   const row=node('article',null,'tuition-notice');
   const info=node('div',null,'notice-info');
   info.append(badge((n.read?'':'● ')+(status[n.type]||n.type),n.type==='OVERDUE'?'danger':'warning'),
    node('h3',n.student+(n.sequence?' · Đợt '+n.sequence:''),'h6'),node('p',n.code+' · Hạn '+date(n.date)),node('strong',money(n.remaining)));
   const actions=node('div',null,'ledger-actions');
   actions.append(button('Xem hóa đơn',async()=>{await api.readNotice(n.id);$('keyword').value=n.code;pagination.reset('hóa đơn');render();document.getElementById('invoice-'+n.invoiceId)?.scrollIntoView({behavior:'smooth',block:'start'});await renderNotices();}));
   const invoice=invoices.find(i=>i.id===n.invoiceId);
   if(invoice && n.paymentIntent) {
    if(n.paymentIntent.qrCode && !n.paymentIntent.simulated)row.append(qrView(n.paymentIntent,136));
    actions.append(button('Mở mã QR',async()=>{await api.readNotice(n.id);showIntent(n.paymentIntent,invoice);},'btn btn-success btn-sm'));
   } else if(invoice && invoice.confirmed && !invoice.reviewRequired) {
    actions.append(button('Thanh toán học phí',()=>payment(invoice),'btn btn-success btn-sm'));
    info.append(node('p','QR được gửi khi đến hạn. Nếu chưa có mã, bạn có thể mở thanh toán hoặc liên hệ trung tâm; cũng có thể chủ động đóng trước.','ledger-help'));
   }
   info.append(actions);row.prepend(info);
   return row;
  });
  if(typeof updateTuitionNoticeIndicator==='function')updateTuitionNoticeIndicator();
 }
 async function renderInternal() {
  const adjustmentLoad=manager?tuitionAdjustments.load():Promise.resolve();
  const changes=await api.changes();const el=$('changes');
  el.hidden=!changes.length;sectionHeading(el,'Đề nghị gia hạn','Thay đổi hạn đóng cần quản lý xem xét; các khoản đã thu được giữ nguyên.',changes.length);
  const changeList=node('div');el.append(changeList);
  pagination.render('gia hạn',changes,changeList,c=>{
   const row=node('article',null,'ledger-card');
   row.append(node('h3',c.student+' · '+c.invoiceCode+' · Kỳ '+c.sequence,'h6'),
    node('p',date(c.oldDate)+' → '+date(c.proposedDate)+' · '+c.reason),
    node('p',(status[c.status]||c.status)+' · Đề nghị bởi '+c.requestedBy+(c.reviewedBy?' · Duyệt bởi '+c.reviewedBy:'')+(c.reviewNote?' · '+c.reviewNote:'')));
   if(manager && c.status==='PENDING') [true,false].forEach(approve=>row.append(button(approve?'Duyệt gia hạn':'Từ chối',()=>{
    open(approve?'Duyệt lịch mới':'Từ chối đề nghị',data=>api.reviewChange(c.id,approve,data.get('note')));
    field('Ghi chú quyết định','note',{multiline:true,maxLength:500});
   })));
   return row;
  });
  await adjustmentLoad;
 }
 async function load() {
  const id=++loadId;$('refresh').disabled=true;
  try {
   const data=await api.invoices(parent);if(id!==loadId)return;
   invoices=data;
   if(initialInvoiceId){pagination.select('hóa đơn',invoices.findIndex(i=>String(i.id)===initialInvoiceId));initialInvoiceId=null;}
   render();
   if(parent)await renderNotices();else await renderInternal();
  } finally {if(id===loadId)$('refresh').disabled=false;}
 }
 $('keyword').addEventListener('input',()=>{pagination.reset('hóa đơn');render();});
 $('state').addEventListener('change',()=>{pagination.reset('hóa đơn');render();});
 $('refresh').addEventListener('click',()=>load().catch(e=>feedback(e.message,true)));
 $('close-action').addEventListener('click',()=>{if(!busy)$('action-dialog').close();});
 $('action-dialog').addEventListener('cancel',e=>{if(busy)e.preventDefault();});
 $('action-dialog').addEventListener('close',()=>{clearTimeout(qrTimer);$('action-content').replaceChildren();});
 $('action-form').addEventListener('submit',async e=>{
  e.preventDefault();if(busy||!action)return;
  busy=true;$('submit-action').disabled=true;$('action-error').textContent='';
  try {
   const result=await action(new FormData($('action-form')));
   if(result?.keepOpen)return;
   $('action-dialog').close();feedback(result?.message||'Đã lưu và cập nhật số dư.');await load();
  } catch(error){$('action-error').textContent=error.message;}
  finally {busy=false;$('submit-action').disabled=false;}
 });
 load().then(()=>{
  const id=new URLSearchParams(location.search).get('invoiceId');
  if(id)document.getElementById('invoice-'+id)?.scrollIntoView({behavior:'smooth',block:'start'});
 }).catch(e=>feedback(e.message,true));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&!$('action-dialog').open)load().catch(()=>{});});
 if(parent)setInterval(()=>{
  if(!document.hidden && !$('action-dialog').open && !busy && !$('refresh').disabled)
   load().catch(()=>{});
 },60000);
})();
