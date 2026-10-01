import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite,renderUseFlow} from '../src/render.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const html=renderSite(config,{VERCEL_ENV:'production'}).html;

test('primary copy keeps the original headline and short description',()=>{
 assert.match(html,/<h1 id="hero-title">조건에 맞는 팀원을 찾고,<br><em>같은 방에서 바로 대화하세요\.<\/em><\/h1>/);
 const hero=html.match(/<p class="hero-description">(.*?)<\/p>/s)[1];
 assert.equal(hero,'롤 듀오·파티를 자동으로 찾거나, 모집방에 직접 참여하세요.<br>음성 채팅까지 한곳에서.');
 assert.ok(hero.replace(/<[^>]*>|\s/g,'').length<=45);
});
test('quick match is one secondary section after the actual room, not two competing choices',()=>{
 assert.ok(html.indexOf('id="preview"')<html.indexOf('id="features"'));
 assert.ok(html.indexOf('id="features"')<html.indexOf('id="quick-match"'));
 assert.ok(html.indexOf('id="quick-match"')<html.indexOf('id="faq"'));
 assert.equal((html.match(/id="quick-match"/g)||[]).length,1);
 assert.match(html,/직접 찾는 대신,<br><em>빠른매치\./);
 assert.match(html,/조건을 정하면 맞는 팀원을 자동으로 찾아드립니다\./);
 assert.doesNotMatch(html,/팀원을 찾는 두 가지 방법|빠른매치 시작하기/);
});
test('copy stays brief and contains visible discovery terms rather than keyword stuffing',()=>{
 assert.equal((html.match(/class="hero-description"/g)||[]).length,1);
 assert.equal((html.match(/class="faq-item"/g)||[]).length,3);
 assert.doesNotMatch(html,/name="keywords"|hero-description-secondary|continuity-section|final-section/);
 for(const term of ['롤 듀오','파티 찾기','빠른매치','음성 채팅'])assert.ok(html.includes(term));
});
test('prelaunch and production search policy remain separate',()=>{
 assert.equal(config.appReady,false);assert.equal(config.allowIndexing,true);
 assert.match(html,/<title>롤 듀오 찾기·파티 구하기 \| 큐메이트<\/title>/);
 assert.match(html,/서비스 준비 중/);assert.match(html,/실제 참가·음성 연결은 되지 않습니다/);
 assert.match(html,/content="index, follow, max-image-preview:large"/);
});
