import { RecruitmentNotice } from '../rooms/RecruitmentNotice';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { hasErrorCode } from '../api/error';
import type { MatchPartyMember } from '../api/types';
import { GameBadge } from '../components/GameSymbol';
import { ReportModal } from '../components/ReportModal';
import { IconLogout, IconMic, IconMicOff, IconSend, IconShield } from '../components/icons';
import { Avatar, Button, Card, CardHead, ConfirmDialog, EmptyState, Tag, useToast } from '../components/ui';
import { roomColors } from '../domain/avatarColor';
import { gameFullLabel } from '../domain/labels';
import { modeChoiceLabel } from '../domain/modeChoice';
import { socialErrorMessage } from '../domain/socialErrors';
import { formatTime } from '../domain/time';
import { knownPosition, toBoardRoom, toMatchPartyRoom, UNKNOWN_NICKNAME } from '../rooms/boardRoom';
import { roomErrorMessage } from '../rooms/errors';
import { RoomWantedPositions } from '../rooms/RoomDeck';
import { RoomMemberProfile } from '../rooms/RoomMemberProfile';
import { boardRoomColors } from '../rooms/roomColors';
import { RoomVoiceSeats, seatName, type SeatMenuAction, type VoiceSeatMember } from '../rooms/RoomVoiceSeats';
import { hasPositions } from '../rooms/summary';
import type { BoardMember, BoardRoom } from '../rooms/types';
import { useAuth } from '../state/AuthContext';
import { usePartySession } from '../state/PartySessionContext';
import { useMatch } from '../state/MatchContext';
import { isMatchRoomId, useRoomSession } from '../state/RoomSessionContext';
import { useSocial } from '../state/SocialContext';
import type { VoiceStatus } from '../webrtc/types';
import type { BoardRoomOutletContext } from './HomePage';

const VOICE_LABEL: Record<VoiceStatus, string> = {
  idle: '마이크 꺼짐',
  connecting: '마이크 준비 중',
  connected: '마이크 켜짐',
  denied: '마이크 권한 필요',
  error: '연결 실패',
};

