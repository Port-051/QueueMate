import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const config = JSON.parse(await readFile(new URL('../site.config.json', import.meta.url), 'utf8'));
const {html} = renderSite(config);
const plain = text => text.replace(/<[^>]*>/g, '').replace(/\s+/g, '');
test('hero keeps the approved proposition but only one short description and primary action', () => {
  const hero = html.slice(html.indexOf('<section class="hero'), html.indexOf('<section id="features"'));
  assert.equal((hero.match(/class="hero-description"/g) || []).length, 1);
  assert.ok(plain(hero.match(/<p class="hero-description">(.*?)<\/p>/s)[1]).length <= 40);
  assert.equal((hero.match(/data-cta=/g) || []).length, 1);
  assert.doesNotMatch(hero, /hero-description-secondary|자동 매칭 알아보기/);
});
test('repeat narrative and final pitch are deleted from HTML, not visually hidden', () => {
  assert.doesNotMatch(html, /continuity-section|final-section|evidence-section|따로 하던 과정을|찾는 방법은 두 가지/);
  assert.equal((html.match(/class="benefit-card"/g) || []).length, 3);
  for (const card of html.matchAll(/class="benefit-card"[\s\S]*?<p>(.*?)<\/p>/g)) {
    assert.ok(plain(card[1]).length <= 40, card[1]);
  }
});
test('real screenshots remain user accessible but closed initially', () => {
  const screens = [...html.matchAll(/<details[^>]*class="screen-details"[^>]*>/g)];
  assert.equal(screens.length, 3);
  for (const [tag] of screens) assert.doesNotMatch(tag, /\bopen(?:\s|=|>)/);
  assert.equal((html.match(/실제 화면 보기/g) || []).length, 3);
});
test('necessary caveats survive in demo and concise FAQs', () => {
  for (const phrase of ['서비스 준비 중', '실제 참가·음성 연결은 되지 않습니다', '브라우저의 마이크 권한', '게임 내 친구 추가·파티 초대', '출시 시 지원 범위']) assert.ok(html.includes(phrase), phrase);
});
