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

const assessment={id:7,studentName:'Học viên thử upload',subject:'DAN',title:'Test Piano',status:'SCHEDULED',testDate:'2026-11-11T10:00:00',score:null};
const image={name:'bai-thi.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==','base64')};

const programs = [{id:31,name:'Piano',subject:'DAN'}, {id:32,name:'Guitar',subject:'DAN'}];

test('parent submits a placement program and sees classes only for the recommended course', async()=>{
 let submitted;
 const {page}=await open('PARENT','course-enrollment.html',(url,request)=>{
  if(url.pathname.endsWith('/children')) return [{id:5,fullName:'Nguyễn An'}];
  if(url.pathname==='/api/course-enrollment/programs') return programs.map(program=>({...program,placementAvailable:true}));
  if(url.pathname.endsWith('/courses')) return [{id:8,name:'Piano nền tảng',tuitionFee:2400000}];
  if(url.pathname.endsWith('/class-recommendations')){
   const recommended=url.searchParams.get('programId')==='32';
   return {recommendationAvailable:recommended,placementStatus:recommended?'COMPLETED':'NOT_AVAILABLE',
    recommendedCourseId:recommended?9:null,recommendedClasses:recommended?[{id:3,className:'Lớp Guitar 1',availableSeats:4}]:[],
    openClasses:[{id:4,className:'Lớp Piano khác',availableSeats:5}]};
  }
  if(request.method()==='POST') submitted=request.postDataJSON();
  if(url.pathname.endsWith('/page')) return {items:[],totalItems:0,page:0,totalPages:0};
 });
 try{
  const program=page.locator('#placementProgramSelect');
  await page.locator('#courseSelect option[value="8"]').waitFor({state:'attached'});
  await page.locator('#placementRequested').check();
  assert.equal(await program.evaluate(el=>el.validity.valueMissing),true);
  assert.equal(await page.locator('#coursePreferenceField').isVisible(),false);
  await program.selectOption('31');
  await page.getByText('Chưa có kết quả Placement Test',{exact:true}).waitFor();
  assert.equal(await page.locator('#parentClassPreviewField').isVisible(),false);
  await program.selectOption('32');
  await page.locator('#parentClassPreviewField').waitFor();
  assert.match(await page.locator('#parentPreviewClassSelect').innerText(),/Lớp Guitar 1/);
  assert.equal(await page.locator('#parentPreviewClassSelect option').count(),1);
  await page.locator('#submitRequestBtn').click();
  await page.getByText(/Đã gửi nhu cầu học/).waitFor();
  assert.equal(submitted.requestedProgramId,32);
  assert.equal(submitted.preferredCourseId,null);
  await page.locator('#placementRequested').uncheck();
  assert.equal(await page.locator('#coursePreferenceField').isVisible(),true);
  assert.equal(await program.isDisabled(),true);
 }finally{await page.close();}
});

