package com.queuemate.gameconfig.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameModeConfigRepository extends JpaRepository<GameModeConfigRow, UUID> {

    /** 활성 모드만 적재한다. 비활성 모드로는 새 매칭을 받지 않는다. */
    List<GameModeConfigRow> findByActiveTrue();
}
