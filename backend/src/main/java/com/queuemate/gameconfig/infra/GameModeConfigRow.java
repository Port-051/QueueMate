package com.queuemate.gameconfig.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * game_mode_configs 한 행. 읽기 전용이다.
 *
 * <p>이 표는 운영자가 손이나 migration으로 고치고 애플리케이션은 읽기만 한다.
 * 그래서 setter도 저장 경로도 두지 않는다. 쓸 수 있게 열어 두면 어느 서비스가
 * 정원을 바꾸는 날이 오고, 그때 이미 만들어진 파티와 어긋난다.
 *
 * <p>policy_json은 매핑하지 않는다. 지금 읽는 코드가 없고, 읽지도 않는 열을
 * 매핑하면 그 모양이 계약처럼 굳는다.
 */
@Entity
@Table(name = "game_mode_configs")
public class GameModeConfigRow {

    @Id
    private UUID id;

    @Column(name = "game_key", nullable = false, updatable = false)
    private String gameKey;

    @Column(name = "mode_key", nullable = false, updatable = false)
    private String modeKey;

    @Column(name = "target_party_size", nullable = false, updatable = false)
    private int targetPartySize;

    @Column(name = "role_uniqueness", nullable = false, updatable = false)
    private boolean roleUniqueness;

    @Column(nullable = false, updatable = false)
    private boolean active;

    protected GameModeConfigRow() {
    }

    public UUID getId() {
        return id;
    }

    public String getGameKey() {
        return gameKey;
    }

    public String getModeKey() {
        return modeKey;
    }

    public int getTargetPartySize() {
        return targetPartySize;
    }

    public boolean isRoleUniqueness() {
        return roleUniqueness;
    }

    public boolean isActive() {
        return active;
    }
}
