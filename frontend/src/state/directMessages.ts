import { useCallback, useMemo, useSyncExternalStore } from 'react';

export interface MessageContact { userId: string; nickname: string; avatarUrl: string | null; }
export interface DirectMessage { id: string; senderId: string; text: string; createdAt: string; example?: boolean; }
export interface DirectConversation {
  contact: MessageContact;
  messages: DirectMessage[];
  pinnedAt: number | null;
  lastReadAt: number;
  draft: string;
}
export interface DirectMessagesSnapshot {
  version: 1;
  seeded: boolean;
  conversations: Record<string, DirectConversation>;
}
export interface DirectMessageEvent {
  ownerId: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  text: string;
  createdAt: string;
}

const STORAGE_PREFIX = 'qm:direct-messages:';
const CHANGE_EVENT = 'qm:direct-messages-changed';
const EMPTY: DirectMessagesSnapshot = { version: 1, seeded: false, conversations: {} };
const cache = new Map<string, DirectMessagesSnapshot>();
const MAX_MESSAGES = 500;
export const DIRECT_MESSAGE_EVENT = 'qm:direct-message';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseSnapshot(raw: string | null): DirectMessagesSnapshot {
  if (!raw) return EMPTY;
  try {
    const value: unknown = JSON.parse(raw);
    if (!isRecord(value) || value.version !== 1 || !isRecord(value.conversations)) return EMPTY;
    const conversations: Record<string, DirectConversation> = {};
    for (const [id, item] of Object.entries(value.conversations)) {
      if (!isRecord(item) || !isRecord(item.contact) || item.contact.userId !== id || typeof item.contact.nickname !== 'string' || !Array.isArray(item.messages)) continue;
      const messages: DirectMessage[] = item.messages.filter((message): message is DirectMessage => isRecord(message)
        && typeof message.id === 'string' && typeof message.senderId === 'string'
        && typeof message.text === 'string' && typeof message.createdAt === 'string'
        && Number.isFinite(Date.parse(message.createdAt))).slice(-MAX_MESSAGES);
      conversations[id] = {
        contact: { userId: id, nickname: item.contact.nickname, avatarUrl: typeof item.contact.avatarUrl === 'string' ? item.contact.avatarUrl : null },
        messages,
        pinnedAt: typeof item.pinnedAt === 'number' && Number.isFinite(item.pinnedAt) ? item.pinnedAt : null,
        lastReadAt: typeof item.lastReadAt === 'number' && Number.isFinite(item.lastReadAt) ? item.lastReadAt : 0,
        draft: typeof item.draft === 'string' ? item.draft.slice(0, 2000) : '',
      };
    }
    return { version: 1, seeded: value.seeded === true, conversations };
  } catch { return EMPTY; }
}

export function readDirectMessages(ownerId: string | null | undefined): DirectMessagesSnapshot {
  if (!ownerId) return EMPTY;
  const existing = cache.get(ownerId);
  if (existing) return existing;
  let snapshot = EMPTY;
  try { snapshot = parseSnapshot(localStorage.getItem(`${STORAGE_PREFIX}${ownerId}`)); } catch { /* Storage can be unavailable in private browser sessions. */ }
  cache.set(ownerId, snapshot);
  return snapshot;
}

export function subscribeDirectMessages(listener: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === null) cache.clear();
    else if (event.key.startsWith(STORAGE_PREFIX)) cache.delete(event.key.slice(STORAGE_PREFIX.length));
    else return;
    listener();
  };
  window.addEventListener(CHANGE_EVENT, listener);
  window.addEventListener('storage', onStorage);
  return () => { window.removeEventListener(CHANGE_EVENT, listener); window.removeEventListener('storage', onStorage); };
}

function write(ownerId: string, snapshot: DirectMessagesSnapshot): void {
  // A failed local save must not look like a successful send or persisted pin.
  localStorage.setItem(`${STORAGE_PREFIX}${ownerId}`, JSON.stringify(snapshot));
  cache.set(ownerId, snapshot);
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: { ownerId } }));
}

function emptyConversation(contact: MessageContact): DirectConversation {
  return { contact, messages: [], pinnedAt: null, lastReadAt: 0, draft: '' };
}

export function unreadMessages(conversation: DirectConversation, ownerId: string): number {
  return conversation.messages.filter(message => message.senderId !== ownerId && Date.parse(message.createdAt) > conversation.lastReadAt).length;
}

export function countUnreadMessages(snapshot: DirectMessagesSnapshot, ownerId: string): number {
  return Object.values(snapshot.conversations).reduce((sum, conversation) => sum + unreadMessages(conversation, ownerId), 0);
}

