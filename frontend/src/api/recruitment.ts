import { request } from './http';
import type { MatchCondition, PlayAmount } from './types';
export interface BoardPreferences {
  ownTier: string | null;
  minTier: string | null;
  maxTier: string | null;
  desiredKeys: string[];
  purposeRequired: boolean;
}
export type BoardType = 'REALTIME' | 'RESERVATION';
export interface BoardWrite {
  type: BoardType;
  condition: MatchCondition;
  preferences: BoardPreferences;
  description: string;
  autoMatch: boolean;
  availableFrom: string | null;
  availableTo: string | null;
  playAmount: PlayAmount | null;
}
export interface BoardPerson { id: string; userId: string; nickname: string; condition: MatchCondition; preferences: BoardPreferences; }
export interface BoardRow extends BoardWrite {
  id: string; userId: string; nickname: string;
  status: 'OPEN' | 'PAUSED' | 'STALE' | 'REQUESTED' | 'JOINED' | 'PROPOSED' | 'MATCHED' | 'CLOSED';
  createdAt: string; confirmedAt: string; bumpedAt: string | null;
  parentId: string | null; requestedParentId: string | null; proposalId: string | null;
  version: number; targetSize: number; members: BoardPerson[]; applicants: BoardPerson[];
  impressions: number; alertEnabled: boolean;
  timing?: { confirmAt: string; hideAt: string | null; suggestAt: string; nextBumpAt: string };
}
export interface BoardSearch {
  type: BoardType; condition: MatchCondition; preferences: BoardPreferences;
  availableFrom: string | null; availableTo: string | null; playAmount: PlayAmount | null;
  sort: 'RECOMMENDED' | 'RECENT'; page: number; pageSize: 5 | 10;
}
export interface BoardPage { items: BoardRow[]; total: number; page: number; hasMore: boolean; asOf: string; }
export interface BoardSuggestion { field: string; label: string; condition: MatchCondition; preferences: BoardPreferences; candidateCount: number; candidates: BoardRow[]; }
export interface BoardSuggestions { currentCount: number; suggestions: BoardSuggestion[]; asOf: string; }
export type BoardAction = 'CONFIRM' | 'BUMP' | 'PAUSE' | 'RESUME' | 'CLOSE' | 'LEAVE' | 'AUTO_ON' | 'AUTO_OFF' | 'ALERT_ON' | 'ALERT_OFF';
export const searchBoard = (body: BoardSearch) => request<BoardPage>('/recruitments/search', { method: 'POST', body });
export const myRecruitments = () => request<BoardRow[]>('/recruitments/mine');
export const getRecruitment = (id: string) => request<BoardRow>(`/recruitments/${id}`);
export const createRecruitment = (body: BoardWrite) => request<BoardRow>('/recruitments', { method: 'POST', body });
export const editRecruitment = (row: BoardRow, body: BoardWrite) => request<BoardRow>(`/recruitments/${row.id}`, { method: 'PUT', body: { ...body, version: row.version } });
export const recruitmentAction = (row: BoardRow, action: BoardAction) => request<BoardRow>(`/recruitments/${row.id}/actions`, { method: 'POST', body: { action, version: row.version } });
export const joinRecruitment = (id: string, sourceId: string) => request<BoardRow>(`/recruitments/${id}/join`, { method: 'POST', body: { sourceId } });
export const respondRecruitment = (id: string, applicantId: string, accept: boolean) => request<BoardRow>(`/recruitments/${id}/respond`, { method: 'POST', body: { applicantId, accept } });
export const recruitmentSuggestions = (id: string) => request<BoardSuggestions>(`/recruitments/${id}/suggestions`);
export const recordImpressions = (ids: string[]) => request<void>('/recruitments/impressions', { method: 'POST', body: { ids } });
