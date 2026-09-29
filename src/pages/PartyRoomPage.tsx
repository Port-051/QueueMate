import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { hasErrorCode } from '../api/error';
import type { UpdatePostRequest, VoicePreference } from '../api/types';
import { GameBadge } from '../components/GameSymbol';
import { ReportModal } from '../components/ReportModal';
import { IconLogout, IconMic, IconMicOff, IconSend, IconShield } from '../components/icons';
import { ActionMenu, Avatar, Button, Card, CardHead, ConfirmDialog, EmptyState, Field, Modal, Tag, useToast } from '../components/ui';
import { PERSPECTIVE_LABEL } from '../domain/gameCatalog';
import { gameFullLabel } from '../domain/labels';
import { groupPerspectives, groupSizes, modeChoice, modeChoiceLabel, modeGroups, pickMode } from '../domain/modeChoice';
import { socialErrorMessage } from '../domain/socialErrors';
import { formatTime } from '../domain/time';
import { perspectiveFromMode, toBoardRoom } from '../rooms/boardRoom';
import { roomErrorMessage } from '../rooms/errors';
import { RoomMemberFacts, RoomRoles } from '../rooms/RoomDeck';
import { canonicalRoomRoles, hasPositions, ROOM_ROLES } from '../rooms/summary';
import type { BoardMember, BoardRoom } from '../rooms/types';
import { useAuth } from '../state/AuthContext';
import { usePartySession } from '../state/PartySessionContext';
import { isMatchRoomId, useRoomSession } from '../state/RoomSessionContext';
import { useSocial } from '../state/SocialContext';
import type { VoiceStatus } from '../webrtc/types';

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
 * - 사람 목록은 id 뿐이라(`GET …/members`) **게시판 방이면 `GET /posts/{postId}` 의 카드로** 닉네임 · 프로필을 붙인다. 자동 매칭 방은 글이 없어 id 만 보여 준다(게임 · 정원도 모른다 — 응답에 없다).
 * - 나가기 `DELETE …/members/me`(늘 204) · 강퇴 `DELETE …/members/{userId}`(방장) · 확정 `POST …/confirm`(게시판 방 · 방장 · 2명 이상 · **되돌릴 수 없다** — 한 번 더 묻는다).
 *   글 고치기 `PATCH /posts/{postId}`(방장 혼자일 때만 — 409 `ROOM_HAS_OTHER_MEMBERS`) · 지우기 `DELETE /posts/{postId}`(만료로 바꾸고 방도 닫힌다).
 * - 음성 · 채팅은 WebRTC 직결(`PartySessionContext`). 친구 추가 · 차단 · 신고는 `SocialContext` · `ReportModal`(5단계 — 우리 API. 신고의 `contextId` 는 게시판 방이면 글 번호, 자동 매칭 방은 없다).
 */
