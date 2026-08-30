import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import { Button, Field } from '../components/ui';
import { IconBolt, IconMic, IconShield, IconTarget } from '../components/icons';
import { USE_MOCK } from '../config';
import { useAuth } from '../state/AuthContext';

const POINTS = [
  { icon: <IconBolt size={16} />, title: '빠른 매칭', desc: '복잡한 과정 없이 조건만 고르면 매칭이 시작됩니다.' },
  { icon: <IconTarget size={16} />, title: '핵심 조건 매칭', desc: '게임 모드, 핵심 조건, 음성, 플레이 목적만 봅니다.' },
  { icon: <IconMic size={16} />, title: '음성 채팅', desc: 'WebRTC 기반으로 파티원과 바로 대화합니다.' },
  { icon: <IconShield size={16} />, title: '안전한 플레이', desc: '차단·신고로 불쾌한 매칭을 걸러냅니다.' },
];

export function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const { login, signup } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/app/home';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const isSignup = mode === 'signup';

  const validate = (): string | null => {
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return '이메일 형식을 확인해주세요';
    if (password.length < 8) return '비밀번호는 8자 이상이어야 합니다';
    if (isSignup && (nickname.trim().length < 2 || nickname.trim().length > 16)) return '닉네임은 2~16자로 입력해주세요';
    return null;
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const invalid = validate();
    if (invalid) { setError(invalid); return; }
    setBusy(true);
    setError(null);
    try {
      if (isSignup) await signup(email, password, nickname.trim());
      else await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(isApiError(err) ? err.message : '요청을 처리하지 못했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <aside className="auth-aside">
        <Link to="/" className="brand" style={{ padding: 0 }}>
          <span className="brand-mark">Q</span>
          <span className="brand-name">Queue<span>Mate</span></span>
        </Link>
        <div>
          <h2>조건이 맞는 팀원과<br /><em>지금, 바로 플레이</em></h2>
          <p style={{ marginTop: 16 }}>
            QueueMate는 LoL · VALORANT · PUBG에서 게임별 핵심 조건만으로<br />
            호환되는 팀원을 자동으로 매칭해주는 서비스입니다.
          </p>
        </div>
        <div className="auth-points">
          {POINTS.map((p) => (
            <div key={p.title} className="auth-point">
              <span className="fi" style={{ color: 'var(--accent-2)' }}>{p.icon}</span>
              <div>
                <b>{p.title}</b>
                <p>{p.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="tabs" style={{ marginBottom: 22 }}>
            <button type="button" className={isSignup ? '' : 'on'} onClick={() => navigate('/login')}>로그인</button>
            <button type="button" className={isSignup ? 'on' : ''} onClick={() => navigate('/signup')}>회원가입</button>
          </div>

          <h1>{isSignup ? '회원가입' : '로그인'}</h1>
          <p className="sub">{isSignup ? '계정을 만들고 바로 매칭을 시작하세요.' : 'QueueMate에 오신 것을 환영합니다!'}</p>

          <form className="auth-form" onSubmit={submit}>
            <Field label="이메일">
              <input className="input" type="email" autoComplete="email" placeholder="이메일 주소를 입력하세요"
                value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            {isSignup ? (
              <Field label="닉네임" hint="2~16자. 파티원에게 보이는 이름입니다.">
                <input className="input" type="text" placeholder="닉네임을 입력하세요"
                  value={nickname} onChange={(e) => setNickname(e.target.value)} />
              </Field>
            ) : null}
            <Field label="비밀번호" hint={isSignup ? '8자 이상' : undefined} error={error ?? undefined}>
              <input className="input" type="password" autoComplete={isSignup ? 'new-password' : 'current-password'}
                placeholder="비밀번호를 입력하세요" value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            <Button type="submit" variant="primary" size="lg" block disabled={busy}>
              {busy ? '처리 중...' : isSignup ? '회원가입' : '로그인'}
            </Button>
          </form>

          <div className="auth-alt">
            {isSignup ? '이미 계정이 있으신가요? ' : '계정이 없으신가요? '}
            <button type="button" onClick={() => navigate(isSignup ? '/login' : '/signup')}>
              {isSignup ? '로그인하기' : '회원가입하기'}
            </button>
          </div>

          {USE_MOCK ? (
            <div className="auth-hint">
              backend 없이 동작하는 mock 모드입니다.<br />
              데모 계정: <b>demo@queuemate.gg</b> / <b>queuemate1</b>
            </div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
