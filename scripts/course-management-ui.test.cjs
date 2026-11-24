const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require('playwright');
let server,browser,origin;
before(async()=>{
 const root=path.resolve(__dirname,'../com_fe');
 server=http.createServer(async(req,res)=>{
  const file=path.resolve(root,'.'+new URL(req.url,'http://local').pathname);
  if(!file.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
  try{res.setHeader('Content-Type',{'.html':'text/html','.js':'text/javascript','.css':'text/css'}[path.extname(file)]||'application/octet-stream');res.end(await fs.readFile(file));}
  catch{res.writeHead(404);res.end();}
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({channel:'chrome',headless:true});
});
after(async()=>{await browser?.close();if(server)await new Promise(r=>server.close(r));});
for(const role of ['ADMIN','STAFF','BRANCH_MANAGER'])test(`${role} selects standard sessions and submits unit pricing without durationWeeks`,async()=>{
 const page=await browser.newPage({viewport:{width:role==='STAFF'?390:1440,height:1000}});const errors=[];let submitted,updated;
 const courses=[{id:1,programId:1,programCode:'PIANO',programName:'Piano',code:'PIA-OLD',name:'Piano căn bản',level:'BEGINNER',totalSessions:20,tuitionFee:3900000,tuitionPerSession:null,totalClasses:1,totalEnrollments:1}];
 page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/**',async route=>{
  const name=route.request().url().endsWith('.css')?'bootstrap.min.css':'bootstrap.bundle.min.js';
  await route.fulfill({body:await fs.readFile(path.join(process.env.CLASS_BOOTSTRAP_CACHE,name)),contentType:name.endsWith('.css')?'text/css':'text/javascript'});
 });
 await page.route('https://cdn.jsdelivr.net/npm/bootstrap-icons@**',r=>r.abort());
 await page.addInitScript(role=>{
  localStorage.setItem('token','test');localStorage.setItem('user',JSON.stringify({role,username:'test'}));
  const original=fetch.bind(window);window.fetch=(input,opts)=>{const u=new URL(input,location.href);return original(u.pathname.startsWith('/api/')?u.pathname+u.search:input,opts);};
 },role);
 await page.route('**/api/**',async route=>{
  const req=route.request(),u=new URL(req.url());let data=[];
  if(u.pathname.endsWith('/programs'))data=[{id:1,code:'PIANO',name:'Piano'}];
  if(u.pathname.endsWith('/levels'))data=['BEGINNER','INTERMEDIATE','ADVANCED'];
  if(u.pathname==='/api/course-management/courses'&&req.method()==='GET')data=courses;
  if(u.pathname==='/api/notifications')data={unreadCount:0,notifications:[]};
  if(u.pathname==='/api/course-management/courses'&&req.method()==='POST'){submitted=req.postDataJSON();data={id:1,...submitted};}
  if(u.pathname==='/api/course-management/courses/1'&&req.method()==='PUT'){updated=req.postDataJSON();data={id:1,...updated};}
  await route.fulfill({json:data});
 });
 try{
  await page.goto(origin+'/pages/course-management.html');
  await page.locator(role==='STAFF'?'#btnOpenCreateModalMobile':'#btnOpenCreateModal').click();
  await page.locator('#fieldProgram').selectOption('1');await page.locator('#fieldLevel').selectOption('BEGINNER');
  await page.locator('#fieldCode').fill('PIA-TEST');await page.locator('#fieldName').fill('Piano thử nghiệm');
  assert.equal(await page.locator('#fieldDurationWeeks').count(),0);
  assert.deepEqual(await page.locator('#fieldTotalSessions option').evaluateAll(items=>items.map(i=>i.value)),['','12','24','36','48','60']);
  assert.equal(await page.locator('#fieldTuitionFee').evaluate(el=>el.readOnly),true);
  await page.locator('#fieldTotalSessions').selectOption('12');await page.locator('#fieldTuitionPerSession').fill('200000');
  for(const count of [12,24,36,48,60]){
   await page.locator('#fieldTotalSessions').selectOption(String(count));
   assert.equal(await page.locator('#fieldTuitionFee').inputValue(),String(count*200000));
  }
  await page.locator('#fieldTotalSessions').selectOption('12');
  assert.equal(await page.locator('#fieldTuitionPerSession').getAttribute('min'),'50000');
  assert.equal(await page.locator('#fieldTuitionPerSession').getAttribute('max'),'1000000');
  for(const price of [50000,1000000]){
   await page.locator('#fieldTuitionPerSession').fill(String(price));
   assert.equal(await page.locator('#fieldTuitionPerSession').evaluate(el=>el.checkValidity()),true);
   assert.equal(await page.locator('#fieldTuitionFee').inputValue(),String(price*12));
  }
  for(const [field,value,expected] of [
   ['fieldTuitionPerSession','-1',/số âm/],['fieldTuitionPerSession','1.5',/thập phân/],['fieldTuitionPerSession','0',/lớn hơn 0/],
   ['fieldTuitionPerSession','1',/50.000 đến 1.000.000/],['fieldTuitionPerSession','49999',/50.000 đến 1.000.000/],
   ['fieldTuitionPerSession','1000001',/50.000 đến 1.000.000/]
  ]){
   await page.locator('#'+field).fill(value);await page.locator('#btnSubmitCourse').click();
   assert.match(await page.locator('#'+field).evaluate(el=>el.validationMessage),expected);
   assert.equal(submitted,undefined);
   await page.locator('#'+field).fill('200000');
  }
  const saved=page.waitForRequest(r=>r.url().endsWith('/api/course-management/courses')&&r.method()==='POST');
  await page.locator('#btnSubmitCourse').click();await saved;
  assert.equal(submitted.totalSessions,12);assert.equal(submitted.tuitionPerSession,200000);
  assert.equal(Object.hasOwn(submitted,'tuitionFee'),false);
  assert.equal(Object.hasOwn(submitted,'durationWeeks'),false);assert.deepEqual(errors,[]);
  await page.locator('#courseFormModal').waitFor({state:'hidden'});
  await page.locator('button[title="Chỉnh sửa"]').first().click();
  assert.equal(await page.locator('#fieldTotalSessions').inputValue(),'');
  assert.equal(await page.locator('#fieldTuitionPerSession').inputValue(),'');
  assert.match(await page.locator('#coursePricingHint').textContent(),/Khóa học cũ: 20 buổi/);
  await page.locator('#btnSubmitCourse').click();assert.equal(updated,undefined);
  await page.locator('#fieldTotalSessions').selectOption('24');
  await page.locator('#fieldTuitionPerSession').fill('200000');
  assert.equal(await page.locator('#fieldTuitionFee').inputValue(),'4800000');
  if(process.env.CLASS_SCREENSHOT_DIR){
   await fs.mkdir(process.env.CLASS_SCREENSHOT_DIR,{recursive:true});
   await page.screenshot({path:path.join(process.env.CLASS_SCREENSHOT_DIR,`course-pricing-${role}.png`),fullPage:true});
  }
  const changed=page.waitForRequest(r=>r.url().endsWith('/api/course-management/courses/1')&&r.method()==='PUT');
  await page.locator('#btnSubmitCourse').click();await changed;
  assert.equal(updated.totalSessions,24);assert.equal(updated.tuitionPerSession,200000);
  assert.deepEqual(errors,[]);
 }catch(error){
  if(process.env.CLASS_SCREENSHOT_DIR){
   await fs.mkdir(process.env.CLASS_SCREENSHOT_DIR,{recursive:true});
   await page.screenshot({path:path.join(process.env.CLASS_SCREENSHOT_DIR,`course-pricing-failed-${role}.png`),fullPage:true});
  }
  throw error;
 }finally{await page.close();}
});
