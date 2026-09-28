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

function memberCard({letter, name, role, tier, host = false}) {
  return `<div class="member-card"><div class="member-name"><span class="avatar">${e(letter)}${host ? `<span class="crown">${icon('crown')}</span>` : ''}</span><strong>${e(name)}</strong></div><dl class="member-facts"><div><dt>티어</dt><dd class="rank">${icon('shield')}${e(tier)}</dd></div><div><dt>포지션</dt><dd>${icon('filters')}${e(role)}</dd></div><div><dt>승률</dt><dd>—</dd></div><div><dt>KDA</dt><dd>—</dd></div></dl><div class="member-caption">전적 연동 대기</div></div>`;
}
function emptySeat(role) {
  return `<div class="member-card empty-seat"><span class="seat-status">모집 중</span><span class="seat-plus">${icon('plus')}</span><strong>${e(role)}</strong><span class="seat-tier">실버 ~ 플래티넘</span><span class="seat-voice">${icon('mic')} 마이크 사용</span></div>`;
}
function roomBubble({title, time, role, compact = false}) {
  return `<article class="room-bubble${compact ? ' secondary-room' : ''}" aria-label="예시 모집방"><header class="room-header"><h3>${e(title)}</h3><span>${e(time)}</span></header><div class="members">${memberCard({letter:'A',name:'테스트 원딜',role:'원딜',tier:'골드 II',host:true})}${emptySeat(role)}</div><span class="bubble-tail" aria-hidden="true"></span></article>`;
}

