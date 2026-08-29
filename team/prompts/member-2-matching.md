# Claude Code Prompt — Member 2 Matching Core

당신은 QueueMate Member 2의 Claude Code 세션이다.

읽기:
- `CLAUDE.md`
- `docs/02_MATCH_CONDITION_SCHEMA.md`
- `docs/03_MATCHING_ENGINE_SPEC.md`
- `docs/04_RESERVATION_MATCHING_SPEC.md`
- `docs/07_REDIS_DESIGN.md`
- `docs/08_HARNESS.md`
- `contracts/openapi.yaml`

소유:
- backend matching/reservation/gameconfig packages
- Redis Lua
- matching fixtures/k6

## 목표
Redis 기반 realtime + reservation matching을 invariant-safe하게 구현한다.

## 구현 순서
1. domain enums/value objects/state machines
2. GameModeConfig loader + seed configs for 3 games
3. MatchCondition compatibility functions
4. Redis key repository
5. one active request guard
6. queue ZSET
7. atomic proposal claim Lua + integration test
8. proposal lifecycle + TTL cleanup
9. all-accept confirm transaction + Party creation boundary interface
10. reservation DB model/repository
11. 30-min slot index
12. reservation candidate overlap
13. reservation proposal lifecycle
14. recovery/admin queue rebuild
15. all mandatory race tests

## Boundaries
Party implementation은 Member 3 소유다. `PartyCreationPort` interface를 정의하고 adapter는 Member 3가 제공하게 하라.
Auth는 `CurrentUserProvider` interface만 의존한다.

## Test first targets
- duplicate match request
- same user two proposal claims
- LoL same-position rejection
- voice REQUIRED/NO_VOICE conflict
- VALORANT same role allowed
- reservation overlap race
- accept vs expire race

Redis 장애에서 DB fallback matching을 만들지 마라. fail-closed.
