import { execFileSync } from 'node:child_process';

/**
 * 방 채팅 채널의 **구독 수**를 로컬 테스트 Redis 에서 읽는다 — `PUBSUB NUMSUB qm:pubsub:chat:{roomId}`(읽기 전용 · 상태를 바꾸지 않는다).
 *
 * chat 은 "이 인스턴스에 그 방의 소켓이 생길 때 `SUBSCRIBE` · 마지막 소켓이 끊기면 `UNSUBSCRIBE`" 한다(`chat/CLAUDE.md` §3.1 · C-4) — 한 인스턴스는 채널마다 구독 하나라
 * 값은 **그 방의 소켓을 가진 chat 인스턴스의 수**다(같은 인스턴스에 소켓이 둘이어도 1). 로컬은 8083(Vite 프록시 `/ws/chat`) 과 8084 두 인스턴스가 같은 Redis 를 본다.
 *
 * `cleanup.ts` 와 같은 길 — `docker.exe exec qm-platform-test-redis redis-cli …`(루트 `START_HERE.md` §6 의 테스트 Redis 6380). 바꿀 수 있는 것 — `E2E_DOCKER` · `E2E_REDIS_CONTAINER`.
 * 못 읽으면(docker 없음 · 컨테이너 없음) 던진다 — 이 값을 보는 스펙은 그 단계가 실패한다(어떤 이유인지 메시지에 적는다).
 */
export function chatSubscriberCount(roomId: string): number {
  const docker = process.env.E2E_DOCKER ?? 'docker.exe';
  const container = process.env.E2E_REDIS_CONTAINER ?? 'qm-platform-test-redis';
  const channel = `qm:pubsub:chat:${roomId}`;
  let out: string;
  try {
    out = execFileSync(docker, ['exec', container, 'redis-cli', 'pubsub', 'numsub', channel], { encoding: 'utf8', timeout: 15_000, stdio: ['ignore', 'pipe', 'pipe'] });
  } catch (error) {
    const cause = error as NodeJS.ErrnoException & { stderr?: string };
    const reason = cause.code === 'ENOENT' ? `${docker} 가 없다` : (cause.stderr || cause.message || String(error)).replace(/\r/g, '').trim().split('\n')[0];
    throw new Error(`Redis 구독 수를 읽지 못했다(${container} · ${channel}) — ${reason}`);
  }
  // redis-cli 는 "채널\n수" 두 줄로 답한다. docker.exe 출력에는 \r 이 섞인다(루트 START_HERE.md §8).
  const lines = out.replace(/\r/g, '').trim().split('\n');
  const count = Number(lines[lines.length - 1]);
  if (!Number.isInteger(count)) throw new Error(`PUBSUB NUMSUB 의 답을 읽지 못했다: ${JSON.stringify(out)}`);
  return count;
}