export function PartyRoomPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const { userId } = useAuth();
  const session = useRoomSession();
  const { messages, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef, setConnectionAttempt } = usePartySession();
  const { isFriend, addFriend, block } = useSocial();
  const navigate = useNavigate();
  const toast = useToast();
  const postId = roomId && !isMatchRoomId(roomId) ? Number(roomId) : null;

  const [room, setRoom] = useState<BoardRoom | null>(null);
  const [postError, setPostError] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<{ kind: 'leave' } | { kind: 'kick'; userId: string; nickname: string } | { kind: 'confirm' } | { kind: 'delete' } | { kind: 'edit' } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);

  // 경로의 방을 내 방으로. 이미 그 방이면 아무것도 안 한다(입장 · 글 쓰기 뒤에 이미 adopt 했다).
  useEffect(() => { if (roomId) session.adopt(roomId); }, [roomId, session.adopt]);

  // 방을 잃었다(닫힘 · 강퇴 · 나감 · 이 방에 없음) — 문구는 RoomSessionContext 가 띄웠다. 홈으로.
  useEffect(() => {
    if (session.gone && session.gone.roomId === roomId) { session.clearGone(); navigate('/app/home', { replace: true }); }
  }, [session.gone, roomId, navigate, session.clearGone]);

  // 게시판 방 — 글(카드 · 상태). 사람 목록이 바뀔 때마다(`session.version`) 다시 읽는다.
  const loadPost = useCallback(async () => {
    if (postId === null) { setRoom(null); return; }
    try {
      const post = await api.getPost(postId);
      setRoom(toBoardRoom(post));
      setPostError(false);
    } catch (err) {
      if (hasErrorCode(err, 'POST_NOT_FOUND')) setRoom(null);
      setPostError(true);
    }
  }, [postId]);
  useEffect(() => { void loadPost(); }, [loadPost, session.version]);

  useEffect(() => { if (messages.length) chatEndRef.current?.scrollIntoView({ block: 'nearest' }); }, [messages]);

  if (!roomId) return <section className="page party-page"><EmptyState title="방을 찾을 수 없습니다" action={<Button variant="primary" onClick={() => navigate('/app/home')}>홈으로</Button>} /></section>;
  if (session.roomId !== roomId || session.loading) return <section className="page" role="status">방을 불러오는 중입니다…</section>;

  const isHost = session.hostId === userId;
  const confirmed = session.confirmed || room?.status === 'CONFIRMED';
  const cards = new Map<string, BoardMember>((room?.members ?? []).map(member => [member.id, member]));
  const members = session.members.map(id => ({ id, card: cards.get(id) ?? null, nickname: cards.get(id)?.nickname ?? (id === userId ? '나' : `#${id}`) }));
  const peerCount = members.filter(m => m.id !== userId).length;
  const canChat = connectedPeers.length > 0;
  const needsReconnect = connectedPeers.length < peerCount;
  const connectionHint = needsReconnect ? (canChat ? `${connectedPeers.length}/${peerCount}명 연결됨 · 연결된 팀원에게만 전송됩니다.` : peerCount ? '팀원 연결 대기 중' : '아직 다른 사람이 없어요') : undefined;
  const canEditPost = Boolean(room && isHost && room.status === 'RECRUITING');
  const nicknameOf = (id: string) => members.find(m => m.id === id)?.nickname ?? `#${id}`;

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

  return (
    <section className="page party-page">
      <div className="page-head row-between">
        <div className="row" style={{ gap: 14 }}>
          {room ? <GameBadge game={room.game} /> : null}
          <div>
            <h1>{room ? room.title : postId !== null ? `게시판 방 #${roomId}` : '자동 매칭 파티'}</h1>
            <div className="row" style={{ gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              {room ? <Tag>{gameFullLabel(room.game)} · {modeChoiceLabel(room.game, room.modeKey, room.perspective)}</Tag> : null}
              <Tag tone={confirmed ? 'ok' : 'accent'}>{confirmed ? '확정된 파티' : '모집 중'}</Tag>
              <Tag>{members.length}{room ? ` / ${room.capacity}` : ''}명</Tag>
              {room ? <Tag>{room.voice === 'REQUIRED' ? '음성 사용' : '음성 안 씀'}</Tag> : null}
            </div>
          </div>
        </div>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          {room && isHost && !confirmed ? <Button variant="primary" disabled={busy || session.members.length < 2} title={session.members.length < 2 ? '2명 이상일 때 확정할 수 있어요' : undefined} onClick={() => setDialog({ kind: 'confirm' })}>파티 확정</Button> : null}
          {canEditPost ? <Button disabled={busy} onClick={() => setDialog({ kind: 'edit' })}>글 고치기</Button> : null}
          {canEditPost ? <Button variant="ghost" disabled={busy} onClick={() => setDialog({ kind: 'delete' })}>글 지우기</Button> : null}
          <Button variant="danger" disabled={busy} onClick={() => setDialog({ kind: 'leave' })}><IconLogout size={15} /> 나가기</Button>
        </div>
      </div>

      {room?.description ? <p className="hint" style={{ marginBottom: 16 }}>{room.description}</p> : null}
      {room && hasPositions(room.game, room.modeKey) ? <p className="hint" style={{ marginBottom: 16 }}>찾는 포지션 <RoomRoles game={room.game} roles={room.wantedPositions} labels /></p> : null}
      {postId !== null && postError && !room ? <div className="banner warn" role="alert" style={{ marginBottom: 20 }}>글 정보를 불러오지 못했어요. <Button size="sm" onClick={() => void loadPost()}>다시 불러오기</Button></div> : null}
      {postId === null ? <div className="banner" role="status" style={{ marginBottom: 20 }}>자동 매칭으로 확정된 파티의 방이에요. 글이 없어 파티원의 닉네임 · 프로필은 보이지 않아요(사용자 번호만).</div> : null}

      <div className="page-grid">
        <div className="stack">
          <Card>
            <CardHead title="음성 채널" right={<Tag tone={voice === 'connected' ? 'ok' : 'default'}>{VOICE_LABEL[voice]}</Tag>} />
            {voiceDetail ? <div className="banner warn" style={{ marginBottom: 14 }}>{voiceDetail}</div> : null}
            <div className="voice-row">
              <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
                {members.map(m => (
                  <div key={m.id} className={connectedPeers.includes(m.id) || (m.id === userId && voice === 'connected') ? 'voice-chip on' : 'voice-chip'}>
                    <Avatar name={m.nickname} size={28} />
                    <span>{m.nickname}</span>
                    {m.id === userId && muted ? <IconMicOff size={14} /> : <IconMic size={14} />}
                  </div>
                ))}
              </div>
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
                    <Avatar name={m.nickname} size={30} />
                    <div>
                      <div className="chat-meta">
                        <b>{m.nickname}</b>
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

        <div className="rail">
          <Card>
            <CardHead title={`파티원 (${members.length}${room ? `/${room.capacity}` : ''})`} />
            {members.map((m) => (
              <div key={m.id} className="list-item" style={{ alignItems: 'flex-start' }}>
                <Avatar name={m.nickname} size={36} />
                <div className="li-main">
                  <b>{m.nickname}{m.id === userId ? ' (나)' : ''}{m.id === session.hostId ? <Tag tone="accent">방장</Tag> : null}</b>
                  {room && m.card ? <RoomMemberFacts room={room} member={m.card} /> : <p className="hint">{postId === null ? `사용자 번호 ${m.id}` : '프로필 정보 없음'}</p>}
                  {m.id !== userId ? (
                    <div className="row" style={{ gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                      {isFriend(m.id)
                        ? <Tag tone="accent">친구</Tag>
                        : <Button size="sm" onClick={() => void onFriendRequest(m.id, m.nickname)}>친구 추가</Button>}
                      {isHost ? <Button size="sm" variant="ghost" onClick={() => setDialog({ kind: 'kick', userId: m.id, nickname: m.nickname })}>내보내기</Button> : null}
                      <ActionMenu label={`${m.nickname} 관리`}>
                        <Button size="sm" onClick={() => void onBlock(m.id, m.nickname)}>차단</Button>
                        <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: m.id, nickname: m.nickname })}><IconShield size={13} /> 신고</Button>
                      </ActionMenu>
                    </div>
                  ) : null}
                </div>
              </div>
            ))}
            {!confirmed && room ? <p className="hint" style={{ marginTop: 8 }}>{isHost ? '원하는 사람이 다 모이면 파티를 확정하세요. 확정하면 새 사람이 들어올 수 없어요.' : '방장이 확정하면 파티가 완성돼요.'}</p> : null}
          </Card>
        </div>
      </div>

      {dialog?.kind === 'leave' ? <ConfirmDialog title="방에서 나갈까요?" confirmLabel="나가기" onClose={() => setDialog(null)} onConfirm={leave}
        description={isHost && !confirmed ? '방장이 나가면 방이 닫히고 글은 만료돼요.' : isHost ? '확정된 방은 남은 사람에게 방장이 넘어가요.' : '언제든 게시판에서 다시 참여할 수 있어요.'} /> : null}
      {dialog?.kind === 'kick' ? <ConfirmDialog title={`${dialog.nickname}님을 내보낼까요?`} confirmLabel="내보내기" onClose={() => setDialog(null)} onConfirm={() => session.kick(dialog.userId)}
        description="내보낸 사람은 다시 들어올 수 있어요(막는 규칙은 아직 없어요)." /> : null}
      {dialog?.kind === 'confirm' ? <ConfirmDialog title="이 멤버로 파티를 확정할까요?" confirmLabel="확정하기" onClose={() => setDialog(null)} onConfirm={async () => { await session.confirm(); toast('파티를 확정했어요', 'ok'); }}
        description={<>지금 방에 있는 {session.members.length}명 전원이 파티원이 돼요. <b>확정은 되돌릴 수 없어요</b> — 그 뒤로는 새 사람이 들어올 수 없고, 빈자리가 생겨도 다시 모집할 수 없어요.</>} /> : null}
      {dialog?.kind === 'delete' ? <ConfirmDialog title="글을 지우고 방을 닫을까요?" confirmLabel="지우기" onClose={() => setDialog(null)} onConfirm={deletePost}
        description="글은 만료로 바뀌어 게시판에 남고, 방은 닫혀 안에 있던 사람이 모두 나가게 돼요." /> : null}
      {dialog?.kind === 'edit' && room ? <EditPostModal room={room} onClose={() => setDialog(null)} onSaved={next => { setRoom(next); setDialog(null); toast('글을 고쳤어요', 'ok'); }} /> : null}
      {reportTarget ? <ReportModal targetUserId={reportTarget.userId} targetNickname={nicknameOf(reportTarget.userId)} contextId={postId !== null ? roomId : null} onClose={() => setReportTarget(null)} /> : null}
    </section>
  );
}

