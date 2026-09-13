import { useEffect, useState } from 'react';
import { USE_MOCK } from '../config';
import type { MessageContact } from '../state/directMessages';
import { Avatar, Button } from './ui';
import { IconMic, IconX } from './icons';

// Frontend preview only: never imply that a remote peer or microphone is connected.
export function DirectVoiceStage({ contact, ownerName }: { contact: MessageContact; ownerName: string }) {
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [unanswered, setUnanswered] = useState(false);
  useEffect(() => {
    if (startedAt === null) return;
    const timer = window.setInterval(() => {
      const seconds = Math.floor((Date.now() - startedAt) / 1000);
      setElapsed(seconds);
      if (seconds >= 30) { setStartedAt(null); setUnanswered(true); }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [startedAt]);
  const calling = startedAt !== null;
  return <section className={`dm-voice-stage${calling ? ' is-calling' : ''}`} aria-label="음성 대화">
    <div className="dm-voice-heading"><h3><IconMic size={18} />음성 대화</h3><span>{USE_MOCK ? '미리보기' : '연결 준비 중'}</span></div>
    <div className="dm-voice-members">
      <div className="dm-voice-member"><Avatar name={ownerName} size={56} /><b>{ownerName}</b><small>나</small></div>
      <div className="dm-voice-member"><Avatar name={contact.nickname} avatarUrl={contact.avatarUrl} size={56} /><b>{contact.nickname}</b><small>{calling ? '응답 대기' : '참여 전'}</small></div>
    </div>
    <div className="dm-voice-footer">
      <p role="status">{calling ? `통화 요청 중 · 00:${String(elapsed).padStart(2, '0')}` : unanswered ? '응답이 없습니다' : '음성으로 함께하기'}</p>
      {calling ? <Button variant="danger" size="sm" onClick={() => setStartedAt(null)}><IconX size={16} />요청 취소</Button>
        : <Button variant="primary" size="sm" disabled={!USE_MOCK} onClick={() => { setElapsed(0); setUnanswered(false); setStartedAt(Date.now()); }}><IconMic size={16} />{unanswered ? '다시 통화하기' : '통화 시작'}</Button>}
    </div>
  </section>;
}
