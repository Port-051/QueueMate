import { useCallback, useMemo, useSyncExternalStore } from 'react';
import type { GameKey, VoicePreference } from '../api/types';
import { canonicalRoomRoles, ROOM_ROLES, roomCapacityLimit } from './summary';
import type { CreateRoomInput, GameRoom, RoomMember, RoomMessage } from './types';

import { autoClosePhase, canAutoClose, nextAutoCloseAt } from './autoClose';

export { roomCapacityLimit } from './summary';

export interface RoomSnapshot { version: 1; rooms: GameRoom[]; }
const PREFIX = 'qm:room-board:v1:';
const CHANGE_EVENT = 'qm:room-board-changed';
const MAX_MESSAGES = 300;
const cache = new Map<string, RoomSnapshot>();
const VOICES: VoicePreference[] = ['REQUIRED', 'OPTIONAL', 'NO_VOICE'];
const MODES: Record<GameKey, string[]> = {
  LOL: ['NORMAL_DRAFT', 'SOLO_DUO_RANKED', 'SWIFTPLAY', 'ARAM'],
  VALORANT: ['COMPETITIVE', 'UNRATED'],
  PUBG: ['SQUAD', 'DUO'],
};
const TITLES: Record<GameKey, string[]> = {
  LOL: ['편하게 협곡 한 바퀴', '포지션 맞춰서 다섯 명', '오늘도 즐겁게 한 판', '천천히 같이 배워요', '오더 맞춰서 이겨봐요', '퇴근하고 함께해요', '실수해도 괜찮아요', '우리 팀 준비 완료', '마지막 한 판 같이 해요'],
  VALORANT: ['차분하게 브리핑해요', '역할 맞춰서 한 팀', '같이 각 맞추실 분', '에임보다 팀워크', '즐겁게 한 판 더', '우리 팀 출발 준비'],
  PUBG: ['치킨 한 마리 같이 해요', '천천히 파밍부터', '안전하게 자기장 타요', '브리핑하면서 즐겨요', '오늘은 같이 TOP 1', '우리 팀 출발 준비'],
};
const NICKNAMES = ['달빛산책', '모카한잔', '구름사이', '오후의게임', '작은용기', '포근한밤', '별빛우산', '한판만더', '초록신호', '오늘도맑음', '조용한합류', '주말의우리'];
const CHAMPIONS = [['Garen', 'Camille'], ['LeeSin', 'Viego'], ['Ahri', 'Orianna'], ['Jinx', 'Ezreal'], ['Lulu', 'Thresh']];

export const roomStorageKey = (userId: string): string => `${PREFIX}${userId}`;
const identifier = (): string => crypto.randomUUID();
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);
const nullableNumber = (value: unknown, max = Infinity): number | null => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= max ? value : null;
const strings = (value: unknown): string[] => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').slice(0, 10) : [];

function seedMember(game: GameKey, roomId: string, index: number, roomIndex: number): RoomMember {
  const n = roomIndex * 3 + index;
  const roles = ROOM_ROLES[game];
  const tier = ['SILVER', 'GOLD', 'PLATINUM', 'GOLD', 'DIAMOND', 'SILVER'][roomIndex % 6];
  return {
    id: `${roomId}-member-${index}`,
    nickname: NICKNAMES[n % NICKNAMES.length],
    avatarUrl: `${import.meta.env.BASE_URL}avatars/avatar-${String((n % 8) + 1).padStart(2, '0')}.webp`,
    tier: roomIndex === 3 && index === 1 ? null : tier,
    division: game === 'VALORANT' ? 1 + (n % 3) : 1 + (n % 4),
    winRate: game === 'PUBG' ? 12 + (n * 3) % 19 : 44 + (n * 3) % 23,
    kda: Math.round((1.5 + ((n * 7) % 30) / 10) * 100) / 100,
    roles: [roles[(index + roomIndex) % roles.length]],
    champions: game === 'LOL' ? CHAMPIONS[(index + roomIndex) % CHAMPIONS.length] : [],
    bio: ['실수해도 괜찮아요. 편하게 즐겨요.', '차분하게 소통하며 같이 해요.', '팀 플레이 좋아해요.', '오늘도 재미있는 한 판!'][n % 4],
    voice: roomIndex % 3 === 2 ? 'NO_VOICE' : 'REQUIRED',
  };
}

