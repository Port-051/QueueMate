import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { hasErrorCode, isApiError } from '../api/error';
import { createEventStream } from '../api/sse';
import type { EventStream } from '../api/sse';
import type {
  CreateReservationRequest, GameKey, MatchCondition, MatchConfirmedPayload, MatchProposalCreatedPayload, MatchRequestView, ReservationView, ServerEvent, VoicePreference,
} from '../api/types';
import { useToast } from '../components/ui';
import { buildMatchRequest, matchErrorMessage, matchRequestError } from '../domain/matchRequest';
import { storedPlayPurpose } from '../domain/gameCatalog';
import { DEFAULT_PLAY_PURPOSE, targetPartySize } from '../domain/gameConfig';
import { rememberCondition } from './recentConditions';
import { useAuth } from './AuthContext';

/**
 * 자동 매칭의 상태 하나(INV-1 — 활성 요청은 사람당 하나). 원본은 `matching/contracts/openapi.yaml`(매칭 요청 · heartbeat · 제안) ·
 * `platform-api.md` "자동 매칭이 게시판 방에 먼저 합류하는 길"(auto-join) · "자동 매칭 파티의 방"(`MATCH_CONFIRMED` 뒤) · `events.md`(`MATCH_*` 5종).
 *
 * - **"매칭 시작"** — ① `POST /posts/auto-join`(같은 본문) → 200 이면 그 방으로(`{kind: 'ROOM'}`) · 404 `NO_MATCHING_POST` 면 ② `POST /match-requests` → `QUEUED`.
 *   ① 의 409(`IN_OTHER_ROOM` · `ALREADY_QUEUED`) · 400 은 멈춘다. ① 의 503(`ROOM_STATE_UNAVAILABLE`)은 계약이 "바로 `matching` 을 부른다" 를 허용해 ② 로 간다.
 * - **상태의 원본은 `GET /match-requests`** — 늘 200 · `IDLE` 이면 활성 요청이 없다. 접수 응답 · 알림 · SSE 재연결 직후 · 탭이 다시 보일 때 · 새로고침이 전부 이것으로 맞춘다.
 *   **주기적으로 묻지 않는다**(2026-10-01 소유자 — 대기 · 제안 중의 3초 폴링을 걷었다). SSE 가 받쳐서다 — `EventSource` 가 스스로 다시 붙고 · `api/sse.ts` 가 60초 침묵이면 새로 열고 ·
 *   다시 붙은 직후 한 번 묻는다(끊긴 동안의 알림은 다시 오지 않는다). 숨은 탭에서 돌아왔을 때도 한 번 묻는다(`visibilitychange`).
 *   서버는 조건을 돌려주지 않으므로 이 브라우저가 `{requestId, condition}` 을 localStorage 에 기억한다(`requestId` 가 같을 때만 쓴다).
 * - **heartbeat** — `QUEUED` · `PROPOSED` 동안 30초마다 `POST /match-requests/heartbeat`. 404 면 이미 빠진 것이라 다시 조회한다(D-43 — 90초 끊기면 서버가 취소).
 * - **제안** — `MATCH_PROPOSAL_CREATED {memberNumber, target, partyId}` → 다시 조회 → `PROPOSED` 면 제안 화면(`/app/proposals/{partyId}`). 수락 · 거절은 204 → 다시 조회.
 *   **제안 화면으로 옮기는 것은 상태가 새 `partyId` 로 `PROPOSED` 가 되는 순간 한 번이다**(`applyView` — 어느 조회가 적었든. 2026-10-01 — 알림이 부른 조회의 답이 순번 가드에 버려지면
 *   제안 화면이 안 열리던 버그). 같은 제안으로는 두 번 옮기지 않는다(`proposalShown` — "게시판으로" 돌아간 사람을 다시 끌고 오지 않게 · 새로 고쳐도 sessionStorage 로 기억한다).
 *   **"같은 제안" 은 `partyId` + 만료 시각이다**(`proposalKey`) — 다시 모인 제안은 같은 `partyId` 로 온다(2026-10-01 실제 서버 검증 — `partyId` 만 보던 때는 두 번째 제안부터 옮기지 않았다).
 *   `MATCH_PROPOSAL_EXPIRED` · `MATCH_CANCELLED` · `MATCH_QUEUE_UPDATED` 는 다시 조회하라는 신호다. 제안의 팀원 목록은 없다(`GET /proposals/{id}` 없음).
 * - **확정** — `MATCH_CONFIRMED {partyId}` → 조작 없이 `POST /match-parties/{partyId}/room` → `{roomId}`(= partyId) → `/app/party/{roomId}`. 503 은 5초 뒤 한 번 더.
 *   알림을 놓쳤으면 상태 조회의 `MATCHED` + `partyId` 가 같은 길을 밟는다(확정 뒤 60초 안 — D-42).
 *   **내가 나간 · 강퇴당한 파티로는 저절로 다시 들어가지 않는다**(2026-10-01 소유자 — 버그 ① · `rememberLeftParty` · localStorage `qm.leftParties.{userId}` — 다른 탭도 본다 · 10분).
 *   제안 화면 · 파티 방 둘 다 **게시판 오른쪽 방 패널**로 열린다(2026-09-30 소유자 지시 — `pages/HomePage.tsx`). 제안 화면에서 확정되면 그 자리를 방으로 갈아 끼운다(뒤로 가기에 끝난 제안이 남지 않게).
 * - **파티의 조건을 기억한다**(`activePartyInfo` — 2026-09-30). 서버의 방 응답에는 게임 · 모드 · 정원이 없어(P-30) 확정하는 순간 이 브라우저가 알던 조건(대기 때 기억한 것)과
 *   제안의 정원(`MATCH_PROPOSAL_CREATED` 의 `target`)을 `activePartyId` 와 함께 적어 둔다 — 방 패널이 게임 · 모드 · `n/정원` 을 그리고 게시판을 그 게임으로 맞춘다.
 *   다른 브라우저에서 들어온 방은 모른다(`null` — 그때는 전처럼 사용자 번호 · 인원만).
 * - 옛 원본의 `SESSION_SNAPSHOT` · `PARTY_*` · `RESERVATION_*` 핸들러는 없다 — 그 이벤트는 오지 않는다. 예약은 대응물이 없어 상태만 남겼다(부르면 404).
 */

