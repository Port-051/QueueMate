/** Export a single-file review copy. Production builds still use separate cacheable assets. */
import {readFile,writeFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const root=new URL('../',import.meta.url);
const config=JSON.parse(await readFile(new URL('site.config.json',root),'utf8'));
const css=await readFile(new URL('src/site.css',root),'utf8');
const svg=await readFile(new URL('public/assets/queuemate-wordmark.svg',root));
let html=renderSite(config,{}).html;
html=html.replace('<link rel="stylesheet" href="/assets/site.css">',`<style>${css}</style>`);
html=html.replaceAll('src="/assets/queuemate-wordmark.svg"',`src="data:image/svg+xml;base64,${svg.toString('base64')}"`);
await writeFile(new URL('preview.html',root),html);
console.log('Single-file preview → preview.html (always noindex; do not deploy this review copy)');
