import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const {html,llms}=renderSite(c);
test('benefit story precedes full screen evidence',()=>{
 assert.ok(html.indexOf('id="preview"')<html.indexOf('class="product-figure'));
 assert.ok(html.indexOf('id="features"')<html.indexOf('class="product-figure'));
 assert.doesNotMatch(html,/지금 만들고 있는 화면, 그대로|화면 먼저 살펴보기/);
 assert.match(html,/인원 확인부터 참가, 음성 대화까지/);
});
test('explains specific friction without unsupported competitor claims',()=>{
 for(const phrase of ['참여 인원과 빈자리','디스코드로 이동하거나','연락처를 주고받','음성·텍스트 대화']) assert.ok(html.includes(phrase),phrase);
 assert.match(html,/서비스·이용 방식에 따라 다릅니다/);
 assert.doesNotMatch(html,/duo\.gg|OP\.GG|ggmate|\d+분 절약|\d+%.*단축|친구 추가가 필요 없/);
});
test('retains microphone permission and game-party prerequisites',()=>{
 assert.match(html,/브라우저.*마이크 권한/);
 assert.match(html,/게임 내 친구 추가·파티 초대/);
 assert.match(html,/마이크를 켜야/);
 assert.match(html,/매칭 완료 시간은 보장하지 않습니다/);
});
test('journey is explicitly a demo, not a live room or voice session',()=>{
 assert.match(html,/참여 인원과 대화는 설명용 예시/);
 assert.match(html,/실제 접속 현황이 아닙니다/);
 assert.doesNotMatch(html,/getUserMedia|autoplay|실시간 접속자/);
 assert.match(html,/href="#join">참가 방식 알아보기/);
 assert.match(html,/id="join"/);
});
test('AI text describes benefits without implying game automation',()=>{
 assert.match(llms,/별도의 디스코드 연락처 교환/);
 assert.match(llms,/게임 실행.*친구 추가와 파티 초대/);
 assert.match(llms,/매칭 완료 시간이나 절약 시간을 보장하지 않습니다/);
});
test('design changes do not silently launch or enable indexing',()=>{
 assert.equal(c.allowIndexing,false);assert.equal(c.appReady,false);
 assert.match(html,/서비스 준비 중/);assert.match(html,/noindex, nofollow/);
 assert.doesNotMatch(html,/href="https:\/\/app\.queue-mate\.com/);
});