function seedRooms(now = Date.now()): GameRoom[] {
  const rooms: GameRoom[] = [];
  for (const game of Object.keys(MODES) as GameKey[]) for (const modeKey of MODES[game]) for (const type of ['REALTIME', 'RESERVATION'] as const) {
    const count = game === 'LOL' && modeKey === 'NORMAL_DRAFT' && type === 'REALTIME' ? 9 : 6;
    const limit = roomCapacityLimit(game, modeKey);
    for (let i = 0; i < count; i++) {
      const id = `example-room-${game.toLowerCase()}-${modeKey.toLowerCase()}-${type.toLowerCase()}-${i}`;
      const capacity = limit === 2 ? 2 : [limit, limit, 3, 2, limit, limit][i % 6];
      const confirmed = i === count - 2 || i === count - 1;
      const amount = i === count - 2 ? capacity : Math.min(capacity - 1, [3, 2, 1, 1, 2, 3][i % 6]);
      const members = Array.from({ length: amount }, (_, index) => seedMember(game, id, index, i));
      const roles = ROOM_ROLES[game];
      const aram = modeKey === 'ARAM';
      if (aram) members.forEach(member => { member.roles = []; });
      let title = TITLES[game][i];
      if (game === 'LOL' && modeKey === 'SOLO_DUO_RANKED') title = ['차분하게 랭크 같이 해요', '서로 맞춰갈 듀오', '한 판씩 같이 올라가요', '실수해도 괜찮아요', '오늘의 듀오 준비 완료', '마지막 랭크 한 판'][i];
      if (aram) title = ['포로랑 같이 놀아요', '눈덩이 들고 모여요', '랜덤 챔피언도 즐겁게', '한타 한 번 더!', '칼바람 출발 준비 완료', '주사위는 넉넉하게'][i];
      if (modeKey === 'SWIFTPLAY') title = ['빠르게 한 판 같이 해요', '잠깐 쉬면서 신속 대전', '가볍게 협곡 산책', '점심시간 한 판', '신속 대전 준비 완료', '짧고 즐겁게 함께해요'][i];
      const createdAt = now - (i + 1) * 90_000;
      rooms.push({
        id, game, modeKey, type, title, capacity, members, ownerId: members[0].id,
        desiredRoles: aram ? [] : [roles[(i + amount) % roles.length], roles[(i + amount + 1) % roles.length]],
        voice: VOICES[i % VOICES.length], status: confirmed ? 'CONFIRMED' : 'OPEN', createdAt,
        availableFrom: type === 'RESERVATION' ? new Date(Math.ceil((now + (i + 1) * 3_600_000) / 1_800_000) * 1_800_000).toISOString() : null,
        messages: [{ id: `${id}-welcome`, authorId: members[0].id, text: '안녕하세요! 편하게 이야기하면서 같이 해요.', createdAt }],
      });
    }
  }
  return rooms;
}

function parseMember(value: unknown, game: GameKey): RoomMember | null {
  if (!record(value) || typeof value.id !== 'string' || !value.id || typeof value.nickname !== 'string') return null;
  return {
    id: value.id, nickname: value.nickname.slice(0, 40), avatarUrl: typeof value.avatarUrl === 'string' ? value.avatarUrl : null,
    tier: typeof value.tier === 'string' ? value.tier : null, division: nullableNumber(value.division, 5),
    winRate: nullableNumber(value.winRate, 100), kda: nullableNumber(value.kda), roles: canonicalRoomRoles(game, strings(value.roles)),
    champions: strings(value.champions).slice(0, 3), bio: typeof value.bio === 'string' ? value.bio.slice(0, 160) : '',
    voice: VOICES.includes(value.voice as VoicePreference) ? value.voice as VoicePreference : 'OPTIONAL',
  };
}