const ACTIVE_PARTY_KEY = 'qm.activeParty.';
const ACTIVE_PARTY_INFO_KEY = 'qm.activePartyInfo.';
const ACTIVE_MATCH_KEY = 'qm.activeMatch.';
const PROPOSAL_SHOWN_KEY = 'qm.proposalShown.';
const LEFT_PARTIES_KEY = 'qm.leftParties.';

/**
 * 내가 나간 · 강퇴당한 빠른매치 파티를 기억하는 시간 — matching 이 확정 뒤 `MATCHED + partyId` 를 답하는 60초(`confirmed-retention-seconds`)보다 넉넉히,
 * 파티 HASH 의 수명(600초 — 그동안은 `POST /match-parties/{partyId}/room` 이 다시 들여보낸다)과 같게.
 */
const LEFT_PARTY_TTL_MS = 10 * 60_000;
/** 로그인 직후 · 새로 고침의 첫 조회가 실패했을 때(잠시 서버가 없다) 다시 해 보는 간격 — 대기 중의 폴링이 아니다(그것은 2026-10-01 에 걷었다). */
const RESTORE_RETRY_MS = 3000;
/** 계약의 규약 — 30초마다. 서버는 90초 안에 다음 신호가 없으면 취소한다. */
const HEARTBEAT_MS = 30_000;

export type StartResult =
  | { kind: 'ROOM'; roomId: string; postId: number }
  | { kind: 'QUEUED'; request: MatchRequestView };

/**
 * 자동 매칭 파티의 조건 — 확정하는 순간 이 브라우저가 알던 것(대기 때 기억한 조건 · 제안의 정원). 서버는 돌려주지 않는다(방 응답에 게임 · 모드 · 정원이 없다 — P-30).
 * `target` 은 제안의 정원이고 없으면 그 모드의 `targetPartySize`(정적 상수 — 서버의 파티 HASH `target` 과 같은 값이다). 조건을 몰랐으면 `game` · `modeKey` 가 `null`.
 */
export interface MatchPartyInfo {
  partyId: string;
  game: GameKey | null;
  modeKey: string | null;
  voicePreference: VoicePreference | null;
  target: number | null;
}

/** 제안 화면이 그리는 것 — 상태 조회의 `PROPOSED` 갈래 + `MATCH_PROPOSAL_CREATED` 가 실어 준 정원(같은 `partyId` 일 때만). */
export interface ProposalState {
  partyId: string;
  /** epoch ms */
  expiresAt: number;
  isAccepted: boolean;
  target: number | null;
  memberNumber: number | null;
}

interface MatchValue {
  /** 활성 요청. `IDLE` 이면 `null`. `MATCHED` 는 파티룸에 들어가면 `null` 이 된다(그 뒤의 상태는 방의 것이다). */
  request: MatchRequestView | null;
  /** 이 브라우저가 기억하는 조건 — 서버는 돌려주지 않는다. 새로고침 뒤 `requestId` 가 다르면 `null`. */
  condition: MatchCondition | null;
  proposal: ProposalState | null;
  /** 자동 매칭 파티의 방 id(= `partyId` · UUID) — `MATCH_CONFIRMED` 뒤 `POST /match-parties/{partyId}/room` 으로 들어간 방. 방 화면(4단계)이 쓴다. */
  activePartyId: string | null;
  /** `activePartyId` 의 조건(게임 · 모드 · 음성 · 정원) — 이 브라우저가 확정 때 적어 둔 것. 모르면 `null`. */
  activePartyInfo: MatchPartyInfo | null;
  reservations: ReservationView[];
  reservationsLoaded: boolean;
  reservationsError: string | null;
  stream: EventStream | null;
  /** "매칭 시작" — auto-join → 404 면 match-requests. 보내기 전의 검증(`matchRequestError`)에 걸리면 `Error` 를 던진다. */
  start(condition: MatchCondition): Promise<StartResult>;
  /** `GET /match-requests` 로 상태를 맞춘다. `MATCHED` 면 파티룸 입장까지 이어진다. */
  refresh(): Promise<MatchRequestView | null>;
  cancel(): Promise<void>;
  accept(): Promise<void>;
  decline(): Promise<void>;
  /** `POST /match-parties/{partyId}/room` — 확정된 파티의 방으로. 돌아오는 값은 `roomId`. 503 은 `Retry-After` 뒤 한 번 더 시도한다. */
  enterPartyRoom(partyId: string): Promise<string>;
  refreshReservations(): Promise<void>;
  saveReservation(body: CreateReservationRequest, id?: string): Promise<void>;
  setActivePartyId(id: string | null): void;
  /**
   * 내가 나간 · 강퇴당한 빠른매치 파티를 적는다 — 그 `partyId` 로는 10분 동안 저절로 다시 들어가지 않는다(버그 ① — 2026-10-01 소유자 · `settleConfirmed`).
   * 방 세션(`RoomSessionContext`)이 빠른매치 방을 `LEFT` · `KICKED` 로 잃을 때 부른다. 게시판 방은 부르지 않는다.
   */
  rememberLeftParty(partyId: string): void;
}

