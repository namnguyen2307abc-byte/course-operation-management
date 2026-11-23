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
  await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
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

test('cashier has only installment payment entry without an extra PayOS status button',async()=>{
 const {page,errors}=await open('STAFF',()=>undefined);
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Kiểm tra thanh toán PayOS',exact:true}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('parent goes directly to the hosted PayOS checkout without an intermediate QR or cancellation',async()=>{
 let created=0,cancelled=0;
 const qr={id:7,orderCode:700,amount:1500000,status:'PENDING',simulated:false,qrCode:'DEMO-ONLY-QR',checkoutUrl:'https://pay.payos.vn/web/demo-only'};
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(url.pathname.endsWith('/notifications'))return [{id:8,invoiceId:11,code:'DEMO-11',student:'Minh An',remaining:1500000,date:today,type:'DUE_TODAY',sequence:2}];
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents')){created++;return qr;}
  if(url.pathname.endsWith('/cancel')){cancelled++;return {};}
 },{width:390,height:844});
 try{
  await page.route('https://pay.payos.vn/**',r=>r.fulfill({contentType:'text/html',body:'<h1>Hosted checkout test fixture</h1>'}));
  await page.locator('#notices').getByRole('button',{name:'Thanh toán học phí',exact:true}).click();
  assert.equal(await page.locator('#action-dialog canvas').count(),0);
  await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
  await page.waitForURL('https://pay.payos.vn/web/demo-only');
  assert.equal(created,1);assert.equal(cancelled,0);assert.deepEqual(errors,[]);
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
  await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
  await page.getByText('Đợt học phí đã được ghi nhận đủ. Vui lòng xem biên lai và thông báo để đối chiếu.',{exact:true}).waitFor();
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

for(const role of ['ADMIN','STAFF','PARENT'])test(role+' extension visibility follows role and paid periods',async()=>{
 let reads=0;
 const data={...invoice,classEndDate:'2099-12-31',installments:invoice.installments.map(d=>({...d,date:d.sequence===3?'2099-12-20':d.date}))};
 const {page,errors}=await open(role,url=>{
  if(url.pathname.includes('plan-change-requests')){reads++;return [];}
  if(url.pathname.endsWith('/invoices')||url.pathname.endsWith('/receivables'))return [data];
 });
 try{
  await page.locator('#records article').first().waitFor();
  assert.equal(await page.getByRole('button',{name:'Gia hạn kỳ 1',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Gia hạn kỳ 2',exact:true}).count(),role==='PARENT'?0:1);
  assert.equal(reads,0);assert.deepEqual(errors,[]);
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

test('schedule identifies late dates and zero or decimal amounts before any API write',async()=>{
 let writes=0;
 const end=new Date(today+'T00:00:00Z');end.setUTCDate(end.getUTCDate()+30);
 const classEndDate=end.toISOString().slice(0,10);
 const later=new Date(end);later.setUTCDate(later.getUTCDate()+1);
 const late=later.toISOString().slice(0,10);
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'){writes++;return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,paid:0,status:'UNPAID',confirmed:false,classEndDate}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  await page.getByLabel('Phương án đóng').selectOption('2');
  await page.locator('[name=date1]').fill(late);
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  assert.match(await page.locator('#action-content').innerText(),/Kỳ 2:.*vượt ngày kết thúc lớp/);
  assert.equal(await page.locator('[name=date1]').getAttribute('max'),classEndDate);
  await page.locator('[name=date1]').fill(classEndDate);
  for(const [amount,message] of [['0',/Kỳ 2:.*lớn hơn 0đ/],['4200000.5',/Kỳ 2:.*số thập phân/]]){
   await page.locator('[name=amount1]').fill(amount);
   await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
   assert.match(await page.locator('#action-content').innerText(),message);
  }
  assert.equal(writes,0);assert.deepEqual(errors,[]);
  await page.locator('[name=amount1]').fill('4200000');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(writes,1);
 }finally{await page.close();}
});

test('placement discount shows score and locks policy while submitting the authoritative percentage',async()=>{
 let posted;
 const discountPolicy={source:'PLACEMENT_TEST',locked:true,placementTestId:81,placementScore:8.5,percentage:20,method:'PERCENTAGE',value:20,amount:1200000,reason:'Placement Test #81: 8.5/10 điểm; ưu đãi 20%.'};
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/plan')){posted=req.postDataJSON();return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,paid:0,status:'UNPAID',confirmed:false,discountPolicy}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  assert.equal(await page.getByLabel('Kiểu ưu đãi').isDisabled(),true);
  assert.equal(await page.getByLabel('Mức giảm (đ hoặc %)').getAttribute('readonly'),'');
  assert.match(await page.locator('#action-content').innerText(),/8.5\/10 điểm/);
  assert.match(await page.locator('#action-content').innerText(),/4.800.000/);
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(posted.discountMethod,'PERCENTAGE');assert.equal(posted.discountValue,20);
  assert.equal(posted.installments[0].amount,4800000);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('a period under 1000 is rejected and no-discount is explicit',async()=>{
 let writes=0;
 const {page}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'){writes++;return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,paid:0,status:'UNPAID',confirmed:false}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  assert.equal(await page.getByLabel('Kiểu ưu đãi').inputValue(),'NONE');
  await page.getByLabel('Phương án đóng').selectOption('2');
  await page.locator('[name=amount1]').fill('999');
  await page.locator('[name=date1]').fill('2099-12-31');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  assert.match(await page.locator('#action-content').innerText(),/Kỳ 2:.*1.000đ/);
  assert.equal(writes,0);
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
   ['PERCENTAGE','30',['-1','0','31','101','10.5','10.0'],['1','10','30']],
   ['FIXED_AMOUNT','960000',['-1','0','999','960001','3200001','1000.5'],['1000','1001','960000']]
  ]){
   await page.getByLabel('Kiểu ưu đãi').selectOption(method);
   assert.equal(await value.getAttribute('max'),max);
   assert.equal(await value.getAttribute('min'),method==='PERCENTAGE'?'1':'1000');assert.equal(await value.getAttribute('step'),'1');
   for(const input of invalid){
    await value.fill(input);assert.equal(await value.evaluate(el=>el.checkValidity()),false,input);
    await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
   }
   for(const input of valid){await value.fill(input);assert.equal(await value.evaluate(el=>el.checkValidity()),true,input);}
  }
  assert.equal(writes,0);
 }finally{await page.close();}
});

test('two periods reserve a day for the last payment and show errors beside fields without native popups',async()=>{
 let writes=0;
 const classEndDate='2099-01-14';
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'){writes++;return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,originalAmount:2160000,total:2160000,paid:0,status:'UNPAID',confirmed:false,classEndDate}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  await page.getByLabel('Phương án đóng').selectOption('2');
  assert.equal(await page.locator('[name=date0]').getAttribute('max'),'2099-01-13');
  await page.locator('[name=date0]').fill(classEndDate);
  await page.locator('[name=date1]').fill(classEndDate);
  await page.locator('[name=amount1]').fill('');
  await page.evaluate(()=>{window.nativeValidationPopups=0;HTMLFormElement.prototype.reportValidity=function(){window.nativeValidationPopups++;return false;};});
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  assert.match(await page.locator('#tuition-plan-date-0-error').innerText(),/13\/01\/2099/);
  assert.match(await page.locator('#tuition-plan-amount-1-error').innerText(),/vui lòng nhập/);
  assert.equal(await page.locator('#tuition-plan-date-1-error').innerText(),'');
  assert.equal(await page.evaluate(()=>window.nativeValidationPopups),0);
  assert.equal(writes,0);
  await page.locator('[name=date0]').fill('2099-01-13');
  await page.locator('[name=amount1]').fill('1512000');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(writes,1);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('three periods propagate the first date through an invalid second date and display the exact allowed range',async()=>{
 let writes=0;
 const classEndDate='2099-01-14';
 const {page,errors}=await open('STAFF',(url,req)=>{
  if(req.method()==='POST'){writes++;return {};}
  if(url.pathname.endsWith('/receivables'))return [{...invoice,paid:0,status:'UNPAID',confirmed:false,classEndDate}];
 });
 try{
  await page.getByRole('button',{name:'Chốt lịch đóng',exact:true}).click();
  await page.getByLabel('Phương án đóng').selectOption('3');
  assert.match(await page.locator('[data-date-hint="0"]').innerText(),/^Hạn đóng chỉ được chọn từ .* đến 12\/01\/2099\.$/);
  await page.locator('[name=date0]').fill('2099-01-12');
  const earlySecond=await page.evaluate(()=>tuitionScheduleValidation.shift(new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date()),5));
  const earlyThird=await page.evaluate(()=>tuitionScheduleValidation.shift(new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date()),7));
  await page.locator('[name=date1]').fill(earlySecond);
  await page.locator('[name=date2]').fill(earlyThird);
  assert.equal(await page.locator('[name=date2]').getAttribute('min'),'2099-01-14');
  assert.equal(await page.locator('[name=date2]').getAttribute('max'),'2099-01-14');
  assert.equal(await page.locator('[data-date-hint="2"]').innerText(),'Hạn đóng chỉ được chọn từ 14/01/2099 đến 14/01/2099.');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  assert.match(await page.locator('#tuition-plan-date-2-error').innerText(),/14\/01\/2099/);
  assert.equal(writes,0);
  await page.locator('[name=date1]').fill('2099-01-13');
  assert.equal(await page.locator('[name=date2]').inputValue(),earlyThird);
  await page.locator('[name=date2]').fill(classEndDate);
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.getByText('Đã lưu và cập nhật số dư.',{exact:true}).waitFor();
  assert.equal(writes,1);assert.deepEqual(errors,[]);
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
   await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
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
  await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
  await page.getByText('MÔ PHỎNG LOCAL',{exact:false}).waitFor();
  await page.getByRole('button',{name:'Đóng',exact:true}).click();
  await page.getByText('Đợt học phí đã đóng đủ. Hóa đơn đã cập nhật.',{exact:true}).waitFor();
 }finally{await page.close();}
});

for(const state of ['CANCELLED','PARTIALLY_PAID','RECONCILE','PAID'])
test(`returning from PayOS ends the attempt automatically and preserves verified state ${state}`,async()=>{
 let cancels=0;
 const current={id:7,amount:1500000,status:state,received:500000,remaining:1000000,simulated:false};
 const {page,errors}=await open('PARENT',(url,req)=>{
  if(url.pathname.endsWith('/cancel')){cancels++;return current;}
 });
 try{
  await page.waitForLoadState('networkidle');
  await page.evaluate(()=>sessionStorage.setItem('tuition-payos-checkout',JSON.stringify({intentId:7,invoiceId:11,path:location.pathname,user:'demo'})));
  await page.goto(origin+'/pages/parent-tuition.html?invoiceId=11&status=PAID&code=00');
  const text={CANCELLED:'Đã hủy lần thanh toán này',PAID:'Đợt học phí đã được ghi nhận đủ',PARTIALLY_PAID:'Đã ghi nhận tiền thực nhận',RECONCILE:'Khoản chuyển chưa được xác minh'}[state];
  await page.locator('#feedback').filter({hasText:text}).waitFor();
  assert.equal(cancels,1);
  assert.equal(await page.locator('#action-dialog').evaluate(el=>el.open),false);
  assert.equal(await page.getByRole('button',{name:'Kiểm tra thanh toán PayOS',exact:true}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('checkout rejects an external URL outside the PayOS payment host',async()=>{
 const {page}=await open('PARENT',(url,req)=>{
  if(req.method()==='POST'&&url.pathname.endsWith('/payment-intents'))return {id:7,amount:1500000,status:'PENDING',simulated:false,checkoutUrl:'https://example.org/fake-checkout'};
 });
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).click();
  await page.getByRole('button',{name:'Thanh toán qua PayOS',exact:true}).click();
  await page.locator('#action-error').filter({hasText:'Đường dẫn thanh toán PayOS không hợp lệ'}).waitFor();
  assert.match(page.url(),/parent-tuition/);
 }finally{await page.close();}
});

test('admin keeps tuition and cash corrections without bank reconciliation UI or requests',async()=>{
 let bankReads=0;
 const {page,errors}=await open('ADMIN',url=>{if(url.pathname.includes('bank-reconciliation'))bankReads++;});
 try{
  await page.getByRole('button',{name:'Thanh toán đợt 2',exact:true}).waitFor();
  assert.equal(await page.locator('#bank-transfers-tab, #bank-transfers').count(),0);
  await page.getByRole('tab',{name:/Điều chỉnh khoản thu nhầm/}).click();
  assert.equal(await page.locator('#reversals').isVisible(),true);
  assert.equal(bankReads,0);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('staff extension form shows the exact range and rejects a date past the next period before posting',async()=>{
 let posted;
 const data={...invoice,classEndDate:'2099-12-31',installments:invoice.installments.map(d=>({...d,date:d.sequence===3?'2099-12-20':d.date}))};
 const {page}=await open('STAFF',(url,req)=>{
  if(url.pathname.endsWith('/receivables'))return [data];
  if(req.method()==='POST'&&url.pathname.endsWith('/due-date')){posted=req.postDataJSON();return {};}
 });
 try{
  await page.getByRole('button',{name:'Gia hạn kỳ 2',exact:true}).click();
  const date=page.getByLabel('Hạn đóng mới',{exact:true});
  assert.equal(await date.getAttribute('max'),'2099-12-19');
  await date.fill('2099-12-20');await page.getByLabel('Lý do gia hạn',{exact:true}).fill('Đã trao đổi với phụ huynh');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  assert.equal(posted,undefined);assert.match(await page.locator('#extension-date-error').innerText(),/19\/12\/2099/);
  await date.fill('2099-12-19');await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.locator('#feedback').filter({hasText:'Đã gia hạn kỳ 2'}).waitFor();
  assert.equal(posted.installmentId,2);assert.equal(posted.proposedDate,'2099-12-19');
 }finally{await page.close();}
});

for(const role of ['ADMIN','STAFF'])test(role+' directly extends without an approval action even with legacy pending history',async()=>{
 let posted,reviews=0;
 const data={...invoice,classEndDate:'2099-12-31',installments:invoice.installments.map(d=>({...d,date:d.sequence===3?'2099-12-20':d.date}))};
 const {page}=await open(role,(url,req)=>{
  if(url.pathname.endsWith('/receivables'))return [data];
  if(req.method()==='POST'&&url.pathname.endsWith('/due-date')){posted=req.postDataJSON();return {};}
  if(url.pathname.endsWith('/review')){reviews++;return {};}
  if(url.pathname.endsWith('/plan-change-requests'))return [{id:5,invoiceId:11,invoiceCode:'DEMO-11',student:'Minh An',sequence:2,oldDate:today,proposedDate:'2099-12-19',reason:'Đề nghị cũ',status:'PENDING',requestedBy:'Staff'}];
 });
 try{
  await page.getByRole('button',{name:'Gia hạn kỳ 2',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Duyệt gia hạn',exact:true}).count(),0);
  await page.getByLabel('Hạn đóng mới',{exact:true}).fill('2099-12-19');
  await page.getByLabel('Lý do gia hạn',{exact:true}).fill('Đã trao đổi');
  await page.getByRole('button',{name:'Xác nhận',exact:true}).click();
  await page.locator('#feedback').filter({hasText:'Đã gia hạn kỳ 2'}).waitFor();
  assert.equal(posted.proposedDate,'2099-12-19');assert.equal(reviews,0);
  assert.equal(await page.locator('#due-date-changes').count(),0);
 }finally{await page.close();}
});