function parseSnapshot(raw: string | null, userId: string): RoomSnapshot | null {
  if (!raw) return null;
  try {
    const saved: unknown = JSON.parse(raw);
    if (!record(saved) || saved.version !== 1 || !Array.isArray(saved.rooms)) return null;
    const rooms: GameRoom[] = [];
    const ids = new Set<string>();
    for (const value of saved.rooms) {
      if (!record(value) || typeof value.id !== 'string' || ids.has(value.id) || typeof value.modeKey !== 'string'
        || !Object.hasOwn(MODES, String(value.game)) || !Array.isArray(value.members) || typeof value.ownerId !== 'string'
        || typeof value.title !== 'string' || !['REALTIME', 'RESERVATION'].includes(String(value.type))) continue;
      const game = value.game as GameKey;
      const capacity = nullableNumber(value.capacity, roomCapacityLimit(game, value.modeKey));
      if (capacity === null || !Number.isInteger(capacity) || capacity < 2) continue;
      const members = value.members.map(member => parseMember(member, game)).filter((member): member is RoomMember => member !== null);
      if (!members.length || members.length > capacity || new Set(members.map(member => member.id)).size !== members.length || !members.some(member => member.id === value.ownerId)) continue;
      const messages: RoomMessage[] = Array.isArray(value.messages) ? value.messages.filter((message): message is RoomMessage => record(message)
        && typeof message.id === 'string' && (typeof message.authorId === 'string' || message.authorId === null)
        && typeof message.text === 'string' && typeof message.createdAt === 'number' && Number.isFinite(message.createdAt)).slice(-MAX_MESSAGES) : [];
      const createdAt = nullableNumber(value.createdAt) ?? Date.now();
      let availableFrom = typeof value.availableFrom === 'string' && Number.isFinite(Date.parse(value.availableFrom)) ? value.availableFrom : null;
      // Refresh only untouched example reservations; a user's actual reservation time never silently moves.
      if (value.id.startsWith('example-room-') && availableFrom && Date.parse(availableFrom) <= Date.now() && !members.some(member => member.id === userId)) {
        availableFrom = new Date(Math.ceil((Date.now() + 3_600_000) / 1_800_000) * 1_800_000).toISOString();
      }
      rooms.push({ id: value.id, game, modeKey: value.modeKey, type: value.type as GameRoom['type'], title: value.title.slice(0, 50),
        ownerId: value.ownerId, capacity, members, desiredRoles: canonicalRoomRoles(game, strings(value.desiredRoles)),
        voice: VOICES.includes(value.voice as VoicePreference) ? value.voice as VoicePreference : 'OPTIONAL',
        status: value.status === 'CONFIRMED' || members.length === capacity ? 'CONFIRMED' : 'OPEN', createdAt, availableFrom, messages, autoCloseAt: nullableNumber(value.autoCloseAt) });
      ids.add(value.id);
    }
    if (rooms.filter(room => room.members.some(member => member.id === userId)).length > 1) return null;
    return { version: 1, rooms };
  } catch { return null; }
}

export function readRoomSnapshot(userId: string): RoomSnapshot {
  const existing = cache.get(userId);
  if (existing) return existing;
  let snapshot: RoomSnapshot | null = null;
  try { snapshot = parseSnapshot(localStorage.getItem(roomStorageKey(userId)), userId); } catch { /* Reading remains possible when browser storage is unavailable. */ }
  snapshot ??= { version: 1, rooms: seedRooms() };
  cache.set(userId, snapshot);
  return snapshot;
}

function subscribe(userId: string, listener: () => void): () => void {
  const local = (event: Event) => { if ((event as CustomEvent<string>).detail === userId) listener(); };
  const storage = (event: StorageEvent) => {
    if (event.key !== null && event.key !== roomStorageKey(userId)) return;
    cache.delete(userId);
    listener();
  };
  window.addEventListener(CHANGE_EVENT, local);
  window.addEventListener('storage', storage);
  return () => { window.removeEventListener(CHANGE_EVENT, local); window.removeEventListener('storage', storage); };
}

function save(userId: string, rooms: GameRoom[]): void {
  const snapshot: RoomSnapshot = { version: 1, rooms };
  try { localStorage.setItem(roomStorageKey(userId), JSON.stringify(snapshot)); }
  catch { throw new Error('방 정보를 저장할 수 없어요. 브라우저 저장 공간을 확인해 주세요.'); }
  cache.set(userId, snapshot);
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: userId }));
}

const activeRoomIn = (rooms: GameRoom[], userId: string): GameRoom | undefined => rooms.find(room => room.members.some(member => member.id === userId));
const systemMessage = (text: string): RoomMessage => ({ id: identifier(), authorId: null, text, createdAt: Date.now() });
const append = (room: GameRoom, message: RoomMessage): GameRoom => ({ ...room, messages: [...room.messages, message].slice(-MAX_MESSAGES) });