const MatchCtx = createContext<MatchValue | null>(null);

const readActiveParty = (userId?: string) => {
  if (!userId) return null;
  try { return localStorage.getItem(`${ACTIVE_PARTY_KEY}${userId}`); } catch { return null; }
};

const writeActiveParty = (userId: string | undefined, id: string | null) => {
  if (!userId) return;
  try {
    if (id) localStorage.setItem(`${ACTIVE_PARTY_KEY}${userId}`, id);
    else localStorage.removeItem(`${ACTIVE_PARTY_KEY}${userId}`);
  } catch {
    /* storage 접근 불가여도 세션 안에서는 state로 동작한다 */
  }
};

const readActivePartyInfo = (userId?: string): MatchPartyInfo | null => {
  if (!userId) return null;
  try {
    const raw = localStorage.getItem(`${ACTIVE_PARTY_INFO_KEY}${userId}`);
    const info = raw ? JSON.parse(raw) as Partial<MatchPartyInfo> : null;
    if (!info?.partyId) return null;
    return { partyId: info.partyId, game: info.game ?? null, modeKey: info.modeKey ?? null, voicePreference: info.voicePreference ?? null, target: typeof info.target === 'number' ? info.target : null };
  } catch { return null; }
};

const writeActivePartyInfo = (userId: string | undefined, info: MatchPartyInfo | null) => {
  if (!userId) return;
  try {
    if (info) localStorage.setItem(`${ACTIVE_PARTY_INFO_KEY}${userId}`, JSON.stringify(info));
    else localStorage.removeItem(`${ACTIVE_PARTY_INFO_KEY}${userId}`);
  } catch { /* 저장하지 못하면 방 패널이 게임 · 정원을 모를 뿐이다 */ }
};

/**
 * 제안 한 건의 열쇠 — **`partyId` + 만료 시각(`expiresAt`)**. matching 은 만료 · 거절 · 취소 뒤 정원이 다시 차 새로 열린 제안에도 **같은 `partyId`** 를 쓴다
 * (2026-10-01 실제 서버로 확인 — 다섯 번 다시 모인 제안이 전부 같은 id). 새로 열린 제안은 만료 시각이 바뀌고 같은 제안의 조회는 같은 값을 답한다.
 * 제안 화면으로 옮긴 기록(`proposalShown`)과 제안 화면의 팀원 카드(`ProposalPage`)가 이것으로 "다른 제안" 을 가른다.
 */
export const proposalKey = (partyId: string, expiresAt: number | undefined | null): string => `${partyId}@${expiresAt ?? ''}`;

/** 제안 화면으로 이미 옮긴 제안(`proposalKey`) — 이 탭에서만(sessionStorage). 못 읽으면 `null`(그때는 이 세션의 ref 만으로 막는다). */
const readProposalShown = (userId?: string): string | null => {
  if (!userId) return null;
  try { return sessionStorage.getItem(`${PROPOSAL_SHOWN_KEY}${userId}`); } catch { return null; }
};

const writeProposalShown = (userId: string | undefined, key: string) => {
  if (!userId) return;
  try { sessionStorage.setItem(`${PROPOSAL_SHOWN_KEY}${userId}`, key); } catch { /* 저장하지 못하면 새로 고친 뒤 한 번 더 옮길 뿐이다 */ }
};

/** 아직 잊지 않은 것만 — `partyId` → 잊는 시각(epoch ms). 숫자가 아니거나 지난 것은 버린다. */
const unexpired = (parties: Record<string, unknown>): Record<string, number> => {
  const now = Date.now();
  return Object.fromEntries(Object.entries(parties).filter((entry): entry is [string, number] => typeof entry[1] === 'number' && entry[1] > now));
};

/**
 * 내가 나간 · 강퇴당한 빠른매치 파티 — `partyId` → 잊는 시각(epoch ms). **localStorage** 라 같은 브라우저의 다른 탭도 본다(2026-10-01 — 한 탭에서 나가면 다른 탭도
 * 그 파티로 다시 들어가지 않게. 다른 탭이 그 방에 있었으면 "이 방에 없음" 으로 방을 잃고, 그 뒤의 조회가 이 기록에 막힌다) · 새로 고쳐도 남는다. 지난 것은 읽을 때 버린다.
 */
