const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require('playwright');
let server,browser,origin;
const root=path.resolve(__dirname,'../com_fe');
before(async()=>{
 server=http.createServer(async(req,res)=>{
  const file=path.resolve(root,'.'+new URL(req.url,'http://local').pathname);
  if(!file.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
  try{res.setHeader('Content-Type',{'.html':'text/html','.js':'text/javascript','.css':'text/css'}[path.extname(file)]||'text/plain');res.end(await fs.readFile(file));}
  catch{res.writeHead(404);res.end();}
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({channel:'chrome',headless:true});
});
after(async()=>{await browser?.close();if(server)await new Promise(r=>server.close(r));});
async function open(role,file,intercept,width=1440){
 const page=await browser.newPage({viewport:{width,height:1000}});page.setDefaultTimeout(7000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/**',async route=>{
  const name=route.request().url().endsWith('.css')?'bootstrap.min.css':'bootstrap.bundle.min.js';
  await route.fulfill({body:await fs.readFile(path.join(process.env.CLASS_BOOTSTRAP_CACHE,name)),contentType:name.endsWith('.css')?'text/css':'text/javascript'});
 });
 await page.route('https://cdn.jsdelivr.net/npm/bootstrap-icons@**',r=>r.abort());
 await page.addInitScript(role=>{
  localStorage.setItem('token','test');localStorage.setItem('user',JSON.stringify({role,username:'test',fullName:'Người kiểm thử'}));
  const original=fetch.bind(window);window.fetch=(input,opts)=>{const u=new URL(input,location.href);return original(u.pathname.startsWith('/api/')?u.pathname+u.search:input,opts);};
 },role);
 await page.route('**/api/**',async route=>{
  const req=route.request(),u=new URL(req.url());
  let data=await intercept(u,req);
  if(data===undefined)data=u.pathname==='/api/notifications'?{unreadCount:0,notifications:[]}:[];
  await route.fulfill({json:data});
 });
 await page.goto(origin+'/pages/'+file);
 return {page,errors};
}
test('weekly slots, compact cards and create-class room lookup survive integration',async()=>{
 const {page,errors}=await open('ADMIN','weekly-schedule.html',u=>{
  if(u.pathname==='/api/schedule/weekly')return {startDate:'2026-11-09',endDate:'2026-11-15',currentDate:'2026-11-11',weekNumber:46,year:2026,currentUserRole:'ADMIN',lessons:[]};
  if(u.pathname==='/api/course-enrollment/courses')return [{id:1,name:'Piano',totalSessions:10}];
  if(u.pathname==='/api/branches')return [{id:1,name:'Cơ sở 1'}];
  if(u.pathname==='/api/branches/1/rooms')return [{id:1,roomName:'Phòng Piano',roomCode:'P1'}];
 });
 try{
  await page.locator('#timetableBody .slot-cell-header').last().waitFor();
  assert.equal(await page.locator('#timetableBody .slot-cell-header').count(),4);
  await page.locator('#btnOpenCreateClassModal').click();
  await page.locator('#newRoomSelect option[value="1"]').waitFor({state:'attached'});
  assert.match(await page.locator('#newRoomSelect').textContent(),/Phòng Piano/);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('teacher cannot edit a past attendance sheet and LATE is absent',async()=>{
 let posted=false;
 const {page,errors}=await open('TEACHER','lesson-attendance.html?lessonId=1',(u,req)=>{
  if(u.pathname.endsWith('/attendance')){
   if(req.method()==='POST')posted=true;
   return {lessonId:1,className:'Piano',lessonDate:'2020-01-01',timeRange:'07:00 - 09:00',canEdit:false,lockReason:'Chỉ được điểm danh trong ngày',students:[{studentId:1,fullName:'Minh An',studentCode:'HV01',status:null}]};
  }
 });
 try{
  await page.locator('#studentList .att-row').waitFor();
  assert.equal(await page.locator('input[value="LATE"]').count(),0);
  assert.equal(await page.locator('input[type=radio]:enabled').count(),0);
  await page.locator('#btnEdit').click();
  assert.match(await page.locator('#attBanner').textContent(),/quá khứ/);
  assert.equal(posted,false);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('notification bell filters alerts, escapes content and marks all read on mobile',async()=>{
 let marked=false;
 const {page,errors}=await open('PARENT','parent-tuition.html',(u,req)=>{
  if(u.pathname==='/api/notifications/read-all'){marked=true;return {};}
  if(u.pathname==='/api/notifications')return {unreadCount:marked?0:2,notifications:[
   {id:1,title:'Vắng buổi học',message:'<script>bad()</script>',type:'ATTENDANCE_ABSENT',isRead:marked},
   {id:2,title:'Đã nhận học phí',message:'Đợt 1',type:'PAYMENT_SUCCESS',isRead:marked}
  ]};
 },390);
 try{
  await page.locator('#notifBellBtn').click();
  await page.locator('#notifDropdownList .notif-item').last().waitFor();
  assert.equal(await page.locator('#notifDropdownList script').count(),0);
  await page.locator('#notifTabAlert').click();
  assert.equal(await page.locator('#notifDropdownList .notif-item').count(),1);
  await page.locator('#notifMarkAllBtn').click();
  await page.waitForFunction(()=>document.querySelector('#notifUnreadBadge').classList.contains('d-none'));
  assert.equal(marked,true);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

const absence={id:5,studentId:1,studentName:'Minh An',parentName:'Phụ huynh A',className:'Piano',
 lessonDate:'2099-12-31',timeSlot:'07:00 - 09:00',lessonTitle:'Buổi 3',reason:'Gia đình có việc',
 status:'PENDING',createdAt:'2026-11-11T10:00:00'};
test('parent notification deep link opens absence details and may cancel a pending request',async()=>{
 let cancelled=false;
 const {page,errors}=await open('PARENT','absence-makeup.html?tab=ABSENCE&absenceId=5',(u,req)=>{
  if(u.pathname==='/api/attendance-makeup/absence-requests')return cancelled?[]:[absence];
  if(u.pathname==='/api/attendance-makeup/absence-requests/5'){
   if(req.method()==='DELETE'){cancelled=true;return {};}
   return absence;
  }
 },390);
 try{
  await page.locator('#modalDetail.show').waitFor();
  assert.match(await page.locator('#detailModalBody').textContent(),/Minh An/);
  assert.equal(await page.locator('#detailModalFooter').getByRole('button',{name:'Duyệt',exact:true}).count(),0);
  await page.locator('#detailModalFooter').getByRole('button',{name:'Hủy đơn',exact:true}).click();
  await page.locator('#modalResolveNote.show').waitFor();
  await page.locator('#btnConfirmResolve').click();
  await page.waitForFunction(()=>document.querySelector('#absenceTableBody').textContent.includes('Không có đơn'));
  assert.equal(cancelled,true);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('staff sees pending requests first, can approve and has no excused or deferred actions',async()=>{
 const {page,errors}=await open('STAFF','absence-makeup.html',(u)=>{
  if(u.pathname==='/api/attendance-makeup/absence-requests')return [
   {...absence,id:6,status:'APPROVED',createdAt:'2026-11-11T12:00:00'},absence];
 });
 try{
  await page.locator('#absenceTableBody tr').first().waitFor();
  assert.match(await page.locator('#absenceTableBody tr').first().textContent(),/#5/);
  assert.equal(await page.locator('#absenceTableBody tr').first().locator('button[onclick^="openApproveModal"]').count(),1);
  assert.equal(await page.getByText('Miễn bù',{exact:true}).count(),0);
  assert.equal(await page.getByText('Chuyển kỳ sau',{exact:true}).count(),0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
