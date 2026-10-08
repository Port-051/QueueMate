"""Verify the product-led QueueMate landing at six widths. Source build only."""
import argparse,json,os
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright
p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:4173');p.add_argument('--output',default='qa-results');p.add_argument('--html');a=p.parse_args();u=urlparse(a.url)
if u.scheme!='http' or u.hostname!='127.0.0.1':raise SystemExit('Only loopback source previews are supported.')
out=Path(a.output);out.mkdir(parents=True,exist_ok=True);report={'scope':'local source build','checks':[],'widths':[]}
def check(name,ok):report['checks'].append({'name':name,'pass':bool(ok)});print(('PASS ' if ok else 'FAIL ')+name)
def load(page):
 if a.html:page.set_content(Path(a.html).read_text(),wait_until='load')
 else:page.goto(a.url,wait_until='networkidle')
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(executable_path=os.environ.get('CHROME_BIN','/usr/bin/chromium'),args=['--no-sandbox','--disable-dev-shm-usage'])
  for width in [320,390,640,768,1024,1440]:
   ctx=browser.new_context(viewport={'width':width,'height':980},reduced_motion='reduce',locale='ko-KR');page=ctx.new_page();errors=[];external=[]
   page.on('pageerror',lambda e:errors.append(str(e)));page.on('request',lambda r:external.append(r.url) if not r.url.startswith(a.url) and not r.url.startswith('data:') else None);load(page)
   for img in page.locator('img:visible').all():img.scroll_into_view_if_needed();img.evaluate('(i)=>i.decode()')
   check(f'{width}px: no page-wide overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
   check(f'{width}px: one headline',page.locator('h1').count()==1)
   check(f'{width}px: hero board visible',page.locator('.hero-screen img').is_visible())
   check(f'{width}px: three value statements',page.locator('.value-points article').count()==3)
   check(f'{width}px: room proof visible once',page.locator('.room-proof img').count()==1 and page.locator('.room-proof img').is_visible())
   check(f'{width}px: quick match visible once',page.locator('.quick-match-figure img').count()==1 and page.locator('.quick-match-figure img').is_visible())
   check(f'{width}px: no old duplicate UI systems',page.locator('.benefit-card,.product-feature-row,.product-journey,.join-demo,.actual-state-switch').count()==0)
   check(f'{width}px: three FAQs',page.locator('.faq-item').count()==3)
   check(f'{width}px: no runtime errors or external traffic',not errors and not external)
   for link in page.locator('a[href^="#"]').all():
    target=link.get_attribute('href')[1:];check(f'{width}px: #{target} target exists',page.locator('[id="'+target+'"]').count()==1)
   page.screenshot(path=str(out/f'page-{width}.png'),full_page=True);report['widths'].append(width);ctx.close()
  ctx=browser.new_context(java_script_enabled=False,viewport={'width':390,'height':844});page=ctx.new_page();load(page)
  check('No JS: all three product images visible',page.locator('.hero-screen img').is_visible() and page.locator('.room-proof img').is_visible() and page.locator('.quick-match-figure img').is_visible())
  page.locator('.faq-item summary').first.click();check('No JS: FAQ opens',page.locator('.faq-item p').first.is_visible());ctx.close();browser.close()
except Exception as e:report['error']=str(e);check('Browser audit completed',False)
finally:
 report['passed']=sum(c['pass'] for c in report['checks']);report['failed']=sum(not c['pass'] for c in report['checks']);(out/'browser.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(1 if report['failed'] else 0)
