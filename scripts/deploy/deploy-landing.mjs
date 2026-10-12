import assert from 'node:assert/strict';
import {writeFile, mkdir} from 'node:fs/promises';
import {command} from './process.mjs';
import {validateLocal} from './release.mjs';
import {verifySite, saveReport} from './verify.mjs';
import {browserSmoke} from './browser-smoke.mjs';
import {vercelProject, vercelDeployment} from './targets.mjs';

const env = process.env, sha = env.GITHUB_SHA;
for (const key of ['VERCEL_TOKEN','VERCEL_ORG_ID','VERCEL_PROJECT_ID','VERCEL_SCOPE']) assert.ok(env[key], `${key} is not configured`);
const directory = '.release/landing-package', dist = `${directory}/.vercel/output/static`;
assert.equal((await validateLocal(dist, sha)).kind, 'landing');
await mkdir(`${directory}/.vercel`, {recursive:true});
await writeFile(`${directory}/.vercel/project.json`, JSON.stringify({projectId:env.VERCEL_PROJECT_ID,orgId:env.VERCEL_ORG_ID,projectName:'queue-mate-landing'}));
const vc = args => command('vercel', [...args, '--cwd', directory, '--scope', env.VERCEL_SCOPE, '--non-interactive']);
const api = async endpoint => JSON.parse(await vc(['api', endpoint + (endpoint.includes('?') ? '&' : '?') + 'teamId=' + env.VERCEL_ORG_ID, '--method','GET']));
const currentProduction = async () => {
  const alias = await api('/v4/aliases/queue-mate.com');
  assert.equal(alias.projectId, env.VERCEL_PROJECT_ID);
  assert.match(alias.deploymentId, /^dpl_[a-zA-Z0-9]+$/);
  return api(`/v13/deployments/${alias.deploymentId}`);
};
vercelProject(await api(`/v9/projects/${env.VERCEL_PROJECT_ID}`), env);
const domains = await api(`/v9/projects/${env.VERCEL_PROJECT_ID}/domains?limit=100`);
assert.ok(!domains.pagination?.next, 'Incomplete domain list');
assert.ok(domains.domains?.some(d => d.name === 'queue-mate.com' && d.verified && !d.gitBranch), 'Production domain mismatch');
const previous = await currentProduction();
vercelDeployment(previous, env.VERCEL_PROJECT_ID);
const report = {sha, origin:'https://queue-mate.com', previous:previous.id, deployed:false, verified:false};
let staged, promoted = false;
try {
  const output = await vc(['deploy','--prebuilt','--prod','--skip-domain','--yes','--meta',`githubCommitSha=${sha}`]);
  const urls = output.match(/https:\/\/[a-zA-Z0-9.-]+\.vercel\.app/g);
  assert.ok(urls?.length, 'Deployment URL missing');
  const url = urls.at(-1);
  staged = await api(`/v13/deployments/${new URL(url).hostname}`);
  vercelDeployment(staged, env.VERCEL_PROJECT_ID);
  assert.equal(staged.meta?.githubCommitSha, sha);
  report.staged = staged.id;
  await verifySite(url, dist, sha, {vercel:true, attempts:3});
  assert.equal((await currentProduction()).id, previous.id, 'Concurrent production change detected');
  promoted = true;
  await vc(['promote', url, '--yes']);
  report.deployed = true;
  report.http = await verifySite(report.origin, dist, sha);
  report.browser = await browserSmoke(report.origin, 'landing');
  report.verified = true;
} catch (error) {
  report.error = error.message;
  if (promoted) {
    try {
      const current = await currentProduction();
      assert.ok(current.id === staged.id || current.id === previous.id, 'Another release owns the domain; do not roll it back');
      if (current.id === staged.id) await vc(['rollback', `https://${previous.url}`, '--yes']);
      assert.equal((await currentProduction()).id, previous.id);
      report.rolledBack = true;
    } catch (rollbackError) { report.rollbackError = rollbackError.message; }
  }
  throw error;
} finally { await saveReport('landing', report); }
