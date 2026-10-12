import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, readFile, rm, symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRelease, validateLocal, safePath, digest} from '../release.mjs';
import {verifyRelease, publicPath} from '../verify.mjs';
import {awsTarget, vercelProject, vercelDeployment} from '../targets.mjs';
import {outputConfig} from '../vercel-output.mjs';

const sha = 'a'.repeat(40);
async function fixture(t) {
  const dir = await mkdtemp(path.join(tmpdir(), 'queuemate-test-'));
  t.after(() => rm(dir, {recursive:true, force:true}));
  await mkdir(`${dir}/brand`);
  await writeFile(`${dir}/index.html`, '<html>release</html>');
  await writeFile(`${dir}/brand/queuemate-mark.svg`, '<svg/>');
  const release = await createRelease(dir, sha, 'app');
  const request = async pathname => ({status:200, bytes:await readFile(dir + (pathname === '/' ? '/index.html' : pathname))});
  return {dir, release, request};
}

test('manifest and public verification accept the exact tested bytes', async t => {
  const {dir, release, request} = await fixture(t);
  assert.deepEqual(await validateLocal(dir, sha), release);
  assert.equal((await verifyRelease('https://app.queue-mate.com', release, request)).verified, true);
});

test('wrong commit and corrupted artifacts cannot be deployed', async t => {
  const {dir} = await fixture(t);
  await assert.rejects(validateLocal(dir, 'b'.repeat(40)), /another commit/);
  await writeFile(`${dir}/brand/queuemate-mark.svg`, '<svg>old</svg>');
  await assert.rejects(validateLocal(dir, sha), /Artifact corrupted/);
});

test('empty manifest is not a successful release', async t => {
  const {dir} = await fixture(t);
  await writeFile(`${dir}/release.json`, JSON.stringify({schema:1, sha, kind:'app', files:{}}));
  await assert.rejects(validateLocal(dir, sha), /Index missing/);
});

test('unsafe file paths and symlinks are rejected', async t => {
  for (const value of ['../env', '/index.html', '.env', 'a/../b', 'a//b', 'a?x=1', 'a\\b']) assert.throws(() => safePath(value));
  const {dir} = await fixture(t);
  await symlink(`${dir}/index.html`, `${dir}/linked.html`);
  await assert.rejects(createRelease(dir, sha, 'app'), /symlinks/);
});

test('old live commit fails even if HTTP is successful', async t => {
  const {release, request} = await fixture(t);
  const stale = async pathname => pathname === '/release.json' ? {status:200, bytes:Buffer.from(JSON.stringify({...release, sha:'b'.repeat(40)}))} : request(pathname);
  await assert.rejects(verifyRelease('https://app.queue-mate.com', release, stale), /different commit/);
});

test('current manifest cannot hide stale logo or an HTML fallback', async t => {
  const {release, request} = await fixture(t);
  const stale = async pathname => pathname.endsWith('.svg') ? {status:200, bytes:Buffer.from('<html>fallback</html>')} : request(pathname);
  await assert.rejects(verifyRelease('https://app.queue-mate.com', release, stale), /Live bytes differ/);
});

test('redirects do not count as serving the tested file', async t => {
  const {release, request} = await fixture(t);
  const redirect = async pathname => pathname === '/' ? {status:307, bytes:Buffer.from('')} : request(pathname);
  await assert.rejects(verifyRelease('https://app.queue-mate.com', release, redirect), /HTTP 307/);
});

test('landing clean URLs and 404 preserve hashes', async () => {
  const files = {'index.html':Buffer.from('home'), 'privacy.html':Buffer.from('privacy'), '404.html':Buffer.from('missing')};
  const release = {schema:1, sha, kind:'landing', files:Object.fromEntries(Object.entries(files).map(([k,v]) => [k,digest(v)]))};
  const mapped = Object.fromEntries(Object.entries(files).map(([k,v]) => [publicPath(k, 'landing'),v]));
  const result = await verifyRelease('https://queue-mate.com', release, async url => url === '/release.json' ? {status:200,bytes:Buffer.from(JSON.stringify(release))} : {status:url === '/404' ? 404 : 200,bytes:mapped[url]});
  assert.equal(result.files, 3);
});

const distribution = {Distribution:{DistributionConfig:{Enabled:true, Aliases:{Items:['app.queue-mate.com']}, Origins:{Items:[{Id:'app',DomainName:'qmate.s3.ap-northeast-2.amazonaws.com',OriginPath:'/web'}]},DefaultCacheBehavior:{TargetOriginId:'app'}}}};
test('AWS target guard binds domain, default origin, bucket and prefix', () => {
  awsTarget(distribution, 'qmate', 'web');
  assert.throws(() => awsTarget(distribution, 'another-bucket', 'web'));
  assert.throws(() => awsTarget(distribution, 'qmate', ''));
  const wrong = structuredClone(distribution);
  wrong.Distribution.DistributionConfig.Aliases.Items = ['some-other-app.com'];
  assert.throws(() => awsTarget(wrong, 'qmate', 'web'));
});

test('Vercel target guard rejects other teams, projects and failed deployments', () => {
  const env = {VERCEL_PROJECT_ID:'prj_expected', VERCEL_ORG_ID:'team_expected'};
  const project = {id:'prj_expected',accountId:'team_expected',name:'queue-mate-landing'};
  vercelProject(project, env);
  assert.throws(() => vercelProject({...project,accountId:'team_wrong'}, env));
  assert.throws(() => vercelDeployment({id:'dpl_abc',projectId:'prj_other',readyState:'READY'}, env.VERCEL_PROJECT_ID));
  assert.throws(() => vercelDeployment({id:'dpl_abc',projectId:env.VERCEL_PROJECT_ID,readyState:'ERROR'}, env.VERCEL_PROJECT_ID));
});

test('prebuilt routing keeps clean HTML URLs, canonical headers and 404 status', async () => {
  const source = JSON.parse(await readFile(new URL('../../../landing/vercel.json', import.meta.url), 'utf8'));
  const config = outputConfig(source, {'index.html':'hash','privacy.html':'hash','404.html':'hash','assets/icon.svg':'hash'});
  assert.equal(config.overrides['privacy.html'].path, 'privacy');
  assert.equal(config.overrides['index.html'].path, 'index');
  assert.equal(config.overrides['assets/icon.svg'], undefined);
  assert.deepEqual(config.routes.at(-1), {src:'/(.*)',dest:'/404',status:404});
  assert.ok(config.routes.some(r => r.headers?.['X-Content-Type-Options'] === 'nosniff'));
  assert.ok(config.routes.some(r => r.status === 308));
});


test('AWS role grants only this repository environment and frontend resources', async () => {
  const template = JSON.parse(await readFile(new URL('../../../infra/deploy/aws-role.json', import.meta.url), 'utf8'));
  const role = template.Resources.DeployRole.Properties;
  const trust = role.AssumeRolePolicyDocument.Statement[0];
  assert.equal(trust.Condition.StringEquals['token.actions.githubusercontent.com:sub'], 'repo:Port-051@293762925/QueueMate@1350658456:environment:production-app');
  assert.equal(trust.Condition.StringEquals['token.actions.githubusercontent.com:aud'], 'sts.amazonaws.com');
  for (const statement of role.Policies[0].PolicyDocument.Statement) {
    assert.notEqual(statement.Resource, '*');
    const actions = [statement.Action].flat();
    assert.ok(actions.every(a => !a.includes('*') && !/Delete|PutBucketPolicy|iam:/.test(a)));
  }
});
