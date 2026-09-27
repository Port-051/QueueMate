"""Idempotently create two local demo users through the real auth API; no token output."""
import json
import urllib.request
import urllib.error

base = 'http://127.0.0.1:8080/api/v1'
for letter in ('a', 'b'):
    payload = {'email': f'demo-{letter}@queuemate.local', 'password': 'QueueMate123!', 'nickname': f'데모 {letter.upper()}'}
    def post(path, body, token=None):
        req = urllib.request.Request(base + path, json.dumps(body).encode(), headers={'Content-Type': 'application/json', **({'Authorization': f'Bearer {token}'} if token else {})}, method='POST')
        with urllib.request.urlopen(req) as response:
            return json.load(response)
    credentials = {key: payload[key] for key in ('email', 'password')}
    try:
        session = post('/auth/login', credentials)
    except urllib.error.HTTPError as error:
        if error.code != 401:
            raise
        post('/auth/signup', payload)
        session = post('/auth/login', credentials)
    for game in ('LOL', 'VALORANT', 'PUBG'):
        try:
            post('/users/me/game-accounts', {'game': game, 'externalGameId': f'QueueMateDemo{letter.upper()}#DEMO', 'region': 'KR'}, session['accessToken'])
        except urllib.error.HTTPError as error:
            if error.code != 409:
                raise
    print(f"Ready: {payload['email']}")
