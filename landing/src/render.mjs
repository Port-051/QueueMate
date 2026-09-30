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

export function renderSite(c, env = {}) {
  const mode = resolveMode(c, env);
  const origin = new URL(c.origin).origin, canonical = `${origin}/`;
  const cta = (extra = '') => c.appReady
    ? `<a class="button button-primary ${extra}" href="${e(c.appUrl)}" data-cta="start-matching">팀원 찾기 시작하기 ${icon('arrow')}</a>`
    : `<a class="button button-primary ${extra}" href="#preview" data-cta="explore-preview">어떻게 간편해지나요? ${icon('arrow')}</a>`;
  const verification = Object.entries({google:'google-site-verification',naver:'naver-site-verification',bing:'msvalidate.01'})
    .filter(([key]) => c.verification?.[key]?.trim()).map(([key,name])=>`<meta name="${name}" content="${e(c.verification[key])}">`).join('\n');
  const imageMeta = c.media?.ogImage ? `<meta property="og:image" content="${e(origin + c.media.ogImage)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기"><meta name="twitter:image" content="${e(origin + c.media.ogImage)}"><meta name="twitter:image:alt" content="큐메이트 — 롤 듀오와 파티 찾기">` : '';
  const schema = {'@context':'https://schema.org','@type':'WebSite',name:c.name,alternateName:c.alternateName,url:canonical,inLanguage:'ko-KR'};
  const faqs = [
    ['기존에 게시글을 보고 연락하는 것과 무엇이 다른가요?', '큐메이트는 모집방에서 참여 인원과 빈자리를 먼저 확인하고, 참가한 방 안에서 음성·텍스트 대화까지 이어가도록 만듭니다. 따로 연락처를 주고받거나 대화할 곳을 정하는 과정을 줄이는 것이 핵심입니다.'],
    ['음성 대화하려면 디스코드가 필요한가요?', '큐메이트의 방 안에서 음성 대화를 이용하는 흐름이라 디스코드로 이동하거나 디스코드 친구를 추가할 필요가 없습니다. 음성을 사용하려면 브라우저의 마이크 권한을 허용하고 마이크를 켜야 합니다.'],
    ['게임 안에서 친구 추가나 파티 초대도 없어지나요?', '아닙니다. 큐메이트가 줄이는 것은 팀원을 구하고 연락처를 교환해 대화할 곳으로 옮겨가는 과정입니다. 실제 게임 실행과 게임 내 친구 추가·파티 초대는 게임에 따라 별도로 필요합니다.'],
    ['방을 직접 고르지 않고 팀원을 찾을 수도 있나요?', '빠른매치에 원하는 조건을 설정해 팀원을 찾는 흐름도 준비하고 있습니다. 모집방에서 직접 고르는 방식과 함께 사용할 수 있으며, 매칭 완료 시간은 보장하지 않습니다.'],
    ['어떤 게임을 위한 서비스인가요?', '리그 오브 레전드, 발로란트, 배틀그라운드를 대상으로 준비하고 있습니다. 롤 듀오 구하기와 파티 찾기에 필요한 포지션·음성 조건 등을 다룹니다. 실제 지원 범위와 마이크 사용 조건은 정식 서비스 공개 시 확인하세요.'],
    ['지금 바로 이용할 수 있나요?', c.appReady ? '팀원 찾기 시작하기를 누르면 매칭 서비스로 이동합니다. 이 소개 페이지의 예시 자체에서는 모집이나 매칭이 실행되지 않습니다.' : '현재 서비스 준비 중입니다. 이 페이지에서는 달라지는 이용 흐름을 안내하며, 실제 매칭·음성 연결은 실행되지 않습니다.'],
  ];
  const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${e(c.title)}</title>
<meta name="description" content="${e(c.description)}"><meta name="robots" content="${mode.indexable ? 'index, follow, max-image-preview:large' : 'noindex, nofollow'}"><meta name="theme-color" content="#05060f"><link rel="canonical" href="${e(canonical)}">
<meta property="og:type" content="website"><meta property="og:locale" content="ko_KR"><meta property="og:site_name" content="${e(c.name)}"><meta property="og:title" content="${e(c.title)}"><meta property="og:description" content="${e(c.description)}"><meta property="og:url" content="${e(canonical)}">
<meta name="twitter:card" content="${c.media?.ogImage ? 'summary_large_image' : 'summary'}"><meta name="twitter:title" content="${e(c.title)}"><meta name="twitter:description" content="${e(c.description)}">${imageMeta}${verification}
<script type="application/ld+json">${jsonForHtml(schema)}</script><link rel="stylesheet" href="/assets/site.css"></head>
<body><a class="skip-link" href="#main">본문으로 바로가기</a>
<header class="site-header"><div class="wrap header-inner"><a href="#main" class="brand-link" aria-label="큐메이트 홈">${wordmark()}</a><nav aria-label="주요 메뉴"><a href="#preview">줄어드는 과정</a><a href="#features">큐메이트의 차이</a><a href="#faq">자주 묻는 질문</a></nav><a class="header-cta" href="${c.appReady ? e(c.appUrl) : '#preview'}">${c.appReady ? '시작하기' : '이용 흐름 보기'} ${icon('arrow')}</a></div></header>
<main id="main" tabindex="-1">
<section class="hero wrap" aria-labelledby="hero-title">
<div class="hero-copy"><p class="hero-kicker">롤 듀오 구하기 · 게임 파티 찾기</p>
<h1 id="hero-title">같이할 사람은 찾았는데,<br>왜 아직 <em>게임을<br>못 하고 있죠?</em></h1>
<p class="hero-description">친구 추가하고, 답장 기다리고, 디스코드로 옮기고.<br>팀원 구한 뒤에도 길었던 준비, 이제 줄여보세요.</p>
<p class="hero-answer"><strong>인원 확인부터 참가, 음성 대화까지.</strong><br>큐메이트 한곳에서 이어집니다.</p>
<div class="hero-actions">${cta()}</div><p class="release-status">${c.appReady ? '팀원을 찾은 뒤 게임 실행·파티 초대는 게임에서 진행합니다.' : '서비스 준비 중 · 아래에서 이용 흐름을 먼저 확인하세요.'}</p>
</div>
<div class="journey" aria-label="큐메이트 이용 흐름 예시">
<div class="journey-top"><span class="journey-eyebrow">이제, 여기서 만나세요</span><span class="demo-label">이용 흐름 예시</span></div>
<div class="journey-card"><div class="journey-icon">${icon('people')}</div><div><span class="step-eyebrow">확인</span><h2>몇 명 모였는지, 묻지 말고 보세요.</h2><div class="party-count"><span>참여 <b>2</b> / 5명</span><span>3자리 남음</span></div><div class="seats" aria-label="참여 중 두 명, 빈자리 세 개"><span class="seat occupied">A</span><span class="seat occupied">B</span><span class="seat empty">+</span><span class="seat empty">+</span><span class="seat empty">+</span></div></div></div>
<div class="journey-connector" aria-hidden="true">${icon('down')}</div>
<div class="journey-card"><div class="journey-icon">${icon('plus')}</div><div><span class="step-eyebrow">참가</span><h2>함께하고 싶은 방에 들어가세요.</h2><p>따로 연락할 사람을 찾는 대신, 방에서 만나요.</p><a class="join-example" href="#join">참가 방식 알아보기 ${icon('arrow')}</a></div></div>
<div class="journey-connector" aria-hidden="true">${icon('down')}</div>
<div class="journey-card voice-card"><div class="journey-icon">${icon('mic')}</div><div><span class="step-eyebrow">대화</span><h2>“안녕하세요. 같이 한 판 할까요?”</h2><p>디스코드 이동 없이, 이곳의 음성 채널에서.</p><span class="voice-hint">${icon('mic')} 브라우저 마이크 허용 후 이용</span></div></div>
<p class="demo-note">참여 인원과 대화는 설명용 예시입니다. 실제 접속 현황이 아닙니다.</p>
</div></section>
<section id="preview" class="section comparison-section" aria-labelledby="preview-title"><div class="wrap">
<div class="section-heading"><p class="eyebrow">줄이고 싶은 건, 게임 전의 번거로움</p><h2 id="preview-title">사람은 찾았는데,<br>연락은 또 다른 일이었으니까.</h2><p>같이할 팀원을 찾은 다음에도 이어지던 준비를 한곳으로 모았습니다.</p></div>
<div class="comparison-grid"><article class="before-flow"><p class="flow-label">여러 곳을 오가던 방식</p><h3>게시글을 찾은 다음에도,<br>할 일이 남습니다.</h3><ol class="old-steps"><li>모집글 찾기</li><li>게임 친구 추가하기</li><li>참여 가능한지 연락하기</li><li>디스코드 연락처·초대 링크 주고받기</li><li>음성 채널로 이동하기</li></ol><p class="flow-foot">“아직 구하시나요?” “디코 어디로 가요?”</p></article>
<article class="after-flow"><p class="flow-label">큐메이트에서는</p><h3>누가 있는지 보고,<br><em>참가해서 이야기하세요.</em></h3><ol class="new-steps"><li><span>01</span><div><strong>모인 인원과 빈자리 확인</strong><p>참여 전에 방 상황을 한눈에.</p></div></li><li><span>02</span><div><strong>원하는 모집방에 참가</strong><p>따로 연락처를 교환하는 대신 방으로.</p></div></li><li><span>03</span><div><strong>방 안에서 음성·텍스트 대화</strong><p>디스코드로 옮겨가지 않고 대화까지.</p></div></li></ol><p class="flow-foot">이제 대화할 곳을 따로 정하지 않아도 됩니다.</p></article></div>
<p class="comparison-note">왼쪽은 게시판과 외부 메신저를 함께 사용하는 흐름의 예시로, 서비스·이용 방식에 따라 다릅니다. 게임 실행과 게임 내 친구 추가·파티 초대는 별도로 필요할 수 있습니다.</p>
</div></section>
<section id="features" class="section wrap" aria-labelledby="features-title"><div class="section-heading"><p class="eyebrow">기능보다, 덜 번거로운 경험</p><h2 id="features-title">팀원을 구하는 데 쓰던 수고를,<br><em>같이 플레이하는 쪽으로.</em></h2></div>
<div class="benefit-grid">
<article class="benefit-card"><span class="benefit-number">01 / 확인을 간편하게</span><div class="feature-icon">${icon('people')}</div><h3>“몇 명 모였어요?”<br>이제 묻지 않아도.</h3><p>게시판에서 참여 중인 인원과 빈자리를 확인하세요. 누가 있는지 보고, 들어갈 방을 고릅니다.</p><a class="text-link" href="#evidence-board">인원이 보이는 모집방 ${icon('arrow')}</a></article>
<article id="join" class="benefit-card"><span class="benefit-number">02 / 연락을 간편하게</span><div class="feature-icon">${icon('plus')}</div><h3>연락처를 주고받기 전에,<br>방에서 먼저 만나요.</h3><p>함께할 방을 골라 참가하면 됩니다. 여러 사람에게 따로 연락하며 대화할 곳을 정하는 대신, 같은 방으로 모이세요.</p><a class="text-link" href="#how-it-works">한곳에서 만나는 흐름 ${icon('arrow')}</a></article>
<article class="benefit-card"><span class="benefit-number">03 / 대화를 간편하게</span><div class="feature-icon">${icon('mic')}</div><h3>“디코 어디로 가요?”<br>여기서 얘기하면 돼요.</h3><p>별도 디스코드 친구 추가·채널 이동 없이, 참가한 방 안에서 음성 대화를 시작하세요. 마이크를 쓰지 않을 때는 텍스트로 이야기합니다.</p><a class="text-link" href="#evidence-room">방 안의 음성·채팅 ${icon('arrow')}</a></article>
</div>
<p class="section-note">음성 대화에는 브라우저 마이크 권한과 마이크 켜기가 필요합니다. 게임 자체가 큐메이트 안에서 실행되는 것은 아닙니다.</p>
</section>
<section id="how-it-works" class="section wrap evidence-section" aria-labelledby="how-title"><div class="section-heading"><p class="eyebrow">그래서, 화면도 이렇게 연결했습니다</p><h2 id="how-title">확인하고, 참가하고, 대화하는 곳이<br>따로 떨어져 있지 않도록.</h2><p>전체 화면을 외울 필요는 없습니다. 달라지는 부분만 확인하세요.</p></div>
<div class="evidence-grid"><article id="evidence-board" class="evidence-card"><div class="evidence-copy"><span>확인</span><h3>모인 사람과 빈자리가 보이는 모집방</h3></div>${renderProductPreview()}</article><article id="evidence-room" class="evidence-card"><div class="evidence-copy"><span>참가 → 대화</span><h3>참가한 방에서 이어지는 음성·채팅</h3></div>${detailCapture('room','모집방 목록 옆의 파티 인원과 마이크 켜기, 채팅 영역','방 안의 음성·채팅')}</article></div>
<div class="quick-match-block"><div><p class="eyebrow">직접 고르는 것도 번거로운 날에는</p><h3>조건만 정하고,<br>빠른매치로 팀원을 찾아보세요.</h3><p>모집방을 하나씩 고르는 방법 외에도,<br>원하는 조건으로 팀원을 찾는 흐름을 준비하고 있습니다.</p></div>${detailCapture('settings','게임 모드, 포지션, 인원, 플레이 목적, 음성 조건을 고르는 빠른매치 설정','빠른매치 조건 설정')}</div>
<p class="section-note">실제 UI를 실행해 촬영한 화면입니다. 닉네임·전적·모집방은 예시 데이터이며, 이 소개 페이지에서 실제 매칭·음성 연결은 실행되지 않습니다.</p>
</section>
<section id="faq" class="section wrap faq-layout" aria-labelledby="faq-title"><div class="section-heading"><p class="eyebrow">미리 확인하세요</p><h2 id="faq-title">어디까지<br>간편해지나요?</h2></div><div class="faq-list">${faqs.map(([q,a])=>`<details><summary>${e(q)}<span class="faq-plus" aria-hidden="true">+</span></summary><p>${e(a)}</p></details>`).join('')}</div></section>
<section class="wrap final-section" aria-labelledby="final-title"><div class="final-card"><p class="eyebrow">같이할 사람을 구하는 더 간편한 방법</p><h2 id="final-title">연락할 곳을 찾지 말고,<br><em>같이할 팀원을 만나세요.</em></h2><p>인원 확인부터 참가, 음성 대화까지. 큐메이트 한곳에서.</p>${cta()}<small>${c.appReady ? '게임 실행과 게임 내 파티 초대는 별도로 진행합니다.' : '서비스 준비 중 · 정식 서비스 연결 전입니다.'}</small></div></section>
</main><footer class="site-footer wrap"><div><a class="brand-link" href="#main" aria-label="큐메이트 홈">${wordmark()}</a><p>게임은 같이. 준비는 간단히.</p></div><div class="footer-meta"><span>${e(new URL(c.origin).hostname)}</span><a href="#main">맨 위로 ↑</a><small>© 2026 QueueMate</small></div></footer></body></html>`;
  const robots = `User-agent: *\nAllow: /\n${mode.indexable ? `\nSitemap: ${origin}/sitemap.xml\n` : '\n# Preview pages use an HTML noindex directive.\n'}`;
  const sitemap = mode.indexable ? `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${e(canonical)}</loc></url></urlset>\n` : null;
  const llms = `# ${c.name} (${c.alternateName})\n\n> ${c.description}\n\n## 공식 소개\n- [서비스 소개](${canonical}): 팀원 찾기부터 참가, 음성 대화까지 한곳에서 이어지는 이용 흐름\n\n## 줄이는 번거로움\n모집방에서 참여 인원과 빈자리를 확인하고, 원하는 방에 참가해 같은 서비스 안에서 대화하는 방식입니다. 별도의 디스코드 연락처 교환이나 음성 채널 이동 과정을 줄이는 것이 핵심입니다.\n\n## 별도로 필요한 것\n브라우저 마이크 권한 및 마이크 켜기, 게임 실행, 게임에 따라 친구 추가와 파티 초대가 필요합니다. 매칭 완료 시간이나 절약 시간을 보장하지 않습니다.\n\n## 공개 상태\n${c.appReady ? '매칭 서비스 링크를 활성화했습니다.' : '서비스 준비 중입니다. 이 랜딩 페이지에서 매칭이나 음성 연결은 실행되지 않습니다.'}\n\n## 화면 예시\nfeature/quick-match-ui 브랜치 904cce415181aeb8a8802fc398f9be9e0835b1fe의 실제 프런트엔드를 실행해 캡처했습니다. 화면 코드를 다시 그린 것이 아닙니다. 닉네임·전적·모집방은 예시 데이터이며, 실제 사용자·모집방·성과 데이터가 아닙니다. 실제 매칭 및 음성 연결 검증이 아닙니다.\n\n## 지원 대상\n리그 오브 레전드, 발로란트, 배틀그라운드. 공개 시 실제 이용 가능한 범위를 확인하세요.\n\n이 안내 파일은 보조 설명이며 검색 또는 AI 노출을 보장하지 않습니다.\n`;
  return {html,robots,sitemap,llms,mode};
}
export function render404() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>페이지를 찾을 수 없습니다 | 큐메이트</title><link rel="stylesheet" href="/assets/site.css"></head><body><main class="error-page wrap"><p class="eyebrow">404 · NOT FOUND</p><h1>이 페이지는<br>찾을 수 없어요.</h1><p>주소를 다시 확인하거나 홈으로 돌아가 주세요.</p><a class="button button-primary" href="/">홈으로 이동 ${icon('arrow')}</a></main></body></html>`;
}
