import assert from 'node:assert/strict';
import {readFile, mkdir, cp, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {getTransformedRoutes} from '@vercel/routing-utils';

export function outputConfig(source, files) {
  const {routes, error} = getTransformedRoutes({headers:source.headers, cleanUrls:source.cleanUrls, trailingSlash:source.trailingSlash});
  assert.ok(!error, JSON.stringify(error));
  const overrides = Object.fromEntries(Object.keys(files).filter(name => name.endsWith('.html')).map(name => [name, {path:name.replace(/\.html$/, ''), contentType:'text/html; charset=utf-8'}]));
  return {version:3, routes:[...routes, {src:'^/$', dest:'/index'}, {handle:'filesystem'}, {src:'/(.*)', dest:'/404', status:404}], overrides};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [dist, destination] = process.argv.slice(2);
  const source = JSON.parse(await readFile('landing/vercel.json', 'utf8'));
  const release = JSON.parse(await readFile(`${dist}/release.json`, 'utf8'));
  await mkdir(`${destination}/.vercel/output`, {recursive:true});
  await cp(dist, `${destination}/.vercel/output/static`, {recursive:true});
  await writeFile(`${destination}/.vercel/output/config.json`, JSON.stringify(outputConfig(source, release.files), null, 2));
  // The CLI checks this target when --prebuilt --prod is used.
  await writeFile(`${destination}/.vercel/output/builds.json`, JSON.stringify({target:'production'}));
}
