import { VoiceIcon } from '../components/FilterSymbols';
import { roomVoice } from './voice';

export function RoomVoice({ value }: { value: string }) {
  const voice = roomVoice(value);
  const label = voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용';
  return <span className={`room-mic-icon ${voice === 'REQUIRED' ? 'is-on' : 'is-off'}`} role="img" aria-label={label} title={label}><VoiceIcon preference={voice} size={21}/></span>;
}
