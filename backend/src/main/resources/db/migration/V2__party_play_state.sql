-- 파티가 게임에 들어갔는지를 구분하기 위한 두 시각.
--
-- status만으로는 부족하다. 파티가 닫히면 status는 CLOSED가 되어, 게임을 마치고 끝난
-- 파티와 준비 단계에서 깨진 파티를 구별할 수 없다. played_at은 CLOSED 이후에도 남는다.
ALTER TABLE parties ADD COLUMN ready_at  TIMESTAMPTZ;
ALTER TABLE parties ADD COLUMN played_at TIMESTAMPTZ;

-- READY로 넘어온 시각을 모르면 게임 시작 판정 자체를 할 수 없다.
-- 이미 READY인 파티는 생성 시각을 기준으로 둔다.
UPDATE parties SET ready_at = created_at WHERE status = 'READY' AND ready_at IS NULL;

ALTER TABLE parties ADD CONSTRAINT parties_ready_at_check
    CHECK (status <> 'READY' OR ready_at IS NOT NULL);

-- PLAYING인데 played_at이 비어 있으면 게임 여부의 근거가 사라진다.
ALTER TABLE parties ADD CONSTRAINT parties_played_at_check
    CHECK (status <> 'PLAYING' OR played_at IS NOT NULL);

-- 전이 대상만 훑는다. 대부분의 파티는 CLOSED이므로 전체 스캔을 피한다.
CREATE INDEX parties_ready_at_idx  ON parties (ready_at)  WHERE status = 'READY';
CREATE INDEX parties_played_at_idx ON parties (played_at) WHERE status = 'PLAYING';
