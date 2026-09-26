import { expect, test } from '@playwright/test';
import { ceilRoomHour, localRoomDateTime, reservationTimeError, roomStartLabel } from '../src/rooms/schedule';

const now = new Date(2026, 8, 27, 10).getTime();
const time = (day: number, hour: number) => new Date(2026, 8, day, hour).toISOString();

test('현재·오늘·내일·이후 날짜와 정오·자정을 표시한다', () => {
  expect(roomStartLabel(null, now)).toBe('지금');
  expect(roomStartLabel(time(27, 15), now)).toBe('오늘 오후 3시');
  expect(roomStartLabel(time(28, 23), now)).toBe('내일 오후 11시');
  expect(roomStartLabel(time(29, 21), now)).toBe('29일 오후 9시');
  expect(roomStartLabel(time(27, 12), now)).toBe('오늘 오후 12시');
  expect(roomStartLabel(time(28, 0), now)).toBe('내일 오전 12시');
  expect(roomStartLabel('invalid', now)).toBe('시간 미정');
});

test('월·연도 경계에서도 내일과 이후 날짜를 구분한다', () => {
  expect(roomStartLabel(new Date(2026, 9, 1, 15).toISOString(), new Date(2026, 8, 30, 23).getTime())).toBe('내일 오후 3시');
  expect(roomStartLabel(new Date(2026, 9, 1, 15).toISOString(), now)).toBe('10월 1일 오후 3시');
  expect(roomStartLabel(new Date(2027, 0, 2, 15).toISOString(), now)).toBe('2027년 1월 2일 오후 3시');
});

test('새 예약은 미래 정각만 허용하고 올림이 날짜를 넘겨도 유효하다', () => {
  expect(reservationTimeError(time(27, 15), now)).toBeNull();
  expect(reservationTimeError(time(27, 10), now)).toContain('현재보다 뒤');
  expect(reservationTimeError(null, now)).toContain('현재보다 뒤');
  expect(reservationTimeError('invalid', now)).toContain('현재보다 뒤');
  expect(reservationTimeError(new Date(2026, 8, 27, 15, 30).toISOString(), now)).toContain('1시간 단위');
  expect(reservationTimeError(new Date(2026, 8, 27, 15, 0, 1).toISOString(), now)).toContain('1시간 단위');
  expect(localRoomDateTime(ceilRoomHour(new Date(2026, 8, 27, 23, 30).getTime()))).toBe('2026-09-28T00:00');
  expect(ceilRoomHour(now)).toBe(now);
});
