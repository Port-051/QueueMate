import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import type { GameKey, MatchCondition, PlayPurpose, VoicePreference } from '../api/types';
import {
  PURPOSE_OPTIONS, VOICE_OPTIONS, availableGames, defaultCondition, gameConfig, keyConditionOptions,
  switchGame, targetPartySize, visibleModes,
} from '../domain/gameConfig';
import { conditionSummary, gameFullLabel, modeLabel } from '../domain/labels';
import { formatDuration } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { readPreferences } from '../state/preferences';
import { readRecentConditions } from '../state/recentConditions';
import { IconBolt, IconCalendar } from './icons';
import { Button, Tag, useToast } from './ui';

/**
 * 홈의 큐 콘솔. 게임 → 조건 → 매칭 시작까지 한 화면에서 끝낸다.
 * 조건 목록은 docs/02 카탈로그 그대로다. 여기서 새 조건을 만들지 않는다.
 */

function ChipRow<T extends string>({
  label, value, options, onChange,
}: { label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="qc-row">
      <span className="qc-key">{label}</span>
      <div className="qc-chips">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            aria-pressed={o.value === value}
            className={o.value === value ? 'chip on' : 'chip'}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/** 마지막에 쓴 조건이 있으면 그대로, 없으면 설정의 기본값으로 연다. */
function initialCondition(): MatchCondition {
  const recent = readRecentConditions()[0];
  if (recent) return recent;
  const prefs = readPreferences();
  return { ...defaultCondition('LOL'), voicePreference: prefs.defaultVoice, playPurpose: prefs.defaultPurpose };
}

export function QueueConsole() {
  const navigate = useNavigate();
  const toast = useToast();
  const { start, cancel, request, condition: queuedCondition } = useMatch();

  const [condition, setCondition] = useState<MatchCondition>(initialCondition);
  const [busy, setBusy] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const recent = useMemo(() => readRecentConditions(), []);

  // 큐 상태의 대기 시간은 queuedAt 기준으로 직접 센다. 서버 이벤트는 1초마다 오지 않는다.
  const queuedAt = request?.queuedAt;
  useEffect(() => {
    if (!queuedAt) return;
    const started = new Date(queuedAt).getTime();
    const tick = () => setElapsed(Math.floor((Date.now() - started) / 1000));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [queuedAt]);

  const cfg = gameConfig(condition.game);
  const partySize = targetPartySize(condition.game, condition.modeKey);

  const startMatching = async () => {
    setBusy(true);
    try {
      await start(condition);
    } catch (err) {
      toast(isApiError(err) ? err.message : '매칭을 시작하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  const cancelMatching = async () => {
    setBusy(true);
    try {
      await cancel();
    } catch (err) {
      toast(isApiError(err) ? err.message : '매칭을 취소하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  // INV-1: 활성 요청은 하나뿐이다. 큐에 있으면 콘솔은 조건 폼 대신 큐 상태를 보여준다.
  if (request) {
    return (
      <div className="queue-console live">
        <div className="qc-head">
          <h2><span className="pulse" /> 팀원을 찾는 중</h2>
          <button type="button" className="qc-detail" onClick={() => navigate(`/app/match/waiting/${request.id}`)}>
            대기 화면 →
          </button>
        </div>
        <div className="qc-live-body">
          <b>
            {queuedCondition
              ? `${gameFullLabel(queuedCondition.game)} · ${modeLabel(queuedCondition.game, queuedCondition.modeKey)}`
              : '매칭 진행 중'}
          </b>
          <div className="qc-live-tags">
            {queuedCondition ? conditionSummary(queuedCondition).map((label) => <Tag key={label}>{label}</Tag>) : null}
          </div>
        </div>
        <div className="qc-foot">
          <div className="qc-meta">
            대기 시간 <b>{formatDuration(elapsed)}</b> · 조건이 맞는 팀원이 모이면 바로 제안이 옵니다
          </div>
          <div className="qc-actions">
            <Button size="lg" disabled={busy} onClick={() => void cancelMatching()}>매칭 취소</Button>
            <Button variant="primary" size="lg" className="qc-start" onClick={() => navigate(`/app/match/waiting/${request.id}`)}>
              대기 화면 열기
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="queue-console">
      <div className="qc-head">
        <h2><IconBolt size={17} /> 큐 매치</h2>
        {recent.length > 0 ? (
          <div className="qc-recent">
            <span>최근 조건</span>
            {recent.map((c, i) => (
              <button key={`${c.game}-${c.modeKey}-${i}`} type="button" onClick={() => setCondition(c)}>
                {conditionSummary(c).slice(0, 2).join(' · ')}
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="qc-games" role="tablist" aria-label="게임 선택">
        {availableGames().map((g) => (
          <button
            key={g.key}
            type="button"
            role="tab"
            aria-selected={g.key === condition.game}
            className={g.key === condition.game ? 'qc-game on' : 'qc-game'}
            onClick={() => setCondition(switchGame(condition, g.key as GameKey))}
          >
            <span className={`game-logo g-${g.key}`}>{g.shortName.slice(0, 3).toUpperCase()}</span>
            <span className="qc-game-name">
              <b>{g.name}</b>
              <small>{g.tagline}</small>
            </span>
          </button>
        ))}
      </div>

      <div className="qc-rows">
        <ChipRow
          label="게임 모드"
          value={condition.modeKey}
          options={visibleModes(condition.game).map((m) => ({ value: m.key, label: m.label }))}
          onChange={(modeKey) => setCondition({ ...condition, modeKey })}
        />
        <ChipRow
          label={cfg.keyCondition.label}
          value={condition.keyCondition.value}
          options={keyConditionOptions(condition.game)}
          onChange={(v) => setCondition({ ...condition, keyCondition: { type: cfg.keyCondition.type, value: v } })}
        />
        <ChipRow
          label="음성 사용"
          value={condition.voicePreference}
          options={VOICE_OPTIONS}
          onChange={(v) => setCondition({ ...condition, voicePreference: v as VoicePreference })}
        />
        <ChipRow
          label="플레이 목적"
          value={condition.playPurpose}
          options={PURPOSE_OPTIONS}
          onChange={(v) => setCondition({ ...condition, playPurpose: v as PlayPurpose })}
        />
      </div>

      <div className="qc-foot">
        <div className="qc-meta">
          <b>{partySize}인</b> 파티로 함께할 팀원을 찾습니다
          <button type="button" className="qc-detail" onClick={() => navigate('/app/match', { state: { condition } })}>
            조건 자세히 보기 →
          </button>
        </div>
        <div className="qc-actions">
          <Button size="lg" onClick={() => navigate('/app/reservations/new', { state: { condition } })}>
            <IconCalendar size={16} /> 예약 매칭
          </Button>
          <Button variant="primary" size="lg" className="qc-start" disabled={busy} onClick={() => void startMatching()}>
            <IconBolt size={17} /> {busy ? '매칭 시작 중...' : '매칭 시작'}
          </Button>
        </div>
      </div>
    </div>
  );
}
