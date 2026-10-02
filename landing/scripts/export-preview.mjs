/** Export a single-file review copy. Production builds still use separate cacheable assets. */
import {readFile,writeFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const root=new URL('../',import.meta.url);
const config=JSON.parse(await readFile(new URL('site.config.json',root),'utf8'));
const css=await readFile(new URL('src/site.css',root),'utf8');
const conciseCss=await readFile(new URL('public/assets/concise.css',root),'utf8');

let html=renderSite(config,{}).html;
const productCss=await readFile(new URL('public/assets/product-ui.css',root),'utf8');
html=html.replace('<link rel="stylesheet" href="/assets/product-ui.css">',`<style>${productCss}</style>`);
html=html.replace('<link rel="stylesheet" href="/assets/concise.css">',`<style>${conciseCss}</style>`);
html=html.replace('<link rel="stylesheet" href="/assets/site.css">',`<style>${css}</style>`);
const images = [...new Set([...html.matchAll(/src="(\/assets\/[a-zA-Z0-9_./-]+\.(?:svg|webp|png))"/g)].map(m=>m[1]))];
for (const file of images) {
  if (file.includes('..')) throw new Error('Unsafe review image path');
  const bytes=await readFile(new URL('public'+file,root));
  const type=file.endsWith('.svg')?'image/svg+xml':file.endsWith('.png')?'image/png':'image/webp';
  const uri=`data:${type};base64,${bytes.toString('base64')}`;
  html=html.replaceAll(`src="${file}"`,`src="${uri}"`).replaceAll(`href="${file}"`,`href="${uri}"`);
}
await writeFile(new URL('preview.html',root),html);
console.log('Single-file preview → preview.html (always noindex; do not deploy this review copy)');
