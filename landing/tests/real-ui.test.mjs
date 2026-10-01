import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {renderSite} from '../src/render.mjs';
const root=new URL('../',import.meta.url);
const config=JSON.parse(await readFile(new URL('site.config.json',root),'utf8'));
const manifest=JSON.parse(await readFile(new URL('public/assets/ui/capture-manifest.json',root),'utf8'));
const html=renderSite(config).html;
test('UI reference is the requested exact source, not the old illustration',()=>{
  assert.equal(config.reference.branch,'feature/quick-match-ui');
  assert.equal(config.reference.commit,'904cce415181aeb8a8802fc398f9be9e0835b1fe');
  assert.equal(manifest.reference.commit,config.reference.commit);
  assert.equal(manifest.sourceUiModified,false);assert.equal(manifest.backendConnected,false);
  assert.deepEqual(manifest.pageErrors,[]);assert.deepEqual(manifest.unexpectedRequests,[]);
});
test('search approval retains the reviewed UI and does not connect the app',()=>{
  assert.equal(config.uiApproved,true);assert.equal(config.contentApproved,true);assert.equal(config.allowIndexing,true);assert.equal(config.appReady,false);
});
test('three real screen captures have descriptive labels and native enlargement links',()=>{
  assert.equal(manifest.screens.length,3);
  for(const name of ['board','settings','room']){
    const src=`/assets/ui/quick-match-${name}.webp`;
    assert.ok(html.includes(`href="${src}"`));assert.ok(html.includes(`src="${src}"`));
  }
  assert.match(html,/실제 UI/);assert.match(html,/예시 데이터/);
  assert.doesNotMatch(html,/class="app-preview"|class="room-bubble"|테스트 서포터|예약 모집 예시/);
});
for(const screen of manifest.screens)test(`${screen.name}: capture bytes match recorded source evidence`,async()=>{
  assert.match(screen.name,/^quick-match-(board|settings|room)$/);
  const bytes=await readFile(new URL(`public/assets/ui/${screen.name}.webp`,root));
  assert.equal(bytes.toString('ascii',0,4),'RIFF');assert.equal(bytes.toString('ascii',8,12),'WEBP');
  assert.equal(bytes.readUInt32LE(4)+8,bytes.length);
  assert.equal(createHash('sha256').update(bytes).digest('hex'),screen.sha256);
  assert.equal(screen.width,1440);assert.equal(screen.height,900);
});
test('AI-readable explanation distinguishes screenshots from live matchmaking',()=>{
  const {llms}=renderSite(config);
  assert.match(llms,/feature\/quick-match-ui/);assert.match(llms,/예시 데이터/);assert.match(llms,/실제 매칭 및 음성 연결 검증이 아닙니다/);
});
test('primary actual room screenshot is eager and lower quick-match screenshot is lazy',()=>{
 assert.match(html,/quick-match-room\.webp"[^>]+fetchpriority="high"/);
 assert.match(html,/quick-match-settings\.webp"[^>]+loading="lazy"/);
});
test('all three original capture links have descriptive enlargement names',()=>{
 const labels=[...html.matchAll(/<a class="capture-link[^"]*"[^>]*aria-label="([^"]+)"/g)];
 assert.equal(labels.length,3);
 for(const [,name]of labels)assert.ok(name.includes('화면 크게 보기'));
});
