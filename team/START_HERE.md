# START HERE — 3명이 바로 Claude Code 병렬 실행

## 0. 저장소 받기

```bash
git clone https://github.com/Port-051/QueueMate.git
cd QueueMate
git switch main
git pull --ff-only
```

## 1. 작업 브랜치

장기 개인 브랜치 대신 작업마다 최신 `main`에서 짧게 분리한다.

```bash
git switch -c codex/작업명
```

커밋 후 푸시하고 `main` 대상 PR을 만든다. CI와 팀원 확인 후 병합하고 작업 브랜치를 삭제한다. 선택적으로 `git worktree add ../qm-작업명 -b codex/다른작업명 main`을 사용한다.

공유 UI와 검증·DB 전환 절차는 [팀 개발 안내](../docs/18_TEAM_WORKFLOW.md)를 따른다.

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
