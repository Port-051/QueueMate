"""Capture the pinned, unmodified frontend with synthetic API display fixtures.
No real account, backend, matching, microphone, or customer data is used.
"""
import argparse, hashlib, json, mimetypes, os
from pathlib import Path
from urllib.parse import urlparse, unquote
from playwright.sync_api import sync_playwright, expect
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('--dist', required=True)
parser.add_argument('--out', required=True)
a = parser.parse_args()
dist = Path(a.dist).resolve()
out = Path(a.out).resolve()
out.mkdir(parents=True, exist_ok=True)
assert (dist/'index.html').is_file(), 'Build the pinned frontend first'
BASE = 'http://127.0.0.1:4173'
NOW = '2026-09-30T10:00:00.000Z'

def profile(nick, tier, champs):
    return dict(game='LOL', gameNickname=nick+'#DEMO', verified=False, tiers={'SOLO':tier,'FLEX':tier}, server=None,
                stats=dict(games=20,wins=12,losses=8,winRate=60,winStreak=2,avgKills=4.2,avgDeaths=3.1,avgAssists=8.4,kda=4.06,
                           detail={'mostChampions':[{'championId':c,'masteryLevel':10,'masteryPoints':12000} for c in champs]},syncedAt=NOW))

def member(uid,nick,tier='GOLD_2',champs=('Ahri','Orianna','Syndra'),host=False):
    return dict(userId=uid,nickname=nick,host=host,profile=profile(nick,tier,champs))

me = dict(userId=900001,nickname='예시 플레이어',createdAt=NOW,socialProviders=['GOOGLE'],gameAccounts=[profile('예시 플레이어','GOLD_2',['Ahri','Orianna','Syndra'])])
posts=[]
for i,(title,mode,capacity,count,role,wanted,champs,tier) in enumerate([
    ('편하게 소통하며 자유 랭크 해요','RANKED_FLEX_5',5,2,'ADC',['TOP','JUNGLE','SUPPORT'],['Jinx','Kaisa','Ahri'],'GOLD_2'),
    ('솔로 랭크, 서포터님 함께해요','RANKED_SOLO',2,1,'ADC',['SUPPORT'],['Kaisa','Jinx','Lulu'],'PLATINUM_3'),
    ('즐겁게 일반 5인 같이 하실 분','NORMAL_5',5,3,'JUNGLE',['TOP','MID'],['LeeSin','Viego','Ahri'],'SILVER_1'),
    ('칼바람 편하게 즐겨요','ARAM_5',5,2,None,[],['Ahri','Lulu','Jinx'],'GOLD_4'),
    ('자유 랭크 3인, 미드 구해요','RANKED_FLEX_3',3,2,'TOP',['MID'],['Azir','Syndra','LeeSin'],'EMERALD_4'),
    ('일반에서 함께 연습해요','NORMAL_3',3,1,'SUPPORT',['ADC','JUNGLE'],['Thresh','Lulu','Orianna'],'GOLD_3'),
]):
    members=[member(900010+i*10,'예시 방장'+str(i+1),tier,champs,True)]
    for j in range(count-1): members.append(member(900011+i*10+j,'예시 팀원'+str(j+1),tier))
    posts.append(dict(postId=600-i,hostId=members[0]['userId'],game='LOL',mode=mode,title=title,description='함께할 팀원을 찾는 UI 촬영용 예시입니다.',voice='REQUIRED' if i%2==0 else 'NO_VOICE',conditions={},wantedPositions=wanted,hostPosition=role,status='RECRUITING',createdAt='2026-09-30T09:58:00.000Z',memberCount=count,capacity=capacity,full=False,host=members[0],members=members))

