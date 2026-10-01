import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';

const config = JSON.parse(await readFile(new URL('../site.config.json', import.meta.url), 'utf8'));
const html = renderSite(config, {VERCEL_ENV: 'production'}).html;
const hero = html.match(/<p class="hero-description">([\s\S]*?)<\/p>/)?.[1];
const cards = [...html.matchAll(/<article[^>]*class="benefit-card">([\s\S]*?)<\/article>/g)];
const text = value => value.replace(/<br\s*\/?\s*>/gi, ' ').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();

test('brief hero names the game, both finding methods and voice chat in visible HTML', () => {
  assert.equal(hero, '롤 듀오·파티를 자동으로 찾거나, 모집방에 직접 참여하세요.<br>음성 채팅까지 한곳에서.');
  assert.equal((hero.match(/<br>/g) || []).length, 1);
  assert.ok(text(hero).replace(/\s/g, '').length <= 40);
  assert.match(html, /<h1 id="hero-title">조건에 맞는<br>팀원을 찾고,<br><em>같은 방에서<br>바로 대화하세요\.<\/em><\/h1>/);
});

test('three short headings describe matching, member selection and same-room voice', () => {
  assert.equal(cards.length, 3);
  assert.deepEqual(cards.map(([, card]) => text(card.match(/<h3>([\s\S]*?)<\/h3>/)[1])), [
    '조건에 맞는 롤 듀오 찾기', '멤버와 빈자리 확인', '같은 방에서 음성 채팅',
  ]);
  const descriptions = cards.map(([, card]) => text(card.match(/<p>([\s\S]*?)<\/p>/)[1]));
  assert.match(descriptions[0], /모드·포지션·음성 조건.*자동 매칭/);
  assert.match(descriptions[1], /티어·포지션.*모집방에 직접 참여/);
  assert.match(descriptions[2], /디스코드 이동 없이.*모집 중에도 같은 방에서/);
  for (const description of descriptions) assert.ok(description.replace(/\s/g, '').length <= 40);
});

test('keyword refinement does not restore long sections or hide terms in SEO-only markup', () => {
  assert.equal((html.match(/class="hero-description"/g) || []).length, 1);
  assert.equal((html.match(/class="faq-item"/g) || []).length, 3);
  assert.doesNotMatch(html, /hero-description-secondary|continuity-section|final-section|name="keywords"/);
  const heroTag = html.match(/<p class="hero-description"[^>]*>/)?.[0];
  assert.equal(heroTag, '<p class="hero-description">');
  assert.equal((html.match(/class="screen-details"/g) || []).length, 3);
});

test('prelaunch disclosure and production search policy survive the copy edit', () => {
  assert.equal(config.appReady, false);
  assert.equal(config.allowIndexing, true);
  assert.match(html, /<title>롤 듀오 찾기·파티 구하기 \| 큐메이트<\/title>/);
  assert.match(html, /서비스 준비 중/);
  assert.match(html, /실제 참가·음성 연결은 되지 않습니다/);
  assert.match(html, /content="index, follow, max-image-preview:large"/);
  assert.match(html, /class="join-demo"/);
});
