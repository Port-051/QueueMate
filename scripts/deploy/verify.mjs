import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import {digest, safePath, validateLocal} from './release.mjs';
import {command} from './process.mjs';

export function publicPath(name, kind) {
  safePath(name);
  if (name === 'index.html') return '/';
  return '/' + (kind === 'landing' ? name.replace(/\.html$/, '') : name);
}
export async function verifyRelease(origin, release, request) {
  const marker = await request('/release.json');
  assert.equal(marker.status, 200, 'Release marker missing');
  const current = JSON.parse(marker.bytes.toString());
  assert.equal(current.schema, release.schema);
  assert.equal(current.kind, release.kind);
  assert.equal(current.sha, release.sha, 'Old or different commit is being served');
  assert.deepEqual(current.files, release.files, 'Release manifest differs');
  // Checking normal URLs catches stale CDN caches; no cache-busting query here.
  for (const [name, hash] of Object.entries(release.files)) {
    const response = await request(publicPath(name, release.kind));
    assert.ok(response.status === 200 || (name === '404.html' && response.status === 404), `HTTP ${response.status}: ${name}`);
    assert.equal(digest(response.bytes), hash, `Live bytes differ: ${name}`);
  }
  return {origin, sha:release.sha, files:Object.keys(release.files).length, verified:true};
}
export async function verifySite(origin, directory, sha, {vercel=false, attempts=12} = {}) {
  const url = new URL(origin);
  assert.equal(url.protocol, 'https:');
  assert.equal(url.origin, origin);
  assert.ok(['queue-mate.com','app.queue-mate.com'].includes(url.hostname) || (vercel && url.hostname.endsWith('.vercel.app')));
  const release = await validateLocal(directory, sha);
  const temporary = vercel ? await mkdtemp(path.join(tmpdir(), 'qmate-http-')) : null;
  const request = async pathname => {
    if (vercel) {
      const file = path.join(temporary, 'body');
      const status = await command('vercel', ['curl', pathname, '--deployment', origin, '--scope', process.env.VERCEL_SCOPE, '--', '--silent', '--show-error', '--max-time', '30', '--output', file, '--write-out', '%{http_code}']);
      return {status:Number(status), bytes:await readFile(file)};
    }
    const response = await fetch(origin + pathname, {redirect:'manual', headers:{'Cache-Control':'no-cache'}, signal:AbortSignal.timeout(30000)});
    return {status:response.status, bytes:Buffer.from(await response.arrayBuffer())};
  };
  try {
    for (let attempt = 1; attempt <= attempts; attempt++) {
      try { return await verifyRelease(origin, release, request); }
      catch (error) {
        if (attempt === attempts) throw error;
        console.log(`Waiting for ${origin}: ${error.message}`);
        await delay(10000);
      }
    }
  } finally { if (temporary) await rm(temporary, {recursive:true,force:true}); }
}
export async function saveReport(name, report) {
  await mkdir('.release/reports', {recursive:true});
  await writeFile(`.release/reports/${name}.json`, JSON.stringify(report, null, 2) + '\n');
}
