# 02. Match Condition Schema

## 1. Design rule
게임별 조건을 하나의 거대한 공통 폼으로 만들지 않는다. 공통 골격 + 게임별 핵심 조건 하나를 사용한다.

## 2. Common fields
```text
game              required
modeKey            required
voicePreference    required
playPurpose        required
```

### VoicePreference
- `REQUIRED`: 음성을 사용할 수 있는 팀원만
- `OPTIONAL`: 음성 여부 무관
- `NO_VOICE`: 음성 사용을 원하지 않음

Compatibility:
- REQUIRED ↔ NO_VOICE = incompatible
- 그 외 = compatible

### PlayPurpose
- `RANK_UP`
- `NORMAL`
- `FUN`

playPurpose는 soft condition이다. 일치 후보를 우선하지만 부족하면 완화할 수 있다.

## 3. LoL
```text
keyCondition.type  = POSITION
keyCondition.value = TOP | JUNGLE | MID | ADC | SUPPORT
```

Rules:
- 게임/모드/rank eligibility/block = hard
- 같은 파티 내 동일 `POSITION` 충돌은 hard reject (해당 mode가 role uniqueness를 요구할 때)
- Position은 사용자가 명시적으로 ANY를 선택하지 않는 한 자동 완화하지 않는다.

초기 mode config 예:
- `SOLO_DUO_RANKED`: targetPartySize=2, positionUniqueness=true
- 다른 mode는 config에서 관리하고 UI 노출 여부도 config로 결정

## 4. VALORANT
```text
keyCondition.type  = ROLE
keyCondition.value = DUELIST | INITIATOR | CONTROLLER | SENTINEL
```

Rules:
- game/mode/rank eligibility/block = hard
- ROLE 중복은 게임 자체에서 불가능한 것이 아니므로 hard reject하지 않는다.
- 서로 다른 ROLE 구성은 더 높은 compatibility tier를 가진다.

초기 mode config:
- `COMPETITIVE`
- `UNRATED`

## 5. PUBG
```text
keyCondition.type  = PLAY_STYLE
keyCondition.value = AGGRESSIVE | BALANCED | SURVIVAL
```

Rules:
- game/mode/platform-or-region rule/block = hard when applicable
- PLAY_STYLE는 soft. exact match 우선.

초기 mode config:
- `DUO`
- `SQUAD`

TPP/FPP 등 세부 mode는 문자열 config로 확장하고 코드 enum을 불필요하게 늘리지 않는다.

## 6. Derived conditions
사용자 폼에서 직접 받지 않고 시스템이 가져오거나 계산:
- linked game account
- rank / eligibility
- region when game account/provider가 제공
- party target size
- block relation
- active request conflict

## 7. Reservation-only additions
```text
availableFrom      ZonedDateTime, 30분 경계
availableTo        ZonedDateTime, 30분 경계
playAmount         ONE_GAME | TWO_PLUS
```

기존 실시간 조건을 복사하고 시간 조건만 추가한다.

## 8. No-condition-creep rule
새 필드를 UI에 추가하려면:
1. 커뮤니티/LFG 데이터에서 반복 사용 확인
2. 실제 mismatch 영향 확인
3. candidate pool fragmentation 측정
4. `docs/12_ELBOW_CONDITION_SELECTION.md`의 elbow 기준 통과

그 전에는 profile 참고 정보로만 둘 수 있다.
