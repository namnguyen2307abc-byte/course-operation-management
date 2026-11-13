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
