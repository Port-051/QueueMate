import type { TierRange } from '../domain/tierRange';
import type { GameKey, VoicePreference } from '../api/types';

export interface RoomMember {
  id: string;
  nickname: string;
  avatarUrl: string | null;
  tier: string | null;
  division: number | null;
  winRate: number | null;
  kda: number | null;
  roles: string[];
  champions: string[];
  bio: string;
  voice: VoicePreference;
}
export interface RoomMessage {
  id: string;
  authorId: string | null;
  text: string;
  createdAt: number;
}
export interface GameRoom {
  id: string;
  game: GameKey;
  modeKey: string;
  type: 'REALTIME' | 'RESERVATION';
  title: string;
  ownerId: string;
  capacity: number;
  members: RoomMember[];
  desiredRoles: string[];
  desiredTierRange?: TierRange;
  voice: VoicePreference;
  status: 'OPEN' | 'CONFIRMED';
  autoCloseAt?: number | null;
  createdAt: number;
  availableFrom: string | null;
  messages: RoomMessage[];
}
export interface CreateRoomInput {
  game: GameKey;
  modeKey: string;
  type: GameRoom['type'];
  title: string;
  capacity: number;
  desiredRoles: string[];
  desiredTierRange?: TierRange;
  voice: VoicePreference;
  availableFrom: string | null;
}
export interface RoomSummary {
  tier: string | null;
  division: number | null;
  winRate: number | null;
  kda: number | null;
  roles: string[];
  ratedCount: number;
}
