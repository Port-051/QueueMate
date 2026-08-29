# START HERE — 3명이 바로 Claude Code 병렬 실행

## 0. 저장소 초기화
압축 해제 후:
```bash
git init
git add .
git commit -m "chore: bootstrap QueueMate spec and harness"
```

## 1. Branch / worktree
```bash
git branch feature/frontend
git branch feature/matching-core
git branch feature/party-platform

git worktree add ../qm-frontend feature/frontend
git worktree add ../qm-matching feature/matching-core
git worktree add ../qm-platform feature/party-platform
```

## 2. Claude Code 실행
각 worktree에서 각각:
```bash
claude
```
그리고 아래 프롬프트 파일 전체를 첫 메시지로 준다.

- Member 1: `team/prompts/member-1-frontend.md`
- Member 2: `team/prompts/member-2-matching.md`
- Member 3: `team/prompts/member-3-party-platform.md`

## 3. 첫날 공통 규칙
세 Claude 모두 처음에는 구현보다 다음을 먼저 확인:
- `CLAUDE.md`
- `contracts/openapi.yaml`
- enum/name mismatch
- 각자 owner directory

contract 문제를 발견하면 구현을 진행하면서 제각각 수정하지 말고 contract commit을 먼저 합친다.

## 4. 병렬 진행
Backend가 없어도 Member 1은 mock adapter로 진행.
Party backend가 없어도 Member 2는 Redis/Testcontainers로 matching 진행.
Matching이 없어도 Member 3는 auth/social/WebSocket signaling을 진행.
