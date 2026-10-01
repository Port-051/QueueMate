"""Verify actual-UI presentation, native screenshot toggle, SEO landmarks and mobile scrolling.
Source build only: never contacts a real backend or uses a microphone.
"""
import argparse, json, os
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

p=argparse.ArgumentParser()
p.add_argument('--url',default='http://127.0.0.1:4173')
p.add_argument('--output',default='qa-results')
p.add_argument('--html')
a=p.parse_args();u=urlparse(a.url)
if u.scheme!='http' or u.hostname!='127.0.0.1': raise SystemExit('Only loopback source previews are supported.')
out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
report={'scope':'offline exported HTML' if a.html else 'local HTTP source build','checks':[],'widths':[],'diagnostics':[]}
def check(name,ok):
 report['checks'].append({'name':name,'pass':bool(ok)});print(('PASS ' if ok else 'FAIL ')+name)
def load(page):
 if a.html:page.set_content(Path(a.html).read_text(),wait_until='load')
 else:page.goto(a.url,wait_until='networkidle')
def paint(page):
 for img in page.locator('img:visible').all():
  img.scroll_into_view_if_needed();img.evaluate('(i)=>i.decode()')
 page.wait_for_timeout(100)

def geometry(page,width):
 check(f'{width}px: no page-wide overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
 check(f'{width}px: quick match follows main product screenshot',page.locator('#quick-match').bounding_box()['y']>=page.locator('#preview').bounding_box()['y']+page.locator('#preview').bounding_box()['height']-1)
 if width<=700:
  sc=page.locator('.joined-screen .actual-image-scroll')
  check(f'{width}px: app screenshot has its own labelled scroll area',sc.is_visible() and sc.get_attribute('aria-label') is not None)
  check(f'{width}px: swipe guidance visible',page.locator('.swipe-hint').is_visible())
  sc.evaluate('(e)=>e.scrollLeft=220')
  check(f'{width}px: screenshot can scroll without widening the page',sc.evaluate('(e)=>e.scrollLeft>0') and page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
  sc.evaluate('(e)=>e.scrollLeft=0')
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(executable_path=os.environ.get('CHROME_BIN','/usr/bin/chromium'),args=['--no-sandbox','--disable-dev-shm-usage'])
  report['browserVersion']=browser.version
  try:
   for width in [320,390,640,768,1024,1440]:
    context=browser.new_context(viewport={'width':width,'height':980},reduced_motion='reduce',locale='ko-KR')
    page=context.new_page();errors=[];external=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.on('request',lambda r: external.append(r.url) if not r.url.startswith(a.url) and not r.url.startswith('data:') else None)
    load(page);paint(page)
    check(f'{width}px: single unchanged primary headline',page.locator('h1').count()==1 and page.locator('h1').inner_text().replace('\n','')=='조건에 맞는 팀원을 찾고,같은 방에서 바로 대화하세요.')
    check(f'{width}px: starts on actual joined UI',page.locator('.joined-screen').is_visible() and not page.locator('.unjoined-screen').is_visible())
    check(f'{width}px: before and after use the actual app captures',all(page.locator(sel+' img').get_attribute('src').startswith(('data:image/webp','/assets/ui/quick-match-')) for sel in ['.joined-screen','.unjoined-screen']))
    check(f'{width}px: fake party cards are absent',page.locator('.self-member,.seat-symbol,.voice-panel,.panel-orbit').count()==0)
    check(f'{width}px: quick match secondary title is visible',page.locator('#quick-match-title').inner_text().replace('\n','')=='직접 찾는 대신,빠른매치.')
    check(f'{width}px: quick match real image is decoded',page.locator('.quick-match-crop img').evaluate('(i)=>i.complete&&i.naturalWidth===1440'))
    check(f'{width}px: quick match follows the product, not a competing CTA',page.locator('.hero-actions a').count()==1 and page.locator('.quick-match-copy .button-primary').count()==0)
    check(f'{width}px: three short FAQs',page.locator('.faq-item').count()==3)
    check(f'{width}px: no runtime errors or external traffic',not errors and not external)
    geometry(page,width)
    toggle=page.locator('.actual-state-switch');toggle.click()
    check(f'{width}px: native toggle reveals participation-before screenshot',page.locator('.unjoined-screen').is_visible() and not page.locator('.joined-screen').is_visible())
    page.locator('.unjoined-screen img').evaluate('(i)=>i.decode()')
    toggle.focus();page.keyboard.press('Enter')
    check(f'{width}px: keyboard returns to joined screenshot',page.locator('.joined-screen').is_visible() and not page.locator('.unjoined-screen').is_visible())
    for i,item in enumerate(page.locator('.faq-item').all()):
     item.locator('summary').click();check(f'{width}px: FAQ {i+1} opens',item.locator('p').is_visible());item.locator('summary').click()
    for link in page.locator('a[href^="#"]').all():
     target=link.get_attribute('href')[1:];check(f'{width}px: #{target} target exists',page.locator('[id="'+target+'"]').count()==1)
    paint(page);page.evaluate('scrollTo(0,0)');page.wait_for_timeout(80)
    page.screenshot(path=str(out/f'page-{width}.png'),full_page=True)
    page.screenshot(path=str(out/f'hero-{width}.png'))
    toggle.click();paint(page);page.evaluate('scrollTo(0,0)');page.wait_for_timeout(50)
    page.screenshot(path=str(out/f'before-{width}.png'),full_page=True)
    page.close();page=context.new_page();load(page)
    page.keyboard.press('Tab');check(f'{width}px: first tab is skip link',page.locator(':focus').get_attribute('href')=='#main')
    page.keyboard.press('Enter');check(f'{width}px: skip link focuses main',page.locator(':focus').get_attribute('id')=='main')
    page.locator('[data-cta="explore-preview"]').click();check(f'{width}px: CTA reaches actual UI',page.url.endswith('#preview'))
    report['widths'].append(width);report['diagnostics'].append({'width':width,'pageErrors':errors,'externalRequests':external});context.close()
   context=browser.new_context(java_script_enabled=False,viewport={'width':390,'height':844})
   page=context.new_page();load(page)
   check('No JS: joined screen shown',page.locator('.joined-screen').is_visible())
   page.locator('.actual-state-switch').click();check('No JS: before screen shown',page.locator('.unjoined-screen').is_visible())
   page.locator('.actual-state-switch').click();check('No JS: after screen restored',page.locator('.joined-screen').is_visible())
   page.locator('.faq-item summary').first.click();check('No JS: FAQ opens',page.locator('.faq-item p').first.is_visible())
   check('No JS: quick match section present',page.locator('#quick-match-title').is_visible());context.close()
  finally:browser.close()
except Exception as e:report['error']=str(e);check('Browser audit completed',False)
finally:
 report['passed']=sum(c['pass'] for c in report['checks']);report['failed']=sum(not c['pass'] for c in report['checks'])
 (out/'browser.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(1 if report['failed'] else 0)
