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
p.add_argument('--html', help='Optional self-contained local HTML export for offline rendering')
a = p.parse_args()
u = urlparse(a.url)
if u.scheme != 'http' or u.hostname != '127.0.0.1':
    raise SystemExit('This source audit accepts only the loopback preview server.')
out = Path(a.output)
out.mkdir(parents=True, exist_ok=True)
report = {'scope': 'offline exported HTML' if a.html else 'local HTTP source build, not the Vercel deployment', 'checks': [], 'widths': [], 'diagnostics': []}

def check(name, ok):
    report['checks'].append({'name': name, 'pass': bool(ok)})
    print(('PASS ' if ok else 'FAIL ') + name)

def load(page):
    if a.html:
        page.set_content(Path(a.html).read_text(), wait_until='load')
        return None
    return page.goto(a.url, wait_until='networkidle')

try:
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=os.environ.get('CHROME_BIN', '/usr/bin/chromium'),
                                     args=['--no-sandbox', '--disable-dev-shm-usage'])
        report['browserVersion'] = browser.version
        try:
            for width in [320, 390, 640, 768, 1024, 1440]:
                context = browser.new_context(viewport={'width': width, 'height': 900}, reduced_motion='reduce', locale='ko-KR')
                page = context.new_page()
                errors, failed, external, bad_responses = [], [], [], []
                page.on('pageerror', lambda e: errors.append(str(e)))
                page.on('console', lambda m: errors.append({'text': m.text, 'location': m.location}) if m.type == 'error' else None)
                page.on('response', lambda r: bad_responses.append({'url': r.url, 'status': r.status}) if r.status >= 400 else None)
                page.on('requestfailed', lambda r: failed.append(r.url))
                page.on('request', lambda r: external.append(r.url) if not r.url.startswith(a.url) else None)
                response = load(page)
                # Scroll real lazy images into view and wait for decoding before full-page capture.
                for image in page.locator('img').all():
                    image.scroll_into_view_if_needed()
                    image.evaluate('(image) => image.decode()')
                page.evaluate('window.scrollTo(0,0)')
                page.wait_for_timeout(150)
                check(f'{width}px: homepage loads', (a.html is not None or response.status == 200) and page.locator('h1').count() == 1)
                check(f'{width}px: no horizontal page overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                check(f'{width}px: no broken images', page.locator('img').evaluate_all('(els) => els.every(i => i.complete && i.naturalWidth > 0)'))
                check(f'{width}px: no console or network errors', not errors and not failed and not bad_responses)
                check(f'{width}px: no external page requests', not external)
                check(f'{width}px: six FAQs', page.locator('.faq-list details').count() == 6)
                toggle = page.locator('.join-demo > summary')
                check(f'{width}px: starts with 2 of 5 members', page.locator('.count-row .before-join').inner_text() == '2' and not page.locator('.voice-panel').is_visible())
                toggle.click()
                check(f'{width}px: example joins as third member', page.locator('.count-row .after-join').is_visible() and page.locator('.count-row .after-join').inner_text() == '3' and page.locator('.self-member').is_visible())
                check(f'{width}px: same-room voice panel opens', page.locator('.voice-panel').is_visible())
                check(f'{width}px: joined layout has no overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                toggle.focus()
                page.keyboard.press('Enter')
                check(f'{width}px: keyboard returns to before state', not page.locator('.voice-panel').is_visible() and page.locator('.count-row .before-join').is_visible())
                page.screenshot(path=str(out / f'page-{width}.png'), full_page=True)
                for i in range(6):
                    item = page.locator('.faq-list details').nth(i)
                    item.locator('summary').click()
                    check(f'{width}px: FAQ {i + 1} opens', item.get_attribute('open') is not None and item.locator('p').is_visible())
                    item.locator('summary').click()
                page.close()
                page = context.new_page()
                load(page)
                page.keyboard.press('Tab')
                check(f'{width}px: first tab is skip link', page.locator(':focus').get_attribute('href') == '#main')
                page.keyboard.press('Enter')
                check(f'{width}px: skip link focuses main', page.locator(':focus').get_attribute('id') == 'main')
                page.locator('[data-cta="explore-preview"]').first.click()
                check(f'{width}px: primary button reaches example', page.url.endswith('#preview'))
                report['widths'].append(width)
                report['diagnostics'].append({'width': width, 'consoleErrors': errors, 'failedRequests': failed, 'badResponses': bad_responses, 'externalRequests': external})
                context.close()
            context = browser.new_context(java_script_enabled=False, viewport={'width': 390, 'height': 844})
            page = context.new_page()
            load(page)
            check('JavaScript disabled: headline visible', page.locator('h1').is_visible())
            page.locator('.join-demo > summary').click()
            check('JavaScript disabled: participation example works', page.locator('.voice-panel').is_visible())
            item = page.locator('.faq-list details').first
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
