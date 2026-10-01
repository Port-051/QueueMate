import test from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));const {html,llms}=renderSite(c);
test('core headline remains',()=>{assert.match(html,/조건에 맞는 팀원을 찾고,/);assert.match(html,/같은 방에서 바로 대화하세요/)});
test('real product walkthrough shows before and after',()=>{for(const x of ['product-journey','들어갈 방을 봅니다','참여하면, 바로 같은 방입니다','음성·채팅','quick-match-board.webp','quick-match-room.webp','3\/5명, 모집 중에도 대화'])assert.match(html,new RegExp(x))});
test('features use actual product screens',()=>{for(const x of ['팀원 찾기부터','조건에 맞는 팀원을 찾습니다','누가 있는지 보고 참여합니다','방을 옮기지 않고 대화합니다','quick-match-settings.webp','quick-match-board.webp','quick-match-room.webp'])assert.ok(html.includes(x),x)});
test('quick match remains secondary',()=>{assert.ok(html.indexOf('id="features"')<html.indexOf('id="quick-match"'));assert.match(html,/직접 찾는 대신,/);assert.match(html,/원하는 조건만 정하세요/)});
test('demo never claims live behavior',()=>{assert.match(html,/실제 참가·음성 연결은 되지 않습니다/);assert.doesNotMatch(html,/getUserMedia|실시간 접속자/)});
test('caveats and AI text remain',()=>{assert.match(html,/브라우저의 마이크 권한/);assert.match(html,/게임 내 친구 추가·파티 초대/);assert.match(llms,/매칭 완료 시간이나 절약 시간은 보장하지 않습니다/)});
