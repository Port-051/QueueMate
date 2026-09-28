import test from 'node:test';
import assert from 'node:assert/strict';
import {TARGET,assertTarget,isPublicPreview,approvalUrl} from '../scripts/publish-existing-preview.mjs';
const fixture=()=>[
  {id:'prj_test',name:TARGET.projectName,accountId:TARGET.teamId,ssoProtection:{deploymentType:'preview'}},
  {id:'dpl_test',projectId:'prj_test',url:TARGET.host,meta:{githubCommitSha:TARGET.sourceSha},readyState:'READY',target:null},
  {deployments:[{uid:'dpl_test'}],pagination:{next:null}},
  {domains:[{name:'q-mate-example.vercel.app'}]},
];
test('only the known isolated preview passes the guard',()=>assert.doesNotThrow(()=>assertTarget(...fixture())));
for(const [name,change] of [
  ['other team',f=>f[0].accountId='team_other'],
  ['other project',f=>f[0].name='matching-app'],
  ['other deployment project',f=>f[1].projectId='prj_other'],
  ['other deployment URL',f=>f[1].url='example.vercel.app'],
  ['other source code',f=>f[1].meta.githubCommitSha='unknown'],
  ['not ready',f=>f[1].readyState='ERROR'],
  ['production',f=>f[1].target='production'],
  ['extra deployment',f=>f[2].deployments.push({uid:'dpl_other'})],
  ['incomplete deployment list',f=>f[2].pagination.next=123],
  ['custom domain',f=>f[3].domains.push({name:'q-mate.com'})],
  ['incomplete domain list',f=>f[3].pagination={next:123}],
  ['password protection',f=>f[0].passwordProtection={deploymentType:'all'}],
  ['unknown SSO mode',f=>f[0].ssoProtection={deploymentType:'unknown'}],
])test(`publication rejects ${name}`,()=>{const f=fixture();change(f);assert.throws(()=>assertTarget(...f));});
const h=new Headers({'content-type':'text/html; charset=utf-8'});
const html='<html lang="ko"><meta content="noindex, nofollow" name="robots"><title>롤 듀오 | QueueMate</title>';
test('public preview is recognized without following login redirects',()=>{assert.equal(isPublicPreview(200,h,html),true);assert.equal(isPublicPreview(302,h,html),false);assert.equal(isPublicPreview(200,h,'<title>Login to Vercel</title>'),false);});
test('indexable HTML is never accepted for this operation',()=>assert.equal(isPublicPreview(200,h,html.replace('noindex','index')),false));
test('only official device authorization URLs are published',()=>{assert.equal(approvalUrl('Open https://vercel.com/oauth/device?user_code=ABCD-EFGH'),'https://vercel.com/oauth/device?user_code=ABCD-EFGH');assert.equal(approvalUrl('https://vercel.com.evil.test/oauth/device?user_code=ABCD-EFGH'),null);assert.equal(approvalUrl('https://evil.test/oauth/device?user_code=ABCD-EFGH'),null);});
