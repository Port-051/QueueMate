import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {renderSite,render404,resolveMode,icon,escapeHtml} from '../src/render.mjs';
const base=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
// Generic fixtures use no account tokens; ownership.test.mjs validates the real configuration.
base.verification={google:'',naver:'',bing:''};
const draft=()=>({...structuredClone(base),contentApproved:false,uiApproved:false,allowIndexing:false,appReady:false});
const approved=()=>({...draft(),contentApproved:true,uiApproved:true,allowIndexing:true});
const live=()=>({...approved(),appReady:true,description:'원하는 조건의 롤 듀오와 파티를 큐메이트에서 찾아보세요.'});
test('approved production configuration permits search and app login connection',()=>{assert.equal(base.allowIndexing,true);assert.equal(base.appReady,true);});
test('unapproved production stays noindex with no sitemap',()=>{const r=renderSite(draft(),{VERCEL_ENV:'production'});assert.match(r.html,/content="noindex, nofollow"/);assert.equal(r.sitemap,null);});
test('approved production has one canonical URL and sitemap entry',()=>{const r=renderSite(approved(),{VERCEL_ENV:'production'});assert.match(r.html,/index, follow, max-image-preview:large/);assert.equal((r.html.match(/rel="canonical"/g)||[]).length,1);assert.match(r.sitemap,/<loc>https:\/\/queue-mate.com\/<\/loc>/);});
test('VERCEL preview cannot be changed to production by local override',()=>{assert.equal(resolveMode(approved(),{VERCEL_ENV:'preview',BUILD_TARGET:'production'}).indexable,false);});
test('development and local builds stay noindex',()=>{for(const env of [{},{VERCEL_ENV:'development'},{BUILD_TARGET:'preview'}])assert.equal(resolveMode(approved(),env).indexable,false);});
test('explicit local production target works after approval',()=>{assert.equal(resolveMode(approved(),{BUILD_TARGET:'production'}).indexable,true);});
test('indexing requires independent copy and UI approval',()=>{for(const key of ['contentApproved','uiApproved'])assert.throws(()=>renderSite({...approved(),[key]:false}),/검토/);});
test('screenshot is not an artificial prerequisite to publish a text-first site',()=>{assert.equal(resolveMode({...approved(),media:{ogImage:''}},{VERCEL_ENV:'production'}).indexable,true);});
test('preview CTA navigates to the example, not an unlaunched app',()=>{const h=renderSite(draft()).html;assert.match(h,/href="#preview" data-cta="explore-preview"/);assert.doesNotMatch(h,/href="https:\/\/app.queue-mate.com/);});
test('approved app uses the configured destination',()=>{assert.match(renderSite(live()).html,/href="https:\/\/app.queue-mate.com\/login" data-cta="start-matching"/);});
test('app activation requires copy review',()=>{assert.throws(()=>renderSite({...draft(),appReady:true}),/문구 검토/);});
test('live configuration description no longer contains preparing metadata',()=>{assert.doesNotMatch(base.description,/준비 중|준비하고 있습니다/);});
test('initial HTML contains one H1, Korean language, title and description',()=>{const h=renderSite(draft()).html;assert.match(h,/<html lang="ko">/);assert.equal((h.match(/<h1\b/g)||[]).length,1);assert.ok(h.includes(`<title>${escapeHtml(base.title)}</title>`));assert.match(h,/name="description"/);});
test('core sections and three native FAQs exist without executable JS',()=>{const h=renderSite(draft()).html;for(const id of ['main','preview','features','quick-match','faq'])assert.match(h,new RegExp(`id="${id}"`));assert.equal((h.match(/<details class="faq-item">/g)||[]).length,3);const scripts=[...h.matchAll(/<script\b([^>]*)>/g)];assert.equal(scripts.length,1);assert.match(scripts[0][1],/application\/ld\+json/);});
test('Website structured data has no fictional rating, price or search action',()=>{const h=renderSite(draft()).html;const s=JSON.parse(h.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/)[1]);assert.equal(s['@type'],'WebSite');assert.equal(s.url,'https://queue-mate.com/');for(const k of ['aggregateRating','offers','potentialAction'])assert.equal(s[k],undefined);});
test('real UI captures are labelled with synthetic data and not connected service',()=>{const h=renderSite(draft()).html;assert.match(h,/실제 UI/);assert.match(h,/예시 데이터/);assert.match(h,/실제 참가·음성 연결은 되지 않습니다/);assert.doesNotMatch(h,/class="room-bubble|테스트 서포터|예약 모집 예시|전적 연동 대기/);});
test('original wordmark is included by exact repository blob SHA',async()=>{const b=await readFile(new URL('../public/assets/queuemate-wordmark.svg',import.meta.url));assert.equal(createHash('sha1').update(`blob ${b.length}\0`).update(b).digest('hex'),'c596bbd685b1ff3b8c50c9bab3d23a9abe69030c');});
test('blank verification values do not create fake ownership tags',()=>{const c=draft();c.verification={google:'',naver:'',bing:''};assert.doesNotMatch(renderSite(c).html,/name="google-site-verification"|name="naver-site-verification"|name="msvalidate.01"/);});
test('provided verification tags are present and escaped',()=>{const c=draft();c.verification={google:'g-value',naver:'n-value',bing:'" test="value'};const h=renderSite(c).html;assert.match(h,/content="g-value"/);assert.match(h,/content="n-value"/);assert.match(h,/content="&quot; test=&quot;value"/);});
test('OG metadata is omitted without an image path',()=>{const c=draft();c.media.ogImage='';const h=renderSite(c).html;assert.doesNotMatch(h,/property="og:image"/);assert.match(h,/name="twitter:card" content="summary"/);});
test('OG image has absolute URL, dimensions, and alternative text',()=>{const c=draft();c.media.ogImage='/assets/og-cover.png';const h=renderSite(c).html;assert.match(h,/https:\/\/queue-mate.com\/assets\/og-cover.png/);assert.match(h,/property="og:image:width" content="1200"/);assert.match(h,/property="og:image:height" content="630"/);assert.match(h,/property="og:image:alt"/);});
test('HTML and JSON-LD input injection is escaped',()=>{const c=draft();c.name='</script><script>alert(1)</script>';c.title='" onload="test';const h=renderSite(c).html;assert.doesNotMatch(h,/<script>alert\(1\)<\/script>/);JSON.parse(h.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/)[1]);assert.match(h,/&quot; onload=&quot;test/);});
test('invalid origin or app URLs are rejected',()=>{for(const origin of ['http://queue-mate.com','https://x:y@queue-mate.com','https://queue-mate.com/path','https://queue-mate.com/?q=x','https://queue-mate.com/#x'])assert.throws(()=>renderSite({...draft(),origin}),/HTTPS/);for(const appUrl of ['javascript:alert(1)','http://app.queue-mate.com','https://x:y@app.queue-mate.com'])assert.throws(()=>renderSite({...draft(),appUrl}),/HTTPS/);});
test('unsafe image paths are rejected',()=>{for(const ogImage of ['/assets/../../secret.png','https://example.com/a.png','/assets/a.svg',123]){const c=draft();c.media.ogImage=ogImage;assert.throws(()=>renderSite(c),/media.ogImage/);}});
test('flags must be booleans and verification values strings',()=>{assert.throws(()=>renderSite({...draft(),allowIndexing:'false'}),/true 또는 false/);const c=draft();c.verification.google=42;assert.throws(()=>renderSite(c),/문자열/);});
test('robots allow crawlers to see noindex, not a robots.txt Noindex directive',()=>{const r=renderSite(draft());assert.match(r.robots,/Allow: \//);assert.doesNotMatch(r.robots,/^Noindex:|Disallow:/mi);});
test('sitemap does not invent app pages, lastmod or synthetic routes',()=>{const r=renderSite(approved(),{VERCEL_ENV:'production'});assert.equal((r.sitemap.match(/<url>/g)||[]).length,1);assert.doesNotMatch(r.sitemap,/lastmod|\/login|\/party/);});
test('404 is noindex with a genuine home navigation link',()=>{const h=render404();assert.match(h,/content="noindex"/);assert.match(h,/href="\/"/);});
test('Vercel host rule protects noncanonical aliases from indexing',async()=>{const v=JSON.parse(await readFile(new URL('../vercel.json',import.meta.url),'utf8'));const host=v.headers.find(h=>h.missing?.some(m=>m.type==='host'));assert.ok(new RegExp(host.missing[0].value).test(new URL(base.origin).hostname));assert.equal(new RegExp(host.missing[0].value).test('queue-mateXcom'),false);assert.ok(host.headers.some(h=>h.key==='X-Robots-Tag'&&h.value.includes('noindex')));});
test('there are no third-party trackers, runtime font downloads, or app imports',async()=>{const h=renderSite(draft()).html;const css=await readFile(new URL('../src/site.css',import.meta.url),'utf8');assert.doesNotMatch(h,/googletagmanager|google-analytics|fbq\(|<script src=/);assert.doesNotMatch(css,/@import|@font-face|url\(https/);});
test('unknown icon names fail explicitly',()=>{assert.throws(()=>icon('missing-icon'),/Unknown icon/);});
test('retained legacy share image is a complete 1200 by 630 WebP container',async()=>{
  const image=await readFile(new URL('../public/assets/og-cover.webp',import.meta.url));
  assert.equal(image.toString('ascii',0,4),'RIFF');
  assert.equal(image.toString('ascii',8,12),'WEBP');
  assert.equal(image.readUInt32LE(4)+8,image.length);
  assert.equal(image.toString('ascii',12,16),'VP8 ');
  assert.equal(image.readUInt16LE(26)&0x3fff,1200);
  assert.equal(image.readUInt16LE(28)&0x3fff,630);
});
test('approved prelaunch can be indexed without enabling the app',()=>{
  const r=renderSite(approved(),{VERCEL_ENV:'production'});
  assert.equal(r.mode.indexable,true);
  assert.match(r.html,/서비스 준비 중/);
  assert.doesNotMatch(r.html,/href="https:\/\/app.queue-mate.com/);
});
test('unknown Vercel environments fail closed',()=>{
  assert.equal(resolveMode(approved(),{VERCEL_ENV:'staging',BUILD_TARGET:'production'}).indexable,false);
});
test('only the exact canonical host is exempt from noindex',async()=>{
  const v=JSON.parse(await readFile(new URL('../vercel.json',import.meta.url),'utf8'));
  const rule=v.headers.find(h=>h.missing?.some(m=>m.type==='host'));
  for(const h of ['www.queue-mate.com','queue-mate.com.example.org','preview.vercel.app','xqueue-mate.com']) assert.equal(new RegExp(rule.missing[0].value).test(h),false);
});
test('primary button endpoints meet 4.5 to 1 white text contrast',async()=>{
  const css=await readFile(new URL('../src/site.css',import.meta.url),'utf8');
  function luminance(hex){const rgb=[0,2,4].map(i=>parseInt(hex.slice(i,i+2),16)/255).map(c=>c<=0.04045?c/12.92:((c+0.055)/1.055)**2.4);return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];}
  for(const hex of ['6f2cff','8241db','7b3bff','8946e3']){assert.ok(css.includes('#'+hex));assert.ok(1.05/(luminance(hex)+.05)>=4.5);}
});

test('keyword metadata focuses on team finding without a keyword list',()=>{
  assert.equal(base.title,'롤 듀오 찾기·파티 구하기 | 큐메이트');
  assert.match(base.description,/롤 같이 할 사람/);
  assert.doesNotMatch(base.description,/서비스 준비 중/);
  assert.doesNotMatch(renderSite(draft()).html,/<meta\s+name="keywords"/i);
});
test('search and sharing metadata use the same escaped title and description',()=>{
  const h=renderSite(draft()).html;
  assert.ok(h.includes(`<title>${escapeHtml(base.title)}</title>`));
  for(const [attribute,key,value] of [
    ['name','description',base.description],
    ['property','og:title',base.title],
    ['property','og:description',base.description],
    ['name','twitter:title',base.title],
    ['name','twitter:description',base.description],
  ]) assert.ok(h.includes(`<meta ${attribute}="${key}" content="${escapeHtml(value)}">`),key);
});
test('metadata-only edits preserve the entire approved body byte for byte',()=>{
  const current=draft();
  const previous={...current,
    title:'롤 듀오 구하기·자동 매칭·파티 음성 채팅 | 큐메이트',
    description:'조건에 맞는 게임 팀원을 자동 매칭하거나 모집방의 멤버와 빈자리를 보고 참여하세요. 모집부터 같은 방에서의 음성·텍스트 대화까지, 큐메이트 한 화면에서 이어집니다. 현재 서비스를 준비하고 있습니다.',
  };
  const body=h=>{const match=h.match(/<body>[\s\S]*<\/body>/);assert.ok(match);return match[0];};
  for(const env of [{VERCEL_ENV:'preview'},{VERCEL_ENV:'production'}]) {
    assert.equal(body(renderSite(current,env).html),body(renderSite(previous,env).html));
  }
});
test('keyword copy preserves canonical, preview exclusion and prelaunch CTA',()=>{
  const r=renderSite(base,{VERCEL_ENV:'preview'});
  assert.ok(r.html.includes('<link rel="canonical" href="https://queue-mate.com/">'));
  assert.equal(base.appUrl,'https://app.queue-mate.com/login');
  assert.match(r.html,/name="robots" content="noindex, nofollow"/);
  assert.equal(r.sitemap,null);
  assert.match(r.html,/href="https:\/\/app.queue-mate.com\/login" data-cta="start-matching"/);
  assert.doesNotMatch(r.html,/name="google-site-verification"|name="naver-site-verification"|name="msvalidate.01"/);
});