report={'reference':{'repository':'Port-051/QueueMate','branch':'feature/quick-match-ui','commit':'904cce415181aeb8a8802fc398f9be9e0835b1fe'},'kind':'actual frontend screenshots with synthetic API display fixtures','backendConnected':False,'sourceUiModified':False,'screens':[]}
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get('CHROME_BIN'),args=['--no-sandbox','--disable-dev-shm-usage','--force-webrtc-ip-handling-policy=disable_non_proxied_udp'])
    try:
        context=browser.new_context(viewport={'width':1440,'height':900},device_scale_factor=1,locale='ko-KR',reduced_motion='reduce')
        context.add_init_script("""{
          const RealDate=Date;const fixed=RealDate.parse('2026-09-30T10:00:00.000Z');
          window.Date=class extends RealDate {constructor(...args){super(...(args.length?args:[fixed]));}static now(){return fixed;}};
          window.EventSource=class extends EventTarget {static CLOSED=2;static OPEN=1;static CONNECTING=0;
            constructor(){super();this.readyState=1;setTimeout(()=>this.onopen?.(new Event('open')),0);}close(){this.readyState=2;}};
        }""")
        state={'room':False}
        api_requests=[]
        unexpected=[]
        def route(r):
            u=urlparse(r.request.url);path=unquote(u.path)
            if u.hostname!='127.0.0.1':
                unexpected.append(r.request.url);r.abort();return
            if path.startswith('/api/v1/'):
                api_requests.append({'path':path,'method':r.request.method})
                data=None
                if path=='/api/v1/users/me':data=me
                elif path=='/api/v1/posts':data={'posts':posts,'nextCursor':None}
                elif path=='/api/v1/posts/600':data={**posts[0],'memberCount':3,'members':posts[0]['members']+[member(900001,'예시 플레이어')]} if state['room'] else posts[0]
                elif path=='/api/v1/rooms/me':data={'roomId':'600' if state['room'] else None}
                elif path=='/api/v1/rooms/600/members':data={'roomId':'600','hostId':str(posts[0]['hostId']),'members':[str(x['userId']) for x in posts[0]['members']]+['900001']}
                elif path=='/api/v1/match-requests':data={'status':'IDLE'}
                elif path=='/api/v1/friends':data={'friends':[]}
                elif path=='/api/v1/friend-requests':data={'requests':[]}
                elif path=='/api/v1/blocks':data={'blocks':[]}
                elif path=='/api/v1/recent-players':data={'players':[]}
                elif path.endswith('/heartbeat') or path.endswith('/signals'):
                    r.fulfill(status=204);return
                else:
                    unexpected.append(path);r.fulfill(status=404,json={'code':'NOT_IN_FIXTURE','message':'No external calls','details':[]});return
                r.fulfill(status=200,json=data);return
            file=(dist/path.lstrip('/')).resolve()
            if not file.is_relative_to(dist):r.abort();return
            if not file.is_file():file=dist/'index.html' if path=='/' or path.startswith('/app/') else file
            if file.is_file():r.fulfill(status=200,body=file.read_bytes(),content_type=mimetypes.guess_type(str(file))[0] or 'application/octet-stream')
            else:unexpected.append(path);r.fulfill(status=404,body='Not found')
        context.route('**/*',route)
        page=context.new_page();errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        try:
            page.goto(BASE+'/app/home',wait_until='networkidle')
            expect(page.locator('.room-deck')).to_have_count(6)
            def capture(name):
                page.evaluate('document.fonts.ready')
                # Load lazy hover-card images before checking their decoding; UI markup and styles stay unchanged.
                page.locator('img').evaluate_all('(xs)=>xs.forEach(x=>x.loading="eager")')
                page.wait_for_function('Array.from(document.images).every(x=>x.complete && x.naturalWidth>0)',timeout=15000)
                assert not errors,errors
                png=out/(name+'.png');page.screenshot(path=str(png),animations='disabled')
                Image.open(png).convert('RGB').save(out/(name+'.webp'),quality=88,method=6)
                report['screens'].append({'name':name,'width':1440,'height':900,'sha256':hashlib.sha256((out/(name+'.webp')).read_bytes()).hexdigest()})
            capture('quick-match-board')
            page.get_by_role('button',name='빠른매치 조건 열기').click()
            expect(page.get_by_role('dialog')).to_be_visible()
            role=page.get_by_role('dialog').get_by_role('button',name='미드',exact=True)
            if role.count():role.first.click()
            capture('quick-match-settings')
            page.get_by_role('button',name='빠른매치 창 닫기').click()
            state['room']=True
            page.goto(BASE+'/app/party/600',wait_until='networkidle')
            expect(page.locator('.room-panel')).to_be_visible()
            expect(page.locator('.room-panel h1')).to_be_visible()
            capture('quick-match-room')
            assert not unexpected,unexpected
        finally:
            report['apiRequests']=api_requests;report['unexpectedRequests']=unexpected;report['pageErrors']=errors
            (out/'capture-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
            page.screenshot(path=str(out/'last-state.png'))
            print(json.dumps(report,ensure_ascii=False,indent=2))
            context.close()
    finally:browser.close()
