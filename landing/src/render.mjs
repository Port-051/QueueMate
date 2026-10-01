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
<a class="capture-link" href="/assets/ui/quick-match-board.webp" aria-label="화면 크게 보기 — 파티 찾기 실제 UI 캡처"><img class="ui-capture" src="/assets/ui/quick-match-board.webp" width="1440" height="900" alt="빠른매치 버튼과 게임 모드·포지션 필터, 구성원과 빈자리를 보여주는 파티 모집방 목록" loading="lazy" decoding="async"><span class="capture-zoom">화면 크게 보기 ↗</span></a>
<figcaption id="preview-caption">실제 UI 캡처 · 예시 데이터</figcaption></figure>`;
}
function detailCapture(name, alt, label) {
  const url = `/assets/ui/quick-match-${name}.webp`;
  return `<figure class="detail-capture"><a class="capture-link" href="${url}" aria-label="화면 크게 보기 — ${e(label)} 실제 UI 캡처"><img src="${url}" width="1440" height="900" alt="${e(alt)}" loading="lazy" decoding="async"><span class="capture-zoom">화면 크게 보기 ↗</span></a><figcaption>실제 UI 캡처 · 예시 데이터</figcaption></figure>`;
}

/** A compact interaction example, not a live room or a screenshot. No API or microphone access. */
export function renderUseFlow() {
  return `<div class="flow-demo" aria-label="모집방 참가와 대화 이용 예시">
