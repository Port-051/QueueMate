/** Legacy OPTIONAL is treated as microphone off until the user chooses to use it. */
export const roomVoice = (value: unknown): 'REQUIRED' | 'NO_VOICE' => value === 'REQUIRED' ? 'REQUIRED' : 'NO_VOICE';
export const ROOM_VOICES = ['REQUIRED', 'NO_VOICE'] as const;
