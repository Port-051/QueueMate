/** Optional GA4 measurement for the canonical production introduction page only. */
export function installAnalytics({measurementId, origin}) {
  const w = window, d = document;
  if (w.location.origin !== origin || w.location.pathname !== '/' || w.location.protocol !== 'https:') return;
  if (w.__qmateAnalyticsInstalled || w[`ga-disable-${measurementId}`]) return;
  if (w.navigator.globalPrivacyControl === true || w.navigator.doNotTrack === '1' || w.doNotTrack === '1') return;
  // A consent manager can explicitly block this loader before it executes.
  if (w.qmateAnalyticsConsent === 'denied') return;
  try { if (w.localStorage.getItem('qmate.analytics.disabled') === 'true') return; } catch { /* Storage may be blocked. */ }
  // Avoid a second installation when a site owner adds another Google tag/GTM.
  if (d.querySelector('script[src*="googletagmanager.com/gtag/js"],script[src*="googletagmanager.com/gtm.js"]')) return;
  w.__qmateAnalyticsInstalled = true;
  w.dataLayer = w.dataLayer || [];
  w.gtag = w.gtag || function () { w.dataLayer.push(arguments); };
  const gtag = w.gtag;
  // No advertising consent is granted by this integration.
  gtag('consent', 'default', {ad_storage:'denied', ad_user_data:'denied', ad_personalization:'denied'});
  const input = new URL(w.location.href), page = new URL('/', origin);
  // Keep only deliberately named campaigns, never login tokens, email or search terms.
  for (const name of ['utm_source','utm_medium','utm_campaign','utm_id','utm_content']) {
    const value = input.searchParams.get(name);
    if (value && /^[a-zA-Z][a-zA-Z0-9_-]{0,79}$/.test(value)) page.searchParams.set(name,value);
  }
  let referrer = '';
  try {
    const ref = new URL(d.referrer);
    if (['http:','https:'].includes(ref.protocol) && !ref.username && !ref.password) referrer = ref.origin + '/';
  } catch { /* Direct visits have no referrer. */ }
  const fields = {
    page_location: page.href, page_referrer: referrer, page_title: d.title,
    allow_google_signals: false, allow_ad_personalization_signals: false,
    cookie_domain: new URL(origin).hostname, cookie_expires: 90 * 24 * 60 * 60,
  };
  if (input.searchParams.get('ga_debug') === '1') fields.debug_mode = true;
  // Global sanitized URL defaults also apply to automatically collected events.
  gtag('set', fields);
  gtag('js', new Date());
  gtag('config', measurementId, {...fields, send_page_view: true});
  const script = d.createElement('script');
  script.async = true;
  script.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(measurementId);
  script.referrerPolicy = 'strict-origin';
  d.head.appendChild(script);
  d.addEventListener('click', event => {
    if (!event.isTrusted || event.defaultPrevented || w[`ga-disable-${measurementId}`] || w.qmateAnalyticsConsent === 'denied') return;
    const link = event.target?.closest?.('a[data-cta="explore-preview"],a.header-cta[href="#preview"]');
    if (!link || link.getAttribute('href') !== '#preview') return;
    // A click is not a sign-up, successful match or completed demo.
    gtag('event', 'demo_cta_click', {
      send_to: measurementId,
      cta_position: link.closest('header') ? 'header' : 'hero',
      ...fields,
    });
  });
}

export function addAnalytics(html, config, mode) {
  const analytics = config.analytics;
  if (analytics === undefined) return html;
  if (!analytics || typeof analytics.enabled !== 'boolean' || typeof analytics.measurementId !== 'string') {
    throw new Error('analytics: enabled와 measurementId를 올바르게 설정하세요.');
  }
  if (analytics.measurementId && !/^G-[A-Z0-9]{10}$/.test(analytics.measurementId)) throw new Error('analytics.measurementId: 올바른 G- 측정 ID가 필요합니다.');
  if (!analytics.enabled) return html;
  if (!analytics.measurementId) throw new Error('analytics.measurementId: 활성화 전에 측정 ID를 입력하세요.');
  if (!mode.production || !mode.indexable) return html;
  if (config.origin !== 'https://queue-mate.com') throw new Error('analytics: 승인된 정식 도메인만 측정합니다.');
  if (!html.includes('</head>') || html.includes('id="qmate-analytics"')) throw new Error('analytics: head 누락 또는 중복 설치입니다.');
  const settings = JSON.stringify({measurementId: analytics.measurementId, origin: config.origin}).replace(/</g,'\\u003c');
  return html.replace('</head>', `<script id="qmate-analytics">(${installAnalytics.toString()})(${settings});</script></head>`);
}