/**
 * 글 고치기 — `PATCH /posts/{postId}`(준 것만 바꾼다). 방에 방장 말고 누가 있으면 서버가 409 `ROOM_HAS_OTHER_MEMBERS` 로 막는다(P-19 — 방 안 사람에게 알릴 길이 없어서다).
 * `game` 은 바꿀 수 없고 PUBG 의 `conditions.perspective` 는 고른 모드의 시점을 따라간다. 모드는 묶음 · 인원 · (PUBG) 시점으로 나눠 고른다(2026-09-29 — `domain/modeChoice.ts`).
 */
function EditPostModal({ room, onClose, onSaved }: { room: BoardRoom; onClose: () => void; onSaved: (room: BoardRoom) => void }) {
  const [mode, setMode] = useState(room.modeKey);
  const [title, setTitle] = useState(room.title);
  const [description, setDescription] = useState(room.description);
  const [voice, setVoice] = useState<VoicePreference>(room.voice);
  const [wanted, setWanted] = useState<string[]>(room.wantedPositions);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const positions = hasPositions(room.game, mode);
  const choice = modeChoice(room.game, mode);
  const sizes = choice ? groupSizes(room.game, choice.group) : [];
  const perspectives = choice ? groupPerspectives(room.game, choice.group) : [];
  const groups = modeGroups(room.game);
  const fixedSize = sizes.length < 2;
  const save = async () => {
    const trimmed = title.trim();
    if (!trimmed) { setError('제목을 입력해 주세요.'); return; }
    if (trimmed.length > 60) { setError('제목은 60자까지예요.'); return; }
    if (description.length > 300) { setError('소개는 300자까지예요.'); return; }
    const body: UpdatePostRequest = { title: trimmed, description, voice, wantedPositions: positions ? canonicalRoomRoles(room.game, wanted) : [] };
    if (mode !== room.modeKey) {
      body.mode = mode;
      if (room.game === 'PUBG') { const perspective = perspectiveFromMode(room.game, mode); body.conditions = perspective ? { perspective } : {}; }
    }
    setBusy(true); setError('');
    try { onSaved(toBoardRoom(await api.updatePost(room.postId, body))); }
    catch (err) { setError(roomErrorMessage(err, '글을 고치지 못했어요')); }
    finally { setBusy(false); }
  };
  return <Modal title="글 고치기" onClose={() => { if (!busy) onClose(); }} foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy} onClick={() => void save()}>{busy ? '저장 중…' : '저장'}</Button></>}>
    {/* 묶음은 한 줄에 같은 폭으로 — 폰 폭(360px)에서도 줄바꿈하지 않는다. */}
    <Field label="게임 모드"><div role="group" aria-label="게임 모드" style={{ display: 'grid', gridTemplateColumns: `repeat(${groups.length}, minmax(0, 1fr))`, gap: 6, maxWidth: groups.length * 120 }}>{groups.map(group => <Button key={group.key} size="sm" variant={choice?.group === group.key ? 'primary' : 'default'} aria-pressed={choice?.group === group.key} style={{ minWidth: 0, paddingInline: 4, whiteSpace: 'nowrap' }} onClick={() => setMode(pickMode(room.game, group.key, choice?.size, choice?.perspective))}>{group.label}</Button>)}</div></Field>
    {choice ? <Field label="인원"><div role="group" aria-label="인원" className="row" style={{ gap: 6 }}>{sizes.map(size => <Button key={size} size="sm" variant={choice.size === size ? 'primary' : 'default'} aria-pressed={choice.size === size} disabled={fixedSize} title={fixedSize ? `이 모드는 ${size}인만 있어요` : undefined} style={fixedSize ? { opacity: 1, cursor: 'default' } : undefined} onClick={() => setMode(pickMode(room.game, choice.group, size, choice.perspective))}>{size}인</Button>)}</div></Field> : null}
    {choice && perspectives.length ? <Field label="시점"><div role="group" aria-label="시점" className="row" style={{ gap: 6 }}>{perspectives.map(view => <Button key={view} size="sm" variant={choice.perspective === view ? 'primary' : 'default'} aria-pressed={choice.perspective === view} onClick={() => setMode(pickMode(room.game, choice.group, choice.size, view))}>{PERSPECTIVE_LABEL[view]}</Button>)}</div></Field> : null}
    <Field label="제목" hint="60자까지"><input className="input" maxLength={60} value={title} onChange={e => setTitle(e.target.value)} /></Field>
    <Field label="소개" hint="300자까지 · 비우면 지워져요"><textarea className="input" rows={3} maxLength={300} value={description} onChange={e => setDescription(e.target.value)} /></Field>
    <Field label="음성"><div className="row" style={{ gap: 8 }}>{(['REQUIRED', 'NO_VOICE'] as const).map(v => <Button key={v} size="sm" variant={voice === v ? 'primary' : 'default'} onClick={() => setVoice(v)}>{v === 'REQUIRED' ? '사용' : '안 씀'}</Button>)}</div></Field>
    {positions ? <Field label="찾는 포지션" hint="아무것도 고르지 않으면 누구든 찾는 글이에요"><div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>{ROOM_ROLES[room.game].map(role => <Button key={role} size="sm" variant={wanted.includes(role) ? 'primary' : 'default'} onClick={() => setWanted(current => current.includes(role) ? current.filter(r => r !== role) : [...current, role])}>{role}</Button>)}</div></Field> : null}
    <p className="hint">방에 다른 사람이 들어와 있으면 고칠 수 없어요(방 안 사람에게 바뀐 조건을 알릴 길이 없어서예요).</p>
    {error ? <p className="banner warn" role="alert">{error}</p> : null}
  </Modal>;
}
