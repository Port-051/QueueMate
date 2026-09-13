import { GameBadge } from '../components/GameSymbol';
import { ProfileSettings } from '../components/ProfileSettings';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey } from '../api/types';
import { IconCheck, IconLogout, IconPencil, IconPlus, IconShield } from '../components/icons';
import { AVATAR_CHOICES, Avatar, Button, ConfirmDialog, Field, Modal, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { gameFullLabel } from '../domain/labels';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';

export function MyInfoPage() {
  const { user, gameAccounts, updateProfile, refreshGameAccounts, logout } = useAuth();
  const { blocks } = useSocial();
  const toast = useToast();
  const navigate = useNavigate();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [linkGame, setLinkGame] = useState<GameKey | null>(null);
  const [externalId, setExternalId] = useState('');
  const [busy, setBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [nicknameOpen, setNicknameOpen] = useState(false);
  const [unlinkTarget, setUnlinkTarget] = useState<{ id: string; game: GameKey } | null>(null);
  // 모달 안에서만 쓰는 임시 선택이다. 저장 전까지 실제 프로필은 건드리지 않는다.
  const [picked, setPicked] = useState<string | null>(null);
  const [savingAvatar, setSavingAvatar] = useState(false);

  const nicknameChanged = nickname.trim() !== user?.nickname;
  const nicknameError = nicknameChanged && (nickname.trim().length < 2 || nickname.trim().length > 16) ? '닉네임은 2~16자로 입력해주세요.' : undefined;

  const saveNickname = async () => {
    const trimmed = nickname.trim();
    if (trimmed.length < 2 || trimmed.length > 16) { toast('닉네임은 2~16자로 입력해주세요', 'error'); return; }
    setBusy(true);
    try {
      await updateProfile({ nickname: trimmed });
      setNicknameOpen(false);
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
    if (!linkGame) return;
    if (!externalId.trim()) { toast('게임 아이디를 입력해주세요', 'error'); return; }
    setBusy(true);
    try {
      await api.linkGameAccount({ game: linkGame, externalGameId: externalId.trim(), region: 'KR' });
      await refreshGameAccounts();
      setExternalId('');
      setLinkGame(null);
      toast('게임 ID를 등록했습니다', 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '게임 ID를 등록하지 못했습니다', 'error');
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
    <section className="page profile-page" aria-label="프로필">
      <header className="profile-identity">
        <button type="button" className="profile-photo" aria-label="프로필 사진 변경" onClick={openAvatarPicker}>
          <Avatar name={user?.nickname ?? '?'} size={88} avatarUrl={user?.avatarUrl ?? null} />
          <span className="profile-photo-edit" aria-hidden="true"><IconPencil size={14} /></span>
        </button>
        <div className="profile-identity-info">
          <h1>{user?.nickname}</h1>
          <nav className="profile-activity" aria-label="내 활동">
            <Link to="/app/messages">메시지</Link>
          </nav>
        </div>
        <Button className="profile-edit-name" variant="ghost" onClick={() => { setNickname(user?.nickname ?? ''); setNicknameOpen(true); }}><IconPencil size={15} />닉네임 변경</Button>
      </header>

      <div className="profile-sections">
          <section className="profile-section" aria-labelledby="profile-games-heading">
            <div className="profile-section-heading">
              <h2 id="profile-games-heading">게임 ID</h2>
              <p>매칭된 팀원에게 공유됩니다.</p>
            </div>
            <div className="profile-game-accounts">
              {GAMES.flatMap((item) => {
                const accounts = gameAccounts.filter((a) => a.game === item.key);
                return (accounts.length ? accounts : [null]).map((account) => <div key={account?.id ?? item.key} className="profile-game-account account-row">
                  <GameBadge game={item.key} />
                  <div className="profile-game-detail"><h3>{item.name}</h3>{account ? <p className="profile-game-id">{account.externalGameId}</p> : null}</div>
                  {account ? <Button size="sm" variant="ghost" className="profile-unlink" aria-label={`${item.name} 연결 해제`} onClick={() => setUnlinkTarget({ id: account.id, game: account.game })}>연결 해제</Button> : <Button size="sm" variant="ghost" aria-label={`${item.name} ID 등록`} onClick={() => { setExternalId(''); setLinkGame(item.key); }}><IconPlus size={15} />ID 등록</Button>}
                </div>);
              })}
            </div>
          </section>
        <ProfileSettings />
        <section className="profile-section" aria-labelledby="profile-privacy-heading">
          <div className="profile-section-heading"><h2 id="profile-privacy-heading">개인정보와 안전</h2></div>
          <div className="profile-privacy">
            <Link className="profile-blocks" to="/app/messages?manage=blocks"><IconShield size={20} /><span>차단 목록</span><b>{blocks.length}</b><span aria-hidden="true">›</span></Link>
            <details className="profile-privacy-details">
              <summary>개인정보 처리 안내</summary>
              <ul>
                <li>파티 음성과 채팅은 파티원끼리 직접 연결되며 서버에 저장되지 않습니다.</li>
                <li>신고는 사유와 식별자만 접수됩니다.</li>
                <li>차단한 사용자는 이후 매칭에서 같은 파티가 되지 않습니다.</li>
              </ul>
            </details>
          </div>
        </section>
        <div className="profile-signout">
          <Button variant="ghost" disabled={loggingOut} onClick={() => void handleLogout()}>
            <IconLogout size={16} /> {loggingOut ? '로그아웃 중…' : '로그아웃'}
          </Button>
        </div>
      </div>
      {nicknameOpen ? <Modal title="닉네임 변경" className="profile-edit-modal" onClose={() => { if (!busy) setNicknameOpen(false); }}>
        <form className="profile-edit-form" onSubmit={(event) => { event.preventDefault(); if (nicknameChanged && !nicknameError && !busy) void saveNickname(); }}>
          <Field label="닉네임" hint="2~16자" error={nicknameError}>
            <input className="input" value={nickname} maxLength={16} disabled={busy} onChange={(event) => setNickname(event.target.value)} />
          </Field>
          <div className="profile-edit-actions"><Button variant="ghost" disabled={busy} onClick={() => setNicknameOpen(false)}>취소</Button><Button type="submit" variant="primary" disabled={busy || !nicknameChanged || Boolean(nicknameError)}>{busy ? '저장 중…' : '변경 사항 저장'}</Button></div>
        </form>
      </Modal> : null}
      {linkGame ? <Modal title={`${gameFullLabel(linkGame)} ID 등록`} className="profile-edit-modal" onClose={() => { if (!busy) setLinkGame(null); }}>
        <form className="profile-edit-form" onSubmit={(event) => { event.preventDefault(); if (!busy) void link(); }}>
          <Field label="게임 ID" hint="게임에 표시되는 ID를 정확히 입력하세요."><input className="input" placeholder={linkGame === 'PUBG' ? '예: QueueMaster' : '예: QueueMaster#KR1'} value={externalId} disabled={busy} onChange={(event) => setExternalId(event.target.value)} /></Field>
          <div className="profile-edit-actions"><Button variant="ghost" disabled={busy} onClick={() => setLinkGame(null)}>취소</Button><Button type="submit" disabled={busy || !externalId.trim()} variant="primary">{busy ? '등록 중…' : 'ID 등록'}</Button></div>
        </form>
      </Modal> : null}
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
        </Modal>
      ) : null}
    </section>
  );
}
