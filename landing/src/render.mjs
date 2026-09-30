/** Static SEO landing: no frontend app imports, trackers, network calls, or client JS. */
export const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const e = escapeHtml;
const jsonForHtml = value => JSON.stringify(value).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');

export function validateConfig(c) {
  for (const key of ['name','alternateName','origin','appUrl','title','description']) {
    if (typeof c[key] !== 'string' || !c[key].trim()) throw new Error(`${key}: 비어 있지 않은 문자열이 필요합니다.`);
  }
  for (const key of ['contentApproved','uiApproved','allowIndexing','appReady']) {
    if (typeof c[key] !== 'boolean') throw new Error(`${key}: true 또는 false가 필요합니다.`);
  }
  const origin = new URL(c.origin), app = new URL(c.appUrl);
  if (origin.protocol !== 'https:' || origin.username || origin.password || origin.pathname !== '/' || origin.search || origin.hash) {
    throw new Error('origin: 경로·쿼리·사용자 정보 없는 HTTPS 도메인을 사용하세요.');
  }
  if (app.protocol !== 'https:' || app.username || app.password) throw new Error('appUrl: 사용자 정보 없는 HTTPS 주소를 사용하세요.');
  if (c.allowIndexing && (!c.contentApproved || !c.uiApproved)) throw new Error('검색 공개 전 문구와 UI 검토가 필요합니다.');
  if (c.appReady && !c.contentApproved) throw new Error('서비스 연결 전 문구 검토가 필요합니다.');
  if (c.appReady && /준비하고 있습니다|준비 중/.test(c.description)) throw new Error('description의 준비 중 표현을 수정하세요.');
  const image = c.media?.ogImage ?? '';
  if (typeof image !== 'string' || (image && !/^\/assets\/[a-zA-Z0-9_-]+\.(png|jpg|jpeg|webp)$/.test(image))) {
    throw new Error('media.ogImage: /assets/파일명.png 형식의 로컬 이미지를 사용하세요.');
  }
  for (const key of ['google','naver','bing']) {
    if (c.verification?.[key] !== undefined && typeof c.verification[key] !== 'string') throw new Error(`verification.${key}: 문자열이 필요합니다.`);
  }
}

export function resolveMode(c, env = {}) {
  validateConfig(c);
  const production = env.VERCEL_ENV !== undefined ? env.VERCEL_ENV === 'production' : env.BUILD_TARGET === 'production';
  return {production, indexable: production && c.allowIndexing, draft: !c.contentApproved || !c.uiApproved};
}

