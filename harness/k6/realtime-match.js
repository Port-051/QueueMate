import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = { vus: 20, duration: '20s' };
const base = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Replace X-Test-User with staging-only fixture auth supplied by Member 3.
  const id = `${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    game: 'LOL', modeKey: 'SOLO_DUO_RANKED',
    keyCondition: { type: 'POSITION', value: (__VU % 2 ? 'JUNGLE' : 'MID') },
    voicePreference: 'OPTIONAL', playPurpose: 'RANK_UP'
  });
  const res = http.post(`${base}/api/v1/match-requests`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Test-User': id }
  });
  check(res, { 'created-or-conflict': r => [201,409].includes(r.status) });
  sleep(0.2);
}
