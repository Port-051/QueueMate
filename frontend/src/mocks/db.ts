import type {
  BlockView, FriendRequestView, FriendView, GameAccountView, MatchCondition, MatchRequestView,
  PartyView, ProposalView, RecentPlayerView, ReservationView, UserProfile,
} from '../api/types';

export const uid = () => crypto.randomUUID();

const hoursAgo = (h: number) => new Date(Date.now() - h * 3600_000).toISOString();

export interface MockAccount { email: string; password: string; profile: UserProfile; }

/** 매칭 후보로 쓰이는 가상 사용자. 공개 탐색 대상이 아니라 mock 데이터일 뿐이다. */
export interface MockUser { userId: string; nickname: string; }

export const CANDIDATES: MockUser[] = [
  { userId: 'u-gankflow', nickname: 'GankFlow' },
  { userId: 'u-playmaker', nickname: 'PlayMaker' },
  { userId: 'u-supportlife', nickname: 'SupportLife' },
  { userId: 'u-lategame', nickname: 'LateGame' },
  { userId: 'u-midtheory', nickname: 'MidTheory' },
  { userId: 'u-aimking', nickname: 'AimKing' },
  { userId: 'u-blueocean', nickname: 'BlueOcean' },
  { userId: 'u-chickendinner', nickname: 'ChickenDinner' },
  { userId: 'u-silentjungle', nickname: 'SilentJungle' },
  { userId: 'u-healingyou', nickname: 'HealingYou' },
];

export interface QueueSim {
  timers: number[];
  waitingSeconds: number;
}

export interface MockProposal {
  view: ProposalView;
  condition: MatchCondition;
  requestId?: string;
  reservationId?: string;
  timers: number[];
}

export interface MockParty {
  view: PartyView;
  condition: MatchCondition;
  timers: number[];
}

export interface MockDb {
  accounts: MockAccount[];
  session: { userId: string; accessToken: string; refreshToken: string } | null;
  me: UserProfile;
  gameAccounts: GameAccountView[];
  friends: FriendView[];
  friendRequests: FriendRequestView[];
  blocks: BlockView[];
  recentPlayers: RecentPlayerView[];
  matchRequests: Map<string, { view: MatchRequestView; condition: MatchCondition; sim: QueueSim }>;
  proposals: Map<string, MockProposal>;
  parties: Map<string, MockParty>;
  reservations: ReservationView[];
}

const DEMO_USER: UserProfile = { id: 'u-me', nickname: 'QueueMaster', avatarUrl: null };

export const DEMO_CREDENTIALS = { email: 'demo@queuemate.gg', password: 'queuemate1' };

function seedFriends(): FriendView[] {
  return [
    { userId: 'u-gankflow', nickname: 'GankFlow', friendedAt: hoursAgo(52) },
    { userId: 'u-supportlife', nickname: 'SupportLife', friendedAt: hoursAgo(120) },
    { userId: 'u-playmaker', nickname: 'PlayMaker', friendedAt: hoursAgo(300) },
    { userId: 'u-midtheory', nickname: 'MidTheory', friendedAt: hoursAgo(700) },
  ];
}

function seedRequests(): FriendRequestView[] {
  return [
    { id: uid(), direction: 'RECEIVED', counterpartUserId: 'u-healingyou', counterpartNickname: 'HealingYou', status: 'PENDING', createdAt: hoursAgo(4) },
    { id: uid(), direction: 'RECEIVED', counterpartUserId: 'u-silentjungle', counterpartNickname: 'SilentJungle', status: 'PENDING', createdAt: hoursAgo(26) },
  ];
}

function seedRecent(): RecentPlayerView[] {
  return [
    { userId: 'u-blueocean', nickname: 'BlueOcean', lastPlayedAt: hoursAgo(0.5), playCount: 3, friend: false },
    { userId: 'u-lategame', nickname: 'LateGame', lastPlayedAt: hoursAgo(1), playCount: 1, friend: false },
    { userId: 'u-chickendinner', nickname: 'ChickenDinner', lastPlayedAt: hoursAgo(2), playCount: 5, friend: false },
    { userId: 'u-gankflow', nickname: 'GankFlow', lastPlayedAt: hoursAgo(20), playCount: 12, friend: true },
    { userId: 'u-aimking', nickname: 'AimKing', lastPlayedAt: hoursAgo(48), playCount: 2, friend: false },
  ];
}

export function createDb(): MockDb {
  return {
    accounts: [{ email: DEMO_CREDENTIALS.email, password: DEMO_CREDENTIALS.password, profile: DEMO_USER }],
    session: null,
    me: { ...DEMO_USER },
    gameAccounts: [
      { id: uid(), game: 'LOL', externalGameId: 'QueueMaster#KR1', region: 'KR', rankCode: null, verifiedAt: hoursAgo(200) },
    ],
    friends: seedFriends(),
    friendRequests: seedRequests(),
    blocks: [],
    recentPlayers: seedRecent(),
    matchRequests: new Map(),
    proposals: new Map(),
    parties: new Map(),
    reservations: [],
  };
}

export const db: MockDb = createDb();

export function clearTimers(timers: number[]): void {
  timers.forEach((t) => window.clearTimeout(t));
  timers.forEach((t) => window.clearInterval(t));
  timers.length = 0;
}
