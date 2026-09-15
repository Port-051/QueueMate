// QueueMate — 누락 아이콘 2종 (icons.tsx 에 붙여넣기용)
// 규격: viewBox 24x24 / fill none / stroke currentColor / stroke-width 1.7 / round cap·join
// 기존 icons.tsx 의 props 타입·기본 size 에 맞춰 시그니처만 바꿔 쓰면 된다.

import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

export function IconSearch({ size = 20, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20.5 20.5 16.1 16.1" />
    </svg>
  );
}

export function IconCopy({ size = 20, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <rect x="9" y="9" width="12" height="12" rx="2.5" />
      <path d="M6 15.5H5.5A2.5 2.5 0 0 1 3 13V5.5A2.5 2.5 0 0 1 5.5 3H13a2.5 2.5 0 0 1 2.5 2.5V6" />
    </svg>
  );
}