<div class="demo-toolbar"><h2>${icon('people')} 파티 참여 체험</h2><span class="demo-label">데모</span></div>
<div class="demo-steps"><span>멤버 확인</span><i aria-hidden="true">→</i><span>참여</span><i aria-hidden="true">→</i><span>음성·채팅</span></div>
<div class="room-preview">
<div class="room-heading"><span class="game-label">롤 · 자유 랭크</span><span class="recruiting">모집 중</span></div>
<h3>자유 랭크 함께해요</h3>
<div class="condition-tags"><span>골드–플래티넘</span><span>${icon('mic')} 음성 사용</span></div>
<div class="count-row"><span>참여 인원</span><strong><span class="before-join">2</span><span class="after-join">3</span><span class="count-total"> / 5명</span></strong></div>
<ul class="member-list" aria-label="모집방 구성원 예시">
<li><span class="avatar">A</span><span class="member-info"><strong>팀원 A <small>방장</small></strong><span>골드 II · 원딜</span></span></li>
<li><span class="avatar avatar-alt">B</span><span class="member-info"><strong>팀원 B</strong><span>플래티넘 IV · 정글</span></span></li>
<li class="after-join self-member"><span class="avatar avatar-self">나</span><span class="member-info"><strong>내 자리</strong><span>미드</span></span>${icon('check')}</li>
</ul>
<details class="join-demo">
<summary class="join-example"><span class="before-join">${icon('plus')} 참여하기</span><span class="after-join">${icon('check')} 참여 전으로</span></summary>
<div class="voice-panel">
<div class="voice-panel-top"><h4>${icon('mic')} 음성·채팅</h4><span>3 / 5명</span></div>
<div class="voice-members"><span><i class="avatar">A</i>팀원 A</span><span><i class="avatar avatar-alt">B</i>팀원 B</span><span><i class="avatar avatar-self">나</i>나</span></div>
<div class="chat-preview"><p><strong>팀원 A</strong> 미드로 오셨네요!</p><p><strong>나</strong> 네, 반가워요.</p></div>
<p class="recruit-note">모집 중에도 함께 대화</p>
</div></details>
<div class="remaining-seats" aria-label="남은 빈자리"><span><i aria-hidden="true">+</i> 빈자리</span><span><i aria-hidden="true">+</i> 빈자리</span></div>
</div>
<div class="before-panel"><span class="panel-icon">${icon('mic')}</span><h4>음성·채팅</h4><span class="panel-hint">참여하기를 눌러보세요.</span></div>
<p class="demo-disclaimer">이용 예시 · 실제 참가·음성 연결은 되지 않습니다.</p>
</div>`;
}

export function renderSite(c, env = {}) {
  const mode = resolveMode(c, env);
  const origin = new URL(c.origin).origin, canonical = `${origin}/`;
  const cta = (extra = '') => c.appReady
    ? `<a class="button button-primary ${extra}" href="${e(c.appUrl)}" data-cta="start-matching">팀원 찾기 시작하기 ${icon('arrow')}</a>`
    : `<a class="button button-primary ${extra}" href="#preview" data-cta="explore-preview">데모 체험하기 ${icon('arrow')}</a>`;
  const verification = Object.entries({google:'google-site-verification',naver:'naver-site-verification',bing:'msvalidate.01'})
    .filter(([key]) => c.verification?.[key]?.trim()).map(([key,name])=>`<meta name="${name}" content="${e(c.verification[key])}">`).join('\n');
  const imageMeta = c.media?.ogImage ? `<meta property="og:image" content="${e(origin + c.media.ogImage)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기"><meta name="twitter:image" content="${e(origin + c.media.ogImage)}"><meta name="twitter:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기">` : '';
  const schema = {'@context':'https://schema.org','@type':'WebSite',name:c.name,alternateName:c.alternateName,url:canonical,inLanguage:'ko-KR'};
  const faqs = [
    ['지금 이용할 수 있나요?', c.appReady ? '시작하기를 누르면 매칭 서비스로 이동합니다. 이 페이지의 데모에서는 실제 참가·음성 연결이 실행되지 않습니다.' : '서비스 준비 중입니다. 지금은 참여 흐름만 체험할 수 있습니다.'],
    ['게임과 음성은 어떻게 연결하나요?', '디스코드는 필요 없습니다. 음성 대화에는 브라우저의 마이크 권한을 허용하고 마이크를 켜야 합니다. 게임 내 친구 추가·파티 초대는 별도로 진행합니다.'],
    ['어떤 게임을 지원하나요?', '롤·발로란트·배틀그라운드를 대상으로 준비 중입니다. 출시 시 지원 범위를 안내합니다.'],
  ];
  const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${e(c.title)}</title>
<meta name="description" content="${e(c.description)}"><meta name="robots" content="${mode.indexable ? 'index, follow, max-image-preview:large' : 'noindex, nofollow'}"><meta name="theme-color" content="#05060f"><link rel="canonical" href="${e(canonical)}">
<meta property="og:type" content="website"><meta property="og:locale" content="ko_KR"><meta property="og:site_name" content="${e(c.name)}"><meta property="og:title" content="${e(c.title)}"><meta property="og:description" content="${e(c.description)}"><meta property="og:url" content="${e(canonical)}">
<meta name="twitter:card" content="${c.media?.ogImage ? 'summary_large_image' : 'summary'}"><meta name="twitter:title" content="${e(c.title)}"><meta name="twitter:description" content="${e(c.description)}">${imageMeta}${verification}
<script type="application/ld+json">${jsonForHtml(schema)}</script><link rel="stylesheet" href="/assets/site.css"><link rel="stylesheet" href="/assets/concise.css"></head>
<body><a class="skip-link" href="#main">본문으로 바로가기</a>
<header class="site-header"><div class="wrap header-inner"><a href="#main" class="brand-link" aria-label="큐메이트 홈">${wordmark()}</a><nav aria-label="주요 메뉴"><a href="#preview">체험</a><a href="#features">기능</a><a href="#faq">FAQ</a></nav><a class="header-cta" href="${c.appReady ? e(c.appUrl) : '#preview'}">${c.appReady ? '시작하기' : '체험하기'} ${icon('arrow')}</a></div></header>
<main id="main" tabindex="-1">
<section class="hero wrap" aria-labelledby="hero-title">
<div class="hero-copy"><p class="hero-kicker">롤 듀오 · 파티 찾기</p>
<h1 id="hero-title">조건에 맞는<br>팀원을 찾고,<br><em>같은 방에서<br>바로 대화하세요.</em></h1>
<p class="hero-description">롤 듀오·파티를 자동으로 찾거나, 모집방에 직접 참여하세요.<br>음성 채팅까지 한곳에서.</p>
<div class="hero-actions">${cta()}</div>
<p class="release-status">${c.appReady ? '게임 내 친구 추가·초대는 별도' : '서비스 준비 중'}</p>
</div>
<div id="preview" class="hero-product" aria-label="모집방 참여 체험">${renderUseFlow()}</div>
</section>
<section id="features" class="section wrap" aria-labelledby="features-title">
<div id="how-it-works" class="section-heading"><h2 id="features-title">팀원 찾기부터 음성 대화까지.</h2></div>
<div class="benefit-grid">
<article id="auto-match" class="benefit-card"><div class="feature-icon">${icon('filters')}</div><h3>조건에 맞는<br>롤 듀오 찾기</h3><p>모드·포지션·음성 조건으로 팀원을 자동 매칭합니다.</p><details id="evidence-settings" class="screen-details"><summary>실제 화면 보기 <span aria-hidden="true">+</span></summary>${detailCapture('settings','게임 모드, 내 포지션, 인원, 플레이 목적, 음성 조건을 고르는 빠른매치 설정','빠른매치 조건 설정')}</details></article>
<article id="join" class="benefit-card"><div class="feature-icon">${icon('people')}</div><h3>멤버와<br>빈자리 확인</h3><p>티어·포지션을 보고 원하는 모집방에 직접 참여하세요.</p><details id="evidence-board" class="screen-details"><summary>실제 화면 보기 <span aria-hidden="true">+</span></summary>${renderProductPreview()}</details></article>
<article class="benefit-card"><div class="feature-icon">${icon('mic')}</div><h3>같은 방에서<br>음성 채팅</h3><p>디스코드 이동 없이, 모집 중에도 같은 방에서 대화하세요.</p><details id="evidence-room" class="screen-details"><summary>실제 화면 보기 <span aria-hidden="true">+</span></summary>${detailCapture('room','모집방 목록 옆에 열린 파티 패널의 참여 인원, 마이크 켜기, 채팅 영역','파티 음성·채팅')}</details></article>
</div></section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><h2 id="faq-title">궁금한 점</h2></div><div class="faq-list">${faqs.map(([q,a])=>`<details class="faq-item"><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
</main><footer class="site-footer wrap"><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><div class="footer-meta"><span>${e(new URL(c.origin).hostname)}</span><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots = `User-agent: *\nAllow: /\n${mode.indexable ? `\nSitemap: ${origin}/sitemap.xml\n` : '\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap = mode.indexable ? `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n` : null;
  const llms = `# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [큐메이트](${canonical}): 조건에 맞는 게임 팀원을 찾고, 같은 방에서 대화하며 파티를 구성하는 서비스\n\n## 팀원을 찾는 두 가지 방법\n1. 자동 매칭: 게임 모드·포지션·음성 사용 등 원하는 조건으로 팀원을 찾습니다.\n2. 직접 파티 찾기: 모집방의 멤버·티어·포지션·빈자리를 확인하고 참여합니다.\n\n## 참가한 뒤의 대화\n같은 방에서 음성·텍스트 대화를 이어갑니다. 정원이 다 차기 전, 모집이 진행되는 동안에도 대화하는 흐름입니다. 디스코드 친구 추가나 별도 음성 채널로 이동하는 과정은 필요하지 않습니다.\n\n## 별도로 필요한 것\n음성 사용에는 브라우저 마이크 권한 허용과 마이크 켜기가 필요합니다. 게임 실행과 게임 내 친구 추가·파티 초대는 게임에 따라 별도로 필요합니다. 매칭 완료 시간이나 절약 시간은 보장하지 않습니다.\n\n## 공개 상태\n${c.appReady ? '매칭 서비스 링크를 활성화했습니다.' : '서비스 준비 중입니다. 소개 페이지의 예시에서는 실제 참가·매칭·음성 연결을 실행하지 않습니다.'}\n\n## 화면 출처와 예시\n아래 실제 화면 캡처는 feature/quick-match-ui 브랜치 904cce415181aeb8a8802fc398f9be9e0835b1fe에서 촬영했습니다. 첫 화면의 참여 흐름은 실제 UI 구성을 요약한 설명용 예시입니다. 모든 인원·닉네임·전적·대화는 예시 데이터이며 실제 사용자 현황이나 성과 데이터가 아닙니다. 실제 매칭 및 음성 연결 검증이 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n\n이 파일은 보조 설명이며 검색 또는 AI 노출을 보장하지 않습니다.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
