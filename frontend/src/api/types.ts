export type GameKey = 'LOL' | 'VALORANT' | 'PUBG';
export type VoicePreference = 'REQUIRED' | 'OPTIONAL' | 'NO_VOICE';
export type PlayPurpose = 'RANK_UP' | 'NORMAL' | 'FUN';
export type PlayAmount = 'ONE_GAME' | 'TWO_PLUS';

export type KeyConditionType = 'POSITION' | 'ROLE' | 'PLAY_STYLE';

export interface MatchCondition {
  game: GameKey;
  modeKey: string;
  keyCondition: { type: KeyConditionType; value: string };
  voicePreference: VoicePreference;
  playPurpose: PlayPurpose;
}

export interface MatchRequestView {
  id: string;
  status: 'QUEUED' | 'PROPOSED' | 'MATCHED' | 'CANCELLED' | 'EXPIRED';
  queuedAt: string;
  proposalId?: string | null;
}

export interface ReservationInput {
  condition: MatchCondition;
  availableFrom: string;
  availableTo: string;
  playAmount: PlayAmount;
}
