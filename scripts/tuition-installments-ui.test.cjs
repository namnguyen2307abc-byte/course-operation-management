const { test,before,after }=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const http=require('node:http');
const path=require('node:path');
const {chromium}=require('playwright');
let server,browser,origin;
const root=path.resolve(__dirname,'../com_fe');
const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
const invoice={id:11,code:'DEMO-11',student:'Minh An',parent:'Phụ huynh A',className:'Piano',
 total:6000000,paid:3000000,balance:3000000,status:'PARTIALLY_PAID',mode:'INSTALLMENTS',revision:2,confirmed:true,originalAmount:6000000,reviewRequired:false,intents:[],receipts:[],
 installments:[{id:1,sequence:1,amount:3000000,paid:3000000,remaining:0,date:today},
 {id:2,sequence:2,amount:1500000,paid:0,remaining:1500000,date:today},
 {id:3,sequence:3,amount:1500000,paid:0,remaining:1500000,date:'2099-12-31'}]};
before(async()=>{
 server=http.createServer(async(req,res)=>{
  const file=path.resolve(root,'.'+new URL(req.url,'http://local').pathname);
  if(!file.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
  try{
   const data=await fs.readFile(file);
   res.setHeader('Content-Type',{'.html':'text/html','.js':'text/javascript','.css':'text/css'}[path.extname(file)]||'text/plain');
   res.end(data);
  }catch{res.writeHead(404);res.end();}
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({channel:'chrome',headless:true});
});
after(async()=>{await browser?.close();if(server)await new Promise(r=>server.close(r));});
async function open(role,intercept,viewport={width:1440,height:1000}) {
 const page=await browser.newPage({viewport});
 page.setDefaultTimeout(7000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://cdn.jsdelivr.net/**',r=>r.abort());
 await page.addInitScript(role=>{
  localStorage.setItem('token','demo');localStorage.setItem('user',JSON.stringify({role,userId:71,username:'demo'}));
  const fetchOriginal=window.fetch.bind(window);
  window.fetch=(input,opts)=>{
   const url=new URL(input,location.href);
   return fetchOriginal(url.pathname.startsWith('/api/')?url.pathname+url.search:input,opts);
  };
 },role);
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url());
  let data=await intercept(url,req);
  if(data===undefined) data=url.pathname.endsWith('/receivables')||url.pathname.endsWith('/invoices')?[invoice]:[];
  await route.fulfill({status:200,json:data===null?{}:data});
 });
 await page.goto(origin+'/pages/'+(role==='PARENT'?'parent-tuition':'tuition-receivables')+'.html');
 return {page,errors};
}
test('parent sees notices and requests only an online payment for the chosen amount',async()=>{
 let posted;
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(url.pathname.endsWith('/notifications'))return [{id:8,invoiceId:11,code:'DEMO-11',student:'Minh An',remaining:1500000,date:today,type:'DUE_TODAY'}];
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents')){
   posted=req.postDataJSON();return {id:7,orderCode:700,amount:posted.amount,status:'PENDING',simulated:true};
  }
 });
 try{
  await page.getByRole('heading',{name:'Thông báo học phí'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Thu tiền mặt',exact:true}).count(),0);
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
  await page.getByText('MÔ PHỎNG LOCAL',{exact:false}).waitFor();
  assert.equal(posted.amount,1500000);
  assert.equal(await page.getByRole('button',{name:'Mô phỏng nhận tiền'}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('cashier posts the fixed installment with a stable idempotency key',async()=>{
 let posted,bellReads=0;
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/collections')){posted=req.postDataJSON();return {};}
  if(url.pathname==='/api/notifications'){
   bellReads++;
   return {unreadCount:posted?1:0,notifications:posted?[{id:401,type:'PAYMENT_SUCCESS',title:'Đã ghi nhận khoản thu học phí',message:'Đã nhận tiền học phí',isRead:false}]:[]};
  }
 });
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Chọn tất toán',exact:true}).count(),0);
  assert.equal(await page.getByLabel('Số tiền thanh toán (đ)',{exact:true}).getAttribute('readonly'),'');
  await page.getByLabel('Số tiền thanh toán (đ)',{exact:true}).evaluate(input=>input.value='1');
  await page.getByLabel('Tiền khách đưa (đ)',{exact:true}).fill('1.700.000');
  assert.match(await page.locator('#action-content').innerText(),/Tiền trả lại: 200\.000/);
  await page.getByRole('button',{name:'Xác nhận đã thu tiền',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(posted.amount,1500000);assert.equal(posted.cashGiven,1700000);
  assert.match(posted.idempotencyKey,/^[a-f0-9-]{36}$/);
  await page.waitForFunction(()=>document.getElementById('notifUnreadBadge')?.textContent==='1');
  assert.ok(bellReads>=2,'successful collection should refresh the shared bell immediately');
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('parent bell shows tuition reminders in alerts and opens the related invoice',async()=>{
 let markedRead=false;
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(url.pathname==='/api/notifications')return {unreadCount:2,notifications:[
   {id:501,type:'TUITION_DUE_TODAY',title:'Học phí đến hạn hôm nay',message:'Minh An - đợt 2 còn phải đóng 1.500.000 đ',targetUrl:'/pages/parent-tuition.html?invoiceId=11',isRead:false},
   {id:502,type:'TUITION_PLAN_CONFIRMED',title:'Đã chốt lịch đóng học phí',message:'Đã xác nhận lịch đóng',targetUrl:'/pages/parent-tuition.html?invoiceId=11',isRead:false}
  ]};
  if(url.pathname==='/api/notifications/501/read'&&req.method()==='PATCH'){markedRead=true;return {};}
 });
 try{
  await page.locator('#notifBellBtn').click();
  await page.locator('#notifDropdownList .notif-item[data-id="501"]').waitFor();
  await page.locator('#notifTabAlert').click();
  assert.equal(await page.locator('#notifDropdownList .notif-item').count(),1);
  assert.equal(await page.locator('#notifDropdownList .bi-wallet2').count(),1);
  await page.screenshot({path:path.resolve(__dirname,'../.local-run/tuition-due-bell.png')});
  await page.locator('#notifDropdownList .notif-item[data-id="501"]').click();
  await page.waitForURL('**/parent-tuition.html?invoiceId=11');
  assert.equal(markedRead,true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('cashier may confirm a real QR only after entering bank verification details',async()=>{
 let posted;
 const qr={id:7,orderCode:700,amount:1500000,status:'PENDING',simulated:false,qrCode:'DEMO-ONLY-QR'};
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(url.pathname.endsWith('/receivables'))return [{...invoice,intents:[qr]}];
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))return qr;
  if(req.method()==='POST'&&url.pathname.endsWith('/confirm-bank')){posted=req.postDataJSON();return {};}
 });
 try{
  assert.equal(await page.getByRole('button',{name:'Mở QR đang chờ',exact:true}).count(),0);
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  await page.getByLabel('Phương thức thanh toán',{exact:true}).selectOption('QR');
  await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Kiểm tra kết quả',exact:true}).count(),0);
  await page.getByRole('button',{name:'Xác nhận đã thu tiền',exact:true}).click();
  await page.getByLabel('Mã giao dịch ngân hàng',{exact:true}).fill('BANK-700');
  await page.getByLabel('Lý do xác nhận thủ công và kết quả kiểm tra',{exact:true}).fill('Đã kiểm tra ghi có ngân hàng, webhook chậm');
  await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'Xác nhận đã thu tiền',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.deepEqual(posted,{amount:1500000,reference:'BANK-700',reason:'Đã kiểm tra ghi có ngân hàng, webhook chậm',bankVerified:true});
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('parent receives a due notification with QR and cannot manually confirm receipt',async()=>{
 const qr={id:7,orderCode:700,amount:1500000,status:'PENDING',simulated:false,qrCode:'DEMO-ONLY-QR'};
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(url.pathname.endsWith('/notifications'))return [{id:8,invoiceId:11,code:'DEMO-11',student:'Minh An',remaining:1500000,date:today,type:'DUE_TODAY',sequence:2,paymentIntent:qr}];
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))return qr;
 },{width:390,height:844});
 try{
  assert.equal(await page.locator('#notices canvas').count(),0);
  await page.locator('#notices').getByRole('button',{name:'Thanh toán học phí',exact:true}).click();
  await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
  await page.locator('#action-dialog canvas').waitFor();
  assert.equal(await page.getByRole('button',{name:'Xác nhận đã thu tiền',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Kiểm tra kết quả',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Hủy yêu cầu QR',exact:true}).count(),0);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('QR completion polling closes modal and advances to the next installment',async()=>{
 let settled=false,polls=0;
 const paid=structuredClone(invoice);
 paid.paid=4500000;paid.balance=1500000;
 paid.installments[1].paid=1500000;paid.installments[1].remaining=0;
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))
   return {id:7,orderCode:700,amount:1500000,status:'PENDING',simulated:true};
  if(url.pathname.endsWith('/payment-intents/7')){
   polls++;settled=true;return {id:7,status:'PAID'};
  }
  if(url.pathname.endsWith('/invoices'))return [settled?paid:invoice];
 });
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
  await page.getByText('Đã nhận tiền chuyển khoản. Giao dịch và biên lai đã hoàn tất.',{exact:true}).waitFor();
  assert.ok(polls>0);
  assert.equal(await page.locator('#action-dialog').evaluate(el=>el.open),false);
  await page.getByRole('button',{name:'Thanh toán đợt 3',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('mobile invoice can scroll its table without overflowing the page',async()=>{
 const {page,errors}=await open('PARENT',()=>undefined,{width:390,height:844});
 try{
  await page.getByRole('heading',{name:'Minh An',exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('overdue first installment stays payable and only admin sees cancellation',async()=>{
 const held={...invoice,paid:0,balance:6000000,status:'UNPAID',reservationPending:true,reservationExpired:true};
 const staff=await open('STAFF',url=>url.pathname.endsWith('/receivables')?[held]:undefined);
 try{
  await staff.page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).waitFor();
  assert.equal(await staff.page.getByRole('button',{name:'Gia hạn giữ chỗ',exact:true}).count(),0);
  assert.equal(await staff.page.getByRole('button',{name:'Hủy giữ chỗ',exact:true}).count(),0);
 }finally{await staff.page.close();}
 let posted;
 const admin=await open('ADMIN',(url,req)=>{
  if(url.pathname.endsWith('/receivables'))return [held];
  if(req.method()==='POST'&&url.pathname.endsWith('/reservation')){posted=req.postDataJSON();return {};}
 });
 try{
  await admin.page.getByRole('button',{name:'Hủy giữ chỗ',exact:true}).click();
  await admin.page.getByLabel('Kết quả liên hệ và lý do hủy',{exact:true}).fill('Đã gọi phụ huynh, chưa nhận tiền');
  await admin.page.getByRole('checkbox').check();
  await admin.page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await admin.page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(posted.action,'CANCEL');assert.equal(posted.contactConfirmed,true);
 }finally{await admin.page.close();}
});

test('invoices paginate five items while all receipts remain visible',async()=>{
 const list=Array.from({length:7},(_,i)=>({...structuredClone(invoice),id:11+i,student:'Học viên '+i,code:'DEMO-'+i}));
 list[0].receipts=Array.from({length:7},(_,i)=>({id:21+i,code:'PT-20261111-0000'+(21+i),amount:100000,cashGiven:100000,changeAmount:0,
  method:'CASH_AT_DESK',cashierId:71,cashier:'Thu ngân A',at:'2026-11-11T09:35:00',balanceAfter:2000000,reversed:false}));
 let reversalReads=0;
 const {page,errors}=await open('STAFF',url=>{
  if(url.pathname.endsWith('/receivables'))return list;
  if(url.pathname.endsWith('/reversals'))reversalReads++;
 });
 try{
  await page.getByRole('heading',{name:'Học viên 0',exact:true}).waitFor();
  assert.equal(await page.locator('#records>article').count(),5);
  const nav=page.getByRole('navigation',{name:'Phân trang hóa đơn',exact:true});
  await nav.getByRole('button',{name:'Sau',exact:true}).click();
  assert.equal(await page.locator('#records>article').count(),2);
  await page.getByRole('heading',{name:'Học viên 6',exact:true}).waitFor();
  await page.locator('#keyword').fill('DEMO-0');
  await page.getByRole('heading',{name:'Học viên 0',exact:true}).waitFor();
  await page.locator('#records summary').click();
  assert.equal(await page.locator('.receipt-history-row').count(),7);
  assert.equal(await page.getByRole('navigation',{name:'Phân trang biên lai 11',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Điều chỉnh thu nhầm',exact:true}).count(),7);
  assert.equal(reversalReads,0);
  assert.equal(await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('staff submits a cash correction without access to management review',async()=>{
 let posted;
 const data=structuredClone(invoice);
 data.receipts=[{id:21,code:'PT-20261111-000021',amount:1000000,method:'CASH_AT_DESK',cashierId:71,at:'2026-11-11T09:35:00',reversed:false}];
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(url.pathname.endsWith('/receivables'))return [data];
  if(req.method()==='POST'&&url.pathname.endsWith('/payments/21/reversal')){posted=req.postDataJSON();return {};}
 });
 try{
  await page.locator('summary').filter({hasText:'Biên lai (1)'}).click();
  await page.getByRole('button',{name:'Điều chỉnh thu nhầm',exact:true}).click();
  await page.getByLabel('Lý do và kết quả kiểm tra',{exact:true}).fill('Nhập sai số tiền mặt');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã gửi đề nghị điều chỉnh; chờ quản lý khác kiểm tra và duyệt.',{exact:true}).waitFor();
  assert.deepEqual(posted,{reason:'Nhập sai số tiền mặt'});
  assert.equal(await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

for(const [role,expected] of [['STAFF',1],['ADMIN',3]])test(role+' only sees permitted cash correction actions',async()=>{
 const data=structuredClone(invoice);
 data.receipts=[71,72,null].map((cashierId,index)=>({id:21+index,code:'PT-'+index,amount:1000000,method:'CASH_AT_DESK',cashierId,at:'2026-11-11T09:35:00',reversed:false}));
 data.receipts.push({id:24,code:'QR-24',amount:1000000,method:'VIET_QR',cashierId:71,at:'2026-11-11T09:35:00',reversed:false});
 const {page,errors}=await open(role,url=>url.pathname.endsWith('/receivables')?[data]:undefined);
 try{
  await page.locator('summary').filter({hasText:'Biên lai (4)'}).click();
  assert.equal(await page.getByRole('button',{name:'Điều chỉnh thu nhầm',exact:true}).count(),expected);
  assert.equal(await page.locator('.receipt-history-row').last().getByRole('button',{name:'Điều chỉnh thu nhầm',exact:true}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('receipt uses saved payment amounts and prints only the receipt',async()=>{
 const data=structuredClone(invoice);
 data.receipts=[{id:21,code:'PAY-21',amount:1000000,cashGiven:1200000,changeAmount:200000,
  method:'CASH_AT_DESK',cashier:'Thu ngân A',at:'2026-11-11T09:35:00',balanceAfter:2000000,reversed:false}];
 const {page,errors}=await open('PARENT',url=>url.pathname.endsWith('/invoices')?[data]:undefined);
 try{
  await page.locator('summary').filter({hasText:'Biên lai (1)'}).click();
  await page.getByRole('button',{name:'Xem biên lai',exact:true}).click();
  const receipt=page.getByRole('article',{name:'Nội dung biên lai'});
  await receipt.getByText('PAY-21',{exact:true}).waitFor();
  assert.match(await receipt.innerText(),/1\.000\.000/);
  assert.match(await receipt.innerText(),/2\.000\.000/);
  assert.match(await receipt.innerText(),/200\.000/);
  assert.equal(await page.getByRole('button',{name:'Điều chỉnh thu nhầm'}).count(),0);
  await page.emulateMedia({media:'print'});
  assert.equal(await page.locator('#records').isVisible(),false);
  assert.equal(await page.locator('.dialog-actions').isVisible(),false);
  assert.equal(await receipt.isVisible(),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('adjustments have a separate searchable five-item tab and overdue contacts are absent',async()=>{
 const rows=Array.from({length:7},(_,i)=>({id:i+1,code:'PT-20261111-'+String(i+1).padStart(6,'0'),student:i===0?'Nguyễn An':'Học viên '+(i+1),amount:1000000,status:i===6?'APPROVED':'PENDING',reason:'Nhập nhầm số tiền',requestedBy:'Quản lý A',reviewedBy:i===6?'Quản lý B':null,reviewNote:i===6?'Đã kiểm tra':null}));
 const {page,errors}=await open('ADMIN',url=>url.pathname.endsWith('/reversals')?rows:undefined);
 try{
  await page.goto(origin+'/pages/tuition-receivables.html?invoiceId=11');
  await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).click();
  await page.locator('.adjustment-card').first().waitFor();
  assert.equal(await page.locator('.adjustment-card').count(),5);
  assert.equal(await page.locator('#records').isVisible(),false);
  assert.equal(new URL(page.url()).searchParams.has('invoiceId'),false);
  await page.reload();await page.locator('.adjustment-card').first().waitFor();
  assert.equal(await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).getAttribute('aria-selected'),'true');
  assert.equal(await page.locator('#follow-ups').count(),0);
  const nav=page.getByRole('navigation',{name:'Phân trang điều chỉnh khoản thu',exact:true});
  await nav.getByRole('button',{name:'Sau',exact:true}).click();
  assert.equal(await page.locator('.adjustment-card').count(),2);
  await page.getByLabel('Tìm mã phiếu, học viên hoặc người lập',{exact:true}).fill('nguyen an');
  assert.equal(await page.locator('.adjustment-card').count(),1);
  await page.locator('.adjustment-card').getByText('Nguyễn An',{exact:true}).waitFor();
  await page.locator('#correction-keyword').fill('');await page.getByLabel('Trạng thái đề nghị',{exact:true}).selectOption('APPROVED');
  assert.equal(await page.locator('.adjustment-card').count(),1);
  await page.locator('.adjustment-card').getByText('Quản lý B',{exact:true}).waitFor();
  await page.getByRole('tab',{name:'Học phí',exact:true}).click();
  assert.equal(await page.locator('#records').isVisible(),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('adjustment review refreshes its status and works on a mobile layout',async()=>{
 let posted,approved=false;
 const row={id:1,code:'PT-20261111-000001',student:'Minh An',amount:1000000,status:'PENDING',reason:'Ghi nhầm số tiền',requestedBy:'Quản lý khác'};
 const {page,errors}=await open('ADMIN',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/reversals/1/review')){posted=req.postDataJSON();approved=true;return {};}
  if(url.pathname.endsWith('/reversals'))return [{...row,status:approved?'APPROVED':'PENDING',reviewedBy:approved?'Admin':null,reviewNote:approved?'Đã kiểm tra phiếu thu và tiền mặt':null}];
 },{width:390,height:844});
 try{
  await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).click();
  await page.getByRole('button',{name:'Duyệt điều chỉnh',exact:true}).click();
  await page.getByLabel('Ghi chú xác minh và quyết định',{exact:true}).fill('Đã kiểm tra phiếu thu và tiền mặt');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã duyệt điều chỉnh và cập nhật tiền đã thu.',{exact:true}).waitFor();
  await page.locator('.adjustment-card .ledger-badge').getByText('Đã duyệt',{exact:true}).waitFor();
  assert.deepEqual(posted,{approve:true,note:'Đã kiểm tra phiếu thu và tiền mặt'});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

for(const role of ['ADMIN','STAFF','PARENT'])test(role+' tuition page omits reconciliation filter and extension workflow',async()=>{
 let extensionReads=0;
 const {page,errors}=await open(role,url=>{
  if(url.pathname.includes('plan-change-requests')){extensionReads++;return [{id:1,status:'PENDING'}];}
 });
 try{
  await page.locator('#records article').first().waitFor();
  assert.deepEqual(await page.locator('#state option').allTextContents(),['Tất cả','Còn phải đóng','Quá hạn','Đã đóng đủ']);
  assert.equal(await page.locator('#changes').count(),0);
  assert.equal(await page.getByRole('button',{name:/Gia hạn kỳ|Duyệt gia hạn/}).count(),0);
  assert.equal(extensionReads,0);
  await page.locator('#state').selectOption('outstanding');
  assert.equal(await page.locator('#records article').count(),1);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('unconfirmed invoice only offers a combined discount and schedule form',async()=>{
 let posted;
 const {page}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/plan')){posted=req.postDataJSON();return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,paid:0,balance:6000000,status:'UNPAID',confirmed:false,mode:'FULL'}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Thanh toán đợt 1',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Ưu đãi học phí',exact:true}).count(),0);
  await page.getByLabel('Kiểu ưu đãi').selectOption('PERCENTAGE');
  await page.getByLabel('Mức giảm (đ hoặc %)').fill('10');
  await page.getByLabel('Lý do ưu đãi').fill('Đăng ký sớm');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(posted.discountMethod,'PERCENTAGE');
  assert.equal(posted.discountValue,10);
  assert.equal(posted.installments[0].amount,5400000);
 }finally{await page.close();}
});

test('discount fields reject negatives, decimals and values beyond their bounds',async()=>{
 let writes=0;
 const data={...structuredClone(invoice),originalAmount:3200000,total:3200000,paid:0,balance:3200000,status:'UNPAID',confirmed:false};
 const {page}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'){writes++;return {};}
  if(url.pathname.endsWith('/receivables'))return [data];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  const value=page.getByLabel('Mức giảm (đ hoặc %)',{exact:true});
  for(const [method,max,invalid,valid] of [
   ['PERCENTAGE','100',['-1','101','10.5','10.0'],['0','10','100']],
   ['FIXED_AMOUNT','3200000',['-1','0','999','3200001','1000.5'],['1000','1001','3200000']]
  ]){
   await page.getByLabel('Kiểu ưu đãi').selectOption(method);
   assert.equal(await value.getAttribute('max'),max);
   assert.equal(await value.getAttribute('min'),method==='PERCENTAGE'?'0':'1000');assert.equal(await value.getAttribute('step'),'1');
   for(const input of invalid){
    await value.fill(input);assert.equal(await value.evaluate(el=>el.checkValidity()),false,input);
    await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
   }
   for(const input of valid){await value.fill(input);assert.equal(await value.evaluate(el=>el.checkValidity()),true,input);}
  }
  assert.equal(writes,0);
 }finally{await page.close();}
});

test('rounded change is credited separately and receipts report actual cash',async()=>{
 let collected=false,posted;
 const data={...structuredClone(invoice),originalAmount:4700,total:4700,paid:0,balance:4700,status:'UNPAID',
  installments:[{id:1,sequence:1,amount:4700,paid:0,remaining:4700,date:today}]};
 const {page}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/collections')){posted=req.postDataJSON();collected=true;return {};}
  if(url.pathname.endsWith('/receivables'))return [collected?{...data,paid:4700,balance:0,status:'PAID',
   receipts:[{id:31,code:'PT-ROUND',amount:4700,cashGiven:5000,changeAmount:400,cashRoundingAmount:100,method:'CASH_AT_DESK',at:'2026-11-14T09:00:00',cashier:'Thu ngân',balanceAfter:0}],
   installments:[{...data.installments[0],paid:4700,remaining:0}]}:data];
 });
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 1',exact:true}).click();
  const given=page.getByLabel('Tiền khách đưa (đ)',{exact:true});
  for(const invalid of ['4699','5000.5','5001']){await given.fill(invalid);assert.equal(await given.evaluate(el=>el.checkValidity()),false,invalid);}
  await given.fill('4700');assert.equal(await given.evaluate(el=>el.checkValidity()),true);
  await given.fill('5000');
  assert.match(await page.locator('#action-content').innerText(),/Tiền trả lại: 400/);
  assert.match(await page.locator('#action-content').innerText(),/hỗ trợ làm tròn: 100/);
  await page.getByRole('button',{name:'Xác nhận đã thu tiền',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(posted.amount,4700);assert.equal(posted.cashGiven,5000);
  assert.match(await page.locator('#summary').innerText(),/4\.600/);
  await page.locator('summary').filter({hasText:'Biên lai (1)'}).click();
  await page.getByRole('button',{name:'Xem biên lai',exact:true}).click();
  const receipt=page.getByRole('article',{name:'Nội dung biên lai'});
  assert.match(await receipt.innerText(),/Trung tâm hỗ trợ làm tròn/);
  assert.match(await receipt.innerText(),/4\.600/);
  await page.screenshot({path:path.resolve(__dirname,'../.local-run/rounded-cash-receipt.png')});
 }finally{await page.close();}
});

for(const [amount,given,change,support] of [[4500,5000,500,0],[4850,5000,200,50],[4701,4701,0,0]])
test(`cash preview ${amount}/${given} returns ${change} and supports ${support}`,async()=>{
 const data={...structuredClone(invoice),total:amount,paid:0,balance:amount,status:'UNPAID',installments:[{id:1,sequence:1,amount,paid:0,remaining:amount,date:today}]};
 const {page}=await open('STAFF',url=>url.pathname.endsWith('/receivables')?[data]:undefined);
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 1',exact:true}).click();
  await page.getByLabel('Tiền khách đưa (đ)',{exact:true}).fill(String(given));
  assert.equal(await page.getByLabel('Tiền khách đưa (đ)',{exact:true}).evaluate(el=>el.checkValidity()),true);
  assert.match(await page.locator('#action-content').innerText(),new RegExp('Tiền trả lại: '+change));
  if(support)assert.match(await page.locator('#action-content').innerText(),new RegExp('hỗ trợ làm tròn: '+support));
 }finally{await page.close();}
});

test('closing an unpaid QR cancels it and reopening creates a new request',async()=>{
 let created=0,cancelled=0;
 const {page}=await open('PARENT',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))return {id:7+(created++),amount:1500000,status:'PENDING',simulated:true};
  if(req.method()==='POST'&&/payment-intents\/\d+\/cancel$/.test(url.pathname)){cancelled++;return {status:'CANCELLED'};}
  if(/payment-intents\/\d+$/.test(url.pathname))return {status:'PENDING'};
 });
 try{
  for(let i=0;i<2;i++){
   await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
   await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
   await page.getByText('MÔ PHỎNG LOCAL',{exact:false}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Mở QR đang chờ',exact:true}).count(),0);
   if(i===0){await page.getByRole('button',{name:'Đóng',exact:true}).click();await page.getByText('Đã hủy QR chưa thanh toán. Lần sau hệ thống sẽ tạo mã mới.',{exact:true}).waitFor();}
  }
  assert.equal(created,2);assert.equal(cancelled,1);
  await page.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pagehide')));
  await page.waitForFunction(()=>document.getElementById('action-dialog').open);
  await page.waitForTimeout(150);
  assert.equal(cancelled,2);
 }finally{await page.close();}
});

test('payment during QR cancellation is shown as paid instead of cancelled',async()=>{
 const {page}=await open('PARENT',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))return {id:7,amount:1500000,status:'PENDING',simulated:true};
  if(req.method()==='POST'&&url.pathname.endsWith('/cancel'))return {status:'PAID'};
 });
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  await page.getByRole('button',{name:'Tạo mã QR',exact:true}).click();
  await page.getByText('MÔ PHỎNG LOCAL',{exact:false}).waitFor();
  await page.getByRole('button',{name:'Đóng',exact:true}).click();
  await page.getByText('Đã nhận tiền chuyển khoản. Biên lai đã cập nhật.',{exact:true}).waitFor();
 }finally{await page.close();}
});