const shapes = {
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  down: '<path d="M12 5v14m-6-6 6 6 6-6"/>',
  bolt: '<path d="m13 2-9 12h7l-1 8 10-13h-8l1-7Z"/>',
  mic: '<rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2m-7 9v3m-4 0h8"/>',
  people: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3m2-17a3 3 0 0 1 0 6m2 4a5 5 0 0 1 2 4v3"/>',
  filters: '<path d="M4 7h7m5 0h4M4 17h3m5 0h8"/><circle cx="13.5" cy="7" r="2.5"/><circle cx="9.5" cy="17" r="2.5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
  chat: '<path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z"/>',
  crown: '<path d="m3 6 5 4 4-7 4 7 5-4-2 12H5L3 6Zm2 14h14"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  shield: '<path d="m12 3 8 4v6c0 5-8 9-8 9s-8-4-8-9V7l8-4Z"/>',
};
export function icon(name) {
  if (!shapes[name]) throw new Error(`Unknown icon: ${name}`);
  return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${shapes[name]}</svg>`;
}
const wordmark = (small = false) => `<img class="brand-wordmark${small ? ' small' : ''}" src="/assets/queuemate-wordmark.svg" width="156" height="24" alt="QueueMate">`;

/** Screenshots from unmodified feature/quick-match-ui @ 904cce4; display data is synthetic. */
export function renderProductPreview() {
  return `<figure class="product-figure actual-ui" aria-labelledby="preview-caption">
<div class="capture-bar"><span>QueueMate · 파티 찾기</span><span class="capture-label">실제 UI 캡처</span></div>
<a class="capture-link" href="/assets/ui/quick-match-board.webp" aria-label="화면 크게 보기 — 파티 찾기 실제 UI 캡처"><img class="ui-capture" src="/assets/ui/quick-match-board.webp" width="1440" height="900" alt="빠른매치 버튼과 게임 모드·포지션 필터, 구성원과 빈자리를 보여주는 파티 모집방 목록" fetchpriority="high" decoding="async"><span class="capture-zoom">화면 크게 보기 ↗</span></a>
<figcaption id="preview-caption">실제 UI를 실행해 촬영한 화면입니다. 닉네임·전적·모집방은 예시 데이터이며, 이 페이지에서 실제 매칭·음성 연결은 실행되지 않습니다.</figcaption></figure>`;
}
function detailCapture(name, alt, label) {
  const url = `/assets/ui/quick-match-${name}.webp`;
  return `<figure class="detail-capture"><a class="capture-link" href="${url}" aria-label="화면 크게 보기 — ${e(label)} 실제 UI 캡처"><img src="${url}" width="1440" height="900" alt="${e(alt)}" loading="lazy" decoding="async"><span class="capture-zoom">화면 크게 보기 ↗</span></a><figcaption>실제 UI 캡처 · 예시 데이터</figcaption></figure>`;
}

export function renderSite(c, env = {}) {
  const mode = resolveMode(c, env);
  const origin = new URL(c.origin).origin, canonical = `${origin}/`;
  const cta = (extra = '') => c.appReady
    ? `<a class="button button-primary ${extra}" href="${e(c.appUrl)}" data-cta="start-matching">팀원 찾기 시작하기 ${icon('arrow')}</a>`
    : `<a class="button button-primary ${extra}" href="#preview" data-cta="explore-preview">화면 먼저 살펴보기 ${icon('arrow')}</a>`;
  const verification = Object.entries({google:'google-site-verification',naver:'naver-site-verification',bing:'msvalidate.01'})
    .filter(([key]) => c.verification?.[key]?.trim()).map(([key,name])=>`<meta name="${name}" content="${e(c.verification[key])}">`).join('\n');
  const imageMeta = c.media?.ogImage ? `<meta property="og:image" content="${e(origin + c.media.ogImage)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기"><meta name="twitter:image" content="${e(origin + c.media.ogImage)}"><meta name="twitter:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기">` : '';
  const schema = {'@context':'https://schema.org','@type':'WebSite',name:c.name,alternateName:c.alternateName,url:canonical,inLanguage:'ko-KR'};
  const faqs = [
    ['큐메이트는 어떤 서비스인가요?', '함께 게임할 팀원을 찾는 서비스입니다. 티어·포지션·마이크 조건을 확인하고, 모집방에서 파티를 구성하며 대화를 이어가는 흐름을 제공합니다.'],
    ['롤 듀오를 찾을 때 어떤 조건을 확인하나요?', '모집방에 표시된 구성원의 티어와 포지션, 비어 있는 자리, 마이크 사용 조건을 확인합니다. 본인이 원하는 플레이 방식에 맞는 방을 선택하는 데 활용할 수 있습니다.'],
    ['음성 채팅을 꼭 사용해야 하나요?', '모집방마다 음성 사용 조건이 다를 수 있습니다. 참여하기 전에 마이크 사용 여부를 확인하세요.'],
    ['빠른매치와 직접 파티 찾기는 무엇이 다른가요?', '빠른매치에서는 조건을 설정해 팀원을 찾는 흐름을 시작합니다. 직접 찾을 때는 모집방의 구성원과 빈자리를 살펴보고 참가합니다. 이 페이지는 두 흐름을 실제 UI 캡처로 소개합니다.'],
    ['어떤 게임을 위한 서비스인가요?', '지원 대상은 리그 오브 레전드, 발로란트, 배틀그라운드입니다. 이 페이지의 예시는 롤을 기준으로 구성했습니다. 실제 이용 가능한 범위는 정식 서비스 공개 시 확인할 수 있습니다.'],
    ['지금 바로 이용할 수 있나요?', c.appReady ? '팀원 찾기 시작하기를 누르면 별도의 매칭 서비스로 이동합니다. 이 소개 페이지 자체에서는 모집이나 매칭이 실행되지 않습니다.' : '현재 서비스를 준비하고 있습니다. 이 페이지에서는 화면과 이용 흐름을 먼저 살펴볼 수 있으며, 실제 매칭과 음성 연결은 실행되지 않습니다.'],
  ];
  const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${e(c.title)}</title>
<meta name="description" content="${e(c.description)}"><meta name="robots" content="${mode.indexable ? 'index, follow, max-image-preview:large' : 'noindex, nofollow'}"><meta name="theme-color" content="#05060f"><link rel="canonical" href="${e(canonical)}">
<meta property="og:type" content="website"><meta property="og:locale" content="ko_KR"><meta property="og:site_name" content="${e(c.name)}"><meta property="og:title" content="${e(c.title)}"><meta property="og:description" content="${e(c.description)}"><meta property="og:url" content="${e(canonical)}">
<meta name="twitter:card" content="${c.media?.ogImage ? 'summary_large_image' : 'summary'}"><meta name="twitter:title" content="${e(c.title)}"><meta name="twitter:description" content="${e(c.description)}">${imageMeta}${verification}
<script type="application/ld+json">${jsonForHtml(schema)}</script><link rel="stylesheet" href="/assets/site.css"></head>
<body><a class="skip-link" href="#main">본문으로 바로가기</a><header class="site-header"><div class="wrap header-inner"><a href="#main" class="brand-link" aria-label="큐메이트 홈">${wordmark()}</a><nav aria-label="주요 메뉴"><a href="#features">서비스 소개</a><a href="#how-it-works">이용 방법</a><a href="#faq">자주 묻는 질문</a></nav><a class="header-cta" href="${c.appReady ? e(c.appUrl) : '#preview'}">${c.appReady ? '시작하기' : '둘러보기'} ${icon('arrow')}</a></div></header>
<main id="main" tabindex="-1"><section class="hero wrap product-first" aria-labelledby="hero-title"><div class="hero-copy"><div class="hero-kicker">큐메이트 · 게임 팀원 찾기</div><h1 id="hero-title">롤 듀오 구하기,<br><em>보고 고르거나, 빠른매치로.</em></h1><p class="hero-description">모집방의 구성원과 빈자리를 확인하고,<br>같은 화면에서 파티를 준비하세요.</p></div><div class="hero-side"><div class="hero-actions">${cta()}<a class="text-link" href="#how-it-works">이용 방법 보기 ${icon('down')}</a></div><p class="release-status">${c.appReady ? '시작하기를 누르면 매칭 서비스로 이동합니다.' : '서비스 준비 중 · 실제 UI 먼저 둘러보기'}</p></div></section>
<section id="preview" class="preview-section wrap" aria-labelledby="preview-title"><div class="preview-heading"><div><p class="eyebrow">파티 찾기</p><h2 id="preview-title">지금 만들고 있는 화면, 그대로.</h2></div><span class="preview-tag">실제 UI · 예시 데이터</span></div>${renderProductPreview()}<div class="preview-points"><span>${icon('check')} 조건을 먼저 확인하고</span><span>${icon('check')} 빈자리에 참여하고</span><span>${icon('check')} 같은 화면에서 대화하고</span></div></section>
<section id="features" class="section wrap" aria-labelledby="features-title"><div class="section-heading"><p class="eyebrow">두 가지 방법으로 팀원 찾기</p><h2 id="features-title">직접 고르는 파티,<br>조건으로 시작하는 빠른매치.</h2></div><div class="feature-grid"><article class="feature-card"><span class="feature-icon">${icon('filters')}</span><h3>빠른매치</h3><p>게임 모드, 내 포지션, 인원, 플레이 목적과 음성 조건을 한곳에서 설정합니다.</p></article><article class="feature-card"><span class="feature-icon">${icon('people')}</span><h3>모집방에서 직접 찾기</h3><p>누가 있는지, 몇 자리가 비었는지 확인하고 함께할 파티를 고릅니다.</p></article><article class="feature-card"><span class="feature-icon">${icon('chat')}</span><h3>같은 화면에서 파티 준비</h3><p>방에 참여하면 옆에 방 패널이 열립니다. 구성원과 대화 영역을 함께 확인합니다.</p></article></div><p class="section-note">캡처는 개발 중인 UI의 이용 흐름입니다. 실제 제공 범위는 서비스 공개 시 안내합니다.</p></section>
<section id="how-it-works" class="section wrap" aria-labelledby="how-title"><div class="section-heading"><p class="eyebrow">실제 화면으로 보는 이용 방법</p><h2 id="how-title">조건을 고르고,<br>한 화면에서 함께 준비하세요.</h2></div><div class="flow-grid"><article class="flow-card"><div class="flow-copy"><span class="feature-number">01</span><h3>빠른매치 조건 설정</h3><p>상단의 ‘빠른매치’를 눌렀을 때 열리는 설정 창입니다. 원하는 조건을 정하고 매칭을 시작하는 화면을 보여줍니다.</p></div>${detailCapture('settings','게임 모드와 내 포지션, 인원, 플레이 목적, 음성 조건을 선택하는 빠른매치 설정 창','빠른매치 조건 설정')}</article><article class="flow-card"><div class="flow-copy"><span class="feature-number">02</span><h3>모집방 옆에서 파티 준비</h3><p>모집방에 참여한 뒤의 화면입니다. 게시판을 벗어나지 않고 구성원과 음성·채팅 영역을 확인할 수 있습니다.</p></div>${detailCapture('room','왼쪽 모집방 목록과 오른쪽의 파티 구성원, 마이크 조작, 채팅 영역을 함께 보여주는 방 패널','방 참여 후')}</article></div><p class="section-note">실제 UI 캡처에 예시 데이터를 사용했습니다. 음성·채팅의 실서비스 연결을 검증한 화면은 아닙니다.</p></section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><p class="eyebrow">FAQ</p><h2 id="faq-title">궁금한 점이<br>있으신가요?</h2><p>시작하기 전에 확인해 보세요.</p></div><div class="faq-list">${faqs.map(([q,a])=>`<details><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
<section class="wrap final-section" aria-labelledby="final-title"><div class="final-card"><p class="eyebrow">NEXT GAME, WITH YOUR MATE</p><h2 id="final-title">다음 판은,<br>함께할 팀원부터.</h2><p>나와 맞는 팀원을 찾는 새로운 방법, 큐메이트.</p>${cta()}<small>${c.appReady ? '매칭 서비스로 이동합니다.' : '정식 서비스 공개를 준비하고 있습니다.'}</small></div></section></main>
<footer class="site-footer wrap"><div><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><p>같이할 사람이 필요한 순간, 큐메이트.</p></div><div class="footer-meta"><span>${e(new URL(c.origin).hostname)}</span><a href="#main">맨 위로 ↑</a><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots = `User-agent: *\nAllow: /\n${mode.indexable ? `\nSitemap: ${origin}/sitemap.xml\n` : '\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap = mode.indexable ? `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n` : null;
  const llms = `# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [서비스 소개](${canonical}): 조건 기반 팀원 찾기, 모집방과 대화의 이용 흐름\n\n## 공개 상태\n${c.appReady ? '매칭 서비스 링크를 활성화했습니다.' : '서비스 준비 중입니다. 이 랜딩 페이지에서 매칭이나 음성 연결은 실행되지 않습니다.'}\n\n## 화면 예시\nfeature/quick-match-ui 브랜치 904cce415181aeb8a8802fc398f9be9e0835b1fe의 실제 프런트엔드를 실행해 캡처했습니다. 화면 코드를 다시 그린 것이 아닙니다. 닉네임·전적·모집방은 예시 데이터이며, 실제 사용자·모집방·성과 데이터가 아닙니다. 실제 매칭 및 음성 연결 검증이 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n\n이 안내 파일은 보조 설명이며 검색 또는 AI 노출을 보장하지 않습니다.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