test('parent can submit a catalog program awaiting advice without requesting an assessment', async()=>{
 let submitted;
 let resultRequests=0;
 const {page,errors}=await open('PARENT','course-enrollment.html',(url,request)=>{
  if(url.pathname.endsWith('/children')) return [{id:5,fullName:'Nguyễn An'}];
  if(url.pathname==='/api/course-enrollment/programs') return [
   ...programs.map(program=>({...program,placementAvailable:true})),
   {id:33,name:'Cờ vua',placementAvailable:false},
   {id:34,name:'Bơi lội',placementAvailable:false}
  ];
  if(url.pathname.endsWith('/courses')) return [{id:8,name:'Piano nền tảng',tuitionFee:2400000}];
  if(url.pathname.endsWith('/class-recommendations')) resultRequests++;
  if(request.method()==='POST') submitted=request.postDataJSON();
  if(url.pathname.endsWith('/page')) return {items:[],totalItems:0,page:0,totalPages:0};
 });
 try{
  const select=page.locator('#placementProgramSelect');
  await select.locator('option[value="34"]').waitFor({state:'attached'});
  await page.locator('#placementRequested').check();
  assert.equal(await select.locator('option').count(),5);
  assert.equal(await select.locator('option[value="33"]').textContent(),'Cờ vua (Chờ tư vấn)');
  await select.selectOption('33');
  await page.getByText('Phòng đào tạo sẽ tư vấn hình thức kiểm tra cho môn này sau khi nhận yêu cầu.',{exact:true}).waitFor();
  assert.equal(await page.locator('#parentClassPreviewField').isVisible(),false);
  await page.locator('#requestNote').fill('Gia đình ưu tiên sáng thứ Bảy.');
  await page.locator('#submitRequestBtn').click();
  await page.getByText('Đã gửi nhu cầu học. Phòng đào tạo sẽ tư vấn hình thức kiểm tra.',{exact:true}).waitFor();
  assert.equal(submitted.requestedProgramId,33);
  assert.equal(submitted.placementRequested,true);
  assert.equal(submitted.note,'Gia đình ưu tiên sáng thứ Bảy.');
  await select.selectOption('34');
  await page.getByText('Phòng đào tạo sẽ tư vấn hình thức kiểm tra cho môn này sau khi nhận yêu cầu.',{exact:true}).waitFor();
  assert.equal(resultRequests,0);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('staff sees the parent note and locked program, and can choose a program for an independent test', async()=>{
 let submitted;
 const {page}=await open('STAFF','placement-test.html',(url,request)=>{
  if(url.pathname.endsWith('/placement-programs')) return programs;
  if(url.pathname==='/api/branches') return [{id:1,name:'Cơ sở 1 - Cầu Giấy',active:true}];
  if(url.pathname==='/api/branches/1/rooms') return [{id:1,roomCode:'P101',roomName:'Phòng Đàn 101',status:'AVAILABLE'}];
  if(url.pathname.endsWith('/staff/requests')) return [
   {id:70,childName:'Nguyễn An',placementRequested:true,status:'WAITING_PLACEMENT',requestedProgramId:31,
    requestedProgramName:'Piano',note:'Ưu tiên thứ Bảy'},
   {id:71,childName:'Đơn cũ',placementRequested:true,status:'WAITING_PLACEMENT',note:'Muốn học Guitar'}
  ];
  if(url.pathname==='/api/placement-tests' && request.method()==='POST'){
   submitted=request.postDataJSON();
   return {...assessment,...submitted,id:18,programName:'Piano'};
  }
 });
 try{
  await page.locator('button[onclick="openCreateModal()"]').click();
  const request=page.locator('#createEnrollmentRequest');
  const program=page.locator('#createProgram');
  await request.selectOption('70');
  await page.getByText('Ưu tiên thứ Bảy',{exact:true}).waitFor();
  await page.waitForFunction(()=>document.querySelector('#createProgram').value==='31');
  assert.equal(await page.locator('#createStudentName').inputValue(),'Nguyễn An');
  assert.equal(await program.isDisabled(),true);
  await request.selectOption('71');
  await program.selectOption('32');
  await request.selectOption('');
  assert.equal(await page.locator('#createParentNote').isVisible(),false);
  assert.equal(await program.inputValue(),'');
  await program.selectOption('32');
  await request.selectOption('70');
  await page.locator('#createTitle').fill('Kiểm tra Piano');
  if(await page.locator('#createRoomName').evaluate(el=>el.tagName==='SELECT')){
   await page.locator('#createRoomName').selectOption({index:1});
  }else{
   await page.locator('#createRoomName').fill('P101');
  }
  const future=await page.evaluate(()=>{
   const date=new Date();date.setDate(date.getDate()+3);
   return new Date(date.getTime()-date.getTimezoneOffset()*60_000).toISOString().slice(0,10);
  });
  await page.locator('#createTestDate').fill(future);
  await page.locator('#createTestTime').fill('10:00');
  await page.locator('#createPlacementTestForm button[type="submit"]').click();
  await page.locator('#createModal').waitFor({state:'hidden'});
  assert.equal(submitted.programId,31);
  assert.equal(submitted.enrollmentRequestId,70);
 }finally{await page.close();}
});

test('placement cancellation requires a reason, preserves a failed draft and retries on the same schedule',async()=>{
 const {page,errors}=await open('STAFF','placement-test.html',u=>u.pathname==='/api/placement-tests'?[assessment]:undefined);
 let attempts=0;
 await page.route('**/api/placement-tests/7/cancel',async route=>{
  attempts++;
  assert.equal(route.request().postDataJSON().cancelReason,'Gia đình xin đổi lịch');
  await route.fulfill(attempts===1?{status:500,json:{message:'Máy chủ tạm thời bận'}}:
   {json:{...assessment,status:'CANCELLED',cancelReason:'Gia đình xin đổi lịch'}});
 });
 try{
  await page.waitForFunction(()=>placementTestsData.some(x=>x.id===7));
  await page.evaluate(()=>openCancelPlacementModal(7));
  await page.locator('#cancelPlacementModal.show').waitFor();
  await page.locator('#btnConfirmCancelPlacement').click();
  assert.equal(attempts,0);
  await page.locator('#cancelReasonInput').fill('Gia đình xin đổi lịch');
  await page.locator('#btnConfirmCancelPlacement').click();
  await page.getByText(/Không thể hủy đơn:/).waitFor();
  assert.equal(await page.locator('#cancelReasonInput').inputValue(),'Gia đình xin đổi lịch');
  assert.equal(await page.evaluate(()=>placementTestsData[0].status),'SCHEDULED');
  await page.locator('#btnConfirmCancelPlacement').click();
  await page.waitForFunction(()=>placementTestsData[0].status==='CANCELLED');
  assert.equal(attempts,2);
  await page.locator('#cancelPlacementModal').waitFor({state:'hidden'});
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('placement cancellation never fakes success for an empty server response',async()=>{
 const {page,errors}=await open('STAFF','placement-test.html',u=>u.pathname==='/api/placement-tests'?[assessment]:undefined);
 await page.route('**/api/placement-tests/7/cancel',r=>r.fulfill({json:null}));
 try{
  await page.waitForFunction(()=>placementTestsData.some(x=>x.id===7));
  await page.evaluate(()=>openCancelPlacementModal(7));
  await page.locator('#cancelPlacementModal.show').waitFor();
  await page.locator('#cancelReasonInput').fill('Gia đình xin đổi lịch');
  await page.locator('#btnConfirmCancelPlacement').click();
  await page.getByText(/Máy chủ chưa xác nhận hủy đơn/).waitFor();
  assert.equal(await page.evaluate(()=>placementTestsData[0].status),'SCHEDULED');
  assert.equal(await page.evaluate(()=>cancellingScheduleId),7);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});

test('placement detail previews an uploaded image from the backend URL',async()=>{
 const {page,errors}=await open('STAFF','placement-test.html',u=>u.pathname==='/api/placement-tests'?
  [{...assessment,imageUrl:'/uploads/placement-tests/exam.png'}]:undefined);
 await page.route('**/uploads/placement-tests/exam.png',r=>r.fulfill({body:image.buffer,contentType:'image/png'}));
 try{
  await page.waitForFunction(()=>placementTestsData.some(x=>x.id===7));
  await page.evaluate(()=>openDetailModal(7));
  const preview=page.locator('#detailMediaAttachment img');
  await preview.waitFor();
  assert.match(await preview.getAttribute('src'),/:8080\/uploads\/placement-tests\/exam\.png$/);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
async function evaluate(intercept){
 const result=await open('TEACHER','placement-test.html',(u,req)=>{
  if(u.pathname==='/api/placement-tests')return [assessment];
  if(u.pathname==='/api/course-enrollment/courses')return [{id:1,name:'Piano',programName:'Piano',code:'PIANO'}];
  return intercept(u,req);
 });
 await result.page.waitForFunction(()=>placementTestsData.some(x=>x.id===7));
 await result.page.evaluate(()=>openEvaluateModal(7));
 await result.page.locator('#evaluateModal.show').waitFor();
 await result.page.locator('#evalExaminerNotes').fill('Bản nháp của giáo viên');
 await result.page.locator('#evalLevelSelect').selectOption('INTERMEDIATE');
 return result;
}
test('successful image upload retains the open assessment and the teacher draft',async()=>{
 const {page,errors}=await evaluate(u=>u.pathname.endsWith('/upload')?{fileUrl:'/uploads/placement-tests/exam.png'}:undefined);
 try{
  await page.locator('#evalAttachmentFile').setInputFiles(image);
  await page.getByText('Đã tải thành công:',{exact:false}).waitFor();
  assert.equal(await page.locator('#evaluateModal').evaluate(e=>e.classList.contains('show')),true);
  assert.equal(await page.locator('#evalMediaUrl').inputValue(),'/uploads/placement-tests/exam.png');
  assert.equal(await page.locator('#evalExaminerNotes').inputValue(),'Bản nháp của giáo viên');
  assert.equal(await page.locator('#evalLevelSelect').inputValue(),'INTERMEDIATE');
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('a failed upload has no fake URL and preserves the assessment',async()=>{
 const {page,errors}=await evaluate(()=>undefined);
 await page.route('**/api/placement-tests/upload',r=>r.fulfill({status:500,json:{message:'Không lưu được file'}}));
 try{
  await page.locator('#evalAttachmentFile').setInputFiles(image);
  await page.getByText(/Chưa tải được file:/).waitFor();
  assert.equal(await page.locator('#evalMediaUrl').inputValue(),'');
  assert.equal(await page.locator('#evalExaminerNotes').inputValue(),'Bản nháp của giáo viên');
  assert.equal(await page.locator('#evaluateModal').evaluate(e=>e.classList.contains('show')),true);
  assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
test('saving is blocked during upload and closing prevents a late upload from changing another assessment',async()=>{
 let release;
 const held=new Promise(r=>release=r);
 const {page,errors}=await evaluate(async u=>{if(u.pathname.endsWith('/upload')){await held;return {fileUrl:'/uploads/placement-tests/old.png'};}});
 try{
  await page.locator('#evalAttachmentFile').setInputFiles(image);
  await page.getByText('Đang tải file lên máy chủ...',{exact:true}).waitFor();
  assert.equal(await page.locator('#evaluatePlacementTestForm button[type=submit]').isDisabled(),true);
  await page.locator('#evaluateModal [data-bs-dismiss=modal]').first().click();
  await page.locator('#evaluateModal').waitFor({state:'hidden'});
  await page.evaluate(()=>openEvaluateModal(7));
  await page.locator('#evaluateModal.show').waitFor();
  const response=page.waitForResponse(r=>r.url().endsWith('/placement-tests/upload'));
  release();await response;
  await page.waitForFunction(()=>document.querySelector('#evalMediaUrl').value==='');
  assert.equal(await page.locator('#evalMediaUrl').inputValue(),'');
  assert.deepEqual(errors,[]);
 }finally{release();await page.close();}
});
