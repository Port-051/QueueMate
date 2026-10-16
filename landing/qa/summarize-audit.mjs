/** Summarize lab evidence without presenting it as real-user Core Web Vitals. */
import {readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
const root = process.argv[2];
if (!root) throw Error('Usage: node summarize-audit.mjs REPORT_DIRECTORY');
const read = name => readFile(path.join(root, name), 'utf8').then(JSON.parse);
const browser = await read('browser.json');
const report = {scope: 'Local build on a GitHub runner. Not production PageSpeed or field data.',
  browser: {version: browser.browserVersion, passed: browser.passed, failed: browser.failed}, accessibility: [], lighthouse: []};
function auditObject(value) {
  if (!value || typeof value !== 'object') return null;
  if (Array.isArray(value.violations)) return value;
  for (const child of Object.values(value)) {const found = auditObject(child); if (found) return found;}
  return null;
}
for (const width of [390, 1440]) {
  const raw = await read(`a11y-${width}.json`), audit = auditObject(raw);
  if (!audit) throw Error(`Accessibility results missing at ${width}px`);
  report.accessibility.push({width, violations: audit.violations.map(v => ({id: v.id, impact: v.impact,
    nodes: v.nodes?.map(n => ({target: n.target, failureSummary: n.failureSummary}))})),
    incomplete: audit.incomplete?.length ?? null});
}
for (const device of ['mobile', 'desktop']) {
  const r = await read(`lighthouse-${device}.report.json`);
  if (r.runtimeError) throw Error(`Lighthouse ${device}: ${r.runtimeError.message}`);
  report.lighthouse.push({device, version: r.lighthouseVersion, fetchTime: r.fetchTime,
    scores: Object.fromEntries(Object.entries(r.categories).map(([k, v]) => [k, v.score == null ? null : Math.round(v.score * 100)])),
    metrics: Object.fromEntries(['first-contentful-paint','largest-contentful-paint','cumulative-layout-shift','total-blocking-time']
      .map(k => [k, {value: r.audits[k]?.numericValue, unit: r.audits[k]?.numericUnit}])),
    failedAudits: Object.entries(r.audits).filter(([, v]) => typeof v.score === 'number' && v.score < 1)
      .map(([id, v]) => ({id, title: v.title, score: v.score, description: v.description, details: v.details})),
    note: 'The preview intentionally remains noindex. Its indexing audit is not suppressed to inflate SEO scores.'});
}
await writeFile(path.join(root, 'summary.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({...report, lighthouse: report.lighthouse.map(({failedAudits, ...r}) => ({...r, failedAudits: failedAudits.map(v => v.id)}))}, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) await writeFile(process.env.GITHUB_STEP_SUMMARY,
  `## SEO source audit (lab, not production)\n\nBrowser checks: ${browser.passed} passed, ${browser.failed} failed.\n\n` +
  report.lighthouse.map(r => `${r.device}: ${JSON.stringify(r.scores)}`).join('\n\n') +
  '\n\nThe noindex SEO deduction is expected. Public access verification is a separate step.\n');
if (browser.failed || report.accessibility.some(a => a.violations.length)) process.exitCode = 1;