/**
 * 방 화면 `/app/party/:roomId` — 게시판 방(글 번호)과 자동 매칭 방(UUID)이 **같은 화면 · 같은 요청**이다(platform-api.md "방" · "자동 매칭 파티의 방").
 * 원본의 Ready/PLAYING 파티 화면(`GET /parties/{id}` · `/ready` · `/leave` · `PARTY_*`)은 대응물이 없어 2026-09-29 에 이것으로 바꿨다.
 *
 * - 방의 상태(방장 · 사람 목록 · 확정 · 접속 확인 · `ROOM_*`)는 `RoomSessionContext` 가 든다 — 이 화면은 경로의 `roomId` 를 `adopt` 하고, 방을 잃으면(`gone`) 홈으로 간다.
 * - 사람 목록은 id 뿐이라(`GET …/members`) **게시판 방이면 `GET /posts/{postId}` 의 카드로** 닉네임 · 프로필을 붙인다.
 *   **빠른매치 방은 팀원 카드 `GET /match-parties/{partyId}/members`(2026-10-01 소유자 결정 — platform P-47)로** 닉네임 · 게임 프로필 · 고른 포지션을 붙인다
 *   (방 안 사람이 바뀔 때마다 다시 받는다 · 좌석 · 작은 창은 게시판 방과 같다 — `toMatchPartyRoom`). 그 전에는 사용자 번호(`#42`)만 보였다.
 *   채팅의 이름도 좌석과 같은 출처(서버가 아는 닉네임)가 먼저다 — 보낸 브라우저가 실어 온 이름은 모를 때만.
 *   자동 매칭 방의 게임 · 모드 · 음성 · 정원은 서버 응답에 없어(P-30) **이 브라우저가 확정 때 적어 둔 조건**(`MatchContext.activePartyInfo` — 대기 때의 조건 · 제안의 정원)으로 그린다.
 *   다른 브라우저에서 들어온 방은 그것이 없어 인원만 보인다(게임은 팀원 카드의 게임 프로필로 안다 · 모드 · 정원은 모른다). 자동 매칭 방은 처음부터 확정이라 "파티 확정" 버튼이 없다.
 * - 나가기 `DELETE …/members/me`(늘 204) · 강퇴 `DELETE …/members/{userId}`(방장) · 확정 `POST …/confirm`(게시판 방 · 방장 · 2명 이상 · **되돌릴 수 없다** — 한 번 더 묻는다).
 *   글 지우기 `DELETE /posts/{postId}`(방장 · 모집 중 — 만료로 바꾸고 방도 닫힌다). 글 고치기(`PATCH /posts/{postId}`)는 없다(2026-10-01 소유자 결정 — platform P-45).
 * - 음성 · 채팅은 WebRTC 직결(`PartySessionContext`). 친구 추가 · 차단 · 신고는 `SocialContext` · `ReportModal`(5단계 — 우리 API. 신고의 `contextId` 는 게시판 방이면 글 번호, 자동 매칭 방은 없다).
 * - **파티원은 음성 칸의 좌석 줄이다**(2026-09-30 소유자 지시 — `rooms/RoomVoiceSeats.tsx`). 옛 오른쪽 "파티원 (n/정원)" 카드와 사람마다의 큰 프로필(티어 · 승률 · KDA 칸)을 걷었다 —
 *   좌석은 게시판 카드의 좌석과 같고(정원만큼 · 빈 자리는 점선 원) 음성 상태가 붙는다. 2026-10-01 부터 사람마다 고른 포지션도 붙는다(게시판 방은 `session.positions` — 확정 뒤에도 · 빠른매치 방은 팀원 카드의 것 — 늘). 친구 추가 · 방장의 내보내기 · 차단 · 신고는 **좌석을 누르면 뜨는 작은 메뉴**(`menuFor`)로 옮겼다(내 좌석은 누를 수 없다).
 *   2026-10-01 부터 그 메뉴 맨 위에 **"프로필 보기"**(게시판 좌석을 눌렀을 때와 같은 큰 프로필 창 `RoomMemberProfile` — 소유자 · 휴대폰은 마우스를 올린 작은 창이 없다)가 있다.
 * - **2026-09-30 부터 이 화면은 게시판 오른쪽 패널이다**(`pages/HomePage.tsx` — 넓은 화면은 게시판을 왼쪽으로 밀고, 좁은 화면은 게시판을 덮는다). 경로 · 하는 일은 그대로이고,
 *   게시판 방의 글을 처음 읽으면 게시판의 게임을 이 방의 게임으로 한 번 맞춘다(`syncRoomGame` — 딥 링크 · 새로 고침).
 */