/** Reference-based HTML illustration, explicitly not an app screenshot or connected service. */
export function renderProductPreview() {
  return `<figure class="product-figure" aria-labelledby="preview-caption">
<div class="product-frame"><div class="frame-bar"><span class="window-dots" aria-hidden="true"><i></i><i></i><i></i></span><span>QueueMate · 팀원 찾기</span><span class="frame-label">UI 미리보기</span></div>
<div class="app-preview"><div class="app-sidebar" aria-hidden="true">${wordmark(true)}<div class="demo-game">LoL<span>리그 오브 레전드</span></div><div class="demo-nav"><span class="selected">${icon('people')} 팀원 찾기</span><span>${icon('clock')} 예약 모집</span><span>${icon('chat')} 메시지</span></div><div class="side-note">찾고, 모이고, 대화까지.<br>한 페이지에서.</div></div>
<div class="app-content"><div class="app-title"><strong>어떤 팀원과 함께할까요?</strong><span>리그 오브 레전드</span></div><div class="demo-filters" role="group" aria-label="조건 예시"><span class="is-active">랭크</span><span>실시간</span><span>${icon('filters')} 포지션</span><span>${icon('mic')} 음성</span></div><div class="demo-workspace"><div class="demo-rooms">${roomBubble({title:'편하게 소통하며 같이 해요',time:'지금',role:'서포터'})}<div class="next-room"><div><span class="status-dot"></span><strong>저녁에 함께할 팀원을 구해요</strong></div><span>${icon('clock')} 예약 모집 예시</span></div></div>
<div class="conversation"><div class="conversation-head"><span class="status-dot"></span><strong>우리 파티</strong><span>대화 예시</span></div><div class="voice-people"><div><span class="avatar speaking">A</span><strong>테스트 원딜</strong><small>${icon('mic')} 음성 사용</small></div><div><span class="avatar avatar-b">B</span><strong>테스트 서포터</strong><small>${icon('mic')} 음성 사용</small></div></div><div class="chat-sample"><span>테스트 서포터</span><p>저 서포터 할게요!</p><div class="sent"><span>테스트 원딜</span><p>좋아요. 같이 한 판 해요.</p></div></div><div class="chat-composer">모집부터 대화까지 한 곳에서 ${icon('chat')}</div></div></div></div></div></div>
<figcaption id="preview-caption">기존 UI 코드를 바탕으로 구성한 화면 예시입니다. 테스트용 정보이며 실제 모집·음성 연결은 실행되지 않습니다.</figcaption></figure>`;
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
    ['지금이 아니라 나중에 함께할 팀원도 찾을 수 있나요?', '실시간 모집과 예약 모집을 구분하는 구성을 준비하고 있습니다. 예약 모집에서는 함께할 시간을 확인하고 팀원을 찾는 방식입니다.'],
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
<main id="main" tabindex="-1"><section class="hero wrap" aria-labelledby="hero-title"><div class="hero-kicker"><span class="status-dot"></span>큐메이트 · 함께할 팀원을 찾는 새로운 방법</div><h1 id="hero-title">롤 듀오 구하기,<br><em>이제 한 페이지에서.</em></h1><p class="hero-description">원하는 조건의 팀원을 찾고, 모이는 동안 대화하세요. <br>팀원 찾기부터 파티 준비까지, 큐메이트에서.</p><div class="hero-actions">${cta()}<a class="button button-secondary" href="#how-it-works">이용 방법 보기 ${icon('down')}</a></div><p class="release-status">${c.appReady ? '시작하기를 누르면 매칭 서비스로 이동합니다.' : '서비스 준비 중 · 지금은 화면과 이용 흐름을 먼저 확인해 보세요.'}</p><div class="game-strip" aria-label="지원 대상 게임"><span>함께할 게임</span><b>LEAGUE OF LEGENDS</b><b>VALORANT</b><b>PUBG</b><small>공개 범위는 출시 시 안내</small></div></section>
<section id="preview" class="preview-section wrap" aria-labelledby="preview-title"><div class="preview-heading"><div><p class="eyebrow">TEAM UP, IN ONE PLACE</p><h2 id="preview-title">어떤 팀원인지 보고, 바로 대화로.</h2></div><span class="preview-tag">화면 예시</span></div>${renderProductPreview()}<div class="preview-points"><span>${icon('check')} 조건을 먼저 확인하고</span><span>${icon('check')} 빈자리에 참여하고</span><span>${icon('check')} 같은 화면에서 대화하고</span></div></section>
<section id="features" class="section wrap" aria-labelledby="features-title"><div class="section-heading"><p class="eyebrow">팀원 찾기에 필요한 것만</p><h2 id="features-title">그냥 한 명 말고,<br>같이하고 싶은 팀원.</h2><p>실력도, 포지션도, 대화 방식도.<br>함께할 팀원을 고를 때 확인하고 싶은 조건을 모았습니다.</p></div><div class="feature-grid"><article class="feature-card"><span class="feature-icon">${icon('filters')}</span><span class="feature-number">01</span><h3>조건부터 맞추세요</h3><p>티어, 포지션, 마이크 사용 여부.<br>나와 맞는 조건의 롤 듀오를 찾아보세요.</p><div class="feature-chips"><span>티어</span><span>포지션</span><span>마이크</span></div></article><article class="feature-card"><span class="feature-icon">${icon('people')}</span><span class="feature-number">02</span><h3>누가 있는지 한눈에</h3><p>방에 있는 사람과 비어 있는 자리를 보고,<br>함께하고 싶은 파티를 선택하세요.</p><div class="mini-seats" aria-hidden="true"><span>A</span><span>B</span><span>+</span><span>+</span></div></article><article class="feature-card"><span class="feature-icon">${icon('mic')}</span><span class="feature-number">03</span><h3>모이는 동안에도 대화</h3><p>새로운 창을 오가는 대신,<br>모집방에서 음성과 채팅으로 이야기하세요.</p><div class="mini-wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><span>우리 파티에서</span></div></article></div><p class="section-note">${c.appReady ? '실제 사용 가능한 기능과 조건은 매칭 서비스에서 확인하세요.' : '위 내용은 준비 중인 서비스의 이용 방식입니다. 실제 제공 범위는 서비스 공개 시 안내합니다.'}</p></section>
<section id="how-it-works" class="how-section section" aria-labelledby="how-title"><div class="wrap how-grid"><div class="section-heading"><p class="eyebrow">복잡한 설명 없이, 세 단계</p><h2 id="how-title">찾고, 모이고,<br><em>함께 시작하세요.</em></h2><p>롤 파티 찾기를 여기저기서 반복하지 않도록.<br>하나의 흐름으로 이어갑니다.</p><a class="text-link" href="#preview">화면 다시 보기 ${icon('arrow')}</a></div><ol class="steps"><li><span class="step-number">01</span><div><h3>원하는 조건을 고르세요</h3><p>게임 모드와 필요한 포지션, 티어 범위,<br>음성 사용 조건을 확인합니다.</p></div></li><li><span class="step-number">02</span><div><h3>함께할 파티를 찾으세요</h3><p>모집방의 구성원과 빈자리를 살펴보고,<br>나에게 맞는 팀원을 찾습니다.</p></div></li><li><span class="step-number">03</span><div><h3>이야기하며 게임을 준비하세요</h3><p>팀원이 모이는 동안 대화를 나누고,<br>함께 플레이할 준비를 합니다.</p></div></li></ol></div></section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><p class="eyebrow">FAQ</p><h2 id="faq-title">궁금한 점이<br>있으신가요?</h2><p>시작하기 전에 확인해 보세요.</p></div><div class="faq-list">${faqs.map(([q,a])=>`<details><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
<section class="wrap final-section" aria-labelledby="final-title"><div class="final-card"><p class="eyebrow">NEXT GAME, WITH YOUR MATE</p><h2 id="final-title">다음 판은,<br>함께할 팀원부터.</h2><p>나와 맞는 팀원을 찾는 새로운 방법, 큐메이트.</p>${cta()}<small>${c.appReady ? '매칭 서비스로 이동합니다.' : '정식 서비스 공개를 준비하고 있습니다.'}</small></div></section></main>
<footer class="site-footer wrap"><div><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><p>같이할 사람이 필요한 순간, 큐메이트.</p></div><div class="footer-meta"><span>q-mate.com</span><a href="#main">맨 위로 ↑</a><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots = `User-agent: *\nAllow: /\n${mode.indexable ? `\nSitemap: ${origin}/sitemap.xml\n` : '\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap = mode.indexable ? `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n` : null;
  const llms = `# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [서비스 소개](${canonical}): 조건 기반 팀원 찾기, 모집방과 대화의 이용 흐름\n\n## 공개 상태\n${c.appReady ? '매칭 서비스 링크를 활성화했습니다.' : '서비스 준비 중입니다. 이 랜딩 페이지에서 매칭이나 음성 연결은 실행되지 않습니다.'}\n\n## 화면 예시\n기존 UI 소스를 참고해 정적으로 구성했습니다. 테스트용 정보이며, 실제 사용자·모집방·성과 데이터가 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n\n이 안내 파일은 보조 설명이며 검색 또는 AI 노출을 보장하지 않습니다.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
