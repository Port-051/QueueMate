/** Read-only post-deployment smoke test; does not register or index the site. */
import {readFile} from 'node:fs/promises';
const [input,mode='preview']=process.argv.slice(2);
if(!input||!['preview','production'].includes(mode)) throw new Error('npm run check:url -- https://HOST preview|production');
const origin=new URL(input);
if(origin.username||origin.password||origin.pathname!=='/'||origin.search||origin.hash||!(origin.protocol==='https:'||(mode==='preview'&&origin.protocol==='http:'&&origin.hostname==='127.0.0.1'))) throw new Error('Use a bare HTTPS origin (localhost HTTP is allowed only for preview).');
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const canonical=new URL(config.origin).origin+'/';
if(mode==='production'&&origin.origin!==new URL(canonical).origin) throw new Error('Production checks must target the canonical domain.');
const checks=[];
const check=(name,pass)=>checks.push({name,pass:Boolean(pass)});
const get=p=>fetch(new URL(p,origin),{redirect:'manual',signal:AbortSignal.timeout(10000)});
try{
 const home=await get('/'),html=await home.text();
 check('Home is 200 HTML (no login wall)',home.status===200&&/text\/html/.test(home.headers.get('content-type')||''));
 check('Korean H1 and title present',/<html lang="ko">/.test(html)&&(html.match(/<h1\b/g)||[]).length===1&&/<title>롤 듀오/.test(html));
 check('Canonical URL',html.includes(`rel="canonical" href="${canonical}"`));
 check('Indexing mode',mode==='production'?/name="robots" content="index, follow/.test(html)&&!/noindex|\bnone\b/i.test(home.headers.get('x-robots-tag')||''):/name="robots" content="noindex/.test(html));
 const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
 check('No duplicate IDs',new Set(ids).size===ids.length);
 check('Anchor destinations exist',[...html.matchAll(/href="#([^"]+)"/g)].every(m=>ids.includes(m[1])));
 check('Six native FAQs',(html.match(/<details>/g)||[]).length===6);
 try{JSON.parse(html.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/)?.[1]||'');check('Valid JSON-LD',true);}catch{check('Valid JSON-LD',false);}
 const robots=await get('/robots.txt'),text=await robots.text();
 check('Crawlers can read noindex',robots.status===200&&/text\/plain/.test(robots.headers.get('content-type')||'')&&/Allow: \//.test(text)&&!/^Disallow:\s*\/\s*$/m.test(text));
 const sitemap=await get('/sitemap.xml'),xml=await sitemap.text();
 check('Sitemap matches mode',mode==='production'?sitemap.status===200&&xml.includes(`<loc>${canonical}</loc>`)&&text.includes(`Sitemap: ${canonical}sitemap.xml`):sitemap.status===404);
 for(const [p,type] of [['/assets/site.css','text/css'],['/assets/queuemate-wordmark.svg','image/svg+xml'],...(config.media?.ogImage?[[config.media.ogImage,'image/']]:[])]){
  const r=await get(p);check(`Asset ${p}`,r.status===200&&(r.headers.get('content-type')||'').includes(type));await r.arrayBuffer();
 }
 const missing=await get('/__qmate_expected_missing__');
 check('Genuine HTTP 404',missing.status===404);
 check('404 is noindex',/name="robots" content="noindex/.test(await missing.text()));
}catch(error){check(`Network or parsing failure: ${error.message}`,false);}
for(const c of checks) console.log(`${c.pass?'PASS':'FAIL'} ${c.name}`);
console.log(`Checked ${origin.origin} (${mode}). Search rankings and Core Web Vitals are not measured here.`);
if(checks.some(c=>!c.pass))process.exitCode=1;
