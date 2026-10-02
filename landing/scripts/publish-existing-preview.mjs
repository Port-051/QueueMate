/** One-time, scoped access change. No deployments, DNS, billing, or search-index changes. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdir, readFile, writeFile, rm} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {stripVTControlCharacters} from 'node:util';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';

export const TARGET = Object.freeze({
  repository: 'Port-051/QueueMate',
  teamId: 'team_o9fkGOIMeATDLtllTawvXtMy',
  teamSlug: 'port-051',
  projectName: 'q-mate-landing-36418820590',
  host: 'q-mate-landing-36418820590-cerit5nsr-port-051.vercel.app',
  sourceSha: 'f63b93e96d7e8982157268ad674a2ae4fec21a91',
});
export function assertTarget(project, deployment, list, domains) {
  assert.equal(project.name, TARGET.projectName, 'Unexpected project name');
  assert.equal(project.accountId, TARGET.teamId, 'Unexpected project owner');
  assert.match(project.id, /^prj_/, 'Missing project ID');
  assert.equal(deployment.projectId, project.id, 'Deployment belongs to another project');
  assert.equal(deployment.url, TARGET.host, 'Unexpected deployment hostname');
  assert.equal(deployment.meta?.githubCommitSha, TARGET.sourceSha, 'Unexpected deployed source');
  assert.equal(deployment.readyState, 'READY', 'Deployment is not ready');
  // Vercel classifies a new project's first deployment as production, even when
  // preview was requested. Identity, source, singleton and domain guards below
  // still apply: this is NOT permission to publish an arbitrary production app.
  // https://vercel.com/docs/domains/working-with-domains/deploying-and-redirecting
  assert.ok([undefined,null,'preview','production'].includes(deployment.target), 'Unknown deployment target');
  assert.ok(Array.isArray(list.deployments), 'Deployment list missing');
  assert.equal(list.deployments.length, 1, 'Refusing to expose other deployments');
  assert.equal(list.deployments[0].uid, deployment.id, 'Deployment list differs from target');
  assert.ok(!list.pagination?.next, 'Deployment list is incomplete');
  assert.ok(Array.isArray(domains.domains), 'Domain list missing');
  assert.ok(!domains.pagination?.next, 'Domain list is incomplete');
  assert.ok(domains.domains.every(d => typeof d.name === 'string' && d.name.endsWith('.vercel.app')), 'Custom domains are outside this change');
  assert.ok(!project.passwordProtection && !project.trustedIps, 'Other protection settings require separate review');
  if (project.ssoProtection) {
    assert.ok(['all','preview','prod_deployment_urls_and_all_previews'].includes(project.ssoProtection.deploymentType), 'Unknown protection mode');
  }
}
export function isPublicPreview(status, headers, html) {
  const metas = html.match(/<meta\b[^>]*>/gi) ?? [];
  const robots = metas.filter(t => /name\s*=\s*["']robots["']/i.test(t));
  return status === 200 && /text\/html/i.test(headers.get('content-type') ?? '') &&
    /<html\b[^>]*lang=["']ko["']/i.test(html) && /롤 듀오/.test(html) && /QueueMate/.test(html) &&
    robots.some(t => /content\s*=\s*["'][^"']*\bnoindex\b/i.test(t));
}
export function approvalUrl(output) {
  const match = stripVTControlCharacters(output).match(/https:\/\/vercel\.com\/oauth\/device\?user_code=[A-Z0-9-]+/);
  return match?.[0] ?? null;
}
async function main() {
  assert.equal(process.env.GITHUB_REPOSITORY, TARGET.repository, 'Unexpected repository');
  assert.equal(process.env.GITHUB_REF, 'refs/heads/feat/seo-landing', 'Unexpected branch');
  const temp = process.env.RUNNER_TEMP;
  assert.ok(temp?.startsWith('/'), 'An absolute runner temp directory is required');
  const cli = `${temp}/qmate-vercel-cli/node_modules/.bin/vercel`;
  const auth = `${temp}/qmate-public-preview-auth`;
  const auditDir = `${temp}/qmate-public-audit`;
  const sha = process.env.GITHUB_SHA;
  const runUrl = `https://github.com/${TARGET.repository}/actions/runs/${process.env.GITHUB_RUN_ID}`;
  const siteUrl = `https://${TARGET.host}/`;
  const childEnv = Object.fromEntries(['PATH','HOME','TMPDIR','RUNNER_TEMP','NODE_EXTRA_CA_CERTS'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
  Object.assign(childEnv, {CI:'1',NO_COLOR:'1',VERCEL_TELEMETRY_DISABLED:'1'});
  const report = {checkedAt:new Date().toISOString(), deploymentUrl:siteUrl, sourceSha:TARGET.sourceSha, changed:false, verified:false};
  let project, oldProtection, attempted = false;
  await mkdir(auth, {recursive:true,mode:0o700});
  await mkdir(auditDir, {recursive:true});
  async function status(state, description, target_url = runUrl) {
    const response = await fetch(`https://api.github.com/repos/${TARGET.repository}/statuses/${sha}`, {
      method:'POST', redirect:'error', signal:AbortSignal.timeout(15000),
      headers:{Authorization:`Bearer ${process.env.GITHUB_TOKEN}`,Accept:'application/vnd.github+json','Content-Type':'application/json'},
      body:JSON.stringify({state,context:'SEO preview publication',description:description.slice(0,140),target_url}),
    });
    assert.ok(response.ok, `Could not publish GitHub status (${response.status})`);
    await response.body?.cancel();
  }
  function execute(command, args, timeoutMs=45000, onOutput=()=>{}) {
    return new Promise((resolve,reject)=>{
      const child=spawn(command,args,{env:childEnv,stdio:['ignore','pipe','pipe']});
      let stdout='',stderr='',timedOut=false;
      const timer=setTimeout(()=>{timedOut=true;child.kill('SIGKILL');},timeoutMs);
      child.stdout.on('data',d=>{stdout+=d;onOutput(stdout+'\n'+stderr);});
      child.stderr.on('data',d=>{stderr+=d;onOutput(stdout+'\n'+stderr);});
      child.on('error',e=>{clearTimeout(timer);reject(e);});
      child.on('close',code=>{clearTimeout(timer);code===0&&!timedOut?resolve(stdout):reject(Error(timedOut?'Command timed out':`Command ${args[0]} exited ${code}; raw output withheld`));});
    });
  }
  const globalArgs=['--global-config',auth,'--no-color'];
  async function api(path, method='GET', body) {
    assert.ok(path.startsWith('/v9/projects/') || path.startsWith('/v13/deployments/') || path.startsWith('/v7/deployments?'), 'API route outside scope');
    assert.ok(['GET','PATCH'].includes(method), 'Only read and scoped update are allowed');
    if(method==='PATCH')assert.equal(path, `/v9/projects/${project.id}`, 'Only the verified project may change');
    const args=['api',path+(path.includes('?')?'&':'?')+`teamId=${TARGET.teamId}`,'-X',method,'--scope',TARGET.teamSlug];
    if(body!==undefined){
      assert.deepEqual(Object.keys(body),['ssoProtection'], 'Only Vercel Authentication may change');
      const input=`${auth}/request.json`;await writeFile(input,JSON.stringify(body),{mode:0o600});args.push('--input',input);
    }
    const text=await execute(cli,[...args,...globalArgs]);
    return JSON.parse(stripVTControlCharacters(text).trim());
  }
  async function probe() {
    const response=await fetch(siteUrl,{redirect:'manual',signal:AbortSignal.timeout(15000)});
    const html=await response.text();
    return {ok:isPublicPreview(response.status,response.headers,html),status:response.status};
  }
  async function verify() {
    const output=await execute(process.execPath,['landing/scripts/check-url.mjs',siteUrl,'preview'],150000);
    await writeFile(`${auditDir}/http-checks.txt`,output);
    console.log(output);
    const assets=[];
    for(const path of ['/assets/site.css','/assets/queuemate-wordmark.svg','/assets/og-cover.webp']) {
      const r=await fetch(new URL(path,siteUrl),{redirect:'manual',signal:AbortSignal.timeout(15000)});
      assert.equal(r.status,200,`Asset not public: ${path}`);
      const bytes=Buffer.from(await r.arrayBuffer());
      const local=await readFile(`landing/dist${path}`);
      const hash=b=>createHash('sha256').update(b).digest('hex');
      assert.equal(hash(bytes),hash(local),`Deployed asset differs: ${path}`);
      assets.push({path,sha256:hash(bytes),bytes:bytes.length});
    }
    report.assets=assets;report.verified=true;
  }
  try {
    const config=JSON.parse(await readFile('landing/site.config.json','utf8'));
    assert.equal(config.allowIndexing,false,'Search indexing must remain disabled');
    assert.equal(config.appReady,false,'App must remain disconnected');
    const initial=await probe();report.initialStatus=initial.status;
    if(!initial.ok) {
      await status('pending','Preparing authorization for existing landing access settings');
      let published=false,publication=Promise.resolve();
      await execute(cli,['login',...globalArgs],600000,output=>{
        const url=approvalUrl(output);
        if(published||!url)return;
        published=true;
        publication=status('pending','Approve to publish only the existing QueueMate landing',url);
        publication.catch(()=>{});
      });
      await publication;
      await status('pending','Verifying exact project, deployment and change scope');
      project=await api(`/v9/projects/${TARGET.projectName}`);
      const deployment=await api(`/v13/deployments/${TARGET.host}`);
      const list=await api(`/v7/deployments?projectId=${encodeURIComponent(project.id)}&limit=2`);
      const domains=await api(`/v9/projects/${project.id}/domains`);
      // Save only nonsecret scope facts so a failed guard is diagnosable.
      report.scope={projectId:project.id,projectName:project.name,accountId:project.accountId,
        deploymentId:deployment.id,target:deployment.target ?? null,readyState:deployment.readyState,
        sourceSha:deployment.meta?.githubCommitSha,deployments:list.deployments?.map(d=>({id:d.uid,url:d.url})),
        domains:domains.domains?.map(d=>d.name)};
      await writeFile(`${auditDir}/report.json`,JSON.stringify(report,null,2)+'\n');
      assertTarget(project,deployment,list,domains);
      report.projectId=project.id;
      oldProtection=project.ssoProtection ? {deploymentType:project.ssoProtection.deploymentType} : null;
      if(oldProtection) {
        attempted=true;
        await api(`/v9/projects/${project.id}`,'PATCH',{ssoProtection:null});
        const updated=await api(`/v9/projects/${project.id}`);
        assert.equal(updated.ssoProtection ?? null,null,'Access setting was not updated');
        report.changed=true;
      }
      let ready=false;
      for(let i=0;i<15;i++){if((await probe()).ok){ready=true;break;}await delay(2000);}
      assert.ok(ready,'Expected anonymous noindex landing is not accessible');
    }
    await verify();
    await status('success','PUBLIC_PREVIEW_VERIFIED; assets and 404 checked; noindex retained',siteUrl);
  } catch(error) {
    report.error=error.message;
    if(attempted&&!report.verified) {
      try {
        const current=await api(`/v9/projects/${project.id}`);
        if(current.ssoProtection==null)await api(`/v9/projects/${project.id}`,'PATCH',{ssoProtection:oldProtection});
        const restored=await api(`/v9/projects/${project.id}`);
        report.restored=JSON.stringify(restored.ssoProtection ? {deploymentType:restored.ssoProtection.deploymentType}:null)===JSON.stringify(oldProtection);
      } catch {report.restored=false;}
    }
    await status('failure',report.restored===false?'Publication check failed; protection restoration needs attention':error.message).catch(()=>{});
    console.error(report.error);process.exitCode=1;
  } finally {
    await execute(cli,['logout',...globalArgs],20000).catch(()=>{});
    await rm(auth,{recursive:true,force:true});
    report.finishedAt=new Date().toISOString();
    await writeFile(`${auditDir}/report.json`,JSON.stringify(report,null,2)+'\n');
    await writeFile(process.env.GITHUB_STEP_SUMMARY,`## Existing landing publication\n\n${siteUrl}\n\nVerified: ${report.verified}\nAccess setting changed: ${report.changed}\nRestored on failure: ${report.restored ?? 'not needed'}\n\nNo deployments, custom domains, DNS, indexing, or app connection changed.\n`);
  }
}
if(process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error=>{console.error(error.message);process.exitCode=1;});
}
