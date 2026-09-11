import { GameBadge } from '../components/GameSymbol';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { PartyView, ServerEvent } from '../api/types';
import { ReportModal } from '../components/ReportModal';
import { IconCheck, IconLogout, IconMic, IconMicOff, IconSend, IconShield } from '../components/icons';
import { Avatar, Button, Card, CardHead, EmptyState, Tag, useToast } from '../components/ui';
import { PARTY_STATUS_LABEL, gameFullLabel, modeLabel } from '../domain/labels';
import { formatTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { createPartyClient } from '../webrtc/createPartyClient';
import type { PartyChatMessage, PartyClient, VoiceStatus } from '../webrtc/types';

const VOICE_LABEL: Record<VoiceStatus, string> = {
  idle: '마이크 꺼짐',
  connecting: '마이크 준비 중',
  connected: '마이크 켜짐',
  denied: '마이크 권한 필요',
  error: '연결 실패',
};

export function PartyRoomPage() {
  const { partyId } = useParams<{ partyId: string }>();
  const { user } = useAuth();
  const { stream, activePartyId, setActivePartyId } = useMatch();
  const { friends, addFriend, block } = useSocial();
  const navigate = useNavigate();
  const toast = useToast();

  const [party, setParty] = useState<PartyView | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const [messages, setMessages] = useState<PartyChatMessage[]>([]);
  const [voice, setVoice] = useState<VoiceStatus>('idle');
  const [voiceDetail, setVoiceDetail] = useState<string | null>(null);
  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [muted, setMuted] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);

  const clientRef = useRef<PartyClient | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);
  const currentRoute = useRef(partyId);
  currentRoute.current = partyId;
  const currentActiveParty = useRef(activePartyId);
  currentActiveParty.current = activePartyId;

  const load = useCallback(async () => {
    if (!partyId) { setLoading(false); return; }
    setLoadError(false);
    try {
      const view = await api.getParty(partyId);
      if (currentRoute.current !== partyId) return;
      setParty(view);
      if (view.status !== 'CLOSED') setActivePartyId(view.id);
      else if (currentActiveParty.current === view.id) setActivePartyId(null);
    } catch {
      if (currentRoute.current === partyId) setLoadError(true);
    } finally {
      if (currentRoute.current === partyId) setLoading(false);
    }
  }, [partyId, setActivePartyId]);

  useEffect(() => { setParty(null); setMessages([]); setDraft(''); setLoading(Boolean(partyId)); void load(); }, [load, partyId]);

  useEffect(() => {
    if (!stream) return;
    return stream.subscribe((event: ServerEvent) => {
      if (event.type.startsWith('PARTY_')) void load();
    });
  }, [stream, load]);

  const memberIds = useMemo(() => party?.members.map((m) => m.userId).join(',') ?? '', [party]);
  const membersRef = useRef(party?.members ?? []);
  membersRef.current = party?.members ?? [];
  const closed = party?.status === 'CLOSED';
  const connectionPartyId = party && !closed ? party.id : null;
  const selfId = user?.id;
  const selfNickname = user?.nickname;

  useEffect(() => {
    if (!connectionPartyId || !selfId || !stream) return;
    setConnectedPeers([]);
    setMuted(false);
    const client = createPartyClient({
      partyId: connectionPartyId,
      selfUserId: selfId,
      selfNickname: selfNickname ?? '플레이어',
      members: membersRef.current,
      stream,
      handlers: {
        onChat: (message) => setMessages((prev) => [...prev, message]),
        onStatus: (status, detail) => { setVoice(status); setVoiceDetail(detail ?? null); },
        onPeer: (peer) => setConnectedPeers((prev) => (
          peer.connected ? [...new Set([...prev, peer.userId])] : prev.filter((id) => id !== peer.userId)
        )),
      },
    });
    clientRef.current = client;
    void client.connect().then(() => client.syncMembers(membersRef.current.map((m) => m.userId)));
    return () => {
      client.close();
      clientRef.current = null;
      setConnectedPeers([]);
    };
  }, [connectionPartyId, selfId, selfNickname, stream, connectionAttempt]);

  useEffect(() => {
    if (!clientRef.current || !memberIds) return;
    clientRef.current.syncMembers(memberIds.split(','));
  }, [memberIds]);

  useEffect(() => { if (messages.length) chatEndRef.current?.scrollIntoView({ block: 'nearest' }); }, [messages]);

  if (loading) return <section className="page" role="status">파티를 불러오는 중입니다…</section>;

  if (!partyId || !party) {
    return (
      <section className="page party-page">
        <EmptyState
          title={loadError ? '파티를 불러오지 못했습니다' : '참여 중인 파티가 없습니다'}
          desc={loadError ? '연결 상태를 확인하고 다시 시도하세요.' : '매칭이 확정되면 파티룸이 자동으로 열립니다.'}
          action={loadError ? <Button variant="primary" onClick={() => void load()}>다시 불러오기</Button> : <Button variant="primary" onClick={() => navigate('/app/match')}>매칭 시작하기</Button>}
        />
      </section>
    );
  }

  const me = party.members.find((m) => m.userId === user?.id);
  const peerCount = party.members.filter((m) => m.userId !== user?.id).length;
  const canChat = !closed && connectedPeers.length > 0;

  const toggleReady = async () => {
    setBusy(true);
    try {
      setParty(await api.setPartyReady(party.id, !me?.ready));
    } catch (err) {
      toast(isApiError(err) ? err.message : '준비 상태를 바꾸지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    setBusy(true);
    try {
      await api.leaveParty(party.id);
      setActivePartyId(null);
      toast('파티에서 나왔습니다');
      navigate('/app/recent');
    } catch (err) {
      toast(isApiError(err) ? err.message : '파티에서 나가지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
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

  const onFriendRequest = async (userId: string, nickname: string) => {
    try {
      await addFriend(userId);
      toast(`${nickname}님에게 친구 요청을 보냈습니다`, 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '친구 요청을 보내지 못했습니다', 'error');
    }
  };

  const onBlock = async (userId: string, nickname: string) => {
    try {
      await block(userId);
      toast(`${nickname}님을 차단했습니다. 앞으로 같은 파티가 되지 않습니다`, 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '차단하지 못했습니다', 'error');
    }
  };

  return (
    <section className="page party-page">
      <div className="page-head row-between">
        <div className="row" style={{ gap: 14 }}>
          <GameBadge game={party.game} />
          <div>
            <h1>{gameFullLabel(party.game)}</h1>
            <div className="row" style={{ gap: 8, marginTop: 8 }}>
              <Tag>{modeLabel(party.game, party.modeKey)}</Tag>
              <Tag tone={party.status === 'READY' ? 'ok' : 'accent'}>{PARTY_STATUS_LABEL[party.status]}</Tag>
              <Tag>{party.members.length} / {party.targetSize}명</Tag>
            </div>
          </div>
        </div>
        {closed ? <Button variant="primary" onClick={() => navigate('/app/match')}>새 매칭 시작하기</Button> : <Button variant="danger" disabled={busy} onClick={() => void leave()}>
          <IconLogout size={15} /> 나가기
        </Button>}
      </div>

      {closed ? <div className="banner" role="status" style={{ marginBottom: 20 }}>종료된 파티입니다. 음성·채팅과 준비 상태 변경을 사용할 수 없습니다.</div> : null}
      {loadError ? <div className="banner warn" role="alert" style={{ marginBottom: 20 }}>최신 파티 상태를 확인하지 못했습니다. <Button size="sm" onClick={() => void load()}>다시 불러오기</Button></div> : null}

      <div className="page-grid">
        <div className="stack">
          <Card>
            <CardHead
              title="음성 채널"
              sub={closed ? '파티가 종료되어 연결을 닫았습니다.' : '마이크는 직접 켤 수 있습니다. 이 화면을 떠나면 음성·채팅 연결이 종료됩니다.'}
              right={<Tag tone={!closed && voice === 'connected' ? 'ok' : 'default'}>{closed ? '종료됨' : VOICE_LABEL[voice]}</Tag>}
            />
            {voiceDetail ? <div className="banner warn" style={{ marginBottom: 14 }}>{voiceDetail}</div> : null}
            <div className="voice-row">
              <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
                {party.members.map((m) => (
                  <div key={m.userId} className={!closed && (connectedPeers.includes(m.userId) || (m.userId === user?.id && voice === 'connected')) ? 'voice-chip on' : 'voice-chip'}>
                    <Avatar name={m.nickname} avatarUrl={m.userId === user?.id ? user?.avatarUrl ?? null : null} size={28} />
                    <span>{m.nickname}</span>
                    {m.userId === user?.id && muted ? <IconMicOff size={14} /> : <IconMic size={14} />}
                  </div>
                ))}
              </div>
              {!closed && voice !== 'connected' ? <Button disabled={!clientRef.current || voice === 'connecting'} onClick={() => void clientRef.current?.startVoice()}>{voice === 'denied' || voice === 'error' ? '마이크 다시 시도' : '마이크 켜기'}</Button> : <Button disabled={closed} onClick={toggleMute}>
                {muted ? <><IconMicOff size={15} /> 음소거 해제</> : <><IconMic size={15} /> 음소거</>}
              </Button>}
            </div>
          </Card>

          <Card className="chat-card">
            <CardHead title="파티 채팅" sub={closed ? '종료된 파티에는 메시지를 보낼 수 없습니다.' : `파티원 ${connectedPeers.length}/${peerCount}명과 연결됨 · 마이크 없이도 채팅할 수 있습니다.`} right={!closed ? <Button size="sm" onClick={() => setConnectionAttempt((n) => n + 1)}>연결 다시 시도</Button> : undefined} />
            {!closed && connectedPeers.length < peerCount ? <p className="hint" role="status">{canChat ? '연결된 파티원에게만 메시지가 전송됩니다.' : '연결된 파티원이 없습니다. 상대가 파티룸에 들어오면 채팅할 수 있습니다.'}</p> : null}
            <div className="chat-log" role="log" aria-label="파티 메시지" aria-live="polite">
              {messages.length === 0 ? (
                <p style={{ color: 'var(--muted)', fontSize: 13 }}>{closed ? '이 화면에 남아 있는 메시지가 없습니다.' : '아직 메시지가 없습니다. 연결된 팀원에게 인사해보세요.'}</p>
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
                aria-label="파티 메시지"
                disabled={closed}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) send(); }}
              />
              <Button variant="primary" disabled={!canChat || !draft.trim()} onClick={send} aria-label="보내기"><IconSend size={16} /></Button>
            </div>
          </Card>
        </div>

        <div className="rail">
          <Card>
            <CardHead title={`파티원 (${party.members.length}/${party.targetSize})`} />
            {party.members.map((m) => (
              <div key={m.userId} className="list-item" style={{ alignItems: 'flex-start' }}>
                <Avatar name={m.nickname} avatarUrl={m.userId === user?.id ? user?.avatarUrl ?? null : null} size={36} />
                <div className="li-main">
                  <b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b>
                  <p>{closed ? '참여 종료' : m.ready ? '준비 완료' : '준비 중'}</p>
                  {(m.gameIds ?? []).map((id) => <div key={id} className="game-id"><code>{id}</code><Button size="sm" aria-label={`${m.nickname} 게임 ID ${id} 복사`} onClick={() => void navigator.clipboard.writeText(id).then(() => toast('게임 ID를 복사했습니다', 'ok')).catch(() => toast('복사하지 못했습니다. ID를 선택해 복사하세요.', 'error'))}>복사</Button></div>)}
                  {!m.gameIds?.length ? <p>등록된 게임 ID가 없습니다.</p> : null}
                  {m.userId !== user?.id ? (
                    <div className="row" style={{ gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                      {friends.some((f) => f.userId === m.userId)
                        ? <Tag tone="accent">친구</Tag>
                        : <Button size="sm" onClick={() => void onFriendRequest(m.userId, m.nickname)}>친구 추가</Button>}
                      <Button size="sm" onClick={() => void onBlock(m.userId, m.nickname)}>차단</Button>
                      <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: m.userId, nickname: m.nickname })}>
                        <IconShield size={13} /> 신고
                      </Button>
                    </div>
                  ) : null}
                </div>
                {m.ready && !closed ? <Tag tone="ok"><IconCheck size={12} /> READY</Tag> : null}
              </div>
            ))}
          </Card>

          <Button variant={me?.ready ? 'default' : 'primary'} size="lg" block disabled={busy || closed || party.status === 'PLAYING'} onClick={() => void toggleReady()}>
            <IconCheck size={16} /> {closed ? '종료된 파티' : party.status === 'PLAYING' ? '게임 진행 중' : me?.ready ? '준비 해제' : '게임 준비 완료'}
          </Button>

          <Card>
            <CardHead title="파티 정보" />
            <div className="summary-row"><span>파티 ID</span><b style={{ fontSize: 12 }}>{party.id.slice(0, 8)}</b></div>
            <div className="summary-row"><span>모드</span><b>{modeLabel(party.game, party.modeKey)}</b></div>
            <div className="summary-row"><span>목표 인원</span><b>{party.targetSize}명</b></div>
            <div className="summary-row"><span>상태</span><b>{PARTY_STATUS_LABEL[party.status]}</b></div>
          </Card>
        </div>
      </div>

      {reportTarget ? (
        <ReportModal
          targetUserId={reportTarget.userId}
          targetNickname={reportTarget.nickname}
          partyId={party.id}
          onClose={() => setReportTarget(null)}
        />
      ) : null}
    </section>
  );
}
