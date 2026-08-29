import type { MatchCondition, MatchRequestView, ReservationInput } from './types';

const json = { 'Content-Type': 'application/json' };

export async function createMatchRequest(condition: MatchCondition): Promise<MatchRequestView> {
  const res = await fetch('/api/v1/match-requests', { method: 'POST', headers: json, body: JSON.stringify(condition) });
  if (!res.ok) throw new Error(`MATCH_REQUEST_FAILED:${res.status}`);
  return res.json();
}

export async function createReservation(input: ReservationInput) {
  const res = await fetch('/api/v1/reservations', { method: 'POST', headers: json, body: JSON.stringify(input) });
  if (!res.ok) throw new Error(`RESERVATION_FAILED:${res.status}`);
  return res.json();
}
