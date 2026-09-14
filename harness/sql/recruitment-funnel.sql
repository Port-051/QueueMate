-- 실제 게임 시작이나 재게시 간격을 대기시간으로 사용하지 않는다.
-- 조회 1: 모집을 시작한 뒤 10분 안에 전원 준비를 마친 비율.
-- 아직 10분이 지나지 않은 모집은 분모에서 제외한다. bump는 새 모집이 아니다.
WITH first_ready AS (
    SELECT recruitment_id, min(occurred_at) AS ready_at
    FROM recruitment_events WHERE event = 'READY' GROUP BY recruitment_id
), cohort AS (
    SELECT r.id, r.created_at, f.ready_at
    FROM recruitments r LEFT JOIN first_ready f ON f.recruitment_id = r.id
    WHERE r.type = 'REALTIME' AND r.created_at <= now() - interval '10 minutes'
)
SELECT count(*) AS mature_recruitments,
       count(*) FILTER (WHERE ready_at <= created_at + interval '10 minutes') AS ready_in_10m,
       round(100.0 * count(*) FILTER (WHERE ready_at <= created_at + interval '10 minutes')
             / nullif(count(*), 0), 2) AS ready_in_10m_percent
FROM cohort;

-- 조회 2: 단계별로 한 모집을 한 번만 센다. SUGGESTED는 제안 계산/제공이며
-- 화면 열람이나 사용자의 동의 횟수는 아니다. CONDITIONS_CHANGED는 수동 수정도 포함한다.
SELECT r.type, e.event, count(DISTINCT e.recruitment_id) AS unique_recruitments
FROM recruitment_events e JOIN recruitments r ON r.id = e.recruitment_id
GROUP BY r.type, e.event ORDER BY r.type, e.event;

-- 조회 3: 약속이 확정된 예약의 최초 준비 완료 시각을 약속 시각과 비교한다.
-- 예약 전체의 성공률이나 약속 시각의 접속률로 해석하지 않는다.
SELECT r.id, p.scheduled_start,
       min(e.occurred_at) AS first_ready_at,
       extract(epoch FROM (min(e.occurred_at) - p.scheduled_start)) / 60 AS ready_minutes_from_appointment
FROM recruitments r JOIN reservations s ON s.id = r.id
JOIN parties p ON p.proposal_id = s.proposal_id
LEFT JOIN recruitment_events e ON e.recruitment_id = r.id AND e.event = 'READY'
WHERE r.type = 'RESERVATION'
GROUP BY r.id, p.scheduled_start ORDER BY p.scheduled_start DESC;
