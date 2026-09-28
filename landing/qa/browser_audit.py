"""Rendered-browser checks for the static landing; no login or application API calls."""
import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

p = argparse.ArgumentParser()
p.add_argument('--url', default='http://127.0.0.1:4173')
p.add_argument('--output', default='qa-results')
a = p.parse_args()
u = urlparse(a.url)
if u.scheme != 'http' or u.hostname != '127.0.0.1':
    raise SystemExit('This source audit accepts only the loopback preview server.')
out = Path(a.output)
out.mkdir(parents=True, exist_ok=True)
report = {'scope': 'local source build, not the Vercel deployment', 'checks': [], 'widths': []}

def check(name, ok):
    report['checks'].append({'name': name, 'pass': bool(ok)})
    print(('PASS ' if ok else 'FAIL ') + name)

try:
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=os.environ.get('CHROME_BIN', '/usr/bin/chromium'),
                                     args=['--no-sandbox', '--disable-dev-shm-usage'])
        report['browserVersion'] = browser.version
        try:
            for width in [320, 390, 640, 768, 1024, 1440]:
                context = browser.new_context(viewport={'width': width, 'height': 900}, reduced_motion='reduce', locale='ko-KR')
                page = context.new_page()
                errors, failed, external = [], [], []
                page.on('pageerror', lambda e: errors.append(str(e)))
                page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
                page.on('requestfailed', lambda r: failed.append(r.url))
                page.on('request', lambda r: external.append(r.url) if not r.url.startswith(a.url) else None)
                response = page.goto(a.url, wait_until='networkidle')
                check(f'{width}px: homepage loads', response.status == 200 and page.locator('h1').count() == 1)
                check(f'{width}px: no horizontal page overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                check(f'{width}px: no broken images', page.locator('img').evaluate_all('(els) => els.every(i => i.complete && i.naturalWidth > 0)'))
                check(f'{width}px: no console or network errors', not errors and not failed)
                check(f'{width}px: no external page requests', not external)
                check(f'{width}px: six FAQs', page.locator('details').count() == 6)
                page.screenshot(path=str(out / f'page-{width}.png'), full_page=True)
                for i in range(6):
                    item = page.locator('details').nth(i)
                    item.locator('summary').click()
                    check(f'{width}px: FAQ {i + 1} opens', item.get_attribute('open') is not None and item.locator('p').is_visible())
                    item.locator('summary').click()
                page.goto(a.url, wait_until='networkidle')
                page.keyboard.press('Tab')
                check(f'{width}px: first tab is skip link', page.locator(':focus').get_attribute('href') == '#main')
                page.keyboard.press('Enter')
                check(f'{width}px: skip link focuses main', page.locator(':focus').get_attribute('id') == 'main')
                page.locator('[data-cta="explore-preview"]').first.click()
                check(f'{width}px: primary button reaches example', page.url.endswith('#preview'))
                report['widths'].append(width)
                context.close()
            context = browser.new_context(java_script_enabled=False, viewport={'width': 390, 'height': 844})
            page = context.new_page()
            page.goto(a.url, wait_until='networkidle')
            check('JavaScript disabled: headline visible', page.locator('h1').is_visible())
            item = page.locator('details').first
            item.locator('summary').click()
            check('JavaScript disabled: native FAQ works', item.locator('p').is_visible())
            context.close()
        finally:
            browser.close()
except Exception as exc:
    report['error'] = str(exc)
    check('Browser audit completed', False)
finally:
    report['passed'] = sum(c['pass'] for c in report['checks'])
    report['failed'] = sum(not c['pass'] for c in report['checks'])
    (out / 'browser.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
raise SystemExit(1 if report['failed'] else 0)
