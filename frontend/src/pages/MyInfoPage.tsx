import { GameBadge } from '../components/GameSymbol';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey } from '../api/types';
import { IconCheck, IconLogout, IconPencil } from '../components/icons';
import { AVATAR_CHOICES, Avatar, Button, Card, CardHead, ConfirmDialog, Field, Modal, Tag, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { gameFullLabel } from '../domain/labels';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';

export function MyInfoPage() {
  const { user, gameAccounts, updateProfile, refreshGameAccounts, logout } = useAuth();
  const { friends, recentPlayers, blocks } = useSocial();
  const toast = useToast();
  const navigate = useNavigate();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [game, setGame] = useState<GameKey>('LOL');
  const [externalId, setExternalId] = useState('');
  const [busy, setBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [unlinkTarget, setUnlinkTarget] = useState<{ id: string; game: GameKey } | null>(null);
  // 모달 안에서만 쓰는 임시 선택이다. 저장 전까지 실제 프로필은 건드리지 않는다.
  const [picked, setPicked] = useState<string | null>(null);
  const [savingAvatar, setSavingAvatar] = useState(false);

  const unlinked = GAMES.filter((g) => !gameAccounts.some((a) => a.game === g.key));

  const selectedGame = unlinked.some((item) => item.key === game) ? game : unlinked[0]?.key;
  const nicknameChanged = nickname.trim() !== user?.nickname;
  const nicknameError = nicknameChanged && (nickname.trim().length < 2 || nickname.trim().length > 16) ? '닉네임은 2~16자로 입력해주세요.' : undefined;

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

  const openAvatarPicker = () => {
    setPicked(user?.avatarUrl ?? null);
    setAvatarOpen(true);
  };

  const saveAvatar = async () => {
    setSavingAvatar(true);
    try {
      // null을 명시해야 서버가 아바타를 지운다. 키를 빼면 유지된다 (contracts UpdateUserRequest).
      await updateProfile({ avatarUrl: picked });
      setAvatarOpen(false);
      toast(picked ? '프로필 사진을 변경했습니다' : '기본 프로필 사진으로 되돌렸습니다', 'ok');
    } catch (err) {
      // 저장이 안 됐으니 화면도 되돌린다. 반영된 것처럼 보이면 안 된다.
      setPicked(user?.avatarUrl ?? null);
      toast(isApiError(err) ? err.message : '프로필 사진을 변경하지 못했습니다', 'error');
    } finally {
      setSavingAvatar(false);
    }
  };

  const link = async () => {
    if (!selectedGame) return;
    if (!externalId.trim()) { toast('게임 아이디를 입력해주세요', 'error'); return; }
    setBusy(true);
    try {
      await api.linkGameAccount({ game: selectedGame, externalGameId: externalId.trim(), region: 'KR' });
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
    await api.unlinkGameAccount(id);
    await refreshGameAccounts();
    toast('게임 계정 연결을 해제했습니다');
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // 서버 요청 실패 시에도 AuthContext가 로컬 세션을 정리한다.
    }
    navigate('/', { replace: true });
  };

  return (
    <section className="page focus-page profile-page">
      <div className="page-head"><h1>내 정보</h1></div>
      <div className="content-sections">
          <Card className="content-section">
            <CardHead title="프로필" sub="매칭 제안과 파티룸에서 팀원에게 보입니다." />
            <form className="profile-form" onSubmit={(event) => { event.preventDefault(); if (nicknameChanged && !nicknameError && !busy) void saveNickname(); }}>
              <div className="profile-avatar">
                <button type="button" className="avatar-edit" aria-label="프로필 사진 변경" onClick={openAvatarPicker}>
                  <Avatar name={user?.nickname ?? '?'} size={72} avatarUrl={user?.avatarUrl ?? null} />
                </button>
                <Button size="sm" variant="ghost" onClick={openAvatarPicker}><IconPencil size={13} /> 사진 변경</Button>
              </div>
              <div className="profile-fields">
                <Field label="닉네임" hint="2~16자 · 공백만으로는 저장할 수 없습니다." error={nicknameError}>
                  <input className="input" value={nickname} maxLength={16} onChange={(event) => setNickname(event.target.value)} />
                </Field>
                <div className="profile-save">
                  <span className="hint" role="status">{nicknameChanged ? '아직 저장하지 않은 변경 사항이 있습니다.' : '변경 사항이 없습니다.'}</span>
                  <Button type="submit" variant="primary" disabled={busy || !nicknameChanged || Boolean(nicknameError)}>{busy ? '저장 중…' : '변경 사항 저장'}</Button>
                </div>
              </div>
            </form>
          </Card>
          <Card className="content-section">
            <CardHead title="게임 ID" sub="파티가 만들어지면 팀원에게 공유됩니다." right={<Tag>{GAMES.length - unlinked.length}/{GAMES.length} 등록</Tag>} />
            <div className="game-account-list">
              {GAMES.flatMap((item) => {
                const accounts = gameAccounts.filter((a) => a.game === item.key);
                return (accounts.length ? accounts : [null]).map((account) => <div key={account?.id ?? item.key} className="list-item account-row">
                  <GameBadge game={item.key} />
                  <div className="li-main"><b>{item.name}</b>{account ? <p className="account-id">{account.externalGameId}{account.region ? ` · ${account.region}` : ''}</p> : <p>게임 ID를 등록하면 팀원이 게임에서 초대할 수 있습니다.</p>}</div>
                  {account ? <Button size="sm" variant="ghost" aria-label={`${item.name} 연결 해제`} onClick={() => setUnlinkTarget({ id: account.id, game: account.game })}>연결 해제</Button> : <Tag>미등록</Tag>}
                </div>);
              })}
            </div>
            {unlinked.length > 0 ? <form className="account-link-form" onSubmit={(event) => { event.preventDefault(); if (!busy) void link(); }}>
              <Field label="등록할 게임"><select className="select" value={selectedGame} onChange={(event) => setGame(event.target.value as GameKey)}>{unlinked.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}</select></Field>
              <Field label="게임 ID"><input className="input" placeholder="예: QueueMaster#KR1" value={externalId} onChange={(event) => setExternalId(event.target.value)} /></Field>
              <Button type="submit" disabled={busy || !externalId.trim()} variant="primary">ID 등록</Button>
            </form> : null}
            <p className="account-note">현재 랭크·지역 자동 조회는 지원하지 않습니다.</p>
          </Card>
        <nav className="profile-links" aria-label="내 활동">
          <Link to="/app/friends">친구 <span>{friends.length}</span></Link>
          <Link to="/app/recent">최근 함께한 사람 <span>{recentPlayers.length}</span></Link>
          <Link to="/app/friends?tab=blocks">차단 목록 <span>{blocks.length}</span></Link>
        </nav>
        <div className="profile-session">
          <Button variant="danger" disabled={loggingOut} onClick={() => void handleLogout()}>
            <IconLogout size={16} /> {loggingOut ? '로그아웃 중…' : '로그아웃'}
          </Button>
        </div>
      </div>
      {unlinkTarget ? <ConfirmDialog title={`${gameFullLabel(unlinkTarget.game)} 연결을 해제할까요?`} description="이 게임의 ID가 파티원에게 표시되지 않습니다. 나중에 다시 등록할 수 있습니다." confirmLabel="연결 해제" onConfirm={() => unlink(unlinkTarget.id)} onClose={() => setUnlinkTarget(null)} /> : null}

      {avatarOpen ? (
        <Modal
          title="프로필 사진"
          onClose={() => setAvatarOpen(false)}
          foot={(
            <>
              <Button variant="primary" disabled={savingAvatar} onClick={() => void saveAvatar()}>저장</Button>
              <Button variant="ghost" disabled={savingAvatar} onClick={() => setAvatarOpen(false)}>취소</Button>
            </>
          )}
        >
          <div className="avatar-picker">
            <button
              type="button"
              className="avatar-opt"
              aria-pressed={picked === null}
              disabled={savingAvatar}
              onClick={() => setPicked(null)}
            >
              <Avatar name={user?.nickname ?? '?'} size={64} />
              <span>기본</span>
              {picked === null ? <span className="ap-check" aria-hidden="true"><IconCheck size={12} /></span> : null}
            </button>
            {AVATAR_CHOICES.map((src, i) => (
              <button
                key={src}
                type="button"
                className="avatar-opt"
                aria-pressed={picked === src}
                disabled={savingAvatar}
                onClick={() => setPicked(src)}
              >
                <img className="ap-img" src={src} alt={`아바타 ${i + 1}`} draggable={false} />
                {picked === src ? <span className="ap-check" aria-hidden="true"><IconCheck size={12} /></span> : null}
              </button>
            ))}
          </div>
          <p className="hint" style={{ marginTop: 14 }}>
            기본을 고르면 닉네임에 맞춰 자동으로 배정된 사진이 쓰입니다.
          </p>
        </Modal>
      ) : null}
    </section>
  );
}
