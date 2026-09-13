import { useState } from 'react';
import type { MatchCondition, PlayPurpose, VoicePreference } from '../api/types';
import {
  PURPOSE_OPTIONS, VOICE_OPTIONS, availableGames, gameConfig, keyConditionOptions, switchGame, visibleModes,
} from '../domain/gameConfig';
import { Button, Modal, OptionRow } from './ui';
import { GameBadge } from './GameSymbol';

/**
 * docs/02 기준 조건 폼. 공통 골격(모드/음성/목적) + 게임별 핵심 조건 하나만 받는다.
 * 실시간·예약 화면이 같은 폼을 쓴다.
 */
export function ConditionForm({ value, onChange, showGame = true }: { value: MatchCondition; onChange: (next: MatchCondition) => void; showGame?: boolean }) {
  const cfg = gameConfig(value.game);
  const [choosingGame, setChoosingGame] = useState(false);

  return (
    <div className="stack condition-form">
      {showGame ? <div className="selected-game" aria-label={`선택한 게임: ${cfg.name}`}>
        <div className="selected-game-title"><GameBadge game={value.game} size={44} /><h2>{cfg.name}</h2></div>
        <Button size="sm" variant="ghost" onClick={() => setChoosingGame(true)}>게임 변경</Button>
      </div> : null}

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
          desc="'사용'과 '사용 안 함'은 서로 매칭되지 않습니다."
          value={value.voicePreference}
          options={VOICE_OPTIONS}
          onChange={(v) => onChange({ ...value, voicePreference: v as VoicePreference })}
        />
        <OptionRow
          label="플레이 목적"
          desc="같은 목적을 우선 추천합니다. 필수 여부는 직접 정할 수 있습니다."
          value={value.playPurpose}
          options={PURPOSE_OPTIONS}
          onChange={(v) => onChange({ ...value, playPurpose: v as PlayPurpose })}
        />
      </div>
      {choosingGame ? <Modal title="게임 변경" onClose={() => setChoosingGame(false)}
        foot={<Button variant="ghost" onClick={() => setChoosingGame(false)}>닫기</Button>}>
        <div className="game-change-list">
          {availableGames().map((game) => <button key={game.key} type="button" className="game-change-option" aria-pressed={game.key === value.game}
            onClick={() => { onChange(switchGame(value, game.key)); setChoosingGame(false); }}>
            <GameBadge game={game.key} /><span>{game.name}</span>{game.key === value.game ? <span className="game-change-check" aria-hidden="true">✓</span> : null}
          </button>)}
        </div>
      </Modal> : null}
    </div>
  );
}
