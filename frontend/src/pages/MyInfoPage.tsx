import { useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey } from '../api/types';
import { IconTrash } from '../components/icons';
import { Avatar, Button, Card, CardHead, Field, Tag, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { gameFullLabel } from '../domain/labels';
import { relativeTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';

export function MyInfoPage() {
  const { user, gameAccounts, updateProfile, refreshGameAccounts } = useAuth();
  const { friends, recentPlayers, blocks } = useSocial();
  const toast = useToast();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [game, setGame] = useState<GameKey>('LOL');
  const [externalId, setExternalId] = useState('');
  const [busy, setBusy] = useState(false);

  const unlinked = GAMES.filter((g) => !gameAccounts.some((a) => a.game === g.key));

  const saveNickname = async () => {
    const trimmed = nickname.trim();
    if (trimmed.length < 2 || trimmed.length > 16) { toast('닉네임은 2~16자로 입력해주세요', 'error'); return; }
    setBusy(true);
    try {
      await updateProfile({ nickname: trimmed });
      toast('닉네임을 변경했습니다', 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '닉네임을 변경하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  const link = async () => {
    if (!externalId.trim()) { toast('게임 아이디를 입력해주세요', 'error'); return; }
    setBusy(true);
    try {
      await api.linkGameAccount({ game, externalGameId: externalId.trim(), region: 'KR' });
      await refreshGameAccounts();
      setExternalId('');
      toast('게임 계정을 연결했습니다', 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '게임 계정을 연결하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  const unlink = async (id: string) => {
    setBusy(true);
    try {
      await api.unlinkGameAccount(id);
      await refreshGameAccounts();
      toast('게임 계정 연결을 해제했습니다');
    } catch (err) {
      toast(isApiError(err) ? err.message : '연결을 해제하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page">
      <div className="page-head">
        <h1>내 정보</h1>
        <p>프로필과 게임 계정 연결을 관리합니다.</p>
      </div>

      <div className="page-grid">
        <div className="stack">
          <Card>
            <CardHead title="프로필" sub="닉네임은 매칭 제안과 파티룸에서 팀원에게 보입니다." />
            <div className="row" style={{ gap: 16, alignItems: 'flex-end' }}>
              <Avatar name={user?.nickname ?? '?'} size={64} />
              <div style={{ flex: 1 }}>
                <Field label="닉네임" hint="2~16자">
                  <input className="input" value={nickname} onChange={(e) => setNickname(e.target.value)} />
                </Field>
              </div>
              <Button variant="primary" disabled={busy} onClick={() => void saveNickname()}>저장</Button>
            </div>
          </Card>

          <Card>
            <CardHead title="연결된 게임 계정" sub="랭크·지역 같은 조건은 연결된 계정에서 시스템이 가져옵니다." />
            {gameAccounts.length === 0 ? (
              <div className="empty">연결된 게임 계정이 없습니다.</div>
            ) : gameAccounts.map((a) => (
              <div key={a.id} className="list-item">
                <span className={`game-logo g-${a.game}`} style={{ width: 34, height: 34, fontSize: 11 }}>{a.game.slice(0, 3)}</span>
                <div className="li-main">
                  <b>{gameFullLabel(a.game)}</b>
                  <p>{a.externalGameId}{a.region ? ` · ${a.region}` : ''}{a.verifiedAt ? ` · ${relativeTime(a.verifiedAt)} 연결` : ''}</p>
                </div>
                <Button size="sm" variant="danger" disabled={busy} onClick={() => void unlink(a.id)}>
                  <IconTrash size={13} /> 해제
                </Button>
              </div>
            ))}

            {unlinked.length > 0 ? (
              <div className="stack" style={{ marginTop: 18, gap: 12 }}>
                <div className="opt-choices">
                  {unlinked.map((g) => (
                    <button key={g.key} type="button" className={g.key === game ? 'opt on' : 'opt'} onClick={() => setGame(g.key)}>
                      {g.shortName}
                    </button>
                  ))}
                </div>
                <div className="row" style={{ gap: 10 }}>
                  <input className="input" placeholder="게임 내 아이디 (예: QueueMaster#KR1)"
                    value={externalId} onChange={(e) => setExternalId(e.target.value)} />
                  <Button disabled={busy} onClick={() => void link()}>연결</Button>
                </div>
              </div>
            ) : null}
          </Card>
        </div>

        <div className="rail">
          <Card>
            <CardHead title="내 활동" />
            <div className="summary-row"><span>친구</span><b>{friends.length}명</b></div>
            <div className="summary-row"><span>최근 함께한 사람</span><b>{recentPlayers.length}명</b></div>
            <div className="summary-row"><span>차단</span><b>{blocks.length}명</b></div>
          </Card>
          <Card>
            <CardHead title="지원 게임" />
            <div className="stack" style={{ gap: 8 }}>
              {GAMES.map((g) => (
                <div key={g.key} className="row" style={{ gap: 10 }}>
                  <span className={`game-logo g-${g.key}`} style={{ width: 30, height: 30, fontSize: 10 }}>{g.shortName.slice(0, 3)}</span>
                  <b style={{ fontSize: 13.5 }}>{g.name}</b>
                  {gameAccounts.some((a) => a.game === g.key) ? <Tag tone="ok">연결됨</Tag> : <Tag>미연결</Tag>}
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>
    </section>
  );
}
