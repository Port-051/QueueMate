import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const {html,llms}=renderSite(c);
const text=html.replace(/<[^>]+>/g,'');
test('approved product headline leads instead of rhetorical advertising',()=>{
 assert.match(text,/조건에 맞는팀원을 찾고,같은 방에서바로 대화하세요/);
 assert.doesNotMatch(html,/왜 아직|몇 명 모였어요|디코 어디로|지금 만들고 있는 화면, 그대로/);
 assert.ok(html.indexOf('id="features"')<html.indexOf('class="product-figure'));
});
test('automatic matching and direct room entry both appear before screenshots',()=>{
 const opening=html.slice(html.indexOf('<main'),html.indexOf('class="product-figure'));
 for(const phrase of ['원하는 조건으로 자동 매칭','모집방의 멤버와 빈자리','조건을 정하면,','팀원을 자동으로 찾습니다.','누구와 함께할지,','참여 전에 확인합니다.','참여한 방에서,','음성 대화를 시작합니다.']) assert.ok(opening.includes(phrase),phrase);
});
test('shows a 2-of-5 to 3-of-5 transition without filling the whole party',()=>{
 assert.match(html,/<span class="before-join">2<\/span><span class="after-join">3<\/span>/);
 assert.match(html,/class="after-join self-member"/);
 assert.match(html,/class="join-demo"/);
 assert.match(html,/class="voice-panel"/);
 assert.match(html,/정원이 다 차기 전에도/);
});
test('native participation example never requests microphone or calls real APIs',()=>{
 assert.match(html,/실제 UI 구성을 요약한 이용 예시/);
 assert.match(html,/참가·음성 연결은 실행되지 않습니다/);
 assert.match(html,/흐름 체험/);
 assert.doesNotMatch(html,/getUserMedia|autoplay|실시간 접속자|<script src=/);
});
test('voice benefit keeps browser permission and game invitation caveats',()=>{
 assert.match(html,/브라우저의 마이크 권한/);
 assert.match(html,/마이크를 켜야/);
 assert.match(html,/게임 내 친구 추가·파티 초대/);
 assert.match(html,/매칭에 걸리는 시간은 참여 인원과 조건에 따라/);
 assert.doesNotMatch(html,/duo\.gg|OP\.GG|ggmate|\d+분 절약|\d+%.*단축/);
});
test('AI text covers both finding methods and conversation during recruitment',()=>{
 for(const phrase of ['자동 매칭','직접 파티 찾기','정원이 다 차기 전','마이크 권한','친구 추가·파티 초대','예시 데이터']) assert.ok(llms.includes(phrase),phrase);
 assert.match(llms,/매칭 완료 시간이나 절약 시간은 보장하지 않습니다/);
});
test('search approval keeps the app offline and local preview unindexed',()=>{
 assert.equal(c.contentApproved,true);assert.equal(c.uiApproved,true);
 assert.equal(c.allowIndexing,true);assert.equal(c.appReady,false);
 assert.match(html,/서비스 준비 중/);assert.match(html,/noindex, nofollow/);
 assert.doesNotMatch(html,/href="https:\/\/app\.queue-mate\.com/);
});
