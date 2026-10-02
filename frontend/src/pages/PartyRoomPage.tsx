import { RecruitmentNotice } from '../rooms/RecruitmentNotice';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { hasErrorCode } from '../api/error';
import type { MatchPartyMember } from '../api/types';
import { ReportModal } from '../components/ReportModal';
import { IconCheck, IconLogout, IconMic, IconMicOff, IconSettings } from '../components/icons';
import { Button, ConfirmDialog, EmptyState, useToast } from '../components/ui';
import { roomColors } from '../domain/avatarColor';
import { socialErrorMessage } from '../domain/socialErrors';
import { knownPosition, toBoardRoom, toMatchPartyRoom, UNKNOWN_NICKNAME } from '../rooms/boardRoom';
import { roomErrorMessage } from '../rooms/errors';
import { RoomConditions } from '../rooms/RoomDeck';
import { RoomConversationChat } from '../rooms/RoomConversationChat';
import { RoomMemberProfile } from '../rooms/RoomMemberProfile';
import { RoomCreatePreview } from '../rooms/RoomCreatePreview';
import { RoomLeaveConfirm } from '../rooms/RoomLeaveConfirm';
import { boardRoomColors } from '../rooms/roomColors';
import { RoomVoiceSeats, seatName, type SeatMenuAction, type VoiceSeatMember } from '../rooms/RoomVoiceSeats';
import type { BoardMember, BoardRoom } from '../rooms/types';
import { useAuth } from '../state/AuthContext';
import { usePartySession } from '../state/PartySessionContext';
import { useMatch } from '../state/MatchContext';
import { isMatchRoomId, useRoomSession } from '../state/RoomSessionContext';
import { useSocial } from '../state/SocialContext';
import type { BoardRoomOutletContext } from './HomePage';

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
 *   글 지우기 `DELETE /posts/{postId}`(방장 · 모집 중 — 만료로 바꾸고 방도 닫힌다). 방 설정은 현재 방장만 `PATCH /posts/{postId}`로 수정한다. 마감 뒤에는 제목·마이크만 수정한다.
 * - 음성 · 채팅은 WebRTC 직결(`PartySessionContext`). 친구 추가 · 차단 · 신고는 `SocialContext` · `ReportModal`(5단계 — 우리 API. 신고의 `contextId` 는 게시판 방이면 글 번호, 자동 매칭 방은 없다).
 * - 파티원은 게시판의 공통 좌석 몸통으로 한 줄에 표시한다. 얼굴·포지션·티어·닉네임·마이크 상태만 남기고,
 *   클릭하면 공통 상세 카드와 메시지·친구·내보내기·차단·신고 동작을 연다. 내 좌석도 상세를 볼 수 있다.
 * - 방 채팅은 7ee7177의 RoomConversation UI를 사용한다. 입력한 내용이 있을 때만 전송 버튼이 나타난다.
 * - **2026-09-30 부터 이 화면은 게시판 오른쪽 패널이다**(`pages/HomePage.tsx` — 넓은 화면은 게시판을 왼쪽으로 밀고, 좁은 화면은 게시판을 덮는다). 경로 · 하는 일은 그대로이고,
 *   게시판 방의 글을 처음 읽으면 게시판의 게임을 이 방의 게임으로 한 번 맞춘다(`syncRoomGame` — 딥 링크 · 새로 고침).
 */
