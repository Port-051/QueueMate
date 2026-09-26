import { expect, test } from '@playwright/test';
import { normalizeTierRange, tierInRange } from '../src/domain/tierRange';
import { roomCapacities } from '../src/rooms/summary';

test('티어 범위는 양 끝을 포함하고 역방향 선택도 정렬한다', () => {
  const range = normalizeTierRange('LOL', { minTier: 'GOLD', maxTier: 'SILVER' });
  expect(range).toEqual({ minTier: 'SILVER', maxTier: 'GOLD' });
  for (const tier of ['SILVER', 'GOLD']) expect(tierInRange('LOL', tier, range)).toBe(true);
  for (const tier of ['BRONZE', 'PLATINUM', null, 'UNKNOWN']) expect(tierInRange('LOL', tier, range)).toBe(false);
});

test('단일 하한·상한·동일 티어·무제한을 구분한다', () => {
  expect(tierInRange('LOL', 'CHALLENGER', { minTier: 'GOLD', maxTier: null })).toBe(true);
  expect(tierInRange('LOL', 'IRON', { minTier: null, maxTier: 'GOLD' })).toBe(true);
  expect(tierInRange('LOL', 'SILVER', { minTier: 'GOLD', maxTier: 'GOLD' })).toBe(false);
  expect(tierInRange('LOL', 'GOLD', { minTier: 'GOLD', maxTier: 'GOLD' })).toBe(true);
  expect(tierInRange('LOL', null)).toBe(true);
  expect(normalizeTierRange('LOL', { minTier: 'RADIANT', maxTier: 'INVALID' })).toEqual({ minTier: null, maxTier: null });
});

test('게임별 순서와 자유 랭크 인원 제한을 분리한다', () => {
  expect(tierInRange('VALORANT', 'ASCENDANT', { minTier: 'DIAMOND', maxTier: 'IMMORTAL' })).toBe(true);
  expect(roomCapacities('LOL', 'SOLO_DUO_RANKED')).toEqual([2]);
  expect(roomCapacities('LOL', 'FLEX_RANKED')).toEqual([2, 3, 5]);
  expect(roomCapacities('LOL', 'NORMAL_DRAFT')).toEqual([2, 3, 4, 5]);
});
