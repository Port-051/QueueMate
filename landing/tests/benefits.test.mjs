import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const {html,llms}=renderSite(c);
const text=html.replace(/<[^>]+>/g,'');
test('approved product headline leads instead of rhetorical advertising',()=>{
 assert.match(text,/조건에 맞는\s*팀원을 찾고,같은 방에서\s*바로 대화하세요/);
 assert.doesNotMatch(html,/왜 아직|몇 명 모였어요|디코 어디로|지금 만들고 있는 화면, 그대로/);
 assert.ok(html.indexOf('id="preview"')<html.indexOf('id="quick-match"'));
});
test('automatic matching and direct room entry stay explicit in the concise page',()=>{
 const opening=html.slice(html.indexOf('<main'),html.indexOf('id="faq"'));
 for(const phrase of ['롤 듀오·파티, 멤버와 빈자리를 보고 참여하세요.','직접 찾는 대신,','빠른매치.','조건을 정하면 맞는 팀원을 자동으로 찾아드립니다.','모집 중에도 같은 방에서 음성·채팅.','디스코드 이동 없이']) assert.ok(opening.includes(phrase),phrase);
});
test('shows a 2-of-5 to 3-of-5 transition without filling the whole party',()=>{
 assert.match(html,/참여 전 · 2\/5명/);assert.match(html,/참여 후 · 3\/5명/);
 assert.match(html,/class="unjoined-screen"/);
 assert.match(html,/class="join-demo"/);
 assert.match(html,/class="joined-screen"/);
 assert.match(html,/모집 중에도 같은 방에서/);
});
test('native participation example never requests microphone or calls real APIs',()=>{
 assert.match(html,/서비스 화면 예시/);
 assert.match(html,/실제 참가·음성 연결은 되지 않습니다/);
 assert.match(html,/서비스 화면 보기/);
 assert.doesNotMatch(html,/getUserMedia|autoplay|실시간 접속자|<script src=/);
});
test('voice benefit keeps browser permission and game invitation caveats',()=>{
 assert.match(html,/브라우저의 마이크 권한/);
 assert.match(html,/마이크를 켜야/);
 assert.match(html,/게임 내 친구 추가·파티 초대/);
 assert.match(llms,/매칭 완료 시간이나 절약 시간은 보장하지 않습니다/);
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
