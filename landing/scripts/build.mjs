import {readFile,writeFile,mkdir,cp,rm,access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {renderSite,render404} from '../src/render.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
try {
  const c = JSON.parse(await readFile(path.join(root,'site.config.json'),'utf8'));
  const rendered = renderSite(c, process.env);
  if(c.media?.ogImage) await access(path.join(root,'public',c.media.ogImage));
  const out=path.join(root,'dist');
  // Only generated output inside this package is cleared; the app/repo is untouched.
  await rm(out,{recursive:true,force:true}); await mkdir(out,{recursive:true});
  await cp(path.join(root,'public'),out,{recursive:true});
  await cp(path.join(root,'src/site.css'),path.join(out,'assets/site.css'));
  await writeFile(path.join(out,'index.html'),rendered.html);
  await writeFile(path.join(out,'404.html'),render404());
  await writeFile(path.join(out,'robots.txt'),rendered.robots);
  await writeFile(path.join(out,'llms.txt'),rendered.llms);
  if(rendered.sitemap) await writeFile(path.join(out,'sitemap.xml'),rendered.sitemap);
  console.log(`Build OK: ${rendered.mode.indexable ? '검색 공개' : 'NOINDEX'} / 앱 ${c.appReady ? '연결' : '연결 전'} / UI 검토 ${c.uiApproved ? '승인' : '대기'}`);
} catch(error) {console.error(`Build failed: ${error.message}`);process.exitCode=1;}
