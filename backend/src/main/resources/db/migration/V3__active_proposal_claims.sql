-- INV-2를 DB에서도 강제한다.
--
-- 지금까지 "한 사용자는 동시에 하나의 활성 proposal에만 속한다"를 지키는 것은
-- Redis atomic claim 하나뿐이었다 (V1 주석 참고). Redis 복제는 비동기이므로
-- failover 순간 아직 복제되지 않은 claim은 사라지고, 새 master는 그 사용자가
-- 비어 있다고 답한다. 그 순간 같은 사람이 두 proposal에 묶인다.
--
-- user_id를 PK로 두면 그 조합이 애초에 저장될 수 없다. Redis가 무엇을 잃든
-- 두 번째 claim은 여기서 막힌다.
--
-- proposal status가 다른 테이블에 있어 partial unique index로는 표현할 수 없었던 것을,
-- "활성인 동안에만 존재하는 행"으로 바꿔 표현한다. 제안이 끝나면 행을 지운다.

CREATE TABLE active_proposal_claims (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    proposal_id UUID        NOT NULL REFERENCES match_proposals (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 제안이 끝날 때 그 제안의 claim을 한 번에 지운다.
CREATE INDEX active_proposal_claims_proposal_idx ON active_proposal_claims (proposal_id);

-- 제안 행은 사라졌는데 claim만 남는 경우를 주기적으로 걷어 내기 위한 색인이다.
CREATE INDEX active_proposal_claims_expires_idx ON active_proposal_claims (expires_at);
