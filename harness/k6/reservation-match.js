import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 10, iterations: 50 };
const base = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    condition: {
      game: 'VALORANT', modeKey: 'COMPETITIVE',
      keyCondition: { type: 'ROLE', value: 'CONTROLLER' },
      voicePreference: 'OPTIONAL', playPurpose: 'RANK_UP'
    },
    availableFrom: '2026-08-30T11:00:00Z',
    availableTo: '2026-08-30T14:00:00Z',
    playAmount: 'ONE_GAME'
  });
  const res = http.post(`${base}/api/v1/reservations`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Test-User': `reservation-${__VU}` }
  });
  check(res, { 'created-or-overlap-conflict': r => [201,409].includes(r.status) });
}
