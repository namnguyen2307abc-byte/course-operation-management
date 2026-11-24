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
for(const role of ['ADMIN','STAFF'])test(`${role} validates course numbers separately and submits without durationWeeks`,async()=>{
 const page=await browser.newPage();const errors=[];let submitted;
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
  if(u.pathname==='/api/notifications')data={unreadCount:0,notifications:[]};
  if(u.pathname==='/api/course-management/courses'&&req.method()==='POST'){submitted=req.postDataJSON();data={id:1,...submitted};}
  await route.fulfill({json:data});
 });
 try{
  await page.goto(origin+'/pages/course-management.html');
  await page.locator('#btnOpenCreateModal').click();
  await page.locator('#fieldProgram').selectOption('1');await page.locator('#fieldLevel').selectOption('BEGINNER');
  await page.locator('#fieldCode').fill('PIA-TEST');await page.locator('#fieldName').fill('Piano thử nghiệm');
  assert.equal(await page.locator('#fieldDurationWeeks').count(),0);
  await page.locator('#fieldTotalSessions').fill('12');await page.locator('#fieldTuitionFee').fill('30000');
  for(const [field,value,expected] of [
   ['fieldTotalSessions','-1',/số âm/],['fieldTotalSessions','1.5',/thập phân/],['fieldTotalSessions','0',/lớn hơn 0/],['fieldTotalSessions','1001',/1000/],
   ['fieldTuitionFee','-1',/số âm/],['fieldTuitionFee','1.5',/thập phân/],['fieldTuitionFee','0',/lớn hơn 0/]
  ]){
   await page.locator('#'+field).fill(value);await page.locator('#btnSubmitCourse').click();
   assert.match(await page.locator('#'+field).evaluate(el=>el.validationMessage),expected);
   assert.equal(submitted,undefined);
   await page.locator('#'+field).fill(field==='fieldTotalSessions'?'12':'30000');
  }
  const saved=page.waitForRequest(r=>r.url().endsWith('/api/course-management/courses')&&r.method()==='POST');
  await page.locator('#btnSubmitCourse').click();await saved;
  assert.equal(submitted.totalSessions,12);assert.equal(submitted.tuitionFee,30000);
  assert.equal(Object.hasOwn(submitted,'durationWeeks'),false);assert.deepEqual(errors,[]);
 }finally{await page.close();}
});
