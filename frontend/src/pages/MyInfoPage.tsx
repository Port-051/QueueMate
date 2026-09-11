import { useRef, useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey } from '../api/types';
import { IconCheck, IconPencil, IconTrash } from '../components/icons';
import { AVATAR_CHOICES, Avatar, Button, Card, CardHead, Field, Modal, Tag, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { gameFullLabel } from '../domain/labels';
import { relativeTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';

/** 계약의 업로드 한도(contracts/openapi.yaml `/users/me/avatar`). */
const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
const AVATAR_ACCEPT = 'image/png,image/jpeg,image/webp';

export function MyInfoPage() {
  const { user, gameAccounts, updateProfile, uploadAvatar, refreshGameAccounts } = useAuth();
  const { friends, recentPlayers, blocks } = useSocial();
  const toast = useToast();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [game, setGame] = useState<GameKey>('LOL');
  const [externalId, setExternalId] = useState('');
  const [busy, setBusy] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  // 모달 안에서만 쓰는 임시 선택이다. 저장 전까지 실제 프로필은 건드리지 않는다.
  const [picked, setPicked] = useState<string | null>(null);
  const [savingAvatar, setSavingAvatar] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

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

  const openAvatarPicker = () => {
    setPicked(user?.avatarUrl ?? null);
    setAvatarOpen(true);
  };

  /**
   * 업로드는 프리셋 선택과 달리 고른 즉시 올라간다. 저장 버튼을 기다리게 하면
   * 파일을 화면에 미리 보여주기 위해 브라우저에만 있는 임시 상태를 또 만들어야 한다.
   */
  const pickFile = async (file: File | undefined) => {
    if (!file) return;
    // 서버도 같은 값으로 막지만, 5MiB를 왕복시킨 뒤 거절하는 것은 낭비다.
    if (file.size > AVATAR_MAX_BYTES) { toast('사진은 5MB까지 올릴 수 있습니다', 'error'); return; }
    setSavingAvatar(true);
    try {
      await uploadAvatar(file);
      setPicked(null);
      setAvatarOpen(false);
      toast('프로필 사진을 변경했습니다', 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '사진을 올리지 못했습니다', 'error');
    } finally {
      setSavingAvatar(false);
      // 같은 파일을 다시 고를 수 있어야 한다. 값이 남아 있으면 change가 안 뜬다.
      if (fileInput.current) fileInput.current.value = '';
    }
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
              <button type="button" className="avatar-edit" aria-label="프로필 사진 변경" onClick={openAvatarPicker}>
                <Avatar name={user?.nickname ?? '?'} size={64} avatarUrl={user?.avatarUrl ?? null} />
              </button>
              <div style={{ flex: 1 }}>
                <Field label="닉네임" hint="2~16자">
                  <input className="input" value={nickname} onChange={(e) => setNickname(e.target.value)} />
                </Field>
              </div>
              <Button onClick={openAvatarPicker}><IconPencil size={13} /> 사진 변경</Button>
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
              className="avatar-opt avatar-upload"
              disabled={savingAvatar}
              onClick={() => fileInput.current?.click()}
            >
              <span className="au-mark" aria-hidden="true"><IconPencil size={16} /></span>
              <span>내 사진 올리기</span>
            </button>
            <input
              ref={fileInput}
              type="file"
              accept={AVATAR_ACCEPT}
              hidden
              onChange={(e) => void pickFile(e.target.files?.[0])}
            />
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
            올린 사진은 정사각형으로 잘려 저장됩니다. PNG·JPEG·WebP, 5MB까지.
            기본을 고르면 닉네임에 맞춰 자동으로 배정된 사진이 쓰입니다.
          </p>
        </Modal>
      ) : null}
    </section>
  );
}