/** Local, per-user room prototype. These actions deliberately make no backend or peer-network claims. */
export function createRoomActions(userId: string) {
  const current = (): GameRoom[] => {
    if (!userId) throw new Error('로그인 후 이용해 주세요.');
    return readRoomSnapshot(userId).rooms;
  };
  const checkedMember = (member: RoomMember, game: GameKey): RoomMember => {
    if (member.id !== userId) throw new Error('본인 프로필로만 참여할 수 있어요.');
    const normalized = parseMember(member, game);
    if (!normalized || !normalized.nickname.trim()) throw new Error('프로필 정보를 확인해 주세요.');
    return normalized;
  };
  const owned = (rooms: GameRoom[], roomId: string): GameRoom => {
    const room = rooms.find(item => item.id === roomId);
    if (!room) throw new Error('방이 종료되었어요.');
    if (room.ownerId !== userId) throw new Error('방장만 할 수 있어요.');
    return room;
  };
  return {
    create(input: CreateRoomInput, member: RoomMember): GameRoom {
      const rooms = current();
      if (activeRoomIn(rooms, userId)) throw new Error('참여 중인 방에서 먼저 나와 주세요.');
      const limit = roomCapacityLimit(input.game, input.modeKey);
      if (!Number.isInteger(input.capacity) || input.capacity < 2 || input.capacity > limit) throw new Error('게임 모드에 맞는 인원을 선택해 주세요.');
      const title = input.title.trim();
      if (!title || title.length > 50) throw new Error('방 이름은 1~50자로 입력해 주세요.');
      if (!['REALTIME', 'RESERVATION'].includes(input.type) || !VOICES.includes(input.voice)) throw new Error('방 설정을 확인해 주세요.');
      const availableFrom = input.type === 'RESERVATION' ? input.availableFrom : null;
      if (input.type === 'RESERVATION' && (!availableFrom || !Number.isFinite(Date.parse(availableFrom)) || Date.parse(availableFrom) <= Date.now())) throw new Error('예약 시간을 현재보다 뒤로 설정해 주세요.');
      const creator = checkedMember(member, input.game);
      const aram = input.game === 'LOL' && input.modeKey === 'ARAM';
      const room: GameRoom = { id: identifier(), ...input, title, ownerId: userId, availableFrom,
        desiredRoles: aram ? [] : canonicalRoomRoles(input.game, input.desiredRoles),
        members: [{ ...creator, roles: aram ? [] : creator.roles }], status: 'OPEN', createdAt: Date.now(),
        messages: [systemMessage('방이 열렸어요. 채팅과 음성으로 먼저 인사해 보세요.')] };
      save(userId, [room, ...rooms]);
      return room;
    },
    join(roomId: string, member: RoomMember): void {
      const rooms = current();
      const active = activeRoomIn(rooms, userId);
      if (active?.id === roomId) return;
      if (active) throw new Error('참여 중인 방에서 먼저 나와 주세요.');
      const room = rooms.find(item => item.id === roomId);
      if (!room) throw new Error('방이 종료되었어요.');
      if (room.status === 'CONFIRMED' || room.members.length >= room.capacity || autoClosePhase(room, Date.now()) === 'due') throw new Error('이미 매칭이 확정된 방이에요.');
      if (room.type === 'RESERVATION' && room.availableFrom && Date.parse(room.availableFrom) <= Date.now()) throw new Error('예약 시간이 지난 방이에요.');
      const entrant = checkedMember(member, room.game);
      if (room.game === 'LOL' && room.modeKey === 'ARAM') entrant.roles = [];
      const members = [...room.members, entrant];
      let joined = append({ ...room, members, status: members.length === room.capacity ? 'CONFIRMED' : 'OPEN' }, systemMessage(`${entrant.nickname} 님이 들어왔어요.`));
      if (joined.status === 'CONFIRMED') joined = append(joined, systemMessage('정원이 모두 차서 매칭이 확정됐어요.'));
      joined.autoCloseAt = nextAutoCloseAt(joined, Date.now());
      save(userId, rooms.map(item => item.id === roomId ? joined : item));
    },
    leave(): void {
      const rooms = current();
      const room = activeRoomIn(rooms, userId);
      if (!room) return;
      const me = room.members.find(member => member.id === userId)!;
      const members = room.members.filter(member => member.id !== userId);
      const ownerId = room.ownerId === userId ? members[0]?.id : room.ownerId;
      let remaining = append({ ...room, members, ownerId: ownerId ?? userId }, systemMessage(`${me.nickname} 님이 나갔어요.`));
      if (members.length && room.ownerId === userId) remaining = append(remaining, systemMessage(`${members[0].nickname} 님이 방장이 되었어요.`));
      remaining.autoCloseAt = nextAutoCloseAt(remaining, Date.now());
      save(userId, members.length ? rooms.map(item => item.id === room.id ? remaining : item) : rooms.filter(item => item.id !== room.id));
    },
    kick(roomId: string, memberId: string): void {
      const rooms = current();
      const room = owned(rooms, roomId);
      if (memberId === userId) throw new Error('방장은 나가기 버튼을 이용해 주세요.');
      const member = room.members.find(item => item.id === memberId);
      if (!member) throw new Error('이미 방에서 나간 사람이에요.');
      const changed = append({ ...room, members: room.members.filter(item => item.id !== memberId) }, systemMessage(`${member.nickname} 님을 내보냈어요.`));
      changed.autoCloseAt = nextAutoCloseAt(changed, Date.now());
      save(userId, rooms.map(item => item.id === roomId ? changed : item));
    },
    confirm(roomId: string): void {
      const rooms = current();
      const room = owned(rooms, roomId);
      if (room.status === 'CONFIRMED') return;
      if (room.members.length < 2) throw new Error('함께할 사람이 들어오면 확정할 수 있어요.');
      const confirmed = append({ ...room, status: 'CONFIRMED', autoCloseAt: null }, systemMessage('방장이 매칭을 확정했어요. 이제 함께 출발해요!'));
      save(userId, rooms.map(item => item.id === roomId ? confirmed : item));
    },
    autoConfirm(roomId: string, expectedDeadline: number): void {
      const rooms = current();
      const room = rooms.find(item => item.id === roomId);
      if (!room || !canAutoClose(room, userId, expectedDeadline, Date.now())) return;
      const closed = append({ ...room, status: 'CONFIRMED', autoCloseAt: null }, systemMessage('안내한 시간이 지나 모집을 마감했어요. 참여한 팀원과 대화는 계속할 수 있어요.'));
      save(userId, rooms.map(item => item.id === roomId ? closed : item));
    },
    extendRecruitment(roomId: string, expectedDeadline: number): void {
      const rooms = current();
      const room = owned(rooms, roomId);
      if (room.autoCloseAt !== expectedDeadline || room.status !== 'OPEN') return;
      if (Date.now() >= expectedDeadline) throw new Error('모집 마감 시간이 지났어요. 방 상태를 확인해 주세요.');
      const continued = append({ ...room, autoCloseAt: nextAutoCloseAt(room, Date.now()) }, systemMessage('방장이 모집을 계속하기로 했어요. 10분 뒤 다시 안내할게요.'));
      save(userId, rooms.map(item => item.id === roomId ? continued : item));
    },
    send(text: string): void {
      const rooms = current();
      const room = activeRoomIn(rooms, userId);
      if (!room) throw new Error('방에 들어온 후 메시지를 보낼 수 있어요.');
      const body = text.trim();
      if (!body || body.length > 2000) throw new Error('메시지는 1~2,000자로 입력해 주세요.');
      const changed = append(room, { id: identifier(), authorId: userId, text: body, createdAt: Date.now() });
      save(userId, rooms.map(item => item.id === room.id ? changed : item));
    },
  };
}

export function useRoomStore(userId: string) {
  const listen = useCallback((listener: () => void) => subscribe(userId, listener), [userId]);
  const read = useCallback(() => readRoomSnapshot(userId), [userId]);
  const snapshot = useSyncExternalStore(listen, read, read);
  const actions = useMemo(() => createRoomActions(userId), [userId]);
  return { rooms: snapshot.rooms, activeRoom: activeRoomIn(snapshot.rooms, userId) ?? null, ...actions };
}
