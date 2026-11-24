(() => {
 if(!checkAuth()) return;
 const role=getCurrentUser().role;
 const parent=document.body.dataset.tuitionView==='parent';
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
  FAILED:'Chưa tạo được mã QR',RECONCILE:'Cần kiểm tra ngân hàng',UPCOMING:'Sắp đến hạn',DUE_TODAY:'Đến hạn hôm nay',OVERDUE:'Đã quá hạn'};
 function badge(text,tone='neutral') {return node('span',text,'ledger-badge tone-'+tone);}
 let invoices=[];
 let action;
 let busy=false;
 let loadId=0;
 let qrTimer;
 let activeQr=null,closingQr=false;
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
  $('action-form').noValidate=false;
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
  let raw=String(value||'').trim();
  if(/^\d{1,3}([.,])\d{3}(?:\1\d{3})*$/.test(raw))raw=raw.replace(/[.,]/g,'');
  if(!/^\d+$/.test(raw)) throw new Error('Nhập tiền VND bằng các chữ số, ví dụ 1500000.');
  if(!Number.isSafeInteger(Number(raw)))throw new Error('Số tiền không hợp lệ.');
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
  const policy=invoice.discountPolicy||{source:'MANUAL',locked:false};
  const locked=policy.source==='PLACEMENT_TEST';
  const methodOf=data=>locked?policy.method:data.get('discountMethod');
  const valueOf=data=>locked?String(policy.value):data.get('discountValue');
  const reasonOf=data=>locked?policy.reason:data.get('discountReason');
  open('Chốt lịch đóng · '+invoice.code, async data=>{
   const dues=[];
   const discount=discountValue(methodOf(data),valueOf(data));
   if(discount>0&&!String(reasonOf(data)||'').trim())throw new Error('Vui lòng nhập lý do áp dụng ưu đãi học phí.');
   if(String(reasonOf(data)||'').length>255)throw new Error('Lý do ưu đãi không được dài quá 255 ký tự.');
   for(let i=0;i<Number(data.get('count'));i++)dues.push({amount:data.get('amount'+i),date:data.get('date'+i)});
   const errors=tuitionScheduleValidation.validate(dues,total,today,invoice.classEndDate);
   showScheduleErrors(errors);
   if(Object.keys(errors).length){
    rows.querySelector('.is-invalid')?.focus();
    throw new Error(errors.installments||('Có '+Object.keys(errors).length+' trường cần sửa. Vui lòng xem thông báo dưới từng ô được đánh dấu.'));
   }
   dues.forEach(d=>d.amount=Number(d.amount));
   await api.plan(invoice.id,{installments:dues,discountMethod:methodOf(data),discountValue:discountValue(methodOf(data),valueOf(data)),discountReason:reasonOf(data)});
  });
  $('action-form').noValidate=true;
  const base=Number(invoice.originalAmount??invoice.total);
  if(policy.blockingReason){$('action-content').append(node('p',policy.blockingReason,'alert alert-danger'));$('submit-action').disabled=true;return;}
  if(locked)$('action-content').append(node('p','Placement Test #'+policy.placementTestId+' · '+policy.placementScore+'/10 điểm · Ưu đãi cố định '+policy.percentage+'%.','alert alert-info'));
  const discountLabel=node('label','Kiểu ưu đãi'),method=node('select',null,'form-select');
  method.name='discountMethod';
  method.add(new Option('Không giảm','NONE'));method.add(new Option('Giảm theo số tiền','FIXED_AMOUNT'));method.add(new Option('Giảm theo phần trăm','PERCENTAGE'));
  method.value=locked?policy.method:Number(invoice.discountAmount)>0?'FIXED_AMOUNT':'NONE';
  method.disabled=locked;
  discountLabel.append(method);$('action-content').append(discountLabel);
  const value=field('Mức giảm (đ hoặc %)','discountValue',{type:'number',min:'0',step:'1',inputMode:'numeric',value:locked?policy.value:invoice.discountAmount||0});
  value.readOnly=locked;
  const reason=field('Lý do ưu đãi','discountReason',{maxLength:255,required:false,value:locked?policy.reason:invoice.discountReason||''});
  reason.readOnly=locked;
  const totalLabel=node('p',null,'alert alert-light');$('action-content').append(totalLabel);
  let total=base;
  if(invoice.classEndDate)$('action-content').append(node('p','Ngày kết thúc lớp: '+date(invoice.classEndDate)+'. Hạn đóng các kỳ không được sau ngày này.','ledger-help'));
  const label=node('label','Phương án đóng'),select=node('select',null,'form-select');
  select.name='count';
  [1,2,3].forEach(n=>select.add(new Option(n===1?'Đóng một lần':n+' kỳ',n)));
  label.append(select);$('action-content').append(label);
  const rows=node('div');$('action-content').append(rows);
  const errorsByField=new Map();
  function showFieldError(name,message){
   const control=$('action-form').elements.namedItem(name),feedback=errorsByField.get(name);
   if(!control||!feedback)return;
   control.setCustomValidity(message||'');control.classList.toggle('is-invalid',!!message);
   control.setAttribute('aria-invalid',message?'true':'false');
   feedback.textContent=message||'';feedback.hidden=!message;
  }
  function showScheduleErrors(errors){
   for(let i=0;i<Number(select.value);i++)for(const name of ['amount','date'])showFieldError(name+i,errors[`installments[${i}].${name}`]);
  }
  function updateDateLimits(){
   const count=Number(select.value),form=$('action-form');
   const dues=Array.from({length:count},(_,i)=>({date:form.elements.namedItem('date'+i)?.value||''}));
   for(let i=0;i<count;i++){
    const due=form.elements.namedItem('date'+i);if(!due)continue;
    const range=tuitionScheduleValidation.bounds(dues,today,invoice.classEndDate,i);
    due.min=range.min;due.max=range.max;
    const hint=rows.querySelector('[data-date-hint="'+i+'"]');
    hint.textContent=range.max&&range.max<range.min?'Không còn ngày hợp lệ cho kỳ này. Vui lòng sửa hạn kỳ trước hoặc chọn ít kỳ hơn.':tuitionScheduleValidation.rangeText(range);
   }
  }
  function discountValue(type,raw) {
   return tuitionScheduleValidation.discount(base,type,raw);
  }
  function render() {
   rows.replaceChildren();errorsByField.clear();$('action-error').textContent='';
   value.max=String(method.value==='NONE'?0:method.value==='PERCENTAGE'?30:Math.floor(base*30/100));
   value.min=String(method.value==='NONE'?0:method.value==='PERCENTAGE'?1:1000);
   value.readOnly=locked||method.value==='NONE';
   if(method.value==='NONE')value.value='0';
   value.setCustomValidity('');
   let discount;
   try{discount=discountValue(method.value,value.value);}
   catch(error){value.setCustomValidity(error.message);totalLabel.className='alert alert-danger';totalLabel.textContent=error.message;return;}
   totalLabel.className='alert alert-light';
   const reduction=locked?Number(policy.amount):method.value==='NONE'?0:method.value==='PERCENTAGE'?Number((BigInt(base)*BigInt(discount)+50n)/100n):discount;
   total=base-reduction;
   reason.required=reduction>0;
   totalLabel.textContent='Học phí gốc '+money(base)+' · Giảm '+money(reduction)+' · Còn phải đóng '+money(total);
   const count=Number(select.value),first=count===1?total:Math.max(1000,Number((BigInt(total)*30n+99n)/100n));
   const amounts=count===1?[first]:count===2?[first,total-first]:[first,Math.floor((total-first)/2),total-first-Math.floor((total-first)/2)];
   for(let i=0;i<count;i++) {
    const group=node('div',null,'ledger-card');
    const amountLabel=node('label','Kỳ '+(i+1)+' · Số tiền (đ)'),amount=node('input',null,'form-control');
    Object.assign(amount,{name:'amount'+i,required:true,inputMode:'numeric',value:amounts[i]});
    const dateLabel=node('label','Hạn đóng'),due=node('input',null,'form-control');
    Object.assign(due,{type:'date',name:'date'+i,required:true,min:today,max:invoice.classEndDate||'',value:i===0?today:''});
    for(const [name,input] of [['amount',amount],['date',due]]){
     const feedback=node('small',null,'invalid-feedback d-block');feedback.hidden=true;
     feedback.id='tuition-plan-'+name+'-'+i+'-error';
     input.setAttribute('aria-describedby',feedback.id);
     errorsByField.set(name+i,feedback);
     input.addEventListener('input',()=>{showFieldError(name+i,'');$('action-error').textContent='';});
    }
    due.addEventListener('change',()=>{
     updateDateLimits();
     const dues=Array.from({length:count},(_,j)=>({amount:$('action-form').elements.namedItem('amount'+j).value,date:$('action-form').elements.namedItem('date'+j).value}));
     const errors=tuitionScheduleValidation.validate(dues,total,today,invoice.classEndDate);
     for(let j=0;j<count;j++)showFieldError('date'+j,errors[`installments[${j}].date`]);
    });
    const hint=node('small',null,'ledger-help');hint.dataset.dateHint=String(i);
    amountLabel.append(amount,errorsByField.get('amount'+i));dateLabel.append(due,hint,errorsByField.get('date'+i));group.append(amountLabel,dateLabel);rows.append(group);
   }
   updateDateLimits();
  }
  select.addEventListener('change',render);method.addEventListener('change',()=>{value.value=method.value==='NONE'?'0':method.value==='PERCENTAGE'?'10':'1000';render();});value.addEventListener('input',render);render();
 }
 function payment(invoice) {
  const next=invoice.installments.find(d=>Number(d.remaining)>0);
  const amount=Number(next?next.remaining:invoice.balance);
  const key=crypto.randomUUID();
  open('Thanh toán đợt '+(next?.sequence||1)+' · '+invoice.code,async data=>{
   if(data.get('method')==='CASH') {
    const cashGiven=integer(data.get('given'));cashQuote(amount,cashGiven);
    await api.collect(invoice.id,{amount,cashGiven,idempotencyKey:key});
   } else {
    const intent=await api.intent(invoice.id,amount,parent);
    if(['CANCELLED','FAILED'].includes(intent.status))throw new Error('Yêu cầu QR đã đóng. Vui lòng tạo mã mới.');
    await checkout(intent,invoice);return {keepOpen:true};
   }
  });
  const context=node('div',null,'payment-context');
  context.append(node('strong',invoice.student),node('span',invoice.className),node('p','Còn phải đóng '+money(invoice.balance)+'. Số tiền cố định theo số còn thiếu của đợt '+(next?.sequence||1)+'.'));
  $('action-content').append(context);
  field('Số tiền thanh toán (đ)','amount',{readOnly:true,value:new Intl.NumberFormat('vi-VN').format(amount)});
  const label=node('label','Phương thức thanh toán'),method=node('select',null,'form-select');
  method.name='method';
  method.setAttribute('aria-label','Phương thức thanh toán');
  if(!parent)method.add(new Option('Tiền mặt tại quầy','CASH'));
  if(Number(invoice.balance)>0)method.add(new Option('Chuyển khoản QR / PayOS','QR'));
  label.append(method);$('action-content').append(label);
  const given=field('Tiền khách đưa (đ)','given',{inputMode:'numeric',value:next?next.remaining:0});
  const change=node('p',null,'ledger-help');$('action-content').append(change);
  function updateChange() {
   change.hidden=method.value!=='CASH';
   given.setCustomValidity('');
   if(method.value!=='CASH')return;
   try {
    const quote=cashQuote(amount,integer(given.value));change.className='ledger-help';
    change.textContent='Tiền trả lại: '+money(quote.change)+(quote.support?' · Trung tâm hỗ trợ làm tròn: '+money(quote.support):'');
   }catch(error){given.setCustomValidity(error.message);change.className='text-danger';change.textContent=error.message;}
  }
  function toggle() {
   given.parentElement.hidden=method.value!=='CASH';given.required=method.value==='CASH';
   $('submit-action').textContent=method.value==='CASH'?'Xác nhận đã thu tiền':'Thanh toán qua PayOS';
   updateChange();
  }
  given.addEventListener('input',updateChange);
  method.addEventListener('change',toggle);toggle();
 }
 function cashQuote(amount,given) {
  if(given<amount)throw new Error('Tiền khách đưa phải lớn hơn hoặc bằng số tiền thanh toán.');
  const raw=given-amount;
  if(raw===0)return {change:0,support:0};
  if(given%100!==0||given===100||given===300)throw new Error('Tiền khách đưa dư phải ghép được từ các mệnh giá từ 200đ trở lên.');
  let change=Math.ceil(raw/100)*100;if(change===100||change===300)change+=100;
  return {change,support:change-raw};
 }
 const checkoutKey='tuition-payos-checkout';
 function checkoutUrl(value) {
  let url;try{url=new URL(value);}catch{throw new Error('Chưa nhận được đường dẫn thanh toán PayOS. Vui lòng kiểm tra lại trạng thái yêu cầu.');}
  if(url.protocol!=='https:'||url.hostname!=='pay.payos.vn'||url.username||url.password||url.port)
   throw new Error('Đường dẫn thanh toán PayOS không hợp lệ. Vui lòng liên hệ trung tâm.');
  return url.href;
 }
 async function checkout(intent,invoice) {
  if(intent.simulated){showIntent(intent,invoice);return;}
  if(intent.status!=='PENDING'){await finishCheckout(intent);return;}
  const url=checkoutUrl(intent.checkoutUrl);
  sessionStorage.setItem(checkoutKey,JSON.stringify({intentId:intent.id,invoiceId:invoice.id,path:location.pathname,user:getCurrentUser().username}));
  // Leaving for the hosted checkout must never cancel the payment being opened.
  activeQr=null;clearTimeout(qrTimer);
  location.assign(url);
 }
 async function finishCheckout(intent) {
  activeQr=null;clearTimeout(qrTimer);$('action-dialog').close();
  await load();
  const messages={PAID:'Đợt học phí đã được ghi nhận đủ. Vui lòng xem biên lai và thông báo để đối chiếu.',
   CANCELLED:'Đã hủy lần thanh toán này. Lần thanh toán tiếp theo sẽ tạo mã mới.',
   FAILED:'Yêu cầu thanh toán đã đóng. Bạn có thể thanh toán lại để tạo mã mới.',
   PARTIALLY_PAID:'Đã ghi nhận tiền thực nhận. Vui lòng xem số còn thiếu của đợt và thanh toán bổ sung.',
   RECONCILE:'Khoản chuyển chưa được xác minh. Vui lòng không chuyển lại số tiền đã gửi.'};
  if(messages[intent.status])sessionStorage.removeItem(checkoutKey);
  feedback(messages[intent.status]||'Yêu cầu đang được xác minh với ngân hàng. Hệ thống sẽ tự cập nhật số dư; vui lòng không chuyển lại khoản đã gửi.',!messages[intent.status]);
  window.refreshNotifications?.();
 }
 let resumingCheckout=false;
 async function resumeCheckout() {
  if(resumingCheckout)return;
  const saved=sessionStorage.getItem(checkoutKey);if(!saved)return;
  let context;try{context=JSON.parse(saved);}catch{sessionStorage.removeItem(checkoutKey);return;}
  if(context.path!==location.pathname||context.user!==getCurrentUser().username)return;
  const invoice=invoices.find(item=>String(item.id)===String(context.invoiceId));if(!invoice)return;
  resumingCheckout=true;
  try{
   // Returning/back from the hosted page ends this attempt. The backend verifies bank
   // receipts before cancellation, including when the URL misleadingly says PAID.
   const intent=await api.cancelIntent(context.intentId,parent);
   await finishCheckout(intent);
  }catch(error){feedback('Chưa xác minh được kết quả PayOS: '+error.message+' Vui lòng tải lại để kiểm tra; không chuyển lại khoản đã gửi.',true);}
  finally{resumingCheckout=false;}
 }
 function showIntent(intent,invoice) {
  open('Mô phỏng thanh toán',null);
  activeQr=intent.simulated?{id:intent.id,token:localStorage.getItem('token')}:null;
  const content=$('action-content');
  content.append(node('p',invoice.student+' · '+invoice.code,'ledger-help'),node('p',money(intent.amount),'qr-amount'));
  const state=node('p',intent.status==='PENDING'?'Đang chờ chuyển khoản':(status[intent.status]||intent.status),'fw-semibold');content.append(state);
  const paymentArea=node('div');content.append(paymentArea);
  content.append(node('p','MÔ PHỎNG LOCAL · Không chuyển tiền thật. Quản trị viên dùng nút mô phỏng để kiểm thử.','alert alert-warning'));
  const actions=node('div',null,'ledger-actions');content.append(actions);
  let simulationButton=null;
  if(role==='ADMIN'){simulationButton=button('Mô phỏng nhận tiền',async()=>{await api.simulate(intent.id);await poll();});actions.append(simulationButton);}
  async function poll() {
   clearTimeout(qrTimer);
   if(!$('action-dialog').open)return;
   try {
    const current=await api.intentStatus(intent.id,parent);
    state.textContent=current.status==='PENDING'?'Đang chờ chuyển khoản':(status[current.status]||current.status);
    if(current.status==='PAID') {
     activeQr=null;
     sessionStorage.removeItem(checkoutKey);
     state.textContent='Đợt học phí này đã đóng đủ. Hóa đơn đã được cập nhật.';
     $('action-dialog').close();
     feedback('Đợt học phí đã được ghi nhận đủ. Vui lòng xem biên lai và thông báo để đối chiếu.');
     await load();window.refreshNotifications?.();return;
    }
    if(['FAILED','CANCELLED'].includes(current.status)) {
     activeQr=null;
     sessionStorage.removeItem(checkoutKey);
     $('action-dialog').close();feedback('Yêu cầu QR cũ không còn hiệu lực. Bạn có thể chọn thanh toán để tạo mã QR mới.',true);
     await load();return;
    }
    if(current.status==='RECONCILE'){activeQr=null;paymentArea.hidden=true;actions.replaceChildren();state.textContent='Đã nhận thông tin chuyển khoản. Giao dịch chưa khớp yêu cầu thanh toán và đang được xác minh; vui lòng không chuyển lại khoản đã gửi.';return;}
   } catch(e) {state.textContent=e.message;}
   if($('action-dialog').open)qrTimer=setTimeout(poll,4000);
  }
  qrTimer=setTimeout(poll,4000);
 }
 async function closeAction() {
  if(busy||closingQr)return;
  if(!activeQr){$('action-dialog').close();try{await load();}catch(error){feedback(error.message,true);}return;}
  closingQr=true;$('close-action').disabled=true;clearTimeout(qrTimer);
  try {
   const result=await api.cancelIntent(activeQr.id,parent);
   activeQr=null;$('action-dialog').close();
   feedback(result?.status==='PAID'?'Đợt học phí đã đóng đủ. Hóa đơn đã cập nhật.':result?.status==='PARTIALLY_PAID'?'Đã ghi nhận tiền chuyển khoản một phần. Bạn có thể mở thanh toán để đóng bổ sung.':'Đã hủy QR chưa thanh toán. Lần sau hệ thống sẽ tạo mã mới.');
   await load();window.refreshNotifications?.();
  } catch(error){$('action-error').textContent='Chưa xác nhận hủy QR: '+error.message;}
  finally{closingQr=false;$('close-action').disabled=false;}
 }
 function cancelOnExit() {
  if(!activeQr)return;
  const endpoint=(parent?'/api/parent-tuition':'/api/tuition-payment')+'/payment-intents/'+activeQr.id+'/cancel';
  fetch((typeof BASE_URL==='string'?BASE_URL:'')+endpoint,{method:'POST',keepalive:true,
   headers:{Authorization:'Bearer '+activeQr.token,'Content-Type':'application/json'}}).catch(()=>{});
 }
 window.addEventListener('pagehide',cancelOnExit);
 document.addEventListener('click',event=>{
  const link=event.target.closest?.('a[href]');
  if(activeQr&&link&&link.target!=='_blank'&&new URL(link.href,location.href).href!==location.href)cancelOnExit();
 });
 function reservation(invoice) {
  open('Hủy hồ sơ chờ thanh toán',data=>api.reservation(invoice.id,{action:'CANCEL',reason:data.get('reason'),contactConfirmed:data.get('contactConfirmed')==='on'}));
  $('action-content').append(node('p',invoice.student+' · Phụ huynh '+invoice.parent+' · '+(invoice.phone||'Chưa có số điện thoại'),'payment-context'),
   node('p','Admin liên hệ phụ huynh và kiểm tra chưa nhận tiền trước khi hủy. QR chưa trả sẽ được đóng trước khi hủy; hóa đơn và ghi danh giữ trạng thái đã hủy để tra cứu.','ledger-help'));
  field('Kết quả liên hệ và lý do hủy','reason',{multiline:true,maxLength:500});
  const label=node('label',null,'bank-verification'),check=node('input',null,'form-check-input');Object.assign(check,{name:'contactConfirmed',type:'checkbox',required:true});
  label.append(check,node('span','Tôi đã liên hệ phụ huynh và kiểm tra trung tâm chưa nhận tiền.'));$('action-content').append(label);
 }
 function addDay(value,days) {
  const parsed=new Date(value+'T00:00:00Z');if(!Number.isFinite(parsed.getTime()))return null;
  parsed.setUTCDate(parsed.getUTCDate()+days);return parsed.toISOString().slice(0,10);
 }
 function extensionRange(invoice,due) {
  if(!invoice.classEndDate)return {error:'Chưa có ngày kết thúc lớp để xác định khoảng gia hạn.'};
  let min=[today,addDay(due.date,1)].sort().at(-1),max=invoice.classEndDate;
  for(const other of invoice.installments){
   if(other.sequence<due.sequence){const lower=addDay(other.date,1);if(lower>min)min=lower;}
   if(other.sequence>due.sequence){const upper=addDay(other.date,-1);if(upper<max)max=upper;}
  }
  if(min>max)return {error:'Kỳ '+due.sequence+' không còn khoảng ngày hợp lệ để gia hạn trước kỳ sau và ngày kết thúc lớp.'};
  return {min,max,hint:'Hạn đóng mới chỉ được chọn từ '+date(min)+' đến '+date(max)+'.'};
 }
 function extendDue(invoice,due) {
  const range=extensionRange(invoice,due);if(range.error)throw new Error(range.error);
  let proposed,reason,error;
  const validate=()=>{
   const message=!proposed.value?'Vui lòng chọn hạn đóng mới. '+range.hint
    :proposed.value<range.min||proposed.value>range.max?range.hint:'';
   error.textContent=message;proposed.classList.toggle('is-invalid',!!message);return message;
  };
  open('Gia hạn kỳ '+due.sequence+' · '+invoice.code,async data=>{
   if(validate())throw new Error('Vui lòng sửa hạn đóng mới theo khoảng ngày được hướng dẫn.');
   if(!reason.value.trim())throw new Error('Vui lòng nhập lý do gia hạn.');
   await api.extendDue(invoice.id,{installmentId:due.id,proposedDate:proposed.value,reason:reason.value.trim()});
   return {message:'Đã gia hạn kỳ '+due.sequence+'. Hạn đóng đã được cập nhật.'};
  });
  $('action-form').noValidate=true;
  $('action-content').append(node('p',invoice.student+' · Kỳ '+due.sequence+' · Hạn hiện tại '+date(due.date),'payment-context'));
  proposed=field('Hạn đóng mới','proposedDate',{type:'date',min:range.min,max:range.max,value:range.min});
  proposed.setAttribute('aria-label','Hạn đóng mới');
  proposed.parentElement.append(node('small',range.hint,'ledger-help'));
  error=node('small','','text-danger');error.id='extension-date-error';proposed.parentElement.append(error);
  proposed.addEventListener('input',validate);
  reason=field('Lý do gia hạn','reason',{multiline:true,maxLength:500});
 }
 function card(invoice) {
  const el=node('article',null,'ledger-card');el.id='invoice-'+invoice.id;
  const head=node('div',null,'invoice-heading'),identity=node('div');
  identity.append(node('p',invoice.code,'invoice-code'),node('h2',invoice.student),node('p',invoice.className,'invoice-class'));
  head.append(identity,badge(status[invoice.status]||invoice.status,invoice.status==='PAID'?'success':invoice.status==='CANCELLED'?'neutral':'warning'));el.append(head);
  if(!parent)el.append(node('p','Phụ huynh: '+invoice.parent+(invoice.phone?' · '+invoice.phone:''),'invoice-parent'));
  const amounts=node('div',null,'invoice-amounts');
  [['Học phí sau ưu đãi',invoice.total],['Đã ghi nhận học phí',invoice.paid],['Còn phải đóng',invoice.balance]].forEach(([label,value])=>{const item=node('div');item.append(node('span',label),node('strong',money(value)));amounts.append(item);});
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
  if(invoice.discountPolicy?.source==='PLACEMENT_TEST')el.append(node('p','Ưu đãi Placement Test #'+invoice.discountPolicy.placementTestId+' · '+invoice.discountPolicy.placementScore+'/10 điểm · Giảm '+invoice.discountPolicy.percentage+'% ('+money(invoice.discountPolicy.amount)+').','ledger-help'));
  if(invoice.classEndDate)el.append(node('p','Ngày kết thúc lớp: '+date(invoice.classEndDate)+'. Hạn đóng học phí không được sau ngày này.','ledger-help'));
  const actions=node('div',null,'ledger-actions');
  if(role==='ADMIN' && invoice.reservationPending && Number(invoice.paid)===0 && invoice.status!=='CANCELLED')
   actions.append(button('Hủy giữ chỗ',()=>reservation(invoice),'btn btn-outline-danger btn-sm'));
  if(!invoice.reviewRequired && invoice.status!=='CANCELLED') {
   if(!invoice.confirmed) {
    if(!parent)actions.append(button('Chốt lịch đóng',()=>configure(invoice),'btn btn-success'));
    else el.append(node('p','Trung tâm đang chốt lịch đóng học phí.','text-secondary'));
   } else {
    if(invoice.intents.some(i=>i.status==='RECONCILE'))el.append(node('p','Giao dịch chuyển khoản chưa được xác minh. Vui lòng không chuyển lại khoản tiền đang được kiểm tra.','alert alert-warning'));
    if(Number(invoice.balance)>0 && !invoice.intents.some(i=>i.status==='RECONCILE')) {
     const next=invoice.installments.find(d=>Number(d.remaining)>0);
     actions.append(button('Thanh toán đợt '+(next?.sequence||1),()=>payment(invoice),'btn btn-success'));
    }
   }
  }
  if(!parent&&invoice.confirmed&&!invoice.reviewRequired&&invoice.status!=='CANCELLED')for(const due of invoice.installments){
   if(Number(due.paid)>0||Number(due.remaining)<=0)continue;
   const range=extensionRange(invoice,due);
   if(range.error){actions.append(node('span',range.error,'ledger-help'));continue;}
   actions.append(button('Gia hạn kỳ '+due.sequence,()=>extendDue(invoice,due)));
  }
  el.append(actions);
  if(invoice.receipts.length) {
   const history=node('details',null,'receipt-history'),summary=node('summary','Biên lai ('+invoice.receipts.length+')');
   history.append(summary);
   const receiptList=node('div');history.append(receiptList);
   receiptList.replaceChildren(...invoice.receipts.map(r=>{
    const row=node('div',null,'receipt-history-row'),info=node('div');
    info.append(node('strong',r.code),node('small',date(r.at)+' · '+(r.method==='CASH_AT_DESK'?'Tiền mặt':'Chuyển khoản PayOS')));
    if(Number(r.cashRoundingAmount)>0)info.append(node('small','Thực thu '+money(Number(r.amount)-Number(r.cashRoundingAmount))+' · Hỗ trợ làm tròn '+money(r.cashRoundingAmount)));
    row.append(info,node('strong',money(r.amount)),badge(r.reversed?'Đã đảo':'Thành công',r.reversed?'danger':'success'),button('Xem biên lai',()=>previewReceipt(invoice,r)));
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
    || state==='overdue'&&i.installments.some(d=>d.overdue)));
  pagination.render('hóa đơn',items,$('records'),card);
  if(!items.length)$('records').append(node('p','Chưa có hóa đơn phù hợp.','ledger-card'));
  const totals=[['Tổng học phí',invoices.filter(i=>i.status!=='CANCELLED').reduce((n,i)=>n+Number(i.total),0)],
   ['Thực thu',invoices.reduce((n,i)=>n+i.receipts.filter(r=>!r.reversed).reduce((sum,r)=>sum+Number(r.amount)-Number(r.cashRoundingAmount||0),0),0)],
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
    actions.append(button('Thanh toán học phí',async()=>{await api.readNotice(n.id);payment(invoice);},'btn btn-success btn-sm'));
   } else if(invoice && invoice.confirmed && !invoice.reviewRequired) {
    actions.append(button('Thanh toán học phí',()=>payment(invoice),'btn btn-success btn-sm'));
    info.append(node('p','QR được gửi khi đến hạn. Nếu chưa có mã, bạn có thể mở thanh toán hoặc liên hệ trung tâm; cũng có thể chủ động đóng trước.','ledger-help'));
   }
   info.append(actions);row.prepend(info);
   return row;
  });
  if(typeof updateTuitionNoticeIndicator==='function')updateTuitionNoticeIndicator();
 }
 async function load() {
  const id=++loadId;$('refresh').disabled=true;
  try {
   const data=await api.invoices(parent);if(id!==loadId)return;
   invoices=data;
   if(initialInvoiceId){pagination.select('hóa đơn',invoices.findIndex(i=>String(i.id)===initialInvoiceId));initialInvoiceId=null;}
   render();
   if(parent)await renderNotices();
  } finally {if(id===loadId)$('refresh').disabled=false;}
 }
 $('keyword').addEventListener('input',()=>{pagination.reset('hóa đơn');render();});
 $('state').addEventListener('change',()=>{pagination.reset('hóa đơn');render();});
 $('refresh').addEventListener('click',()=>load().catch(e=>feedback(e.message,true)));
 $('close-action').addEventListener('click',closeAction);
 $('action-dialog').addEventListener('cancel',e=>{if(busy||activeQr){e.preventDefault();if(activeQr)closeAction();}});
 $('action-dialog').addEventListener('close',()=>{clearTimeout(qrTimer);if(activeQr){cancelOnExit();activeQr=null;}$('action-content').replaceChildren();});
 $('action-form').addEventListener('submit',async e=>{
  e.preventDefault();if(busy||!action)return;
  busy=true;$('submit-action').disabled=true;$('action-error').textContent='';
  try {
   const result=await action(new FormData($('action-form')));
   if(result?.keepOpen)return;
   activeQr=null;$('action-dialog').close();feedback(result?.message||'Đã lưu và cập nhật số dư.');await load();
   window.refreshNotifications?.();
  } catch(error){$('action-error').textContent=error.message;}
  finally {busy=false;$('submit-action').disabled=false;}
 });
 load().then(async()=>{
  await resumeCheckout();
  const id=new URLSearchParams(location.search).get('invoiceId');
  if(id)document.getElementById('invoice-'+id)?.scrollIntoView({behavior:'smooth',block:'start'});
 }).catch(e=>feedback(e.message,true));
 window.addEventListener('pageshow',event=>{if(event.persisted)load().then(resumeCheckout).catch(e=>feedback(e.message,true));});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&!$('action-dialog').open)load().catch(()=>{});});
 if(parent)setInterval(()=>{
  if(!document.hidden && !$('action-dialog').open && !busy && !$('refresh').disabled)
   load().catch(()=>{});
 },60000);
})();
