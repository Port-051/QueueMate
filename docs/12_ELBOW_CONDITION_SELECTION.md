# 12. Elbow-Based Condition Selection

## 목적
조건을 늘리면 만족도는 올라갈 수 있지만 대기열이 잘게 쪼개져 매칭 성립률이 떨어진다.
따라서 **실제로 LFG에서 많이 쓰이는 조건 중, 추가 효용이 급격히 줄어드는 지점까지만** 사용자 입력으로 채택한다.

## 1. 데이터 단위
게임별 LFG/커뮤니티 모집 글을 다음처럼 정규화한다.
```text
post_id
game
mode
condition:role/position
condition:voice
condition:purpose
condition:play_style
condition:character
condition:age
...
```

## 2. 각 조건의 신호
- `Frequency`: 모집 글에서 실제로 명시되는 빈도
- `MismatchImpact`: 인터뷰/불만에서 안 맞았을 때 게임 경험을 망치는 정도
- `FragmentationCost`: 해당 조건을 hard filter로 쓸 때 compatible pool 감소율
- `Availability`: 게임 API/계정으로 자동 파악 가능하면 사용자 입력 우선순위를 낮춤

## 3. Selection procedure
1. 게임별 candidate condition 목록 생성
2. Frequency + MismatchImpact로 우선순위 정렬
3. 상위 k개 조건을 쓸 때 커뮤니티 요구사항 cumulative coverage 계산
4. k를 늘릴 때 coverage 개선폭과 candidate pool 감소폭을 함께 plot
5. **추가 조건의 한계 효용이 급격히 감소하는 knee/elbow 직전**을 채택
6. hard filter가 아니라 soft/profile로 둘 수 있는 항목은 입력 필드에서 제외

## 4. 현재 고정 결과
초기 사용자가 적다는 전제에서 다음까지만 사용한다.

### LoL
1. 게임 모드
2. 희망 포지션
3. 음성 사용
4. 플레이 목적

### VALORANT
1. 게임 모드
2. 선호 역할군
3. 음성 사용
4. 플레이 목적

### PUBG
1. 게임 모드
2. 플레이 스타일
3. 음성 사용
4. 플레이 목적

예약은 모든 게임에:
5. 플레이 가능한 시간
6. 플레이할 양

## 5. Conditions intentionally excluded from filtering
현재는 매칭 필터로 넣지 않는다.
- 특정 챔피언/에이전트
- 세부 전략
- 나이
- 성별
- 성격 유형
- 세부 커뮤니케이션 성향

실제 데이터가 elbow 결과를 바꿀 때만 재논의한다.

## 6. 운영 재평가
실사용 데이터가 쌓이면 조건별로:
- 선택 분포
- exact candidate 수
- condition relaxation 발생률
- 해당 조건 때문에 후보 0이 된 비율
- post-match block/report/friend 지표
를 함께 보고 유지/삭제/soft 전환한다.
