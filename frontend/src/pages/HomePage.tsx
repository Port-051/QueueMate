import { USE_MOCK } from '../config';
import { LegacyRecruitmentHome } from './LegacyRecruitmentHome';
import { RoomBoardHome } from '../rooms/RoomBoardHome';

/** Room membership is a frontend prototype until the group-room API is available. */
export function HomePage() {
  return USE_MOCK && import.meta.env.VITE_HOME_LAYOUT !== 'legacy'
    ? <RoomBoardHome /> : <LegacyRecruitmentHome />;
}
