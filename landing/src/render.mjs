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
<figcaption id="preview-caption">실제 UI를 실행해 촬영한 화면입니다. 닉네임·전적·모집방은 예시 데이터이며, 이 페이지에서 실제 매칭·음성 연결은 실행되지 않습니다.</figcaption></figure>`;
}
function detailCapture(name, alt, label) {
  const url = `/assets/ui/quick-match-${name}.webp`;
  return `<figure class="detail-capture"><a class="capture-link" href="${url}" aria-label="화면 크게 보기 — ${e(label)} 실제 UI 캡처"><img src="${url}" width="1440" height="900" alt="${e(alt)}" loading="lazy" decoding="async"><span class="capture-zoom">화면 크게 보기 ↗</span></a><figcaption>실제 UI 캡처 · 예시 데이터</figcaption></figure>`;
}

/** A compact interaction example, not a live room or a screenshot. No API or microphone access. */
export function renderUseFlow() {
  return `<div class="flow-demo" aria-label="모집방 참가와 대화 이용 예시">
<div class="demo-toolbar"><span>${icon('people')} 파티 찾기</span><span class="demo-label">이용 예시</span></div>
<div class="demo-steps"><span>01 인원 확인</span><i aria-hidden="true">→</i><span>02 참가</span><i aria-hidden="true">→</i><span>03 같은 방에서 대화</span></div>
<div class="room-preview">
<div class="room-heading"><span class="game-label">리그 오브 레전드</span><span class="recruiting">모집 중</span></div>
<h3>편하게 소통하며 자유 랭크 해요</h3>
<div class="condition-tags"><span>자유 랭크 · 5인</span><span>골드–플래티넘</span><span>${icon('mic')} 음성 사용</span></div>
<div class="count-row"><span>현재 참여 인원</span><strong><span class="before-join">2</span><span class="after-join">3</span><span class="count-total"> / 5명</span></strong></div>
<ul class="member-list" aria-label="모집방 구성원 예시">
<li><span class="avatar">A</span><span class="member-info"><strong>예시 팀원 A <small>방장</small></strong><span>골드 II · 원딜</span></span></li>
<li><span class="avatar avatar-alt">B</span><span class="member-info"><strong>예시 팀원 B</strong><span>플래티넘 IV · 정글</span></span></li>
<li class="after-join self-member"><span class="avatar avatar-self">나</span><span class="member-info"><strong>내 자리</strong><span>미드 · 참여 완료 예시</span></span>${icon('check')}</li>
</ul>
<details class="join-demo">
<summary class="join-example"><span class="before-join">${icon('plus')} 참여하기 <small>흐름 체험</small></span><span class="after-join">${icon('check')} 참여 전으로 돌아가기</span></summary>
<div class="voice-panel">
<div class="voice-panel-top"><h4>${icon('mic')} 파티 음성 채널</h4><span>3 / 5명</span></div>
<p class="voice-explainer">같은 방에 모인 사람들과 대화합니다.</p>
<div class="voice-members"><span><i class="avatar">A</i>팀원 A</span><span><i class="avatar avatar-alt">B</i>팀원 B</span><span><i class="avatar avatar-self">나</i>나</span></div>
<div class="voice-ready">${icon('mic')} 마이크를 켜면 음성 대화 시작</div>
<div class="chat-preview"><span>파티 채팅</span><p><strong>팀원 A</strong> 미드 자리로 오셨네요.</p><p><strong>나</strong> 네, 남은 두 분 기다리면 되겠네요.</p></div>
<p class="recruit-note">정원이 차기 전에도 함께 이야기하며 기다립니다.</p>
</div></details>
<div class="remaining-seats" aria-label="남은 빈자리"><span><i aria-hidden="true">+</i> 남은 자리</span><span><i aria-hidden="true">+</i> 남은 자리</span></div>
<p class="vacancy-note"><span class="before-join">3자리</span><span class="after-join">2자리</span>를 더 모집하고 있습니다.</p>
</div>
<div class="before-panel"><span class="panel-icon">${icon('chat')}</span><h4>방에 참가하면,<br>대화도 같은 곳에서.</h4><p>구성원 목록 옆에서<br>음성·채팅 패널이 열립니다.</p><span class="panel-hint">왼쪽 ‘참여하기’를 눌러보세요.</span></div>
<p class="demo-disclaimer">실제 UI 구성을 요약한 이용 예시입니다. 참가·음성 연결은 실행되지 않습니다.</p>
</div>`;
}

export function renderSite(c, env = {}) {
  const mode = resolveMode(c, env);
  const origin = new URL(c.origin).origin, canonical = `${origin}/`;
  const cta = (extra = '') => c.appReady
    ? `<a class="button button-primary ${extra}" href="${e(c.appUrl)}" data-cta="start-matching">팀원 찾기 시작하기 ${icon('arrow')}</a>`
    : `<a class="button button-primary ${extra}" href="#preview" data-cta="explore-preview">참여 흐름 체험하기 ${icon('arrow')}</a>`;
  const verification = Object.entries({google:'google-site-verification',naver:'naver-site-verification',bing:'msvalidate.01'})
    .filter(([key]) => c.verification?.[key]?.trim()).map(([key,name])=>`<meta name="${name}" content="${e(c.verification[key])}">`).join('\n');
  const imageMeta = c.media?.ogImage ? `<meta property="og:image" content="${e(origin + c.media.ogImage)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기"><meta name="twitter:image" content="${e(origin + c.media.ogImage)}"><meta name="twitter:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기">` : '';
  const schema = {'@context':'https://schema.org','@type':'WebSite',name:c.name,alternateName:c.alternateName,url:canonical,inLanguage:'ko-KR'};
  const faqs = [
    ['큐메이트는 어떤 서비스인가요?', '조건에 맞는 게임 팀원을 찾고 파티를 구성하는 서비스입니다. 원하는 조건으로 자동 매칭하거나 모집방의 멤버와 빈자리를 보고 직접 참여할 수 있습니다. 참가한 방에서 음성·텍스트 대화까지 이어집니다.'],
    ['자동 매칭과 직접 파티 찾기는 무엇이 다른가요?', '자동 매칭은 원하는 조건을 설정해 팀원을 찾는 방식입니다. 직접 찾기는 모집방의 멤버·티어·포지션·빈자리를 확인하고 들어갈 방을 고르는 방식입니다. 매칭에 걸리는 시간은 참여 인원과 조건에 따라 달라집니다.'],
    ['인원이 다 모여야 대화할 수 있나요?', '아닙니다. 방에 참가하면 모집이 진행되는 동안에도 같은 방의 사람들과 음성·텍스트로 대화하는 흐름입니다. 음성 대화를 사용하려면 브라우저의 마이크 권한을 허용하고 마이크를 켜야 합니다.'],
    ['디스코드나 게임 친구 추가가 필요한가요?', '큐메이트 안의 음성 대화에는 별도 디스코드 친구 추가나 채널 이동이 필요하지 않습니다. 게임 실행과 게임 내 친구 추가·파티 초대는 게임에 따라 별도로 필요합니다.'],
    ['어떤 게임의 팀원을 찾을 수 있나요?', '지원 대상은 리그 오브 레전드, 발로란트, 배틀그라운드입니다. 롤 듀오 구하기와 파티 찾기를 예시로 설명하며, 실제 이용 가능한 범위는 정식 서비스 공개 시 안내합니다.'],
    ['지금 바로 이용할 수 있나요?', c.appReady ? '팀원 찾기 시작하기를 누르면 매칭 서비스로 이동합니다. 이 소개 페이지의 이용 예시는 실제 참가·음성 연결을 실행하지 않습니다.' : '현재 서비스 준비 중입니다. 이 페이지에서는 이용 흐름만 체험할 수 있으며, 실제 참가·매칭·음성 연결은 실행되지 않습니다.'],
  ];
  const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${e(c.title)}</title>
<meta name="description" content="${e(c.description)}"><meta name="robots" content="${mode.indexable ? 'index, follow, max-image-preview:large' : 'noindex, nofollow'}"><meta name="theme-color" content="#05060f"><link rel="canonical" href="${e(canonical)}">
<meta property="og:type" content="website"><meta property="og:locale" content="ko_KR"><meta property="og:site_name" content="${e(c.name)}"><meta property="og:title" content="${e(c.title)}"><meta property="og:description" content="${e(c.description)}"><meta property="og:url" content="${e(canonical)}">
<meta name="twitter:card" content="${c.media?.ogImage ? 'summary_large_image' : 'summary'}"><meta name="twitter:title" content="${e(c.title)}"><meta name="twitter:description" content="${e(c.description)}">${imageMeta}${verification}
<script type="application/ld+json">${jsonForHtml(schema)}</script><link rel="stylesheet" href="/assets/site.css"></head>
<body><a class="skip-link" href="#main">본문으로 바로가기</a>
<header class="site-header"><div class="wrap header-inner"><a href="#main" class="brand-link" aria-label="큐메이트 홈">${wordmark()}</a><nav aria-label="주요 메뉴"><a href="#features">팀원 찾는 방법</a><a href="#preview">참여부터 대화까지</a><a href="#faq">자주 묻는 질문</a></nav><a class="header-cta" href="${c.appReady ? e(c.appUrl) : '#preview'}">${c.appReady ? '시작하기' : '이용 흐름 보기'} ${icon('arrow')}</a></div></header>
<main id="main" tabindex="-1">
<section class="hero wrap" aria-labelledby="hero-title">
<div class="hero-copy"><p class="hero-kicker">게임 팀원 찾기 · 자동 매칭 · 파티 음성 채팅</p>
<h1 id="hero-title">조건에 맞는<br>팀원을 찾고,<br><em>같은 방에서<br>바로 대화하세요.</em></h1>
<p class="hero-description">원하는 조건으로 자동 매칭하거나, <br>모집방의 멤버와 빈자리를 확인하고 참여하세요.</p>
<p class="hero-description hero-description-secondary">따로 연락할 곳을 정할 필요 없이, <br>모집부터 음성 대화까지 큐메이트 한 화면에서 이어집니다.</p>
<div class="hero-actions">${cta()}<a class="text-link" href="#auto-match">자동 매칭 알아보기 ${icon('arrow')}</a></div>
<p class="release-status">${c.appReady ? '게임 실행·친구 추가·파티 초대는 게임에서 진행합니다.' : '서비스 준비 중 · 실제 참가와 음성 연결은 정식 서비스에서 제공됩니다.'}</p>
</div>
<div id="preview" class="hero-product" aria-labelledby="preview-title"><div class="demo-heading"><h2 id="preview-title">인원을 보고, 참가하면, 같은 방에서 대화.</h2><span>롤 파티 찾기 예시</span></div>${renderUseFlow()}</div>
</section>
<section id="features" class="section wrap" aria-labelledby="features-title">
<div class="section-heading"><p class="eyebrow">팀원을 찾는 것부터, 함께 준비하는 것까지</p><h2 id="features-title">따로 하던 과정을<br><em>한 화면에 모았습니다.</em></h2></div>
<div class="benefit-grid">
<article id="auto-match" class="benefit-card"><span class="benefit-number">01 · 자동 매칭</span><div class="feature-icon">${icon('filters')}</div><h3>조건을 정하면,<br>팀원을 자동으로 찾습니다.</h3><p>모집방을 하나씩 고르는 대신, 게임 모드·포지션·음성 사용 등 원하는 조건으로 자동 매칭을 시작하세요.</p><a class="text-link" href="#evidence-settings">빠른매치 조건 설정 보기 ${icon('arrow')}</a></article>
<article id="join" class="benefit-card"><span class="benefit-number">02 · 직접 파티 찾기</span><div class="feature-icon">${icon('people')}</div><h3>누구와 함께할지,<br>참여 전에 확인합니다.</h3><p>모집방에서 참여 중인 멤버·티어·포지션·빈자리를 확인하세요. 누가 있는지 보고, 함께할 방을 고를 수 있습니다.</p><a class="text-link" href="#evidence-board">멤버와 빈자리 보기 ${icon('arrow')}</a></article>
<article class="benefit-card"><span class="benefit-number">03 · 파티 음성 채팅</span><div class="feature-icon">${icon('mic')}</div><h3>참여한 방에서,<br>음성 대화를 시작합니다.</h3><p>디스코드로 옮겨갈 필요 없이 같은 방에서 음성·텍스트로 이야기하세요. 정원이 다 차기 전에도 함께 대화하며 기다립니다.</p><a class="text-link" href="#evidence-room">파티 음성·채팅 보기 ${icon('arrow')}</a></article>
</div></section>
<section class="continuity-section" aria-labelledby="continuity-title"><div class="wrap continuity-inner"><div><p class="eyebrow">참가에서 끝나지 않는 파티 찾기</p><h2 id="continuity-title">모이는 동안에도,<br>함께 이야기할 수 있습니다.</h2><p>방에 두 명만 있어도 대화는 시작할 수 있습니다.<br>같이할 사람을 더 기다리는 동안 플레이할 포지션과 방식을 맞춰보세요.</p></div><div class="continuity-flow"><span>멤버·빈자리 확인</span>${icon('arrow')}<span>방 참가</span>${icon('arrow')}<strong>모집 중에도 음성·채팅</strong><p>연락처 교환이나 별도 음성 채널 이동 없이</p></div></div></section>
<section id="how-it-works" class="section wrap evidence-section" aria-labelledby="how-title"><div class="section-heading"><p class="eyebrow">큐메이트에서 이렇게 이어집니다</p><h2 id="how-title">찾는 방법은 두 가지.<br>만나서 대화하는 곳은 한곳.</h2><p>조건에 맞춰 찾거나, 직접 고르거나. 참가한 뒤에는 같은 파티 공간에서 대화합니다.</p></div>
<div class="evidence-grid">
<article id="evidence-settings" class="evidence-card"><div class="evidence-copy"><span>자동 매칭</span><h3>원하는 조건으로 팀원 찾기</h3></div>${detailCapture('settings','게임 모드, 내 포지션, 인원, 플레이 목적, 음성 조건을 고르는 빠른매치 설정','빠른매치 조건 설정')}</article>
<article id="evidence-board" class="evidence-card"><div class="evidence-copy"><span>직접 참가</span><h3>멤버와 빈자리를 보고 방 선택</h3></div>${renderProductPreview()}</article>
<article id="evidence-room" class="evidence-card"><div class="evidence-copy"><span>참가 후</span><h3>모집방 옆에서 음성·채팅</h3></div>${detailCapture('room','모집방 목록 옆에 열린 파티 패널의 참여 인원, 마이크 켜기, 채팅 영역','파티 음성·채팅')}</article>
</div><p class="section-note">실제 UI를 실행한 캡처입니다. 닉네임·전적·모집방은 예시 데이터입니다. 음성 사용에는 마이크 권한 허용과 마이크 켜기가 필요하며, 게임 내 친구 추가·파티 초대는 별도로 진행합니다.</p></section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><p class="eyebrow">자주 묻는 질문</p><h2 id="faq-title">이용 전에<br>확인하세요.</h2></div><div class="faq-list">${faqs.map(([q,a])=>`<details><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
<section class="wrap final-section" aria-labelledby="final-title"><div class="final-card"><p class="eyebrow">큐메이트 · 게임 팀원 찾기</p><h2 id="final-title">함께할 팀원을 찾고,<br><em>같은 방에서 준비하세요.</em></h2><p>자동 매칭 · 멤버와 빈자리 확인 · 파티 음성 채팅</p>${cta()}<small>${c.appReady ? '실제 게임 실행과 파티 초대는 게임에서 진행합니다.' : '서비스 준비 중 · 정식 서비스 연결 전입니다.'}</small></div></section>
</main><footer class="site-footer wrap"><div><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><p>팀원 찾기부터 파티 대화까지, 한곳에서.</p></div><div class="footer-meta"><span>${e(new URL(c.origin).hostname)}</span><a href="#main">맨 위로 ↑</a><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots = `User-agent: *\nAllow: /\n${mode.indexable ? `\nSitemap: ${origin}/sitemap.xml\n` : '\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap = mode.indexable ? `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n` : null;
  const llms = `# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [큐메이트](${canonical}): 조건에 맞는 게임 팀원을 찾고, 같은 방에서 대화하며 파티를 구성하는 서비스\n\n## 팀원을 찾는 두 가지 방법\n1. 자동 매칭: 게임 모드·포지션·음성 사용 등 원하는 조건으로 팀원을 찾습니다.\n2. 직접 파티 찾기: 모집방의 멤버·티어·포지션·빈자리를 확인하고 참여합니다.\n\n## 참가한 뒤의 대화\n같은 방에서 음성·텍스트 대화를 이어갑니다. 정원이 다 차기 전, 모집이 진행되는 동안에도 대화하는 흐름입니다. 디스코드 친구 추가나 별도 음성 채널로 이동하는 과정은 필요하지 않습니다.\n\n## 별도로 필요한 것\n음성 사용에는 브라우저 마이크 권한 허용과 마이크 켜기가 필요합니다. 게임 실행과 게임 내 친구 추가·파티 초대는 게임에 따라 별도로 필요합니다. 매칭 완료 시간이나 절약 시간은 보장하지 않습니다.\n\n## 공개 상태\n${c.appReady ? '매칭 서비스 링크를 활성화했습니다.' : '서비스 준비 중입니다. 소개 페이지의 예시에서는 실제 참가·매칭·음성 연결을 실행하지 않습니다.'}\n\n## 화면 출처와 예시\n아래 실제 화면 캡처는 feature/quick-match-ui 브랜치 904cce415181aeb8a8802fc398f9be9e0835b1fe에서 촬영했습니다. 첫 화면의 참여 흐름은 실제 UI 구성을 요약한 설명용 예시입니다. 모든 인원·닉네임·전적·대화는 예시 데이터이며 실제 사용자 현황이나 성과 데이터가 아닙니다. 실제 매칭 및 음성 연결 검증이 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n\n이 파일은 보조 설명이며 검색 또는 AI 노출을 보장하지 않습니다.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
