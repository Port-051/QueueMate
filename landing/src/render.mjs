/** Static product presentation. Optional production analytics are installed separately by the build. */
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
  bow: '<path d="M5 3c11 1 15 5 16 16M5 3l2 14 14 2M3 21 19 5m-5 0h5v5"/>',
  jungle: '<path d="m12 3 7 9h-4l5 7H4l5-7H5l7-9Zm0 16v3"/>',
  lane: '<path d="m9 3 8 8-6 6-8-8 6-6Zm6 4 6 6-8 8-6-6"/>',
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

/** Actual feature/quick-match-ui screenshots. Each product screen appears once in the persuasion flow. */
export function renderUseFlow() {
  return `<figure class="hero-screen"><a class="capture-link" href="/assets/ui/quick-match-board.webp" aria-label="큐메이트 모집방 실제 화면 크게 보기"><img src="/assets/ui/quick-match-board.webp" width="1440" height="900" alt="멤버와 빈자리를 확인할 수 있는 큐메이트 실제 모집방 화면" fetchpriority="high" decoding="async"></a><figcaption>실제 UI · 예시 데이터</figcaption></figure>`;
}

export function renderSite(c, env = {}) {
  const mode = resolveMode(c, env);
  const origin = new URL(c.origin).origin, canonical = `${origin}/`;
  const cta = (extra = '') => c.appReady
    ? `<a class="button button-primary ${extra}" href="${e(c.appUrl)}" data-cta="start-matching">팀원 찾기 시작하기 ${icon('arrow')}</a>`
    : `<a class="button button-primary ${extra}" href="#preview" data-cta="explore-preview">서비스 화면 보기 ${icon('arrow')}</a>`;
  const verification = Object.entries({google:'google-site-verification',naver:'naver-site-verification',bing:'msvalidate.01'}).filter(([key])=>c.verification?.[key]?.trim()).map(([key,name])=>`<meta name="${name}" content="${e(c.verification[key])}">`).join('\n');
  const imageMeta = c.media?.ogImage ? `<meta property="og:image" content="${e(origin+c.media.ogImage)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기"><meta name="twitter:image" content="${e(origin+c.media.ogImage)}"><meta name="twitter:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기">` : '';
  const schema={'@context':'https://schema.org','@type':'WebSite',name:c.name,alternateName:c.alternateName,url:canonical,inLanguage:'ko-KR'};
  const faqs=[
    ['지금 이용할 수 있나요?',c.appReady?'시작하기를 누르면 매칭 서비스로 이동합니다.':'서비스 준비 중입니다. 지금은 실제 서비스 화면을 확인할 수 있습니다.'],
    ['음성 대화는 어떻게 하나요?','브라우저에서 마이크 권한을 허용하면 참여한 모집방에서 음성 대화를 사용할 수 있습니다. 게임 내 친구 추가·파티 초대는 별도로 진행합니다.'],
    ['어떤 게임을 지원하나요?','롤·발로란트·배틀그라운드를 대상으로 준비 중입니다. 출시 시 실제 지원 범위를 안내합니다.'],
  ];
  const html=`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${e(c.title)}</title>
<meta name="description" content="${e(c.description)}"><meta name="robots" content="${mode.indexable?'index, follow, max-image-preview:large':'noindex, nofollow'}"><meta name="theme-color" content="#080a10"><link rel="canonical" href="${e(canonical)}">
<meta property="og:type" content="website"><meta property="og:locale" content="ko_KR"><meta property="og:site_name" content="${e(c.name)}"><meta property="og:title" content="${e(c.title)}"><meta property="og:description" content="${e(c.description)}"><meta property="og:url" content="${e(canonical)}">
<meta name="twitter:card" content="${c.media?.ogImage?'summary_large_image':'summary'}"><meta name="twitter:title" content="${e(c.title)}"><meta name="twitter:description" content="${e(c.description)}">${imageMeta}${verification}
<script type="application/ld+json">${jsonForHtml(schema)}</script><link rel="stylesheet" href="/assets/site.css"><link rel="stylesheet" href="/assets/concise.css"><link rel="stylesheet" href="/assets/product-ui.css"></head>
<body><a class="skip-link" href="#main">본문으로 바로가기</a>
<header class="site-header"><div class="wrap header-inner"><a href="#main" class="brand-link" aria-label="큐메이트 홈">${wordmark()}</a><nav aria-label="주요 메뉴"><a href="#preview">서비스</a><a href="#quick-match">빠른매치</a><a href="#faq">FAQ</a></nav><a class="header-cta" href="${c.appReady?e(c.appUrl):'#preview'}">${c.appReady?'시작하기':'화면 보기'} ${icon('arrow')}</a></div></header>
<main id="main" tabindex="-1">
<section class="hero wrap" aria-labelledby="hero-title"><div class="hero-copy"><p class="hero-kicker">롤 듀오 · 파티 찾기</p><h1 id="hero-title">팀원 찾기부터 대화까지,<br><em>한곳에서.</em></h1><p class="hero-description">멤버와 빈자리를 보고 바로 참여하고,<br>모인 사람들과 같은 방에서 바로 대화하세요.</p><div class="hero-actions">${cta()}</div><p class="release-status">${c.appReady?'게임 내 친구 추가·초대는 별도':'서비스 준비 중'}</p></div><div id="preview" class="hero-product">${renderUseFlow()}</div></section>
<section id="features" class="value-section wrap" aria-labelledby="features-title">
<div class="value-intro"><span>게시글을 찾고, 연락하고, 음성방을 다시 만드는 과정 대신</span><h2 id="features-title">보고. 들어가고.<br><em>바로 이야기합니다.</em></h2></div>
<div class="value-points"><article><strong>01</strong><h3>누가 있는지 먼저 봅니다.</h3><p>티어·포지션과 남은 자리를 확인하고 들어갈 방을 고릅니다.</p></article><article><strong>02</strong><h3>원하는 방에 바로 참여합니다.</h3><p>별도의 모집글이나 연락 과정을 거치지 않고 같은 모집방에 합류합니다.</p></article><article><strong>03</strong><h3>모집 중에도 대화합니다.</h3><p>정원이 다 차기 전부터 참여한 사람들과 음성·채팅을 이어갑니다.</p></article></div>
<div class="room-proof"><div class="room-proof-copy"><span>참여한 다음</span><h3>다른 곳으로<br>옮겨갈 필요 없습니다.</h3><p>모집방 옆에 음성·채팅이 바로 열립니다. 사람을 더 기다리는 동안에도 이미 모인 팀원과 이야기할 수 있습니다.</p></div><figure><a class="capture-link" href="/assets/ui/quick-match-room.webp" aria-label="참여 후 음성 채팅 실제 화면 크게 보기"><img src="/assets/ui/quick-match-room.webp" width="1440" height="900" alt="모집방 옆에 음성 채널과 채팅 패널이 열린 큐메이트 실제 화면" loading="lazy" decoding="async"></a><figcaption>참여 후 · 실제 UI, 예시 데이터</figcaption></figure></div>
</section>
<section id="quick-match" class="quick-match-section wrap" aria-labelledby="quick-match-title"><div class="quick-match-copy"><span>방을 직접 고르기 귀찮다면</span><h2 id="quick-match-title">조건만 정하고,<br><em>빠른매치.</em></h2><p>모드·포지션·인원·플레이 목적·음성 조건을 정하면 맞는 팀원을 자동으로 찾습니다.</p></div><figure class="quick-match-figure"><a class="capture-link quick-match-crop" href="/assets/ui/quick-match-settings.webp" aria-label="빠른매치 설정 실제 화면 크게 보기"><img src="/assets/ui/quick-match-settings.webp" width="1440" height="900" alt="게임 모드, 포지션, 인원, 플레이 목적과 음성 조건을 고르는 큐메이트 빠른매치 화면" loading="lazy" decoding="async"></a><figcaption>빠른매치 설정 · 실제 UI, 예시 데이터</figcaption></figure></section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><h2 id="faq-title">궁금한 점</h2></div><div class="faq-list">${faqs.map(([q,a])=>`<details class="faq-item"><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
<p class="demo-disclaimer wrap">화면의 닉네임·전적은 예시 데이터입니다. 이 랜딩에서는 실제 참가·음성 연결은 되지 않습니다.</p>
</main><footer class="site-footer wrap"><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><div class="footer-meta"><span>${e(new URL(c.origin).hostname)}</span><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots=`User-agent: *\nAllow: /\n${mode.indexable?`\nSitemap: ${origin}/sitemap.xml\n`:'\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap=mode.indexable?`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n`:null;
  const llms=`# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [큐메이트](${canonical}): 조건에 맞는 게임 팀원을 찾고, 모집방에서 음성·텍스트 대화를 이어가는 서비스\n\n## 이용 흐름\n모집방에서 현재 멤버의 티어·포지션과 빈자리를 확인한 뒤 참여합니다. 참여한 뒤에는 같은 화면의 음성·채팅으로 대화를 이어갑니다. 정원이 다 차기 전에도 대화할 수 있습니다.\n\n## 빠른매치\n직접 모집방을 고르는 대신 게임 모드·포지션·인원·플레이 목적·음성 조건을 정해 자동으로 팀원을 찾을 수 있습니다.\n\n## 별도로 필요한 것\n음성 사용에는 브라우저 마이크 권한 허용과 마이크 켜기가 필요합니다. 게임 실행과 게임 내 친구 추가·파티 초대는 게임에 따라 별도로 필요합니다. 매칭 완료 시간이나 절약 시간은 보장하지 않습니다.\n\n## 공개 상태\n${c.appReady?'매칭 서비스 링크를 활성화했습니다.':'서비스 준비 중입니다. 소개 페이지에서는 실제 참가·매칭·음성 연결을 실행하지 않습니다.'}\n\n## 화면 출처\n실제 UI 캡처는 feature/quick-match-ui 브랜치 904cce415181aeb8a8802fc398f9be9e0835b1fe에서 촬영했습니다. 닉네임·전적·대화는 예시 데이터입니다. 실제 매칭 및 음성 연결 검증이 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
