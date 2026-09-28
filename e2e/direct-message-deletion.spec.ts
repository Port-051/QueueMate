import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.goto('/login');
});

test('대화를 삭제하면 메시지·초안·고정·읽지 않음을 지우고 새로고침 후에도 예시를 복구하지 않는다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const dm = await import('/src/state/directMessages.ts');
    const owner = 'delete-owner';
    const contact = { userId: 'former-teammate', nickname: '이전 팀원', avatarUrl: null };
    dm.ensureDirectContacts(owner, [contact]);
    dm.receiveDirectMessage(owner, contact, '삭제할 대화');
    dm.saveDirectMessageDraft(owner, contact, '삭제할 초안');
    dm.toggleDirectMessagePin(owner, contact);
    dm.deleteDirectConversation(owner, contact);
    dm.ensureDirectContacts(owner, [contact], true);
    return { conversation: dm.readDirectMessages(owner).conversations[contact.userId], unread: dm.countUnreadMessages(dm.readDirectMessages(owner), owner) };
  });
  expect(result.conversation).toMatchObject({ messages: [], draft: '', pinnedAt: null, lastReadAt: 0 });
  expect(result.unread).toBe(0);
  await page.reload();
  const restored = await page.evaluate(async () => {
    const dm = await import('/src/state/directMessages.ts');
    dm.ensureDirectContacts('delete-owner', [{ userId: 'former-teammate', nickname: '바뀐 이름', avatarUrl: null }], true);
    return dm.readDirectMessages('delete-owner').conversations['former-teammate'];
  });
  expect(restored).toMatchObject({ contact: { nickname: '바뀐 이름' }, messages: [], draft: '', pinnedAt: null, lastReadAt: 0 });
});

test('내 대화 삭제는 다른 계정에 영향을 주지 않고 새 송수신으로 대화를 다시 시작할 수 있다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const dm = await import('/src/state/directMessages.ts');
    const contact = { userId: 'non-friend-contact', nickname: '새 상대', avatarUrl: null };
    dm.receiveDirectMessage('owner-a', contact, 'A 계정 대화');
    dm.receiveDirectMessage('owner-b', contact, 'B 계정 대화');
    dm.toggleDirectMessagePin('owner-b', contact);
    dm.deleteDirectConversation('owner-a', contact);
    const other = dm.readDirectMessages('owner-b').conversations[contact.userId];
    dm.receiveDirectMessage('owner-a', contact, '다시 받은 메시지');
    const received = dm.readDirectMessages('owner-a').conversations[contact.userId];
    const unread = dm.unreadMessages(received, 'owner-a');
    dm.deleteDirectConversation('owner-a', contact);
    dm.sendDirectMessage('owner-a', contact, '다시 보낸 메시지');
    return { other, received, unread, sent: dm.readDirectMessages('owner-a').conversations[contact.userId] };
  });
  expect(result.other.messages.map(message => message.text)).toEqual(['B 계정 대화']);
  expect(result.other.pinnedAt).not.toBeNull();
  expect(result.received.messages.map(message => message.text)).toEqual(['다시 받은 메시지']);
  expect(result.unread).toBe(1);
  expect(result.sent.messages.map(message => message.text)).toEqual(['다시 보낸 메시지']);
  expect(result.sent.pinnedAt).toBeNull();
});

test('대화 삭제 저장이 실패하면 기존 대화와 고정·초안을 유지한다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const dm = await import('/src/state/directMessages.ts');
    const owner = 'storage-failure-owner';
    const contact = { userId: 'contact', nickname: '상대', avatarUrl: null };
    dm.receiveDirectMessage(owner, contact, '보존할 메시지');
    dm.saveDirectMessageDraft(owner, contact, '보존할 초안');
    dm.toggleDirectMessagePin(owner, contact);
    const before = dm.readDirectMessages(owner);
    const persisted = localStorage.getItem(`qm:direct-messages:${owner}`);
    const original = Storage.prototype.setItem;
    let error = '';
    try {
      Storage.prototype.setItem = function (key, value) {
        if (key === `qm:direct-messages:${owner}`) throw new DOMException('저장 공간 부족', 'QuotaExceededError');
        original.call(this, key, value);
      };
      dm.deleteDirectConversation(owner, contact);
    } catch (failure) {
      error = failure instanceof Error ? failure.name : String(failure);
    } finally {
      Storage.prototype.setItem = original;
    }
    return { error, unchanged: dm.readDirectMessages(owner) === before, persisted: localStorage.getItem(`qm:direct-messages:${owner}`) === persisted };
  });
  expect(result).toEqual({ error: 'QuotaExceededError', unchanged: true, persisted: true });
});
