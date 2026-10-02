import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
import {renderSite} from '../src/render.mjs';
import {addAnalytics,installAnalytics} from '../src/analytics.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const settings={measurementId:'G-89S881436M',origin:'https://queue-mate.com'};
const body=s=>s.match(/<body>[\s\S]*<\/body>/)[0];
function browser(options={}) {
 const appended=[],listeners={};
 const window={location:new URL(options.url??settings.origin+'/'),navigator:options.navigator??{},localStorage:{getItem(){if(options.storageThrows)throw Error('blocked');return options.optOut??null;}},...options.window};
 const document={title:config.title,referrer:options.referrer??'',head:{appendChild(s){appended.push(s);}},createElement(){return {};},querySelector(){return options.existingTag??null;},addEventListener(n,f){listeners[n]=f;}};
 const context={window,document,URL,Date};
 const run=()=>runInNewContext(`(${installAnalytics.toString()})(${JSON.stringify(settings)})`,context);
 run();
 const commands=()=>Array.from(window.dataLayer??[],a=>Array.from(a));
 const click=(position='hero',event={})=>listeners.click?.({isTrusted:true,defaultPrevented:false,target:{closest(){return {getAttribute(){return '#preview';},closest(){return position==='header'?{}:null;}}}},...event});
 return {window,document,appended,listeners,commands,click,run};
}
test('configured measurement ID is the one provided by the owner',()=>assert.deepEqual(config.analytics,{enabled:true,measurementId:settings.measurementId}));
test('only the production indexable build adds one analytics bootstrap',()=>{
 const r=renderSite(config,{BUILD_TARGET:'production'}),h=addAnalytics(r.html,config,r.mode);
 assert.match(h,/id="qmate-analytics"/);assert.ok(h.includes(settings.measurementId));assert.equal(h.split('id="qmate-analytics"').length-1,1);
 assert.equal(body(h),body(r.html));
 assert.equal(h.replace(/<script id="qmate-analytics">[\s\S]*?<\/script>/,''),r.html);
});
for(const env of [{},{VERCEL_ENV:'preview'},{VERCEL_ENV:'development'},{VERCEL_ENV:'preview',BUILD_TARGET:'production'}])test(`preview sends no analytics: ${JSON.stringify(env)}`,()=>{
 const r=renderSite(config,env);assert.equal(addAnalytics(r.html,config,r.mode),r.html);
});
test('disabled or absent analytics is a full rollback',()=>{
 const r=renderSite(config,{BUILD_TARGET:'production'});
 for(const analytics of [undefined,{...config.analytics,enabled:false}])assert.equal(addAnalytics(r.html,{...config,analytics},r.mode),r.html);
});
test('invalid analytics configuration fails instead of injecting arbitrary text',()=>{
 const r=renderSite(config,{BUILD_TARGET:'production'});
 for(const analytics of [null,{}, {enabled:'true',measurementId:settings.measurementId},{enabled:true,measurementId:''},{enabled:true,measurementId:'G-<script>'}])assert.throws(()=>addAnalytics(r.html,{...config,analytics},r.mode),/analytics/);
 assert.throws(()=>addAnalytics(r.html,{...config,origin:'https://other.com'},r.mode),/analytics/);
 assert.throws(()=>addAnalytics(addAnalytics(r.html,config,r.mode),config,r.mode),/중복/);
});
for(const url of ['https://preview.vercel.app/','http://queue-mate.com/','https://www.queue-mate.com/','https://queue-mate.com/404','https://queue-mate.com.evil.test/'])test(`runtime never contacts Google on ${url}`,()=>{
 const b=browser({url});assert.equal(b.appended.length,0);assert.equal(b.commands().length,0);
});
test('loader is asynchronous and initializes just one automatic page view',()=>{
 const b=browser();b.run();assert.equal(b.appended.length,1);assert.equal(b.appended[0].async,true);
 assert.equal(b.appended[0].src,'https://www.googletagmanager.com/gtag/js?id='+settings.measurementId);
 assert.equal(b.commands().filter(c=>c[0]==='config').length,1);
 assert.equal(b.commands().find(c=>c[0]==='config')[2].send_page_view,true);
 assert.equal(b.commands().filter(c=>c[0]==='event').length,0);
});
test('advertising features are disabled without claiming visitors granted consent',()=>{
 const cmds=browser().commands(),consent=cmds[0];assert.equal(consent[0],'consent');
 for(const name of ['ad_storage','ad_user_data','ad_personalization'])assert.equal(consent[2][name],'denied');
 assert.equal(consent[2].analytics_storage,undefined);
 const c=cmds.find(c=>c[0]==='config')[2];assert.equal(c.allow_google_signals,false);assert.equal(c.allow_ad_personalization_signals,false);assert.equal(c.user_id,undefined);
});
test('known campaigns survive while email, tokens, free-text terms and fragments are removed',()=>{
 const b=browser({url:settings.origin+'/?utm_source=naver&utm_medium=organic&utm_campaign=launch&utm_content=hero&utm_term=private&email=test%40mail.com&token=secret#ga',referrer:'https://search.naver.com/search.naver?query=private'});
 const c=b.commands().find(c=>c[0]==='config')[2];
 assert.equal(c.page_referrer,'https://search.naver.com/');assert.equal(c.page_location,settings.origin+'/?utm_source=naver&utm_medium=organic&utm_campaign=launch&utm_content=hero');
 assert.doesNotMatch(JSON.stringify(b.commands()),/private|mail\.com|secret|#ga/);
});
test('unsafe campaign values and bad referrers are not forwarded',()=>{
 const b=browser({url:settings.origin+'/?utm_source=a%40email.com&utm_id=01012345678',referrer:'https://user:password@example.com/private'});
 const c=b.commands().find(c=>c[0]==='config')[2];assert.equal(c.page_referrer,'');assert.equal(c.page_location,settings.origin+'/');
});
test('hero and header demo clicks are single custom events, not conversions',()=>{
 const b=browser();b.click();b.click('header');
 const events=b.commands().filter(c=>c[0]==='event');assert.equal(events.length,2);
 assert.deepEqual(events.map(c=>[c[1],c[2].cta_position,c[2].send_to]),[['demo_cta_click','hero',settings.measurementId],['demo_cta_click','header',settings.measurementId]]);
});
test('unrelated clicks, cancelled events and synthetic tests are not counted',()=>{
 const b=browser();b.click('hero',{isTrusted:false});b.click('hero',{defaultPrevented:true});b.click('hero',{target:{closest(){return null;}}});
 assert.equal(b.commands().filter(c=>c[0]==='event').length,0);
});
for(const options of [{navigator:{globalPrivacyControl:true}},{navigator:{doNotTrack:'1'}},{window:{'ga-disable-G-89S881436M':true}},{window:{qmateAnalyticsConsent:'denied'}},{optOut:'true'},{existingTag:{}}])test(`privacy or duplicate-installation block: ${JSON.stringify(options)}`,()=>{
 const b=browser(options);assert.equal(b.appended.length,0);assert.equal(b.commands().length,0);
});
test('blocked storage does not break the page or loader',()=>assert.equal(browser({storageThrows:true}).appended.length,1));
test('custom click stops after an explicit opt-out',()=>{
 const b=browser();b.window['ga-disable-G-89S881436M']=true;b.click();assert.equal(b.commands().filter(c=>c[0]==='event').length,0);
});
test('debug mode is explicit and its query parameter never reaches page_location',()=>{
 const c=browser({url:settings.origin+'/?ga_debug=1'}).commands().find(c=>c[0]==='config')[2];assert.equal(c.debug_mode,true);assert.equal(c.page_location,settings.origin+'/');
 assert.equal(browser().commands().find(c=>c[0]==='config')[2].debug_mode,undefined);
});
