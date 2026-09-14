import { useState } from 'react';
import type { BoardRow } from '../api/recruitment';
import { errorMessage } from '../api/error';
import { useAuth } from '../state/AuthContext';
import { useDuoOffers } from '../state/duoOffers';
import { Avatar, Button, useToast } from './ui';
import { MatchConditionSummary } from './MatchConditionSummary';
import { introductionForRow } from '../domain/introduction';
import { IntroductionStats } from './RecruitmentList';
import { RankBadge } from './RankBadge';
import { IconCheck, IconX } from './icons';

export function DuoOffersPanel({ source }: { source: BoardRow }) {
  const { user } = useAuth();
  const offers = useDuoOffers(user?.id ?? '').filter(offer => offer.sourceId === source.id);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();
  const found = offers.filter(offer => offer.status === 'FOUND' || offer.status === 'RECEIVED');
  const sent = offers.filter(offer => offer.status === 'SENT');
  const act = async (id: string, peerId?: string) => {
    if (!user || busy) return;
    setBusy(id);
    try {
      const mock = await import('../mocks/recruitment');
      if (peerId) mock.sendDuoInterest(source.id, peerId);
      else mock.dismissDuoOffer(user.id, id);
    } catch (error) { toast(errorMessage(error), 'error'); }
    finally { setBusy(null); }
  };
  if (!found.length && !sent.length) return null;
  return <section className="duo-offers" aria-label="함께할 상대">
    <div className="sr-only" role="status" aria-live="polite">{found.length ? `${found.map(offer => offer.peer.nickname).join(', ')}님을 찾았어요` : ''}</div>
    {found.map(offer => {
      const introduction = introductionForRow(offer.peer);
      return <article className="duo-offer" key={offer.id} aria-label={`${offer.peer.nickname} 매칭 제안`}>
        <div className="duo-offer-label"><span className="discovery-dot" />{offer.status === 'RECEIVED' ? '먼저 오케이를 보냈어요' : '상대 발견'}</div>
        <div className="duo-offer-person"><Avatar name={offer.peer.nickname} size={32} /><strong>{offer.peer.nickname}</strong><RankBadge game={offer.peer.condition.game} tier={offer.peer.preferences.ownTier} division={introduction.rankDivision} /></div>
        <MatchConditionSummary record={offer.peer} />
        <IntroductionStats game={offer.peer.condition.game} introduction={introduction} />
        {offer.peer.description ? <p className="duo-offer-bio">{offer.peer.description}</p> : null}
        <div className="duo-decision-actions">
          <Button className="duo-decision dismiss" aria-label="다음에" title="다음에" disabled={Boolean(busy)} onClick={() => void act(offer.id)}><IconX size={21} /></Button>
          <Button className="duo-decision accept" aria-label="같이 할래요" title="같이 할래요" variant="primary" disabled={Boolean(busy) || source.status !== 'OPEN'} onClick={() => void act(offer.id, offer.peer.id)}><IconCheck size={23} /></Button>
        </div>
      </article>;
    })}
    {sent.length ? <section className="duo-sent" aria-label="보낸 오케이"><h3>응답 대기 <span>{sent.length}</span></h3>{sent.map(offer => <div className="duo-sent-person" key={offer.id}><Avatar name={offer.peer.nickname} size={28} /><div><strong>{offer.peer.nickname}</strong></div><Button size="sm" variant="ghost" disabled={Boolean(busy)} aria-label={`${offer.peer.nickname} 오케이 취소`} title="오케이 취소" onClick={() => void act(offer.id)}><IconX size={16} /></Button></div>)}</section> : null}
  </section>;
}