const readLeftParties = (userId?: string): Record<string, number> => {
  if (!userId) return {};
  try {
    const raw = localStorage.getItem(`${LEFT_PARTIES_KEY}${userId}`);
    return unexpired(raw ? JSON.parse(raw) as Record<string, unknown> : {});
  } catch { return {}; }
};

const writeLeftParties = (userId: string | undefined, parties: Record<string, number>) => {
  if (!userId) return;
  try {
    if (Object.keys(parties).length) localStorage.setItem(`${LEFT_PARTIES_KEY}${userId}`, JSON.stringify(parties));
    else localStorage.removeItem(`${LEFT_PARTIES_KEY}${userId}`);
  } catch { /* 저장하지 못하면 이 세션의 ref 만으로 막는다 — 새로 고친 뒤 · 다른 탭은 60초 안이면 한 번 더 들어갈 수 있다 */ }
};

interface SavedMatch { requestId: string; condition: MatchCondition; }

const readSavedMatch = (userId: string): SavedMatch | null => {
  try {
    const raw = localStorage.getItem(`${ACTIVE_MATCH_KEY}${userId}`);
    const saved = raw ? JSON.parse(raw) as Partial<SavedMatch> : null;
    if (!saved?.requestId || !saved.condition) return null;
    // 옛 저장값의 목적 `NORMAL` 은 `TRYHARD`(빡겜)로 옮겨 읽는다(2026-09-29 — matching D-49 · 라벨이 비지 않게).
    const playPurpose = storedPlayPurpose(saved.condition.playPurpose) ?? DEFAULT_PLAY_PURPOSE;
    return { requestId: saved.requestId, condition: { ...saved.condition, playPurpose } };
  } catch { return null; }
};

const writeSavedMatch = (userId: string, saved: SavedMatch | null) => {
  try {
    if (saved) localStorage.setItem(`${ACTIVE_MATCH_KEY}${userId}`, JSON.stringify(saved));
    else localStorage.removeItem(`${ACTIVE_MATCH_KEY}${userId}`);
  } catch { /* Matching still works when browser storage is unavailable. */ }
};

const sleep = (ms: number) => new Promise<void>((resolve) => { window.setTimeout(resolve, ms); });

const isActive = (view: MatchRequestView | null) => view?.status === 'QUEUED' || view?.status === 'PROPOSED';

export function MatchProvider({ children }: { children: ReactNode }) {
  const { status, userId } = useAuth();
  return <MatchSession key={`${status}:${userId ?? ''}`}>{children}</MatchSession>;
}

