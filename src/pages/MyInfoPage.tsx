import { FilterTierIcon } from '../components/FilterSymbols';
import '../styles/introduction.css';
import { GameBadge } from '../components/GameSymbol';
import { SocialProviderIcon } from '../components/SocialProviderIcon';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey, GameProfile, LolMostChampion, SocialProvider } from '../api/types';
import { GameAccountForm, STATS_SOURCE, statsFromApi } from '../components/GameAccountForm';
import { IconCheck, IconLogout, IconPencil, IconPlus, IconShield } from '../components/icons';
import { AVATAR_CHOICES, avatarImageSrc, Avatar, Button, ConfirmDialog, Field, Modal, Tag, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { GAME_CATALOG, TIER_LADDER_LABEL } from '../domain/gameCatalog';
import { gameFullLabel, rankLabel } from '../domain/labels';
import { championName } from '../domain/champions';
import { profileTier } from '../domain/profileTier';
import { accountRank } from '../rooms/accountRank';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';
import { PROVIDER_LABEL, settingsNoticeMessage, takeSettingsNotice } from '../state/settingsNotice';

/** 소셜 계정 절의 줄 순서 — 로그인 화면의 버튼 순서와 같다(`GOOGLE` 은 2026-09-29 소유자 결정). */
const SOCIAL_PROVIDERS: SocialProvider[] = ['KAKAO', 'DISCORD', 'GOOGLE'];

const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
const AVATAR_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

const SERVER_LABEL = { STEAM: '스팀', KAKAO: '카카오' } as const;

const syncedLabel = (iso: string) => new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const number = (value: number | null, digits = 1) => value === null ? '—' : Number.isInteger(value) ? String(value) : value.toFixed(digits);
const finite = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
/** PUBG `detail.seasonMode` — 값 목록이 계약에 없어 아는 둘만 옮기고 나머지는 받은 그대로(Claude 가 정한 세부). */
const SEASON_MODE_LABEL: Record<string, string> = { RANKED: '랭크 시즌 합산', NORMAL: '일반 시즌 합산' };
/** 챔피언 한 줄의 뒤쪽 — `숙련도 29 (292,707)`. 판 수 · 승률은 싣지 않는다(2026-09-30 소유자 결정). 둘 다 없으면 `null`(이름만 그린다). */
const masteryLabel = ({ masteryLevel, masteryPoints }: LolMostChampion): string | null => {
  const level = finite(masteryLevel);
  const points = finite(masteryPoints);
  const parts = [level !== null ? `숙련도 ${level}` : null, points !== null ? `(${points.toLocaleString('ko-KR')})` : null].filter(Boolean);
  return parts.length ? parts.join(' ') : null;
};

/** 사다리마다 한 줄 — `솔로랭크 · 골드 4`. 값이 없으면 "언랭"(2026-09-29 — 티어가 사다리마다 따로다). */
function LadderTiers({ game, profile }: { game: GameKey; profile: GameProfile }) {
  return <>{GAME_CATALOG[game].tierLadders.map((ladder) => {
    const tier = profileTier(profile, ladder);
    return <span className="row-tier" key={ladder}><FilterTierIcon game={game} tier={accountRank(tier).tier} size={22} /><span>{TIER_LADDER_LABEL[ladder]} · {rankLabel(tier) ?? '언랭'}</span></span>;
  })}</>;
}

/**
 * 게임 프로필 카드 하나(platform-api.md "게임 프로필"). 세 게임이 같은 모양이고 게임마다 비는 칸이 다르다 — `null` 은 "정보 없음"으로.
 * **티어는 사다리마다 한 줄이다**(LoL 솔로 · 자유 / VALORANT 경쟁전 / PUBG 랭크 — `tiers`). LoL · PUBG 는 `tiers` · `stats` 가 게임사 API 에서 오고(연결 · 수정 때, 그리고
 * 로그인 · 재발급 때 1시간이 지났으면 서버가 뒤에서 — P-42. 전적 갱신 버튼은 2026-09-30 에 없어졌다. PUBG 는 2026-09-29 부터),
 * VALORANT 는 자기신고라 `stats` 가 늘 `null` 이다. PUBG 의 전적은 판 수 · 치킨률 · K/D · 평균 딜량이다(`stats.detail` — `wins` · `kda` 들은 늘 `null`).
 * LoL 의 챔피언 줄은 **숙련도 높은 챔피언 셋**이고 숙련도 레벨 · 점수만 보여 준다(`stats.detail.mostChampions` — 2026-09-30 소유자 결정. 전에는 최근 경기의 판 수 · 승률도 실었다).
 */
function GameProfileCard({ game, profile, onEdit, onUnlink }: {
  game: GameKey; profile: GameProfile | null; onEdit: () => void; onUnlink: () => void;
}) {
  const name = gameFullLabel(game);
  const stats = profile?.stats ?? null;
  const champions = game === 'LOL' ? stats?.detail?.mostChampions ?? [] : [];
  const fromApi = statsFromApi(game);
  const detail = stats?.detail ?? null;
  const pubg = game === 'PUBG' ? { top1Rate: finite(detail?.top1Rate), kd: finite(detail?.kd), avgDamage: finite(detail?.avgDamage) } : null;
  const seasonMode = pubg && typeof detail?.seasonMode === 'string' ? detail.seasonMode : null;
  return <div className="profile-game-card">
    <div className="profile-game-account">
      <GameBadge game={game} />
      <div className="profile-game-detail">
        <h3>{name}</h3>
        {profile ? <p className="profile-game-id">{profile.gameNickname}</p> : <p className="profile-game-unregistered">연결된 계정이 없습니다</p>}
      </div>
      <div className="profile-game-head-actions">
        {profile ? <>
          <Button size="sm" variant="ghost" aria-label={`${name} 계정 수정`} onClick={onEdit}><IconPencil size={14} />수정</Button>
          <Button size="sm" variant="ghost" className="profile-unlink" aria-label={`${name} 연결 해제`} onClick={onUnlink}>연결 해제</Button>
        </> : <Button size="sm" variant="ghost" aria-label={`${name} 계정 연결`} onClick={onEdit}><IconPlus size={15} />계정 연결</Button>}
      </div>
    </div>
    {profile ? <div className="profile-game-body">
      <div className="profile-game-facts">
        <LadderTiers game={game} profile={profile} />
        {profile.server ? <span>서버 · {SERVER_LABEL[profile.server]}</span> : null}
        {profile.verified ? <Tag tone="ok">인증됨</Tag> : <Tag>{fromApi ? `${STATS_SOURCE[game]} 조회` : '자기신고'}</Tag>}
      </div>
      {stats ? <>
        {pubg ? <dl className="profile-game-stats">
          <div><dt>판 수</dt><dd>{stats.games}판</dd></div>
          <div><dt>치킨률</dt><dd>{pubg.top1Rate !== null ? `${number(pubg.top1Rate)}%` : '—'}</dd></div>
          <div><dt>K/D</dt><dd>{pubg.kd !== null ? pubg.kd.toFixed(2) : '—'}</dd></div>
          <div><dt>평균 딜량</dt><dd>{pubg.avgDamage !== null ? Math.round(pubg.avgDamage).toLocaleString('ko-KR') : '—'}</dd></div>
        </dl> : <dl className="profile-game-stats">
          <div><dt>최근 경기</dt><dd>{stats.games}판</dd></div>
          <div><dt>승률</dt><dd>{stats.winRate !== null ? `${stats.winRate}%` : '—'}{stats.wins !== null && stats.losses !== null ? <small style={{ marginLeft: 6, fontWeight: 400, color: 'var(--muted)' }}>{stats.wins}승 {stats.losses}패</small> : null}</dd></div>
          <div><dt>KDA</dt><dd>{stats.kda !== null ? stats.kda.toFixed(2) : `${number(stats.avgKills)} / ${number(stats.avgDeaths)}`}</dd></div>
          <div><dt>연승</dt><dd>{stats.winStreak !== null ? `${stats.winStreak}연승` : '—'}</dd></div>
        </dl>}
        {champions.length ? <div className="profile-game-champions" aria-label="숙련도 높은 챔피언">
          {champions.map((champion) => {
            const mastery = masteryLabel(champion);
            return <span className="profile-game-champion" key={champion.championId}>
              <b>{championName(champion.championId) ?? champion.championId}</b>
              {mastery ? <span>{mastery}</span> : null}
            </span>;
          })}
        </div> : null}
        <p className="profile-game-synced">{syncedLabel(stats.syncedAt)} 기준{seasonMode ? ` · ${SEASON_MODE_LABEL[seasonMode] ?? seasonMode}` : ''}{fromApi ? ' · 1시간이 지나면 다음 접속 때 새로 가져옵니다' : ''}</p>
      </> : <p className="profile-game-synced">{fromApi ? '전적 정보가 없습니다. 다음 접속 때 가져오고, 수정에서 다시 저장하면 바로 가져옵니다.' : '전적 정보 없음 — 이 게임의 전적 연동은 아직 없습니다.'}</p>}
    </div> : null}
  </div>;
}

export function MyInfoPage() {
  const { user, gameAccounts, updateProfile, uploadAvatar, removeGameAccount, refreshSession, logout } = useAuth();
  const { blocks } = useSocial();
  const toast = useToast();
  const navigate = useNavigate();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [linkGame, setLinkGame] = useState<GameKey | null>(null);
  const [busy, setBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [nicknameOpen, setNicknameOpen] = useState(false);
  const [unlinkTarget, setUnlinkTarget] = useState<GameKey | null>(null);
  // 모달 안에서만 쓰는 임시 선택이다. 저장 전까지 실제 프로필은 건드리지 않는다.
  const [picked, setPicked] = useState<string | null>(null);
  const [savingAvatar, setSavingAvatar] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const [unlinkSocialTarget, setUnlinkSocialTarget] = useState<SocialProvider | null>(null);

  // 소셜 계정 잇기의 결과(`/settings?linked=|error=` → `SettingsRedirectPage` 가 담은 쪽지)를 한 번만 보여 준다.
  useEffect(() => {
    const notice = takeSettingsNotice();
    const shown = notice ? settingsNoticeMessage(notice) : null;
    if (shown) toast(shown.text, shown.tone);
    if (notice?.linked) void refreshSession();
  }, [toast, refreshSession]);

  const unlinkSocial = async (provider: SocialProvider) => {
    try {
      await api.unlinkSocial(provider);
      await refreshSession();
      toast(`${PROVIDER_LABEL[provider]} 계정 연결을 끊었습니다`, 'ok');
    } catch (err) {
      if (isApiError(err) && err.code === 'LAST_SOCIAL_IDENTITY') toast('마지막 로그인 수단은 끊을 수 없습니다', 'error');
      else toast(isApiError(err) ? err.message : '연결을 끊지 못했습니다', 'error');
    }
  };

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
    setPicked(null);
    setAvatarOpen(true);
  };

  const pickFile = async (file: File | undefined) => {
    // Clear immediately so the same file can be selected again after a failed upload.
    if (fileInput.current) fileInput.current.value = '';
    if (!file || savingAvatar) return;
    if (file.size > AVATAR_MAX_BYTES) { toast('사진은 5MB까지 올릴 수 있습니다', 'error'); return; }
    if (!AVATAR_TYPES.includes(file.type)) { toast('PNG, JPEG, WebP 사진을 선택해 주세요', 'error'); return; }
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
    }
  };

  const saveAvatar = async () => {
    // 우리 백엔드에 아바타(`avatarUrl`)가 없다 — `PATCH /users/me` 는 닉네임만 받는다(platform-api.md "계정"). 화면의 처지는 미정(START_HERE.md §5).
    setAvatarOpen(false);
    toast('프로필 사진은 아직 지원하지 않습니다', 'info');
  };

  /** `DELETE …/game-accounts/{game}` — 없어도 204. 목록에서 그 자리에서 뺀다. */
  const unlink = async (game: GameKey) => {
    await api.deleteGameAccount(game);
    removeGameAccount(game);
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

  const editing = linkGame ? gameAccounts.find((account) => account.game === linkGame) ?? null : null;

  return (
    <section className="page profile-page" aria-label="프로필">
      <header className="profile-identity">
        <button type="button" className="profile-photo" aria-label="프로필 사진 변경" onClick={openAvatarPicker}>
          <Avatar name={user?.nickname ?? '?'} size={88} />
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
        {/* `#games` — 매칭 폼의 "게임 계정 연결하기" 가 여기로 온다(2026-09-29 — 게임 계정은 선택이다. 없는 게임은 카드에 "계정 연결" 버튼이 뜬다). */}
        <section id="games" className="profile-section" aria-labelledby="profile-games-heading">
          <div className="profile-section-heading">
            <h2 id="profile-games-heading">게임 계정</h2>
            <p>게임마다 하나 · 선택입니다. 연결하면 티어 · 전적이 파티원과 게시판 카드에 표시되고 랭크 모드로 매칭할 수 있습니다. 티어는 랭크 큐마다 따로입니다. LOL 은 Riot, PUBG 는 PUBG 에서 티어 · 전적을 가져오고 VALORANT 는 직접 적습니다.</p>
          </div>
          <div className="profile-game-accounts">
            {GAMES.map((item) => <GameProfileCard key={item.key} game={item.key} profile={gameAccounts.find((a) => a.game === item.key) ?? null}
              onEdit={() => setLinkGame(item.key)} onUnlink={() => setUnlinkTarget(item.key)} />)}
          </div>
        </section>
        {/* `#settings` — 소셜 계정 잇기의 결과(`/settings?linked=|error=` → `SettingsRedirectPage`)가 여기로 온다. 그 자리는 바로 아래 "매칭 기본값" 절이었는데
            2026-09-30 소유자 지시로 그 절을 뺐다 — 잇기의 결과가 닿는 이 절로 옮겼다. */}
        <section id="settings" className="profile-section" aria-labelledby="profile-social-heading">
          <div className="profile-section-heading">
            <h2 id="profile-social-heading">소셜 계정</h2>
            <p>로그인에 쓰는 계정입니다. 하나는 남겨야 합니다.</p>
          </div>
          <div className="profile-game-accounts">
            {SOCIAL_PROVIDERS.map((provider) => {
              const linked = user?.socialProviders.includes(provider) ?? false;
              return <div key={provider} className="profile-game-account account-row">
                <span className={`social-mark s-${provider}`} aria-hidden="true"><SocialProviderIcon provider={provider} size={20} /></span>
                <div className="profile-game-detail"><h3>{PROVIDER_LABEL[provider]}</h3><p className="profile-game-id">{linked ? '연결됨' : '연결 안 됨'}</p></div>
                {linked
                  ? <Button size="sm" variant="ghost" className="profile-unlink" aria-label={`${PROVIDER_LABEL[provider]} 연결 끊기`} onClick={() => setUnlinkSocialTarget(provider)}>연결 끊기</Button>
                  : <Button size="sm" variant="ghost" aria-label={`${PROVIDER_LABEL[provider]} 연결하기`} onClick={() => window.location.assign(api.oauthStartPath(provider))}><IconPlus size={15} />연결하기</Button>}
              </div>;
            })}
          </div>
        </section>
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
      {linkGame ? <Modal title={`${gameFullLabel(linkGame)} ${editing ? '계정 수정' : '계정 연결'}`} className="profile-edit-modal" onClose={() => setLinkGame(null)}>
        <GameAccountForm game={linkGame} initial={editing} onCancel={() => setLinkGame(null)}
          onSaved={(profile) => { setLinkGame(null); toast(editing ? `${profile.gameNickname} 계정을 수정했습니다` : `${profile.gameNickname} 계정을 연결했습니다`, 'ok'); }} />
      </Modal> : null}
      {unlinkSocialTarget ? <ConfirmDialog title={`${PROVIDER_LABEL[unlinkSocialTarget]} 계정 연결을 끊을까요?`} description="이 계정으로는 더 이상 로그인할 수 없습니다. 마지막 하나는 끊을 수 없습니다." confirmLabel="연결 끊기" onConfirm={() => unlinkSocial(unlinkSocialTarget)} onClose={() => setUnlinkSocialTarget(null)} /> : null}
      {unlinkTarget ? <ConfirmDialog title={`${gameFullLabel(unlinkTarget)} 연결을 해제할까요?`} description="이 게임의 닉네임 · 티어 · 전적이 파티원에게 표시되지 않습니다. 나중에 다시 연결할 수 있습니다." confirmLabel="연결 해제" onConfirm={() => unlink(unlinkTarget)} onClose={() => setUnlinkTarget(null)} /> : null}

      {avatarOpen ? (
        <Modal
          title="프로필 사진"
          onClose={() => { if (!savingAvatar) setAvatarOpen(false); }}
          foot={(
            <>
              <Button variant="primary" disabled={savingAvatar} onClick={() => void saveAvatar()}>저장</Button>
              <Button variant="ghost" disabled={savingAvatar} onClick={() => setAvatarOpen(false)}>취소</Button>
            </>
          )}
        >
          <div className="avatar-picker">
            <button type="button" className="avatar-opt avatar-upload" disabled={savingAvatar} onClick={() => fileInput.current?.click()}>
              <span className="au-mark" aria-hidden="true"><IconPencil size={16} /></span>
              <span>{savingAvatar ? '저장 중…' : '내 사진 올리기'}</span>
            </button>
            <input ref={fileInput} type="file" accept={AVATAR_TYPES.join(',')} aria-label="프로필 사진 파일" hidden disabled={savingAvatar} onChange={event => void pickFile(event.target.files?.[0])} />
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
                <img className="ap-img" src={avatarImageSrc(src)} alt={`아바타 ${i + 1}`} draggable={false} />
                {picked === src ? <span className="ap-check" aria-hidden="true"><IconCheck size={12} /></span> : null}
              </button>
            ))}
          </div>
          <p className="hint" style={{ marginTop: 14 }}>PNG·JPEG·WebP · 최대 5MB · 정사각형으로 저장</p>
        </Modal>
      ) : null}
    </section>
  );
}
