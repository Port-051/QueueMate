import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { PartyView, ServerEvent } from '../api/types';
import { ReportModal } from '../components/ReportModal';
import { IconCheck, IconLogout, IconMic, IconMicOff, IconPlus, IconSend, IconShield } from '../components/icons';
import { Avatar, Button, Card, CardHead, EmptyState, Modal, Tag, useToast } from '../components/ui';
import { PARTY_STATUS_LABEL, gameFullLabel, modeLabel } from '../domain/labels';
import { formatTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { createPartyClient } from '../webrtc/createPartyClient';
import type { PartyChatMessage, PartyClient, VoiceStatus } from '../webrtc/types';

const VOICE_LABEL: Record<VoiceStatus, string> = {
  idle: '연결 안 됨',
  connecting: '연결 중',
  connected: '음성 연결됨',
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
  const [messages, setMessages] = useState<PartyChatMessage[]>([]);
  const [voice, setVoice] = useState<VoiceStatus>('idle');
  const [voiceDetail, setVoiceDetail] = useState<string | null>(null);
  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [muted, setMuted] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);

  const clientRef = useRef<PartyClient | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async () => {
    if (!partyId) return;
    try {
      const view = await api.getParty(partyId);
      setParty(view);
      if (view.status !== 'CLOSED') setActivePartyId(view.id);
    } catch {
      setParty(null);
    }
  }, [partyId, setActivePartyId]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!stream) return;
    return stream.subscribe((event: ServerEvent) => {
      if (event.type.startsWith('PARTY_') && event.type !== 'PARTY_INVITE_RECEIVED') void load();
    });
  }, [stream, load]);

  const memberIds = useMemo(() => party?.members.map((m) => m.userId).join(',') ?? '', [party]);

  useEffect(() => {
    if (!party || !user || clientRef.current) return;
    const client = createPartyClient({
      partyId: party.id,
      selfUserId: user.id,
      selfNickname: user.nickname,
      members: party.members.map((m) => ({ userId: m.userId, nickname: m.nickname })),
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
    void client.connect().then(() => client.syncMembers(party.members.map((m) => m.userId)));
    return () => {
      client.close();
      clientRef.current = null;
    };
  }, [party, user, stream]);

  useEffect(() => {
    if (!clientRef.current || !memberIds) return;
    clientRef.current.syncMembers(memberIds.split(','));
  }, [memberIds]);

  useEffect(() => { chatEndRef.current?.scrollIntoView({ block: 'end' }); }, [messages]);

  if (!partyId || !party) {
    return (
      <section className="page">
        <EmptyState
          title={activePartyId ? '파티를 불러오지 못했습니다' : '참여 중인 파티가 없습니다'}
          desc="매칭이 확정되면 파티룸이 자동으로 열립니다."
          action={<Button variant="primary" onClick={() => navigate('/app/match')}>매칭 시작하기</Button>}
        />
      </section>
    );
  }

  const me = party.members.find((m) => m.userId === user?.id);
  const invitable = friends.filter((f) => !party.members.some((m) => m.userId === f.userId));

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

  const invite = async (friendUserId: string) => {
    try {
      await api.invitePartyMember(party.id, friendUserId);
      toast('초대를 보냈습니다', 'ok');
      setInviting(false);
    } catch (err) {
      toast(isApiError(err) ? err.message : '초대하지 못했습니다', 'error');
    }
  };

  const toggleMute = () => {
    const next = !muted;
    setMuted(next);
    clientRef.current?.setMuted(next);
  };

  const send = () => {
    const text = draft.trim();
    if (!text) return;
    clientRef.current?.sendChat(text);
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
    <section className="page">
      <div className="page-head row-between">
        <div className="row" style={{ gap: 14 }}>
          <span className={`game-logo g-${party.game}`}>{party.game.slice(0, 3)}</span>
          <div>
            <h1>{gameFullLabel(party.game)}</h1>
            <div className="row" style={{ gap: 8, marginTop: 8 }}>
              <Tag>{modeLabel(party.game, party.modeKey)}</Tag>
              <Tag tone={party.status === 'READY' ? 'ok' : 'accent'}>{PARTY_STATUS_LABEL[party.status]}</Tag>
              <Tag>{party.members.length} / {party.targetSize}명</Tag>
            </div>
          </div>
        </div>
        <div className="row" style={{ gap: 10 }}>
          <Button onClick={() => setInviting(true)} disabled={party.members.length >= party.targetSize}>
            <IconPlus size={15} /> 친구 초대
          </Button>
          <Button variant="danger" disabled={busy} onClick={() => void leave()}>
            <IconLogout size={15} /> 나가기
          </Button>
        </div>
      </div>

      <div className="page-grid">
        <div className="stack">
          <Card>
            <CardHead
              title="음성 채널"
              sub="파티원과 직접 연결됩니다. 서버는 음성을 저장하지 않습니다."
              right={<Tag tone={voice === 'connected' ? 'ok' : voice === 'connecting' ? 'accent' : 'warn'}>{VOICE_LABEL[voice]}</Tag>}
            />
            {voiceDetail ? <div className="banner warn" style={{ marginBottom: 14 }}>{voiceDetail}</div> : null}
            <div className="voice-row">
              <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
                {party.members.map((m) => (
                  <div key={m.userId} className={connectedPeers.includes(m.userId) || m.userId === user?.id ? 'voice-chip on' : 'voice-chip'}>
                    <Avatar name={m.nickname} size={28} />
                    <span>{m.nickname}</span>
                    {m.userId === user?.id && muted ? <IconMicOff size={14} /> : <IconMic size={14} />}
                  </div>
                ))}
              </div>
              <Button onClick={toggleMute}>
                {muted ? <><IconMicOff size={15} /> 음소거 해제</> : <><IconMic size={15} /> 음소거</>}
              </Button>
            </div>
          </Card>

          <Card className="chat-card">
            <CardHead title="파티 채팅" sub="WebRTC DataChannel로 파티원끼리 직접 주고받습니다." />
            <div className="chat-log">
              {messages.length === 0 ? (
                <p style={{ color: 'var(--muted)', fontSize: 13 }}>아직 메시지가 없습니다. 인사로 시작해보세요.</p>
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
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') send(); }}
              />
              <Button variant="primary" onClick={send} aria-label="보내기"><IconSend size={16} /></Button>
            </div>
          </Card>
        </div>

        <div className="rail">
          <Card>
            <CardHead title={`파티원 (${party.members.length}/${party.targetSize})`} />
            {party.members.map((m) => (
              <div key={m.userId} className="list-item" style={{ alignItems: 'flex-start' }}>
                <Avatar name={m.nickname} size={36} />
                <div className="li-main">
                  <b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b>
                  <p>{m.ready ? '준비 완료' : '준비 중'}</p>
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
                {m.ready ? <Tag tone="ok"><IconCheck size={12} /> READY</Tag> : null}
              </div>
            ))}
          </Card>

          <Button variant={me?.ready ? 'default' : 'primary'} size="lg" block disabled={busy} onClick={() => void toggleReady()}>
            <IconCheck size={16} /> {me?.ready ? '준비 해제' : '게임 준비 완료'}
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

      {inviting ? (
        <Modal title="친구 초대" onClose={() => setInviting(false)} foot={<Button variant="ghost" onClick={() => setInviting(false)}>닫기</Button>}>
          {invitable.length === 0 ? (
            <p style={{ color: 'var(--muted)', fontSize: 13.5 }}>초대할 수 있는 친구가 없습니다.</p>
          ) : invitable.map((f) => (
            <div key={f.userId} className="list-item">
              <Avatar name={f.nickname} size={34} />
              <div className="li-main"><b>{f.nickname}</b></div>
              <Button size="sm" variant="primary" onClick={() => void invite(f.userId)}>초대</Button>
            </div>
          ))}
        </Modal>
      ) : null}

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
