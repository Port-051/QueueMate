type P = { size?: number };
type Selectable = P & { filled?: boolean };

const s = (n = 18) => ({ width: n, height: n, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const });

export const IconHome = ({ size, filled = false }: Selectable) => (
  <svg {...s(size)} viewBox="1.5 1.5 21 21" fill={filled ? 'currentColor' : 'none'} stroke={filled ? 'none' : 'currentColor'} aria-hidden="true">
    <path d="M10 3.2a3 3 0 0 1 4 0l6 5.5a3 3 0 0 1 1 2.2V18a3 3 0 0 1-3 3h-3v-6a3 3 0 0 0-6 0v6H6a3 3 0 0 1-3-3v-7.1a3 3 0 0 1 1-2.2z" />
  </svg>
);
export const IconMatch = ({ size }: P) => (<svg {...s(size)}><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="3" /><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3" /></svg>);
export const IconCalendar = ({ size }: P) => (<svg {...s(size)}><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>);
export const IconParty = ({ size, filled = false }: Selectable) => (
  <svg {...s(size)} fill={filled ? 'currentColor' : 'none'} stroke={filled ? 'none' : 'currentColor'} aria-hidden="true">
    <circle cx="9" cy="8.5" r="3.2" />
    {filled ? <>
      <path d="M2.8 20c0-3.4 2.8-5.6 6.2-5.6s6.2 2.2 6.2 5.6a1 1 0 0 1-1 1H3.8a1 1 0 0 1-1-1z" />
      <path d="M16 5.3a3.2 3.2 0 1 1 0 6.4 5.4 5.4 0 0 0 0-6.4M17 14.4c3 0 5 2.3 5 5.6a1 1 0 0 1-1 1h-3.5a9 9 0 0 0-1.7-6.4z" />
    </> : <>
      <path d="M2.8 20c0-3.4 2.8-5.6 6.2-5.6s6.2 2.2 6.2 5.6" />
      <path d="M16.5 6.2a3 3 0 0 1 0 5.6M18.6 14.8c1.7.8 2.7 2.4 2.7 4.4" />
    </>}
  </svg>
);
export const IconUser = ({ size, filled = false }: Selectable) => (
  <svg {...s(size)} fill={filled ? 'currentColor' : 'none'} stroke={filled ? 'none' : 'currentColor'} aria-hidden="true">
    <circle cx="12" cy="8" r="3.6" />
    <path d="M4.5 20c0-3.5 3.3-5.7 7.5-5.7s7.5 2.2 7.5 5.7a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1z" />
  </svg>
);
export const IconClock = ({ size, filled = false }: Selectable) => (
  <svg {...s(size)} fill={filled ? 'currentColor' : 'none'} stroke={filled ? 'none' : 'currentColor'} aria-hidden="true">
    {filled ? <path fillRule="evenodd" d="M12 3.2a8.8 8.8 0 1 1 0 17.6 8.8 8.8 0 0 1 0-17.6M11.2 6.9a.8.8 0 0 1 1.6 0v4.7l3 1.8a.8.8 0 1 1-.8 1.4l-3.4-2a.8.8 0 0 1-.4-.7z" /> : <>
      <circle cx="12" cy="12" r="8.8" /><path d="M12 7v5.3l3.4 2" />
    </>}
  </svg>
);
export const IconSettings = ({ size }: P) => (
  <svg {...s(size)} fill="currentColor" stroke="none" aria-hidden="true">
    <path fillRule="evenodd" d="M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16M12 8.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8" />
    {[0, 45, 90, 135, 180, 225, 270, 315].map((angle) => <rect key={angle} x="10" y="1" width="4" height="5" rx=".8" transform={`rotate(${angle} 12 12)`} />)}
  </svg>
);
export const IconBell = ({ size }: P) => (<svg {...s(size)}><path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6z" /><path d="M10.3 19.5a2 2 0 0 0 3.4 0" /></svg>);
export const IconMic = ({ size }: P) => (<svg {...s(size)}><rect x="9" y="2.5" width="6" height="11" rx="3" /><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3.5" /></svg>);
export const IconMicOff = ({ size }: P) => (<svg {...s(size)}><path d="M9 5.5a3 3 0 0 1 6 0v5M9 10v.6a3 3 0 0 0 4.6 2.5" /><path d="M5.5 11.5a6.5 6.5 0 0 0 10 5.5M18.5 11.5a6.4 6.4 0 0 1-.5 2.5M12 18v3.5" /><path d="m3.5 3 17 18" /></svg>);
export const IconMicOptional = ({ size }: P) => (<svg {...s(size)}><rect x="6" y="2.5" width="6" height="10" rx="3" /><path d="M2.5 10.5a6.5 6.5 0 0 0 13 0M9 17v4.5M15 18h7m-2-2 2 2-2 2m-3-4-2 2 2 2" /></svg>);
export const IconBolt = ({ size }: P) => (<svg {...s(size)}><path d="M13.5 2.5 4.8 13.4h5.9l-1 8.1 8.7-10.9h-5.9z" /></svg>);
export const IconCheck = ({ size }: P) => (<svg {...s(size)}><path d="m4.5 12.5 5 5 10-11" /></svg>);
export const IconX = ({ size }: P) => (<svg {...s(size)}><path d="M5.5 5.5 18.5 18.5M18.5 5.5 5.5 18.5" /></svg>);
export const IconChat = ({ size }: P) => (<svg {...s(size)}><path d="M4 5.5h16v11H9.5L5 20.5v-4H4z" /></svg>);
export const IconShield = ({ size }: P) => (<svg {...s(size)}><path d="M12 2.8 4.8 5.7v5.6c0 4.7 3 8 7.2 9.9 4.2-1.9 7.2-5.2 7.2-9.9V5.7z" /></svg>);
export const IconTarget = ({ size }: P) => (<svg {...s(size)}><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="4.6" /><circle cx="12" cy="12" r="1" fill="currentColor" /></svg>);
export const IconLogout = ({ size }: P) => (<svg {...s(size)}><path d="M14.5 8.5V5.5h-9v13h9v-3" /><path d="M10.5 12h10M17.5 8.7l3.3 3.3-3.3 3.3" /></svg>);
export const IconPlus = ({ size }: P) => (<svg {...s(size)}><path d="M12 5v14M5 12h14" /></svg>);
export const IconTrash = ({ size }: P) => (<svg {...s(size)}><path d="M4.5 6.5h15M9.5 6.5V4h5v2.5M6.5 6.5 7.5 20h9l1-13.5" /></svg>);
export const IconPencil = ({ size }: P) => (<svg {...s(size)}><path d="M4.5 19.5h4L20 8a2.5 2.5 0 0 0-3.5-3.5L5 16z" /></svg>);
export const IconSend = ({ size }: P) => (<svg {...s(size)}><path d="M21 3 10.5 13.5M21 3l-7 18-3.5-7.5L3 10z" /></svg>);
export const IconSearch = ({ size }: P) => (<svg {...s(size)}><circle cx="11" cy="11" r="7" /><path d="M20.5 20.5 16.1 16.1" /></svg>);
export const IconCopy = ({ size }: P) => (<svg {...s(size)}><rect x="9" y="9" width="12" height="12" rx="2.5" /><path d="M6 15.5H5.5A2.5 2.5 0 0 1 3 13V5.5A2.5 2.5 0 0 1 5.5 3H13a2.5 2.5 0 0 1 2.5 2.5V6" /></svg>);

export const IconPause = ({ size }: P) => (<svg {...s(size)} aria-hidden="true"><path d="M8 5v14M16 5v14" /></svg>);
export const IconPlay = ({ size }: P) => (<svg {...s(size)} aria-hidden="true"><path d="m8 5 11 7-11 7z" /></svg>);