export function PartyRoomPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const { user, userId } = useAuth();
  const session = useRoomSession();
  const { messages, voiceActivity, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef, setConnectionAttempt } = usePartySession();
  const { isFriend, requestTo, addFriend, block } = useSocial();
  const { activePartyInfo, stream } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const postId = roomId && !isMatchRoomId(roomId) ? Number(roomId) : null;
  // 자동 매칭 방 — 확정 때 이 브라우저가 적어 둔 조건(게임 · 모드 · 음성 · 정원). 이 방의 것일 때만.
  const party = postId === null && activePartyInfo?.partyId === roomId ? activePartyInfo : null;
  const partyGame = party?.game ?? null;
  const partyMode = party?.game && party.modeKey ? party.modeKey : null;

  const [room, setRoom] = useState<BoardRoom | null>(null);
  const [postError, setPostError] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<{ kind: 'leave' } | { kind: 'kick'; userId: string; nickname: string } | { kind: 'confirm' } | { kind: 'delete' } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);
  /** 좌석 메뉴의 "프로필 보기" 로 연 사람(사용자 번호) — 그 사람이 방에서 나가면 창도 닫힌다. */
  const [profileId, setProfileId] = useState<string | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);

  // 경로의 방을 내 방으로. 이미 그 방이면 아무것도 안 한다(입장 · 글 쓰기 뒤에 이미 adopt 했다).
  useEffect(() => { if (roomId) session.adopt(roomId); }, [roomId, session.adopt]);

  // 방을 잃었다(닫힘 · 강퇴 · 나감 · 이 방에 없음) — 문구는 RoomSessionContext 가 띄웠다. 홈으로.
  useEffect(() => {
    if (session.gone && session.gone.roomId === roomId) { session.clearGone(); navigate('/app/home', { replace: true }); }
  }, [session.gone, roomId, navigate, session.clearGone]);

  // 게시판 방 — 글(카드 · 상태). 사람 목록이 바뀔 때마다(`session.version`) 다시 읽는다.
  const postSeq = useRef(0);
  const loadPost = useCallback(async () => {
    const seq = ++postSeq.current;
    if (postId === null) { setRoom(null); return; }
    try {
      const post = await api.getPost(postId);
      if (seq !== postSeq.current) return;
      setRoom(toBoardRoom(post));
      setPostError(false);
    } catch (err) {
      if (seq !== postSeq.current) return;
      if (hasErrorCode(err, 'POST_NOT_FOUND')) setRoom(null);
      setPostError(true);
    }
  }, [postId]);
  useEffect(() => { void loadPost(); }, [loadPost, session.version]);
  useEffect(() => stream?.subscribe(event => { if (event.type === 'BOARD_CHANGED') void loadPost(); }), [stream, loadPost]);

  // 빠른매치 방 — 팀원 카드(2026-10-01 소유자 결정 — platform P-47 `GET /match-parties/{partyId}/members`): 닉네임 · 게임 프로필 · 고른 포지션.
  // 방 안 사람이 바뀔 때(들어오고 나감)마다 다시 받는다(파티원 밖의 사람은 이 방에 못 들어온다). 게임은 확정 때 적어 둔 것이 있으면 싣고 없으면 뺀다(서버가 확정된 파티의 게임을 안다).
  // 늦게 온 응답이 새 응답을 덮지 않게 차례(`teamSeq`)를 본다. 실패하면 받아 둔 것을 그대로 쓴다.
  const matchRoomId = roomId && postId === null ? roomId : null;
  const memberKey = session.roomId === roomId ? [...session.members].sort().join(',') : '';
  const [team, setTeam] = useState<{ roomId: string; members: MatchPartyMember[] } | null>(null);
  const [teamErrorRoom, setTeamErrorRoom] = useState<string | null>(null);
  const teamSeq = useRef(0);
  const loadTeam = useCallback(async () => {
    if (!matchRoomId) return;
    const seq = ++teamSeq.current;
    try {
      const view = await api.getMatchPartyMembers(matchRoomId, partyGame ?? undefined);
      if (seq !== teamSeq.current) return;
      setTeam({ roomId: matchRoomId, members: view.members });
      setTeamErrorRoom(null);
    } catch {
      if (seq === teamSeq.current) setTeamErrorRoom(matchRoomId);
    }
  }, [matchRoomId, partyGame]);
  useEffect(() => { if (matchRoomId && memberKey) void loadTeam(); }, [loadTeam, matchRoomId, memberKey]);
  const teamMembers = team && team.roomId === roomId ? team.members : null;
  // 빠른매치 방의 게임 — 확정 때 적어 둔 것, 없으면(다른 브라우저에서 들어온 방) 팀원 카드의 게임 프로필(그 파티의 게임에 연결한 것이다).
  const knownGame = partyGame ?? teamMembers?.find(member => member.profile)?.profile?.game ?? null;

  // 게시판(왼쪽)을 이 방의 게임으로 — 방마다 한 번(그 뒤 사용자가 게임을 바꾸면 그대로 둔다).
  const syncRoomGame = useOutletContext<BoardRoomOutletContext | undefined>()?.syncRoomGame;
  const roomGame = room?.game ?? knownGame ?? undefined;
  useEffect(() => { if (roomId && roomGame && syncRoomGame) syncRoomGame(roomId, roomGame); }, [roomId, roomGame, syncRoomGame]);

  useEffect(() => { if (messages.length) chatEndRef.current?.scrollIntoView({ block: 'nearest' }); }, [messages]);

  if (!roomId) return <section className="page party-page"><EmptyState title="방을 찾을 수 없습니다" action={<Button variant="primary" onClick={() => navigate('/app/home')}>홈으로</Button>} /></section>;
  if (session.roomId !== roomId || session.loading) return <section className="page" role="status">방을 불러오는 중입니다…</section>;

  const isHost = session.hostId === userId;
  const confirmed = session.confirmed || room?.status === 'CONFIRMED';
  // 정원 — 게시판 방은 글의 `capacity`(P-41), 자동 매칭 방은 확정 때 적어 둔 파티의 정원. 모르면 인원만.
  const capacity = room?.capacity ?? party?.target ?? null;
  // 좌석이 그리는 방 — 게시판 방은 글, 빠른매치 방은 팀원 카드를 편 것(`toMatchPartyRoom` — 게임을 알 때만 · 방장은 지금의 방장 · 고른 포지션을 늘 붙인다).
  const partyRoom = !room && teamMembers && knownGame
    ? toMatchPartyRoom({ partyId: roomId, game: knownGame, modeKey: partyMode, voice: party?.voicePreference ?? null, capacity, hostId: session.hostId, members: teamMembers })
    : null;
  const seatRoom = room ?? partyRoom;
  const cards = new Map<string, BoardMember>((seatRoom?.members ?? []).map(member => [member.id, member]));
  // 서버가 아는 닉네임 — 게시판 방은 글의 카드, 빠른매치 방은 팀원 카드(게임을 몰라 카드를 못 펴도 닉네임은 있다 · 가입하지 않은 번호는 게시판 카드처럼 "알 수 없음").
  // 좌석 · 채팅 · 신고 창이 이것을 같이 쓴다(같은 출처 — 2026-10-01).
  const teamNames = new Map((teamMembers ?? []).map(member => [String(member.userId), member.nickname ?? UNKNOWN_NICKNAME] as const));
  const serverName = (id: string) => cards.get(id)?.nickname ?? teamNames.get(id) ?? null;
  // 포지션 — 게시판 방은 확정 뒤에도 방 안 사람 목록의 값을 표시한다(P-52).
  // 빠른매치 방은 팀원 카드의 고른 포지션이다(P-47 — 처음부터 확정인 방이지만 그린다 · `RoomDeck` `seatPosition` 의 `quickMatch`).
  const positionOf = (id: string) => room ? knownPosition(room.game, session.positions[id]) : cards.get(id)?.position ?? null;
  // 좌석의 이름 — 서버가 아는 닉네임, 나는 내 닉네임. 그 밖은 아직 모른다(`null` — 막 들어와 카드를 다시 받는 사이 · 좌석이 자리표시를 그린다).
  // 사용자 번호(`#27`)를 이름 자리에 그리지 않는다(2026-10-01 소유자 — 번호가 잠깐 보였다가 닉네임으로 바뀌었다).
  const members: VoiceSeatMember[] = session.members.map(id => ({ id, card: cards.get(id) ?? null, nickname: serverName(id) ?? (id === userId ? user?.nickname ?? null : null), position: positionOf(id) }));
  const peerCount = members.filter(m => m.id !== userId).length;
  const canChat = connectedPeers.length > 0;
  const needsReconnect = connectedPeers.length < peerCount;
  const connectionHint = needsReconnect ? (canChat ? `${connectedPeers.length}/${peerCount}명 연결됨 · 연결된 팀원에게만 전송됩니다.` : peerCount ? '팀원 연결 대기 중' : '아직 다른 사람이 없어요') : undefined;
  const canDeletePost = Boolean(room && isHost && room.status === 'RECRUITING');
  const nicknameOf = (id: string) => seatName(members.find(m => m.id === id) ?? { nickname: null });
  // 채팅의 이름 — 서버가 아는 닉네임이 먼저다(좌석과 같은 출처 — 전에는 좌석은 번호 · 채팅은 보낸 브라우저가 실어 온 닉네임이라 어긋났다). 모르면 실어 온 이름.
  const chatName = (id: string, sent: string) => serverName(id) ?? sent;
  // 얼굴 색 — 한 방의 사람은 모두 다른 색이다(2026-09-30 소유자). 음성 칸 좌석과 채팅이 이 표 하나를 쓴다.
  // 게시판 방은 왼쪽 게시판 카드와 같은 표(글쓴이가 방장 · 카드의 사람 먼저 — 카드에 아직 없는 지금의 방 안 사람은 남은 색), 자동 매칭 방은 지금의 방장 · 방 안 사람으로 정한다.
  const faceColors = room ? boardRoomColors(room, session.members) : roomColors(session.members, session.hostId);

  const leave = async () => {
    setBusy(true);
    try { await session.leave(); toast('방에서 나왔어요'); }
    catch (err) { toast(roomErrorMessage(err, '방에서 나가지 못했어요'), 'error'); }
    finally { setBusy(false); }
  };

  const deletePost = async () => {
    if (!room) return;
    await api.deletePost(room.postId).catch(err => { throw new Error(roomErrorMessage(err, '글을 지우지 못했어요')); });
    toast('글을 지우고 방을 닫았어요');
    // 서버가 방을 닫으며 ROOM_CLOSED 를 보내지만, 놓쳐도 되게 나가기(늘 204)로 내 방을 정리한다.
    await session.leave();
  };

  const toggleMute = () => {
    const next = !muted;
    setMuted(next);
    clientRef.current?.setMuted(next);
  };

  const send = () => {
    const text = draft.trim();
    if (!text || !canChat) return;
    const sent = clientRef.current?.sendChat(text) ?? 0;
    if (!sent) { toast('전송하지 못했습니다. 파티원 연결을 확인하고 다시 시도하세요.', 'error'); return; }
    if (sent < peerCount) toast(`연결된 ${sent}명에게만 전송했습니다. 연결되지 않은 파티원에게는 전달되지 않습니다.`, 'info');
    setDraft('');
  };

  const onFriendRequest = async (targetId: string, nickname: string) => {
    try { await addFriend(targetId); toast(`${nickname}님에게 친구 요청을 보냈습니다`, 'ok'); }
    catch (err) { toast(socialErrorMessage(err, '친구 요청을 보내지 못했습니다'), 'error'); }
  };

  const onBlock = async (targetId: string, nickname: string) => {
    try { await block(targetId); toast(`${nickname}님을 차단했습니다. 앞으로 같은 파티가 되지 않습니다`, 'ok'); }
    catch (err) { toast(socialErrorMessage(err, '차단하지 못했습니다'), 'error'); }
  };

  // 좌석을 누르면 뜨는 작은 메뉴 — 옛 파티원 카드의 버튼 줄(친구 추가 · 방장의 내보내기 · "···" 의 차단 · 신고)과 같은 일이다. 내 좌석은 메뉴가 없다(`RoomVoiceSeats`).
  // 이미 보낸 친구 요청이면 누를 수 없는 "친구 요청 보냄", 친구면 줄 대신 머리에 "친구"(Claude 가 정한 세부).
  const menuFor = (member: VoiceSeatMember): { note?: string; actions: SeatMenuAction[] } => {
    const friend = isFriend(member.id);
    const name = seatName(member);
    const actions: SeatMenuAction[] = [{ key: 'message', label: '메시지 보내기', onSelect: () => navigate(`/app/messages/${member.id}`) }];
    // 프로필 보기 — 게시판 좌석을 눌렀을 때와 같은 큰 프로필 창(2026-10-01 소유자 — 휴대폰은 마우스를 올린 작은 창이 없다 · 게시판 방 · 빠른매치 방 둘 다).
    // 카드가 없으면(이름을 아직 모른다 · 빠른매치 방인데 게임을 모른다) 줄이 없다. 그 게임 계정이 없는 사람도 연다 — 창에 닉네임과 "이 게임의 계정을 아직 연결하지 않았어요" 가 보인다
    // (제안 화면 팀원 좌석 · 게시판 좌석과 같다 — 2026-10-01 검증 뒤 맞췄다. 처음엔 누를 수 없는 "프로필 없음 · 게임 계정 미연결" 이었다).
    if (member.card && seatRoom) actions.push({ key: 'profile', label: '프로필 보기', onSelect: () => setProfileId(member.id) });
    if (!friend) actions.push(requestTo(member.id)
      ? { key: 'friend', label: '친구 요청 보냄', disabled: true, onSelect: () => {} }
      : { key: 'friend', label: '친구 추가', onSelect: () => void onFriendRequest(member.id, name) });
    if (isHost) actions.push({ key: 'kick', label: '내보내기', tone: 'danger', onSelect: () => setDialog({ kind: 'kick', userId: member.id, nickname: name }) });
    actions.push({ key: 'block', label: '차단', onSelect: () => void onBlock(member.id, name) });
    actions.push({ key: 'report', label: <><IconShield size={13} /> 신고</>, onSelect: () => setReportTarget({ userId: member.id, nickname: name }) });
    const note = [member.id === session.hostId ? '방장' : null, friend ? '친구' : null].filter(Boolean).join(' · ');
    return { note: note || undefined, actions };
  };
  // 좌석 아래 안내(한 줄씩) — 게시판 방의 확정 안내(옛 파티원 카드 밑에 있던 것) · 다른 사람이 있으면 좌석을 누르면 무엇을 할 수 있는지.
  const guide = [
    !confirmed && room ? isHost ? '원하는 사람이 다 모이면 파티를 확정하세요. 확정하면 새 사람이 들어올 수 없어요.' : '방장이 확정하면 파티가 완성돼요.' : null,
    peerCount ? `파티원을 누르면 프로필 보기 · 친구 추가${isHost ? ' · 내보내기' : ''} · 차단 · 신고를 할 수 있어요.` : null,
  ].filter((line): line is string => Boolean(line));
  // "프로필 보기" 로 연 사람 — 좌석과 같은 카드(방장 왕관은 지금의 방장 · 포지션은 좌석에 붙인 값). 방에서 나갔거나 카드가 없어졌으면 창이 닫힌다.
  const profileSeat = profileId ? members.find(member => member.id === profileId) : undefined;
  const profileMember = profileSeat?.card ? { ...profileSeat.card, host: profileSeat.id === session.hostId, position: profileSeat.position } : null;

  return (
    <section className="page party-page">
      <div className="page-head row-between">
        <div className="row" style={{ gap: 14 }}>
          {room ? <GameBadge game={room.game} /> : knownGame ? <GameBadge game={knownGame} /> : null}
          <div>
            <h1>{room ? room.title : postId !== null ? `게시판 방 #${roomId}` : '빠른매치 파티'}</h1>
            <div className="row" style={{ gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              {room ? <Tag>{gameFullLabel(room.game)} · {modeChoiceLabel(room.game, room.modeKey, room.perspective)}</Tag>
                : knownGame ? <Tag>{gameFullLabel(knownGame)}{partyMode ? ` · ${modeChoiceLabel(knownGame, partyMode)}` : ''}</Tag> : null}
              <Tag tone={confirmed ? 'ok' : 'accent'}>{confirmed ? '확정된 파티' : '모집 중'}</Tag>
              <Tag>{members.length}{capacity ? ` / ${capacity}` : ''}명</Tag>
              {room ? <Tag>{room.voice === 'REQUIRED' ? '음성 사용' : '음성 안 씀'}</Tag>
                : party?.voicePreference ? <Tag>{party.voicePreference === 'REQUIRED' ? '음성 사용' : '음성 안 씀'}</Tag> : null}
            </div>
          </div>
        </div>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          {room && isHost && !confirmed ? <Button variant="primary" disabled={busy || session.members.length < 2} title={session.members.length < 2 ? '2명 이상일 때 확정할 수 있어요' : undefined} onClick={() => setDialog({ kind: 'confirm' })}>파티 확정</Button> : null}
          {canDeletePost ? <Button variant="ghost" disabled={busy} onClick={() => setDialog({ kind: 'delete' })}>글 지우기</Button> : null}
          <Button variant="danger" disabled={busy} onClick={() => setDialog({ kind: 'leave' })}><IconLogout size={15} /> 나가기</Button>
        </div>
      </div>

      {room && !confirmed ? <RecruitmentNotice room={room} isHost={isHost} onChanged={loadPost} onConfirm={session.confirm} /> : null}
      {room?.description ? <p className="hint" style={{ marginBottom: 16 }}>{room.description}</p> : null}
      {room && hasPositions(room.game, room.modeKey) ? <p className="hint" style={{ marginBottom: 16 }}>찾는 포지션 <RoomWantedPositions room={room} /></p> : null}
      {postId !== null && postError && !room ? <div className="banner warn" role="alert" style={{ marginBottom: 20 }}>글 정보를 불러오지 못했어요. <Button size="sm" onClick={() => void loadPost()}>다시 불러오기</Button></div> : null}
      {matchRoomId && teamErrorRoom === matchRoomId && !teamMembers ? <div className="banner warn" role="alert" style={{ marginBottom: 20 }}>파티원 정보를 불러오지 못했어요. <Button size="sm" onClick={() => void loadTeam()}>다시 불러오기</Button></div> : null}

      <div className="stack">
        <Card className="voice-card">
          <CardHead title="음성 채널" right={<Tag tone={voice === 'connected' ? 'ok' : 'default'}>{VOICE_LABEL[voice]}</Tag>} />
          {voiceDetail ? <div className="banner warn" style={{ marginBottom: 14 }}>{voiceDetail}</div> : null}
          <RoomVoiceSeats room={seatRoom} members={members} colors={faceColors} hostId={session.hostId} selfId={userId} capacity={capacity}
            voice={voice} muted={muted} connectedPeers={connectedPeers} voiceActivity={voiceActivity} menuFor={menuFor} />
          <div className="room-voice-foot">
            {guide.length ? <p className="hint">{guide.map(line => <span key={line}>{line}</span>)}</p> : null}
            {voice !== 'connected' ? <Button disabled={!clientRef.current || voice === 'connecting'} onClick={() => void clientRef.current?.startVoice()}>{voice === 'denied' || voice === 'error' ? '마이크 다시 시도' : '마이크 켜기'}</Button> : <Button onClick={toggleMute}>
              {muted ? <><IconMicOff size={15} /> 음소거 해제</> : <><IconMic size={15} /> 음소거</>}
            </Button>}
          </div>
        </Card>

        <Card className="chat-card">
          <CardHead title="채팅" sub={connectionHint} right={needsReconnect && peerCount ? <Button size="sm" onClick={() => setConnectionAttempt((n) => n + 1)}>연결 다시 시도</Button> : undefined} />
          <div className="chat-log" role="log" aria-label="방 메시지" aria-live="polite">
            {messages.length === 0 ? (
              <p style={{ color: 'var(--muted)', fontSize: 13 }}>메시지가 없습니다. 채팅은 브라우저끼리 직접 오가고 서버에 남지 않아요.</p>
            ) : messages.map((m) => (
              m.system ? (
                <p key={m.id} className="chat-system">{m.text}</p>
              ) : (
                <div key={m.id} className="chat-line">
                  <Avatar userId={m.userId} name={chatName(m.userId, m.nickname)} color={faceColors.get(m.userId)} size={30} />
                  <div>
                    <div className="chat-meta">
                      <b>{chatName(m.userId, m.nickname)}</b>
                      <span>{formatTime(m.at)}</span>
                    </div>
                    <p>{m.text}</p>
                  </div>
                </div>
              )
            ))}
            <div ref={chatEndRef} />
          </div>
          <div className="chat-input">
            <input
              className="input"
              placeholder="메시지를 입력하세요"
              value={draft}
              aria-label="방 메시지"
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) send(); }}
            />
            <Button variant="primary" disabled={!canChat || !draft.trim()} onClick={send} aria-label="보내기"><IconSend size={16} /></Button>
          </div>
        </Card>
      </div>

      {dialog?.kind === 'leave' ? <ConfirmDialog title="방에서 나갈까요?" confirmLabel="나가기" onClose={() => setDialog(null)} onConfirm={leave}
        description={isHost && !confirmed ? '방장이 나가면 방이 닫히고 글은 만료돼요.' : isHost ? '확정된 방은 남은 사람에게 방장이 넘어가요.' : confirmed ? '확정된 파티라 나가면 게시판에서 다시 들어올 수 없어요.' : '언제든 게시판에서 다시 참여할 수 있어요.'} /> : null}
      {/* 강퇴 뒤 10분 동안 그 방에 다시 못 들어온다(P-32 — 게시판 방만. 자동 매칭 방은 서버가 막지 않아 그 말을 하지 않는다 — 미정). 확정된 게시판 방은 애초에 새 입장이 없다. */}
      {dialog?.kind === 'kick' ? <ConfirmDialog title={`${dialog.nickname}님을 내보낼까요?`} confirmLabel="내보내기" onClose={() => setDialog(null)} onConfirm={() => session.kick(dialog.userId)}
        description={postId === null ? '내보낸 사람은 이 방에서 나가게 돼요.' : confirmed ? '확정된 방이라 내보낸 사람은 다시 들어올 수 없어요.' : '내보낸 사람은 10분 동안 이 방에 다시 들어올 수 없어요.'} /> : null}
      {dialog?.kind === 'confirm' ? <ConfirmDialog title="이 멤버로 파티를 확정할까요?" confirmLabel="확정하기" onClose={() => setDialog(null)} onConfirm={async () => { await session.confirm(); toast('파티를 확정했어요', 'ok'); }}
        description={<>지금 방에 있는 {session.members.length}명 전원이 파티원이 돼요. <b>확정은 되돌릴 수 없어요</b> — 그 뒤로는 새 사람이 들어올 수 없고, 빈자리가 생겨도 다시 모집할 수 없어요.</>} /> : null}
      {dialog?.kind === 'delete' ? <ConfirmDialog title="글을 지우고 방을 닫을까요?" confirmLabel="지우기" onClose={() => setDialog(null)} onConfirm={deletePost}
        description="글은 만료로 바뀌어 게시판에 남고, 방은 닫혀 안에 있던 사람이 모두 나가게 돼요." /> : null}
      {profileMember && seatRoom ? <RoomMemberProfile room={seatRoom} member={profileMember} color={faceColors.get(profileMember.id)} onClose={() => setProfileId(null)} /> : null}
      {reportTarget ? <ReportModal targetUserId={reportTarget.userId} targetNickname={nicknameOf(reportTarget.userId)} contextId={postId !== null ? roomId : null} onClose={() => setReportTarget(null)} /> : null}
    </section>
  );
}
