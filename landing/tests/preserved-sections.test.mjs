import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {renderSite,renderUseFlow} from '../src/render.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const source=await readFile(new URL('../src/render.mjs',import.meta.url),'utf8');
const html=renderSite(config,{VERCEL_ENV:'production'}).html;
const feature=html.match(/<section id="features"[\s\S]*?<\/div><\/section>/)?.[0];

test('original feature section from b9f49db is restored verbatim',()=>{
 const section=source.match(/<section id="features"[\s\S]*?<\/div><\/section>/)?.[0];
 assert.ok(section);
 // Locks the requested restoration, including copy, decorative visuals and card layout.
 assert.equal(createHash('sha256').update(section).digest('hex'),'9319c67bbcf9641dfd29c7c1bebda82242255afdb91a7c7257c11cc345fa51f1');
 assert.match(feature,/<h2 id="features-title">팀원 찾기부터<br><em>음성 대화까지\.<\/em><\/h2>/);
});
test('overview remains independent of the additional quick-match explanation',()=>{
 const sections=[...html.matchAll(/<section\b[^>]*>/g)].map(([tag])=>tag);
 assert.equal(sections.length,4);
 assert.match(sections[0],/class="hero wrap"/);
 assert.match(sections[1],/id="features"/);
 assert.match(sections[2],/id="quick-match"/);
 assert.match(sections[3],/id="faq"/);
 assert.doesNotMatch(feature,/quick-match-copy|quick-match-title/);
 assert.match(html,/<a href="#features">기능<\/a>/);
});
test('all three original benefit cards remain distinct with short descriptions',()=>{
 const cards=[...feature.matchAll(/<article\b[^>]*class="benefit-card">([\s\S]*?)<\/article>/g)];
 assert.equal(cards.length,3);
 assert.deepEqual(cards.map(([,card])=>card.match(/<h3>(.*?)<\/h3>/)[1]),[
  '조건에 맞는 롤 듀오 찾기','멤버와 빈자리 확인','같은 방에서 음성 채팅',
 ]);
 for(const [,card]of cards){
  assert.ok(card.match(/<p>(.*?)<\/p>/)[1].length<60);
  assert.match(card,/class="screen-details"/);
 }
});
test('only the primary demo uses actual screenshots; no fake room or extra request is restored',()=>{
 const flow=renderUseFlow();
 assert.match(flow,/class="actual-showcase"/);
 assert.match(flow,/quick-match-room\.webp/);assert.match(flow,/quick-match-board\.webp/);
 assert.doesNotMatch(flow,/feature-visual|flow-demo|self-member|voice-panel|getUserMedia|<script/);
 assert.match(flow,/실제 참가·음성 연결은 되지 않습니다/);
 const ids=[...html.matchAll(/\sid="([^"]+)"/g)].map(([,id])=>id);
 assert.equal(ids.length,new Set(ids).size,'Every restored anchor must remain unique');
});