export function PartyRoomPage({ activeRoomId, onRoomGame }: { activeRoomId?: string; onRoomGame?: BoardRoomOutletContext['syncRoomGame'] } = {}) {
  const params = useParams<{ roomId: string }>();
  const roomId = activeRoomId ?? params.roomId;
  const { user, userId } = useAuth();
  const session = useRoomSession();
  const { messages, voiceActivity, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef } = usePartySession();
  const { isFriend, requestTo, addFriend, block, unblock, isBlocked } = useSocial();
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
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<{ kind: 'leave' } | { kind: 'settings' } | { kind: 'closeRecruitment' } | { kind: 'kick'; userId: string; nickname: string } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);
  /** 채팅의 아바타·닉네임으로 연 사람 — 그 사람이 방에서 나가면 창도 닫힌다. */
  const [profileId, setProfileId] = useState<string | null>(null);

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
  useEffect(() => stream?.subscribe(event => { if (event.type === 'BOARD_CHANGED') { void loadPost(); void session.refresh(); } }), [stream, loadPost, session.refresh]);

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
  const outlet = useOutletContext<BoardRoomOutletContext | undefined>();
  const syncRoomGame = onRoomGame ?? outlet?.syncRoomGame;
  const roomGame = room?.game ?? knownGame ?? undefined;
  useEffect(() => { if (roomId && roomGame && syncRoomGame) syncRoomGame(roomId, roomGame); }, [roomId, roomGame, syncRoomGame]);


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
  const connectionHint = needsReconnect ? (canChat ? '일부 팀원 연결 대기 중 · 연결된 팀원에게만 전송됩니다.' : peerCount ? '팀원 연결 대기 중' : '아직 다른 사람이 없어요') : undefined;
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

  const toggleMute = () => {
    const next = !muted;
    setMuted(next);
    clientRef.current?.setMuted(next);
  };

  const send = (text: string): boolean => {
    if (!text || !canChat) return false;
    const sent = clientRef.current?.sendChat(text) ?? 0;
    if (!sent) { toast('전송하지 못했습니다. 파티원 연결을 확인하고 다시 시도하세요.', 'error'); return false; }
    if (sent < peerCount) toast(`연결된 ${sent}명에게만 전송했습니다. 연결되지 않은 파티원에게는 전달되지 않습니다.`, 'info');
    return true;
  };

  const onFriendRequest = async (targetId: string, nickname: string) => {
    try { await addFriend(targetId); toast(`${nickname}님에게 친구 요청을 보냈습니다`, 'ok'); }
    catch (err) { toast(socialErrorMessage(err, '친구 요청을 보내지 못했습니다'), 'error'); }
  };

  const onBlock = async (targetId: string, nickname: string) => {
    try {
      if (isBlocked(targetId)) { await unblock(targetId); toast(`${nickname}님 차단을 해제했습니다`, 'ok'); }
      else { await block(targetId); toast(`${nickname}님을 차단했습니다. 앞으로 같은 파티가 되지 않습니다`, 'ok'); }
    }
    catch (err) { toast(socialErrorMessage(err, '차단 상태를 변경하지 못했습니다'), 'error'); }
  };

  // 좌석을 누르면 뜨는 작은 메뉴 — 옛 파티원 카드의 버튼 줄(친구 추가 · 방장의 내보내기 · "···" 의 차단 · 신고)과 같은 일이다. 내 좌석은 메뉴가 없다(`RoomVoiceSeats`).
  // 친구 상태도 같은 버튼 자리에 남겨 친구 추가가 없는 이유를 보여준다.
  const menuFor = (member: VoiceSeatMember): { note?: string; actions: SeatMenuAction[] } => {
    const friend = isFriend(member.id);
    const name = seatName(member);
    const actions: SeatMenuAction[] = [{ key: 'message', label: '메시지 보내기', onSelect: () => navigate(`/app/messages/${member.id}`) }];
    actions.push(friend
      ? { key: 'friend-added', label: '친구', disabled: true, onSelect: () => {} }
      : requestTo(member.id)
      ? { key: 'friend', label: '친구 요청 보냄', disabled: true, onSelect: () => {} }
      : { key: 'friend', label: '친구 추가', disabled: isBlocked(member.id), onSelect: () => void onFriendRequest(member.id, name) });
    if (isHost) actions.push({ key: 'kick', label: '내보내기', tone: 'danger', onSelect: () => setDialog({ kind: 'kick', userId: member.id, nickname: name }) });
    actions.push({ key: isBlocked(member.id) ? 'unblock' : 'block', label: isBlocked(member.id) ? '차단 해제' : '차단', onSelect: () => void onBlock(member.id, name) });
    actions.push({ key: 'report', label: '신고', onSelect: () => setReportTarget({ userId: member.id, nickname: name }) });
    const note = [member.id === session.hostId ? '방장' : null, friend ? '친구' : null].filter(Boolean).join(' · ');
    return { note: note || undefined, actions };
  };
  // "프로필 보기" 로 연 사람 — 좌석과 같은 카드(방장 왕관은 지금의 방장 · 포지션은 좌석에 붙인 값). 방에서 나갔거나 카드가 없어졌으면 창이 닫힌다.
  const profileSeat = profileId ? members.find(member => member.id === profileId) : undefined;
  const profileMember = profileSeat?.card ? { ...profileSeat.card, host: profileSeat.id === session.hostId, position: profileSeat.position } : null;

  return (
    <section className="page party-page">
      <div className="party-room-header">
        <div className="party-room-heading">
          <h1 title={room?.title}>{room ? room.title : postId !== null ? `게시판 방 #${roomId}` : '빠른매치 파티'}</h1>
          {seatRoom ? <p className="room-row-meta" aria-label="방 조건"><RoomConditions room={seatRoom} /></p> : null}
        </div>

      </div>

      {room && !confirmed ? <RecruitmentNotice room={room} isHost={isHost} onChanged={loadPost} onConfirm={session.confirm} /> : null}
      {room?.description ? <p className="hint party-room-description">{room.description}</p> : null}
      {postId !== null && postError && !room ? <div className="banner warn" role="alert">글 정보를 불러오지 못했어요. <Button size="sm" onClick={() => void loadPost()}>다시 불러오기</Button></div> : null}
      {matchRoomId && teamErrorRoom === matchRoomId && !teamMembers ? <div className="banner warn" role="alert">파티원 정보를 불러오지 못했어요. <Button size="sm" onClick={() => void loadTeam()}>다시 불러오기</Button></div> : null}
      {voiceDetail ? <div className="banner warn" role="alert">{voiceDetail}</div> : null}

      <RoomVoiceSeats room={seatRoom} members={members} colors={faceColors} hostId={session.hostId} selfId={userId} capacity={capacity}
        voice={voice} muted={muted} connectedPeers={connectedPeers} voiceActivity={voiceActivity} menuFor={menuFor} />
      <div className="party-room-call-controls" role="group" aria-label="방 통화 제어">
        <button type="button" className={`room-mic-toggle${voice === 'connected' && !muted ? ' is-on' : ' is-off'}`}
          disabled={!clientRef.current || voice === 'connecting'} aria-pressed={voice === 'connected' && !muted}
          title={voiceDetail || (voice === 'connecting' ? '마이크 준비 중' : undefined)}
          onClick={() => voice === 'connected' ? toggleMute() : void clientRef.current?.startVoice()}>
          {voice === 'connected' && !muted ? <><IconMic size={20} /><span>음소거</span></> : <><IconMicOff size={20} /><span>음소거 해제</span></>}
        </button>
        {isHost && room ? <button type="button" className="room-settings-toggle" aria-haspopup="dialog" onClick={() => setDialog({ kind: 'settings' })}><IconSettings size={18} /><span>방 설정</span></button> : null}
        {isHost && room && !confirmed ? <button type="button" className="room-settings-toggle room-close-recruitment" aria-haspopup="dialog"
          disabled={session.members.length < 2} title={session.members.length < 2 ? '2명 이상 모이면 모집을 마감할 수 있어요.' : undefined}
          onClick={() => setDialog({ kind: 'closeRecruitment' })}><IconCheck size={18} /><span>모집 마감</span></button> : null}
        <Button className="party-room-leave" variant="danger" aria-label="방 나가기" title="방 나가기" disabled={busy} onClick={() => setDialog({ kind: 'leave' })}><IconLogout size={20} /></Button>
      </div>
      <RoomConversationChat key={roomId} messages={messages} canSend={canChat} connectionHint={connectionHint} nameOf={chatName}
        colorOf={id => faceColors.get(id)} canOpenProfile={id => cards.has(id)} isVerified={id => cards.get(id)?.profile?.verified === true} onProfile={id => setProfileId(current => current === id ? null : id)} onSend={send} />

      {dialog?.kind === 'settings' && isHost && room ? <RoomCreatePreview key={`settings:${room.id}`} game={room.game} initialRoom={room}
        onClose={() => setDialog(null)} onConfirm={async body => {
          const updated = await api.updatePost(room.postId, body);
          ++postSeq.current;
          setRoom(toBoardRoom(updated));
          setDialog(null);
          toast('방 설정을 변경했어요', 'ok');
          void session.refresh();
        }} /> : null}
      {dialog?.kind === 'leave' ? <RoomLeaveConfirm room={seatRoom} isHost={isHost} confirmed={Boolean(confirmed)} memberCount={session.members.length}
        onClose={() => setDialog(null)} onConfirm={leave} /> : null}
      {dialog?.kind === 'closeRecruitment' && isHost && room && !confirmed ? <ConfirmDialog title="모집을 마감할까요?" confirmLabel="모집 마감" cancelLabel="취소" busyLabel="마감 중…"
        className="room-action-dialog room-leave-confirm" closeLabel="모집 마감 창 닫기" onClose={() => setDialog(null)} onConfirm={async () => {
          await session.confirm();
          setDialog(null);
          toast('모집을 마감했어요. 음성과 채팅은 계속 사용할 수 있어요.', 'ok');
          void loadPost();
        }} description={<>
          <div className="room-dialog-summary"><h3>{room.title}</h3><p className="room-row-meta" aria-label="방 조건"><RoomConditions room={room} /></p></div>
          <div className="room-leave-notice is-warning"><strong>현재 멤버로 파티가 확정돼요.</strong><p>새로운 팀원이 참가할 수 없고, 다시 모집할 수 없어요. 방에 있는 멤버와 음성·채팅은 계속 사용할 수 있어요.</p></div>
        </>} /> : null}
      {/* 강퇴 뒤 10분 동안 그 방에 다시 못 들어온다(P-32 — 게시판 방만. 자동 매칭 방은 서버가 막지 않아 그 말을 하지 않는다 — 미정). 확정된 게시판 방은 애초에 새 입장이 없다. */}
      {dialog?.kind === 'kick' ? <ConfirmDialog title={`${dialog.nickname}님을 내보낼까요?`} confirmLabel="내보내기" onClose={() => setDialog(null)} onConfirm={() => session.kick(dialog.userId)}
        description={postId === null ? '내보낸 사람은 이 방에서 나가게 돼요.' : confirmed ? '확정된 방이라 내보낸 사람은 다시 들어올 수 없어요.' : '내보낸 사람은 10분 동안 이 방에 다시 들어올 수 없어요.'} /> : null}
      {profileMember && seatRoom ? <RoomMemberProfile room={seatRoom} member={profileMember} color={faceColors.get(profileMember.id)} onClose={() => setProfileId(null)} /> : null}
      {reportTarget ? <ReportModal targetUserId={reportTarget.userId} targetNickname={nicknameOf(reportTarget.userId)} contextId={postId !== null ? roomId : null} onClose={() => setReportTarget(null)} /> : null}
    </section>
  );
}
