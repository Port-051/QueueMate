import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite,renderUseFlow} from '../src/render.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const html=renderSite(config,{VERCEL_ENV:'production'}).html;

test('one concise hero description and one primary action remain',()=>{
 const hero=html.slice(html.indexOf('<section class="hero'),html.indexOf('<section id="quick-match"'));
 assert.equal((hero.match(/class="hero-description"/g)||[]).length,1);
 assert.equal((hero.match(/data-cta=/g)||[]).length,1);
 assert.doesNotMatch(hero,/hero-description-secondary|빠른매치 시작하기/);
});
test('redundant feature grids and closing pitches are not restored',()=>{
 assert.doesNotMatch(html,/class="benefit-grid"|class="feature-visual |continuity-section|final-section/);
 assert.equal((html.match(/class="quick-match-copy"/g)||[]).length,1);
});
test('actual screenshots are shown directly rather than buried under three disclosures',()=>{
 assert.equal((html.match(/class="screen-details"/g)||[]).length,0);
 assert.match(html,/<details class="join-demo" open>/);
 assert.match(html,/class="quick-match-figure"/);
});
test('necessary release, microphone and game caveats remain concise',()=>{
 for(const term of ['서비스 준비 중','실제 참가·음성 연결은 되지 않습니다','브라우저의 마이크 권한','게임 내 친구 추가·파티 초대','출시 시 지원 범위'])assert.ok(html.includes(term));
});
