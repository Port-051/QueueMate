import type { GameKey, MatchCondition, PlayPurpose, VoicePreference } from '../api/types';
import {
  PURPOSE_OPTIONS, VOICE_OPTIONS, availableGames, gameConfig, keyConditionOptions, switchGame, visibleModes,
} from '../domain/gameConfig';
import { OptionRow } from './ui';
import { GameWordmark } from './GameWordmark';

/**
 * docs/02 기준 조건 폼. 공통 골격(모드/음성/목적) + 게임별 핵심 조건 하나만 받는다.
 * 실시간·예약 화면이 같은 폼을 쓴다.
 */
export function ConditionForm({ value, onChange }: { value: MatchCondition; onChange: (next: MatchCondition) => void }) {
  const cfg = gameConfig(value.game);

  return (
    <div className="stack">
      <div>
        <div className="card-title" style={{ marginBottom: 12 }}>게임 선택</div>
        <div className="game-picker">
          {availableGames().map((g) => (
            <button
              key={g.key}
              type="button"
              aria-pressed={g.key === value.game}
              aria-label={g.name}
              className={`game-card g-${g.key}${g.key === value.game ? ' on' : ''}`}
              onClick={() => onChange(switchGame(value, g.key as GameKey))}
            >
              {/* 배경 아트는 `.game-card.g-{GAME}`이 깐다. 이름은 로고가 대신한다. */}
              <GameWordmark game={g.key as GameKey} />
              <div className="gc-sub">{g.tagline}</div>
              {g.key === value.game ? <span className="gc-check">✓</span> : null}
            </button>
          ))}
        </div>
      </div>

      <div className="card">
        <OptionRow
          label="게임 모드"
          desc="어떤 모드로 플레이할까요?"
          value={value.modeKey}
          options={visibleModes(value.game).map((m) => ({ value: m.key, label: m.label }))}
          onChange={(modeKey) => onChange({ ...value, modeKey })}
        />
        <OptionRow
          label={cfg.keyCondition.label}
          desc={cfg.keyCondition.desc}
          value={value.keyCondition.value}
          options={keyConditionOptions(value.game)}
          onChange={(v) => onChange({ ...value, keyCondition: { type: cfg.keyCondition.type, value: v } })}
        />
        <OptionRow
          label="음성 사용"
          desc="음성 채팅 사용 여부를 선택하세요. '사용'과 '사용 안 함'은 서로 매칭되지 않습니다."
          value={value.voicePreference}
          options={VOICE_OPTIONS}
          onChange={(v) => onChange({ ...value, voicePreference: v as VoicePreference })}
        />
        <OptionRow
          label="플레이 목적"
          desc="대기가 길어지면 목적 조건만 자동으로 완화될 수 있습니다."
          value={value.playPurpose}
          options={PURPOSE_OPTIONS}
          onChange={(v) => onChange({ ...value, playPurpose: v as PlayPurpose })}
        />
      </div>
    </div>
  );
}
