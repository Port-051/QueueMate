import type { GameKey, MemberCard, PostResponse, PubgPerspective } from '../api/types';
import { modeSeed } from '../domain/gameCatalog';
import { tierForMode } from '../domain/profileTier';
import { accountRank } from './accountRank';
import type { BoardMember, BoardRoom } from './types';

const UNKNOWN_NICKNAME = '알 수 없음';

const finite = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;

/**
 * 카드 한 장 → 보드의 사람. 가입하지 않은 번호(`nickname: null`)도 인원수와 어긋나지 않게 그린다(platform CLAUDE.md §3.3).
 * **티어는 그 글의 모드의 사다리 티어다** — 모드에 사다리가 없으면(일반 · 칼바람 · 언레이티드) 그 사람 사다리들 가운데 가장 높은 티어(`domain/profileTier.ts` `tierForMode` — Claude 가 정한 세부).
 * PUBG 의 두 칸은 치킨률 · K/D 다(`stats.detail.top1Rate` · `kd` — PUBG 는 `winRate` · `kda` 가 늘 `null` 이다).
 */
export function toBoardMember(card: MemberCard, game: GameKey, modeKey: string): BoardMember {
  const profile = card.profile;
  const picked = tierForMode(game, modeKey, profile);
  const rank = accountRank(picked.tier);
  const stats = profile?.stats ?? null;
  const champions = stats?.detail?.mostChampions ?? null;
  const pubg = game === 'PUBG';
  return {
    id: String(card.userId),
    nickname: card.nickname ?? UNKNOWN_NICKNAME,
    host: card.host,
    tier: rank.tier,
    division: rank.division,
    tierLadder: picked.ladder,
    winRate: pubg ? finite(stats?.detail?.top1Rate) : stats?.winRate ?? null,
    kda: pubg ? finite(stats?.detail?.kd) : stats?.kda ?? null,
    champions: Array.isArray(champions) ? champions.slice(0, 3).map(c => c.championId) : [],
    profile,
    card,
  };
}

const perspectiveOf = (game: GameKey, post: PostResponse): PubgPerspective | null => {
  if (game !== 'PUBG') return null;
  const value = post.conditions?.perspective;
  return value === 'TPP' || value === 'FPP' ? value : null;
};

/** `PostResponse` → `BoardRoom`. 순수 함수 — 목록 · 단건 · 글 쓰기 응답이 다 이것을 거친다. */
export function toBoardRoom(post: PostResponse): BoardRoom {
  return {
    id: String(post.postId),
    postId: post.postId,
    game: post.game,
    modeKey: post.mode ?? '',
    title: post.title,
    description: post.description ?? '',
    hostId: String(post.hostId),
    capacity: post.capacity,
    memberCount: post.memberCount,
    full: post.full,
    status: post.status,
    voice: post.voice,
    perspective: perspectiveOf(post.game, post),
    wantedPositions: post.wantedPositions ?? [],
    createdAt: post.createdAt,
    host: toBoardMember(post.host, post.game, post.mode ?? ''),
    members: (post.members ?? []).map(card => toBoardMember(card, post.game, post.mode ?? '')),
    post,
  };
}

/**
 * PUBG 모드의 시점 — 카탈로그 모드의 `perspective`(프런트 전용 UI 메타 · 2026-09-29)에서 읽는다. 값은 모드 이름에 접힌 시점과 같다(`RANKED_DUO_TPP` → `TPP` —
 * 서버의 auto-join 은 이름에서 같은 규칙으로 읽는다, `AutoJoinService#perspectiveOf`). 글의 `conditions.perspective` 는 이것으로 채운다(글 쓰기 · 글 고치기).
 */
export function perspectiveFromMode(game: GameKey, modeKey: string): PubgPerspective | null {
  return game === 'PUBG' ? modeSeed(game, modeKey)?.perspective ?? null : null;
}

/** 보드에서 참여 버튼을 누르기 전에 거르는 것 — 서버가 어차피 거절하는 것을 미리 문구로. `null` 이면 눌러도 된다. */
export function roomEntryError(room: BoardRoom, selfId: string, activeRoomId: string | null): string | null {
  if (room.status === 'CONFIRMED') return '확정된 방이에요';
  if (room.status === 'EXPIRED') return '모집이 끝났어요';
  if (room.full || room.memberCount >= room.capacity) return '정원이 가득 찼어요';
  if (activeRoomId === room.id || room.members.some(member => member.id === selfId)) return '이미 참여 중인 방이에요';
  if (activeRoomId) return '다른 방에 참여 중이에요. 나온 뒤 참여할 수 있어요';
  return null;
}
