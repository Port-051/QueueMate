const SYMBOL = `${import.meta.env.BASE_URL}brand/queuemate-symbol.svg`;
const WORDMARK = `${import.meta.env.BASE_URL}brand/queuemate-wordmark.svg`;

export function LogoMark({ size = 34, className = '' }: { size?: number; className?: string }) {
  return <img src={SYMBOL} width={size} height={size} className={`logo-mark ${className}`.trim()} alt="" aria-hidden="true" draggable={false} />;
}

/**
 * 로고 심볼(말풍선 둘이 겹쳐 Q 가 된 모양)의 **단색 실루엣** — 얼굴(`Avatar`)의 가운데 아이콘이다(2026-09-30 소유자 결정 — Discord 식 기본 아바타).
 * 심볼 원본은 PNG 뿐이라(`public/brand/README.md` — 벡터가 아니다) 그 윤곽을 따라 새로 그렸다: 두 말풍선의 합집합 + 겹친 자리의 둥근 네모 구멍(`evenodd`) · 앞 말풍선의 꼬리.
 * 좌표는 원본 PNG(1254²)의 픽셀에서 (316, 293)을 뺀 것이고, 구멍의 오른쪽 위 모서리는 원본처럼 비스듬히 깎았다. 두 말풍선 사이의 가는 틈 · 두 보라의 명암은 뺐다(작은 얼굴에서는 보이지 않는다).
 * 색은 `currentColor`(얼굴은 흰색). 크기는 부모가 정한다(`.avatar-glyph` — 얼굴 지름의 60%). 따라 그린 모양은 Claude 가 정한 세부다.
 */
export function LogoGlyph({ className = '' }: { className?: string }) {
  return <svg className={className || undefined} viewBox="0 0 622 668" fill="currentColor" aria-hidden="true" focusable="false">
    <path fillRule="evenodd" d="M125 0H334A110 110 0 0 1 444 110V169H502A120 120 0 0 1 622 289V487Q622 545 577 569L598 634Q608 668 574 668L448 597H287A120 120 0 0 1 167 477V442H125A125 125 0 0 1 0 317V125A125 125 0 0 1 125 0ZM244 170H376L458 252V383A75 75 0 0 1 383 458H239A70 70 0 0 1 169 388V245A75 75 0 0 1 244 170Z" />
  </svg>;
}

export function Logo({ size = 34, wordmark = true, className = '' }: { size?: number; wordmark?: boolean; className?: string }) {
  return <span className={`brand ${className}`.trim()} role="img" aria-label="QueueMate">
    <LogoMark size={size} />
    {wordmark ? <img className="brand-wordmark" src={WORDMARK} alt="" aria-hidden="true" draggable={false} /> : null}
  </span>;
}
