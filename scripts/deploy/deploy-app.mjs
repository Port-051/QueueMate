import assert from 'node:assert/strict';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {command} from './process.mjs';
import {validateLocal} from './release.mjs';
import {verifySite, saveReport} from './verify.mjs';
import {browserSmoke} from './browser-smoke.mjs';
import {awsTarget} from './targets.mjs';

const {AWS_S3_BUCKET:bucket, AWS_CLOUDFRONT_DISTRIBUTION_ID:distribution, GITHUB_SHA:sha} = process.env;
const prefix = process.env.AWS_S3_PREFIX || '';
assert.match(bucket || '', /^[a-z0-9][a-z0-9.-]+[a-z0-9]$/);
assert.match(distribution || '', /^[A-Z0-9]+$/);
assert.ok(!prefix || (/^[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*$/.test(prefix)));
const dist = '.release/app', backup = '.release/backup';
const manifest = await validateLocal(dist, sha);
assert.equal(manifest.kind, 'app');
const aws = args => command('aws', args);
const uri = key => `s3://${bucket}/${prefix ? prefix + '/' : ''}${key}`;
const json = async args => JSON.parse(await aws([...args, '--output', 'json']));
const config = await json(['cloudfront', 'get-distribution', '--id', distribution]);
awsTarget(config, bucket, prefix);
const listed = await json(['s3api', 'list-objects-v2', '--bucket', bucket, '--prefix', prefix ? prefix + '/' : '']);
const keys = new Set((listed.Contents || []).map(o => o.Key));
const files = [...Object.keys(manifest.files), 'release.json'];
await mkdir(backup, {recursive:true});
const previous = [];
for (const file of files) {
  if (!keys.has((prefix ? prefix + '/' : '') + file)) continue;
  await mkdir(path.dirname(path.join(backup, file)), {recursive:true});
  await aws(['s3','cp',uri(file),path.join(backup,file),'--only-show-errors']);
  previous.push(file);
}
assert.ok(previous.includes('index.html'), 'Existing production index.html must be backed up first');
const put = (directory, file, key = file) => aws(['s3','cp',path.join(directory,file),uri(key),'--cache-control', /^assets\/[^/]+-[a-zA-Z0-9_-]{8,}\./.test(file) ? 'public,max-age=31536000,immutable' : 'no-cache,max-age=0,must-revalidate','--only-show-errors']);
async function invalidate() {
  const result = await json(['cloudfront','create-invalidation','--distribution-id',distribution,'--paths','/*']);
  await aws(['cloudfront','wait','invalidation-completed','--distribution-id',distribution,'--id',result.Invalidation.Id]);
}
const report = {sha, origin:'https://app.queue-mate.com', deployed:false, verified:false};
let started = false;
try {
  // Retain tested releases. Never delete old hashed files: open browser tabs may use them.
  for (const file of files) await put(dist, file, `_releases/${sha}/${file}`);
  started = true;
  for (const file of files.filter(f => !['index.html','release.json'].includes(f))) await put(dist, file);
  await put(dist, 'index.html');
  await put(dist, 'release.json');
  report.deployed = true;
  await invalidate();
  report.http = await verifySite(report.origin, dist, sha);
  report.browser = await browserSmoke(report.origin, 'app');
  report.verified = true;
} catch (error) {
  report.error = error.message;
  if (started) {
    try {
      for (const file of previous.filter(f => !['index.html','release.json'].includes(f))) await put(backup, file);
      await put(backup, 'index.html');
      if (!previous.includes('release.json')) await writeFile(path.join(backup,'release.json'), JSON.stringify({schema:1, sha:null, status:'rolled_back', failedSha:sha}));
      await put(backup, 'release.json');
      await invalidate();
      const actual = await fetch(report.origin + '/', {signal:AbortSignal.timeout(30000)});
      assert.equal(actual.status, 200);
      assert.equal(Buffer.from(await actual.arrayBuffer()).equals(await readFile(path.join(backup, 'index.html'))), true);
      report.rolledBack = true;
    } catch (rollbackError) { report.rollbackError = rollbackError.message; }
  }
  throw error;
} finally { await saveReport('app', report); }
