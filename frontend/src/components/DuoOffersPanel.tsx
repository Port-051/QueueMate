import { useState } from 'react';
import type { BoardRow } from '../api/recruitment';
import { errorMessage } from '../api/error';
import { useAuth } from '../state/AuthContext';
import { useDuoOffers } from '../state/duoOffers';
import { Avatar, Button, useToast } from './ui';
import { ParticipantIntroduction } from './ParticipantIntroduction';

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
    {sent.length ? <section className="duo-sent" aria-label="보낸 오케이"><h3>오케이 보냄 <span>{sent.length}</span></h3><p>상대의 응답을 기다리며 계속 모집 중이에요.</p>{sent.map(offer => <div className="duo-sent-person" key={offer.id}><Avatar name={offer.peer.nickname} size={30} /><div><strong>{offer.peer.nickname}</strong><span>응답 기다리는 중</span></div><Button size="sm" variant="ghost" disabled={Boolean(busy)} aria-label={`${offer.peer.nickname} 오케이 취소`} onClick={() => void act(offer.id)}>취소</Button></div>)}</section> : null}
    {found.length ? <><h3>조건이 맞는 상대를 찾았어요</h3>{found.map(offer => <article className="duo-offer" key={offer.id} aria-label={`${offer.peer.nickname} 매칭 제안`}>
      <div className="duo-offer-person"><Avatar name={offer.peer.nickname} size={36} /><strong>{offer.peer.nickname}</strong></div>
      {offer.status === 'RECEIVED' ? <p className="duo-offer-received">상대가 먼저 오케이를 보냈어요</p> : null}
      <ParticipantIntroduction nickname={offer.peer.nickname} record={offer.peer} sourceId={offer.peer.id} />
      <div className="matching-rail-actions"><Button disabled={Boolean(busy)} onClick={() => void act(offer.id)}>다음에</Button><Button variant="primary" disabled={Boolean(busy) || source.status !== 'OPEN'} onClick={() => void act(offer.id, offer.peer.id)}>같이 할래요</Button></div>
    </article>)}</> : null}

  </section>;
}
