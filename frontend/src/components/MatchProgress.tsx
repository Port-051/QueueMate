export function MatchProgress({ step }: { step: 0 | 1 | 2 }) {
  return <ol className="match-progress" aria-label="매칭 진행 단계">
    {['팀원 모집', '전원 수락', '파티 준비'].map((label, index) => <li key={label}
      className={index === step ? 'current' : index < step ? 'complete' : ''}
      aria-current={index === step ? 'step' : undefined}>
      <span aria-hidden="true">{index < step ? '✓' : index + 1}</span>{label}
    </li>)}
  </ol>;
}