export function ensureDirectContacts(ownerId: string, contacts: MessageContact[], seedExamples = false): void {
  const previous = readDirectMessages(ownerId);
  const conversations = { ...previous.conversations };
  let changed = false;
  for (const contact of contacts) {
    if (contact.userId === ownerId) continue;
    const old = conversations[contact.userId];
    if (!old) { conversations[contact.userId] = emptyConversation(contact); changed = true; }
    else if (old.contact.nickname !== contact.nickname || old.contact.avatarUrl !== contact.avatarUrl) {
      conversations[contact.userId] = { ...old, contact }; changed = true;
    }
  }
  const seeded = previous.seeded || (seedExamples && contacts.length > 0);
  if (!previous.seeded && seeded) {
    const examples = [
      ['아까 마지막 한타 좋았어요!', '다음에도 같이 해요.', '오늘 저녁에 한 판 더 할까요?'],
      ['듀오 고마워요. 재밌었어요!', '저도요! 다음에 또 해요.'],
      ['방금 플레이 좋았어요 👏', '다음에 자리 나면 알려주세요.'],
    ];
    contacts.slice(0, 3).forEach((contact, index) => {
      const old = conversations[contact.userId];
      if (!old || old.messages.length) return;
      const base = Date.now() - (index + 1) * 3_600_000;
      const messages = examples[index].map((text, order) => ({
        id: `example-${contact.userId}-${order}`, senderId: order === 1 && index !== 2 ? ownerId : contact.userId,
        text, createdAt: new Date(base + order * 60_000).toISOString(), example: true,
      }));
      conversations[contact.userId] = { ...old, messages, lastReadAt: index === 0 || index === 2 ? 0 : Date.now() };
    });
    changed = true;
  }
  if (changed) write(ownerId, { version: 1, seeded, conversations });
}

function updateConversation(ownerId: string, contact: MessageContact, update: (old: DirectConversation) => DirectConversation): void {
  const snapshot = readDirectMessages(ownerId);
  const old = snapshot.conversations[contact.userId] ?? emptyConversation(contact);
  const next = update(old);
  if (next === old) return;
  write(ownerId, { ...snapshot, conversations: { ...snapshot.conversations, [contact.userId]: next } });
}

export function markDirectMessagesRead(ownerId: string, contact: MessageContact): void {
  updateConversation(ownerId, contact, old => unreadMessages(old, ownerId) === 0 ? old : {
    ...old, lastReadAt: Math.max(Date.now(), ...old.messages.map(message => Date.parse(message.createdAt))),
  });
}

export function toggleDirectMessagePin(ownerId: string, contact: MessageContact): void {
  updateConversation(ownerId, contact, old => ({ ...old, pinnedAt: old.pinnedAt === null ? Date.now() : null }));
}

export function deleteDirectConversation(ownerId: string, contact: MessageContact): void {
  const snapshot = readDirectMessages(ownerId);
  // Deletion also completes example initialization so a later contact refresh
  // cannot replace the removed conversation with a sample exchange.
  write(ownerId, {
    ...snapshot,
    seeded: true,
    conversations: { ...snapshot.conversations, [contact.userId]: emptyConversation(contact) },
  });
}

export function saveDirectMessageDraft(ownerId: string, contact: MessageContact, draft: string): void {
  updateConversation(ownerId, contact, old => old.draft === draft ? old : { ...old, draft: draft.slice(0, 2000) });
}

function appendMessage(ownerId: string, contact: MessageContact, text: string, incoming: boolean): DirectMessage {
  const body = text.trim();
  if (!body || body.length > 2000) throw new Error('메시지는 1~2,000자로 입력해주세요.');
  const current = readDirectMessages(ownerId).conversations[contact.userId];
  const latest = current?.messages.at(-1);
  const createdAt = Math.max(Date.now(), latest ? Date.parse(latest.createdAt) + 1 : 0, incoming ? (current?.lastReadAt ?? 0) + 1 : 0);
  const message: DirectMessage = { id: crypto.randomUUID(), senderId: incoming ? contact.userId : ownerId, text: body, createdAt: new Date(createdAt).toISOString() };
  updateConversation(ownerId, contact, old => ({
    ...old, contact, messages: [...old.messages, message].slice(-MAX_MESSAGES),
    draft: incoming ? old.draft : '',
  }));
  if (incoming) window.dispatchEvent(new CustomEvent<DirectMessageEvent>(DIRECT_MESSAGE_EVENT, { detail: {
    ownerId, conversationId: contact.userId, senderId: contact.userId, senderName: contact.nickname, text: body, createdAt: message.createdAt,
  } }));
  return message;
}

export function sendDirectMessage(ownerId: string, contact: MessageContact, text: string): DirectMessage {
  return appendMessage(ownerId, contact, text, false);
}

/** Local preview entry point, also used by notification demos. No network request is made. */
export function receiveDirectMessage(ownerId: string, contact: MessageContact, text: string): DirectMessage {
  return appendMessage(ownerId, contact, text, true);
}

export function useDirectMessages(ownerId: string | null | undefined) {
  const getSnapshot = useCallback(() => readDirectMessages(ownerId), [ownerId]);
  const snapshot = useSyncExternalStore(subscribeDirectMessages, getSnapshot, () => EMPTY);
  const conversations = useMemo(() => Object.values(snapshot.conversations), [snapshot]);
  return { snapshot, conversations, unreadCount: ownerId ? countUnreadMessages(snapshot, ownerId) : 0 };
}
