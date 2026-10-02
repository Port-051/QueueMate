import { execFileSync } from 'node:child_process';
import { NICKNAMES } from './fixtures';

/**
 * e2e 가 끝나면(Playwright `globalTeardown` — `playwright.config.ts`) **e2e 사람(`e2e-a` · `e2e-b` · `e2e-c`)이 방장인 끝난 글**(`status <> 'RECRUITING'` — 만료 · 확정)을 지운다
 * (2026-09-30 소유자 지시 — "지금 지우고, 테스트가 끝날 때마다 지우게").
 *
 * - **글을 지우는 API 는 없다**(`DELETE /posts/{postId}` 는 만료로 바꿀 뿐이고, 끝난 글도 목록에 계속 남는다 — P-20). 그래서 **로컬 테스트 DB 에 직접** 지운다 —
 *   `docker.exe exec qm-platform-test-pg psql …`(루트 `START_HERE.md` §6 의 테스트 PostgreSQL 5433). **로컬의 이 스위트에서만 쓰는 정리다** — 앱 · 백엔드의 동작이 아니다.
 * - 글을 지우면 FK 로 그 글의 파티 · 파티원 기록(`parties` · `party_members`)과 찾는 포지션 줄이 같이 지워지고, 최근 함께한 사람의 `last_party_id` 는 비워진다(V1 — `ON DELETE CASCADE` · `SET NULL`).
 *   e2e 사람끼리의 최근 함께한 사람 줄 자체는 남는다. **모집 중인 글 · 남의 글 · `dev-tester` 의 것은 건드리지 않는다.**
 * - 바꿀 수 있는 것(환경변수) — `E2E_DOCKER`(기본 `docker.exe`) · `E2E_PG_CONTAINER`(기본 `qm-platform-test-pg`) · `E2E_PG_DB`(기본 `queuemate`) · `E2E_PG_USER`(기본 `postgres`).
 *   `E2E_KEEP_POSTS=1` 이면 지우지 않는다(실패한 뒤 글을 들여다볼 때).
 * - **정리가 안 돼도 실행을 실패시키지 않는다** — `docker.exe` 가 없거나 컨테이너가 안 떠 있으면 경고 한 줄을 남기고 넘어간다.
 */
export function deleteEndedE2ePosts(label = '정리'): void {
  if (process.env.E2E_KEEP_POSTS === '1') {
    console.log(`[e2e ${label}] E2E_KEEP_POSTS=1 — 끝난 글을 지우지 않는다`);
    return;
  }
  const docker = process.env.E2E_DOCKER ?? 'docker.exe';
  const container = process.env.E2E_PG_CONTAINER ?? 'qm-platform-test-pg';
  const database = process.env.E2E_PG_DB ?? 'queuemate';
  const user = process.env.E2E_PG_USER ?? 'postgres';
  const nicknames = Object.values(NICKNAMES).map((name) => `'${name.replace(/'/g, "''")}'`).join(', ');
  const sql = `DELETE FROM recruit_posts p USING users u WHERE p.host_id = u.id AND u.nickname IN (${nicknames}) AND p.status <> 'RECRUITING'`;
  try {
    const out = execFileSync(docker, ['exec', container, 'psql', '-U', user, '-d', database, '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql], {
      encoding: 'utf8', timeout: 30_000, stdio: ['ignore', 'pipe', 'pipe'],
    });
    // docker.exe 의 출력에는 \r 이 섞인다(루트 START_HERE.md §8).
    const count = /DELETE (\d+)/.exec(out.replace(/\r/g, ''))?.[1] ?? '?';
    console.log(`[e2e ${label}] ${Object.values(NICKNAMES).join(' · ')} 의 끝난 글 ${count}개를 지웠다(${container} · ${database})`);
  } catch (error) {
    const cause = error as NodeJS.ErrnoException & { stderr?: string };
    const reason = cause.code === 'ENOENT' ? `${docker} 가 없다` : (cause.stderr || cause.message || String(error)).replace(/\r/g, '').trim().split('\n')[0];
    console.warn(`[e2e ${label}] 끝난 글을 지우지 못해 건너뛴다 — ${reason}`);
  }
}

/** Playwright `globalTeardown` — 스위트가 다 돈 뒤 한 번(성공 · 실패와 상관없이). */
export default async function globalTeardown(): Promise<void> {
  deleteEndedE2ePosts('정리');
}
