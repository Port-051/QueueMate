import { IconChat, IconLogout, IconShield, IconUser } from '../components/icons';

export function MemberActionIcon({ action }: { action: string }) {
  if (action === 'message') return <IconChat size={15} />;
  if (action === 'kick') return <IconLogout size={15} />;
  if (action === 'report') return <IconShield size={15} />;
  if (action === 'friend') return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><circle cx="9" cy="7" r="3.5" /><path d="M2 21v-2a7 7 0 0 1 14 0v2M19 6v8M15 10h8" /></svg>;
  if (action === 'friend-added') return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="9" cy="7" r="3.5" /><path d="M2 21v-2a7 7 0 0 1 14 0v2m-1-9 3 3 5-6" /></svg>;
  if (action === 'block' || action === 'unblock') return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" />{action === 'block' ? <path d="m6 6 12 12" /> : <path d="m7 12 3 3 7-7" />}</svg>;
  return <IconUser size={15} />;
}