function MatchSession({ children }: { children: ReactNode }) {
  const { status, userId, gameAccounts } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const pathnameRef = useRef(location.pathname);
  pathnameRef.current = location.pathname;
  const toast = useToast();

  const [request, setRequest] = useState<MatchRequestView | null>(null);
  const [condition, setCondition] = useState<MatchCondition | null>(null);
  const [proposalMeta, setProposalMeta] = useState<{ partyId: string; target: number; memberNumber: number } | null>(null);
  const [activePartyId, setActivePartyIdState] = useState<string | null>(() => readActiveParty(userId ?? undefined));
  const [activePartyInfo, setActivePartyInfo] = useState<MatchPartyInfo | null>(() => readActivePartyInfo(userId ?? undefined));
  const [reservations, setReservations] = useState<ReservationView[]>([]);
  const [reservationsLoaded, setReservationsLoaded] = useState(false);
  const [reservationsError, setReservationsError] = useState<string | null>(null);
  const [stream, setStream] = useState<EventStream | null>(null);

  const requestRef = useRef<MatchRequestView | null>(null);
  requestRef.current = request;
  const conditionRef = useRef<MatchCondition | null>(null);
  conditionRef.current = condition;
  const proposalMetaRef = useRef(proposalMeta);
  proposalMetaRef.current = proposalMeta;
  const activePartyRef = useRef<string | null>(null);
  activePartyRef.current = activePartyId;
  const activePartyInfoRef = useRef<MatchPartyInfo | null>(activePartyInfo);
  activePartyInfoRef.current = activePartyInfo;
  const gameAccountsRef = useRef(gameAccounts);
  gameAccountsRef.current = gameAccounts;
  const live = useRef(true);
  useEffect(() => {
    live.current = true;
    return () => { live.current = false; };
  }, []);
  /** 내가 취소를 눌러 IDLE 이 된 것인지 — 아니면 서버가 거둔 것이라(heartbeat 끊김 등) 알려 준다. */
  const cancelling = useRef(false);
  /** 같은 파티의 방에 두 번 들어가지 않게(알림과 조회 — 재연결 · 탭 복귀 — 가 겹친다). */
  const entering = useRef<string | null>(null);
  /** 조회의 차례 — 늦게 도착한 옛 조회의 답이 이미 적은 새 답을 덮지 않게(`refresh`). */
  const refreshSeq = useRef(0);
  /** 마지막으로 화면에 적은 조회의 차례와 그 답 — 더 새 답이 이미 적혔으면 옛 답은 버리고 이것을 돌려준다. */
  const appliedSeq = useRef(0);
  const appliedView = useRef<MatchRequestView | null>(null);
  /** 제안 화면으로 이미 옮긴 제안(`proposalKey` — `partyId` + 만료 시각) — 같은 제안으로 두 번 옮기지 않는다(`applyView`). 새로 고쳐도 이 탭에서는 기억한다. */
  const proposalShown = useRef<string | null>(readProposalShown(userId ?? undefined));
  /** 내가 나간 · 강퇴당한 빠른매치 파티(`rememberLeftParty`) — 그 `partyId` 로는 저절로 다시 들어가지 않는다(`settleConfirmed`). 저장소를 못 쓰는 때의 받침이다. */
  const leftParties = useRef<Record<string, number>>(readLeftParties(userId ?? undefined));

  const rememberLeftParty = useCallback((partyId: string) => {
    // 적을 때 지난 것을 함께 버린다(localStorage 라 브라우저를 다시 열어도 남는다 — 쌓이지 않게).
    const parties = { ...unexpired(leftParties.current), ...readLeftParties(userId ?? undefined), [partyId]: Date.now() + LEFT_PARTY_TTL_MS };
    leftParties.current = parties;
    writeLeftParties(userId ?? undefined, parties);
  }, [userId]);

  const isLeftParty = useCallback((partyId: string) => {
    const until = readLeftParties(userId ?? undefined)[partyId] ?? leftParties.current[partyId];
    return until !== undefined && until > Date.now();
  }, [userId]);

  const setActivePartyId = useCallback((id: string | null) => {
    if (!live.current) return;
    writeActiveParty(userId ?? undefined, id);
    setActivePartyIdState(id);
    // 다른 파티(또는 없음)면 옛 조건을 버린다 — 조건은 확정 때 `rememberPartyInfo` 가 새로 적는다.
    // (상태 갱신 함수 안에서 저장소를 지우면 렌더 때 늦게 돌아 방금 적은 조건까지 지웠다 — 부르는 순서대로 바로 쓴다.)
    if (activePartyInfoRef.current?.partyId !== id) {
      activePartyInfoRef.current = null;
      writeActivePartyInfo(userId ?? undefined, null);
      setActivePartyInfo(null);
    }
  }, [userId]);

  const rememberPartyInfo = useCallback((info: MatchPartyInfo) => {
    if (!live.current) return;
    activePartyInfoRef.current = info;
    writeActivePartyInfo(userId ?? undefined, info);
    setActivePartyInfo(info);
  }, [userId]);

  const enterPartyRoom = useCallback(async (partyId: string, retry = true): Promise<string> => {
    try {
      const { roomId } = await api.enterMatchPartyRoom(partyId);
      setActivePartyId(roomId);
      return roomId;
    } catch (err) {
      // Redis 에 닿지 못했다(fail-closed) — 계약의 Retry-After 만큼 기다렸다가 한 번만 더. 파티 HASH 는 10분 산다.
      if (retry && isApiError(err) && err.status === 503) {
        await sleep((err.retryAfterSeconds ?? 5) * 1000);
        if (!live.current) throw err;
        return enterPartyRoom(partyId, false);
      }
      throw err;
    }
  }, [setActivePartyId]);

  /**
   * 확정된 파티 — 방을 만들거나 들어가고 방 화면으로. 알림(`MATCH_CONFIRMED`)과 상태 조회(`MATCHED`)가 같은 길을 밟는다.
   * **내가 나간 · 강퇴당한 파티로는 들어가지 않는다**(2026-10-01 소유자 — 버그 ①). matching 은 확정 뒤 60초 동안 `GET /match-requests` 에 `MATCHED + partyId` 를 답하고
   * `POST /match-parties/{partyId}/room` 은 파티원을 다시 들여보내서, 방에서 나온 뒤의 조회(재연결 · 탭 복귀 · 새로 고침)마다 같은 방에 다시 넣었다. 서버는 바꾸지 않는다 —
   * 방 세션이 그 방을 잃을 때 적은 목록(`rememberLeftParty`)을 여기서 본다. 다른 `partyId`(다시 빠른매치를 해서 확정된 파티)는 그대로 들어간다.
   */
  const settleConfirmed = useCallback(async (partyId: string) => {
    if (entering.current === partyId || activePartyRef.current === partyId || isLeftParty(partyId)) return;
    entering.current = partyId;
    // 서버는 파티의 조건을 돌려주지 않는다 — 지우기 전에 이 브라우저가 알던 조건 · 제안의 정원을 적어 둔다(방 패널의 게임 · 모드 · n/정원).
    const known = conditionRef.current ?? (userId ? readSavedMatch(userId)?.condition ?? null : null);
    const meta = proposalMetaRef.current?.partyId === partyId ? proposalMetaRef.current : null;
    try {
      const roomId = await enterPartyRoom(partyId);
      if (!live.current) return;
      rememberPartyInfo({
        partyId: roomId,
        game: known?.game ?? null,
        modeKey: known?.modeKey ?? null,
        voicePreference: known?.voicePreference ?? null,
        target: meta?.target ?? (known ? targetPartySize(known.game, known.modeKey) : null),
      });
      setRequest(null);
      setCondition(null);
      setProposalMeta(null);
      if (userId) writeSavedMatch(userId, null);
      toast('파티가 확정되었습니다', 'ok');
      // 제안 화면(방 패널)에서 왔으면 그 자리를 방으로 갈아 끼운다 — 뒤로 가기에 끝난 제안이 남지 않게.
      navigate(`/app/party/${roomId}`, { replace: pathnameRef.current.startsWith('/app/proposals/') });
    } catch (err) {
      if (!live.current) return;
      toast(matchErrorMessage(err, '파티룸에 들어가지 못했습니다'), 'error');
      // 파티 HASH 가 사라졌다(확정 뒤 10분) — 대기 화면으로. 상태 조회가 IDLE 을 답한다.
      if (hasErrorCode(err, 'MATCH_PARTY_NOT_FOUND', 'NOT_PARTY_MEMBER')) { setRequest(null); setCondition(null); }
    } finally {
      if (entering.current === partyId) entering.current = null;
    }
  }, [enterPartyRoom, isLeftParty, navigate, rememberPartyInfo, toast, userId]);

  /** 조회 응답 하나를 화면 상태로. `IDLE` 은 `null`, `MATCHED` 는 파티룸 입장까지. */
  const applyView = useCallback((view: MatchRequestView) => {
    const previous = requestRef.current;
    if (view.status === 'IDLE') {
      if (previous && isActive(previous) && !cancelling.current && !entering.current) {
        toast(previous.status === 'PROPOSED' ? '제안이 끝나 대기열에서 빠졌습니다' : '빠른매치 대기가 끝났습니다. 다시 시작할 수 있어요', 'info');
      }
      cancelling.current = false;
      setRequest(null);
      setCondition(null);
      setProposalMeta(null);
      if (userId) writeSavedMatch(userId, null);
      return;
    }
    if (view.status === 'MATCHED') {
      if (view.partyId) void settleConfirmed(view.partyId);
      else setRequest(view);
      return;
    }
    setRequest(view);
    // 조건은 서버가 돌려주지 않는다 — 이 브라우저가 같은 requestId 로 기억해 둔 것만 쓴다.
    if (userId && view.requestId && previous?.requestId !== view.requestId) {
      const saved = readSavedMatch(userId);
      setCondition(saved?.requestId === view.requestId ? saved.condition : null);
    }
    // 새 제안 — 상태가 처음 보는 제안(`proposalKey` — `partyId` + 만료 시각)으로 `PROPOSED` 가 된 순간 한 번만 알리고 제안 화면(방 패널)으로 옮긴다. 어느 조회가 적었든 여기다
    // (알림이 부른 조회의 답이 버려져도 나중 조회가 적으면 옮긴다 — 2026-10-01 버그 ②). 같은 제안으로는 다시 옮기지 않는다("게시판으로" 돌아간 사람을 끌고 오지 않게).
    // 열쇠에 만료 시각을 넣은 까닭 — 다시 모인 제안이 같은 `partyId` 라 `partyId` 만 보면 두 번째 제안부터 옮기지 않았다(2026-10-01 실제 서버 검증).
    const key = view.status === 'PROPOSED' && view.partyId ? proposalKey(view.partyId, view.expiresAt) : null;
    if (key && view.partyId && proposalShown.current !== key) {
      proposalShown.current = key;
      writeProposalShown(userId ?? undefined, key);
      toast('조건에 맞는 팀원을 찾았습니다', 'ok');
      if (!pathnameRef.current.startsWith('/app/proposals/')) navigate(`/app/proposals/${view.partyId}`);
    }
  }, [navigate, settleConfirmed, toast, userId]);

  /**
   * `GET /match-requests` 로 상태를 맞춘다. 조회가 겹치면 **더 새 조회의 답이 이미 적혔을 때만** 옛 답을 버린다(그때는 적힌 답을 돌려준다 — 부르는 쪽이 지금 상태를 본다).
   * 전에는 더 새 조회가 시작되기만 해도 옛 답을 버려, 새 조회가 실패하면 아무 답도 적히지 않았다 — 3초 폴링이 없어진 뒤에는 다음 알림 · 탭 복귀까지 옛 상태가 남는다(2026-10-01).
   */
  const refresh = useCallback(async () => {
    const seq = ++refreshSeq.current;
    const view = await api.getMatchRequest();
    if (!live.current) return null;
    if (seq < appliedSeq.current) return appliedView.current;
    appliedSeq.current = seq;
    appliedView.current = view;
    applyView(view);
    return view;
  }, [applyView]);

  const refreshReservations = useCallback(async () => {
    try {
      const saved = await api.listReservations();
      if (!live.current) return;
      setReservations(saved);
      setReservationsError(null);
    } catch (err) {
      if (live.current) setReservationsError('예약을 불러오지 못했습니다.');
      throw err;
    } finally {
      if (live.current) setReservationsLoaded(true);
    }
  }, []);

  const saveReservation = useCallback(async (body: CreateReservationRequest, id?: string) => {
    const saved = id ? await api.updateReservation(id, body) : await api.createReservation(body);
    if (!live.current) return;
    setReservations((prev) => [...prev.filter((item) => item.id !== saved.id), saved]);
  }, []);

  // SSE — 인증은 쿠키라 넘길 것이 없다. 로그인 상태에서만 연다.
  useEffect(() => {
    if (status !== 'authenticated') {
      setStream(null);
      if (status === 'anonymous') {
        setRequest(null); setCondition(null); setProposalMeta(null); setReservations([]);
        setReservationsLoaded(false); setReservationsError(null);
      }
      return;
    }
    const created = createEventStream();
    setStream(created);
    return () => created.close();
  }, [status]);

  // 로그인 직후 · 새로고침 — 상태의 원본으로 맞춘다. 실패하면(잠시 서버가 없다) 조금 뒤 다시(성공하면 멈춘다).
  useEffect(() => {
    if (status !== 'authenticated') return;
    let disposed = false;
    let timer: number | undefined;
    const restore = async () => {
      try { await refresh(); }
      catch { if (!disposed) timer = window.setTimeout(() => void restore(), RESTORE_RETRY_MS); }
    };
    void restore();
    return () => { disposed = true; window.clearTimeout(timer); };
  }, [status, refresh]);

  // SSE 재연결 직후 — 끊긴 동안의 알림은 다시 오지 않는다(`Last-Event-ID` 재개 없음). 상태를 다시 받는다.
  useEffect(() => {
    if (!stream) return;
    let first = true;
    return stream.subscribeStatus((state) => {
      if (state !== 'connected') return;
      if (first) { first = false; return; } // 첫 연결은 위 restore 가 맡는다
      void refresh().catch(() => {});
    });
  }, [stream, refresh]);

  // 숨은 탭에서 돌아왔을 때 — 한 번 묻는다. 숨은 동안 브라우저가 연결 · 타이머를 늦추거나 멈춰 알림을 놓쳤을 수 있다(대기 중이 아니어도 — 다른 탭에서 시작한 매칭도 맞춘다).
  useEffect(() => {
    if (status !== 'authenticated') return;
    const onVisible = () => { if (document.visibilityState === 'visible') void refresh().catch(() => {}); };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [status, refresh]);

  useEffect(() => {
    if (!stream) return;
    return stream.subscribe((event: ServerEvent) => {
      if (!live.current) return;
      switch (event.type) {
        // 대기 상태가 바뀌었다(새 파티 · 정원 미달 합류). 인원(memberCount/target)은 조회가 답한다.
        case 'MATCH_QUEUE_UPDATED':
          void refresh().catch(() => {});
          break;
        // 정원을 기억하고 다시 조회 — 알림 · 제안 화면으로 옮기는 것은 `applyView` 가 상태가 `PROPOSED` 가 되는 순간에 한다(이 조회의 답이 버려져도 다음 조회가 옮긴다).
        case 'MATCH_PROPOSAL_CREATED': {
          const p = event.payload as unknown as MatchProposalCreatedPayload;
          if (p.partyId) setProposalMeta({ partyId: p.partyId, target: p.target, memberNumber: p.memberNumber });
          void refresh().catch(() => {});
          break;
        }
        // 시한 만료 — 그 제안에 있던 전원이 받는다. 수락한 사람은 파티에 남아 다시 기다리고(QUEUED) 안 한 사람은 빠진다(IDLE) — 조회가 가른다.
        // 다시 모인 제안은 같은 `partyId` 라(2026-10-01 실제 서버) 조회가 벌써 새 제안(`PROPOSED`)을 답하면 "시간이 지났다" 고 하지 않고 제안 화면에서 내보내지 않는다 — 새 제안은 `applyView` 가 알린다.
        case 'MATCH_PROPOSAL_EXPIRED':
          setProposalMeta(null);
          void refresh().then((view) => {
            if (!live.current || view?.status === 'PROPOSED') return;
            toast(view?.status === 'QUEUED' ? '제안 시간이 지나 다시 팀원을 찾습니다' : '제안 시간이 지났습니다', view?.status === 'QUEUED' ? 'info' : 'error');
            if (pathnameRef.current.startsWith('/app/proposals/')) navigate('/app/home');
          }).catch(() => {});
          break;
        // 파티원 누가 취소했다 — 남은 사람에게만. 파티는 다시 모으는 중이다.
        case 'MATCH_CANCELLED':
          void refresh().then((view) => {
            if (live.current && isActive(view)) toast('파티원이 나가 다시 팀원을 찾습니다', 'info');
            if (live.current && pathnameRef.current.startsWith('/app/proposals/') && view?.status !== 'PROPOSED') navigate('/app/home');
          }).catch(() => {});
          break;
        case 'MATCH_CONFIRMED': {
          const p = event.payload as unknown as MatchConfirmedPayload;
          if (p.partyId) void settleConfirmed(p.partyId);
          break;
        }
        default:
          break;
      }
    });
  }, [stream, navigate, toast, refresh, settleConfirmed]);

  const waiting = isActive(request);

  // 대기 · 제안 중의 3초 폴링은 없다(2026-10-01 소유자 — 알림 · SSE 재연결 직후 · 탭 복귀의 조회로 맞춘다. 머리 주석).

  // heartbeat — 대기 · 제안 중 30초마다. 404 면 이미 빠진 것이라 조회로 맞춘다.
  useEffect(() => {
    if (status !== 'authenticated' || !waiting) return;
    let cancelled = false;
    const beat = async () => {
      try { await api.heartbeatMatchRequest(); }
      catch (err) {
        if (cancelled) return;
        if (hasErrorCode(err, 'MATCH_REQUEST_NOT_FOUND')) void refresh().catch(() => {});
        /* 그 밖(503 · 네트워크)은 다음 신호에서 — 유예가 90초다 */
      }
    };
    void beat();
    const timer = window.setInterval(() => { void beat(); }, HEARTBEAT_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [status, waiting, refresh]);

  const start = useCallback(async (next: MatchCondition): Promise<StartResult> => {
    const problem = matchRequestError(next, gameAccountsRef.current);
    if (problem) throw new Error(problem);
    const body = buildMatchRequest(next, gameAccountsRef.current);
    // ① 조건이 맞는 열린 게시판 방이 있으면 서버가 바로 넣는다 — 대기열을 거치지 않는다(콜드 스타트).
    try {
      const joined = await api.autoJoinPost(body);
      rememberCondition(next);
      return { kind: 'ROOM', roomId: String(joined.roomId), postId: joined.postId };
    } catch (err) {
      // 404 = 맞는 방이 없다 → 대기열. 503 = platform 이 Redis 를 못 읽었다 → 계약대로 바로 matching 으로. 그 밖(409 · 400)은 멈춘다.
      if (!hasErrorCode(err, 'NO_MATCHING_POST', 'ROOM_STATE_UNAVAILABLE')) throw err;
    }
    // ② 대기열 매칭 — 201 은 "큐에 들어갔다" 이고 배정은 알림과 조회가 말한다.
    const created = await api.createMatchRequest(body);
    if (!live.current) return { kind: 'QUEUED', request: created };
    rememberCondition(next);
    cancelling.current = false;
    setCondition(next);
    setRequest(created);
    if (userId && created.requestId) writeSavedMatch(userId, { requestId: created.requestId, condition: next });
    return { kind: 'QUEUED', request: created };
  }, [userId]);

  const cancel = useCallback(async () => {
    const current = requestRef.current;
    if (!current?.requestId) return;
    cancelling.current = true;
    try {
      await api.cancelMatchRequest(current.requestId);
    } catch (err) {
      // 이미 빠졌다(만료 · 서버가 거둠 · 옛 requestId) — 취소된 것과 같다.
      if (!hasErrorCode(err, 'MATCH_REQUEST_NOT_FOUND', 'MATCH_REQUEST_MISMATCH')) { cancelling.current = false; throw err; }
    }
    if (!live.current) return;
    setRequest(null);
    setCondition(null);
    setProposalMeta(null);
    if (userId) writeSavedMatch(userId, null);
    if (pathnameRef.current.startsWith('/app/proposals/')) navigate('/app/home');
  }, [navigate, userId]);

  const accept = useCallback(async () => {
    const partyId = requestRef.current?.status === 'PROPOSED' ? requestRef.current.partyId : undefined;
    if (!partyId) return;
    try {
      await api.acceptProposal(partyId);
    } finally {
      // 204 든 404(끝난 제안) 든 지금 상태는 조회가 답한다. 확정은 MATCH_CONFIRMED 가 말한다.
      await refresh().catch(() => {});
    }
  }, [refresh]);

  const decline = useCallback(async () => {
    const partyId = requestRef.current?.status === 'PROPOSED' ? requestRef.current.partyId : undefined;
    if (!partyId) return;
    try {
      await api.declineProposal(partyId);
    } finally {
      await refresh().catch(() => {});
      if (live.current && pathnameRef.current.startsWith('/app/proposals/')) navigate('/app/home');
    }
  }, [refresh, navigate]);

  const proposal = useMemo<ProposalState | null>(() => {
    if (request?.status !== 'PROPOSED' || !request.partyId || request.expiresAt === undefined) return null;
    const meta = proposalMeta?.partyId === request.partyId ? proposalMeta : null;
    return { partyId: request.partyId, expiresAt: request.expiresAt, isAccepted: Boolean(request.isAccepted), target: meta?.target ?? null, memberNumber: meta?.memberNumber ?? null };
  }, [request, proposalMeta]);

  const value = useMemo<MatchValue>(() => ({
    request, condition, proposal, activePartyId, activePartyInfo, reservations, reservationsLoaded, reservationsError, stream,
    start, refresh, cancel, accept, decline, enterPartyRoom, refreshReservations, saveReservation, setActivePartyId, rememberLeftParty,
  }), [request, condition, proposal, activePartyId, activePartyInfo, reservations, reservationsLoaded, reservationsError, stream,
    start, refresh, cancel, accept, decline, enterPartyRoom, refreshReservations, saveReservation, setActivePartyId, rememberLeftParty]);

  return <MatchCtx.Provider value={value}>{children}</MatchCtx.Provider>;
}

export function useMatch(): MatchValue {
  const ctx = useContext(MatchCtx);
  if (!ctx) throw new Error('useMatch must be used inside MatchProvider');
  return ctx;
}
