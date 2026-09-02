package com.queuemate.gameconfig.service;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.error.ServiceUnavailableException;
import com.queuemate.common.metrics.QueueMateMetrics;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.gameconfig.infra.GameModeConfigRepository;
import com.queuemate.gameconfig.infra.GameModeConfigRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * game_mode_configs를 읽어 메모리에 들고 있는 카탈로그.
 *
 * <p>표가 source of truth이고 애플리케이션은 스냅샷을 본다. 매 후보마다 표를 읽지 않는
 * 이유는 hard filter가 후보 한 명마다 평가되기 때문이다. 대기열 100명이면 한 번의 매칭
 * 시도가 100번의 왕복이 되고, 매칭 루프의 지연이 DB 부하에 묶인다. 설정은 배포 때만
 * 바뀌는데 그 대가를 후보마다 치를 이유가 없다.
 *
 * <p>스냅샷의 대가는 낡음이다. 갱신 주기 안에서는 옛 정원으로 파티가 만들어질 수 있다.
 * 이 값이 배포 때만 바뀐다는 전제 위에서 받아들인 대가다. 정원을 운영 중에 바꾸면 이미
 * 만들어진 파티와 어긋나므로, 값 변경은 스키마 변경과 같은 무게로 다뤄야 한다.
 *
 * <p>더 위험한 것은 갱신 실패가 조용하다는 점이다. 서버는 계속 돌고 응답도 정상이고
 * 다만 바꾼 설정이 반영되지 않는다. {@code queuemate.gamemode.reload.failed}가
 * 그 침묵을 깨는 유일한 신호다.
 */
@Component
public class GameModeCatalog implements GameModeConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(GameModeCatalog.class);

    private final GameModeConfigRepository rows;
    private final QueueMateMetrics metrics;

    /**
     * 적재에 한 번이라도 성공하기 전에는 null이다.
     *
     * <p>비어 있는 것과 못 읽은 것을 구분하기 위해 빈 맵을 초기값으로 두지 않는다.
     * 둘은 다른 사건이고 운영자가 볼 화면이 다르다. 표가 비었으면 모드가 없다는 뜻이라
     * 매칭 요청이 거절되고, 표를 못 읽었으면 판단할 근거가 없다는 뜻이라 503이다.
     */
    private volatile Map<GameKey, List<GameModeConfig>> snapshot;

    public GameModeCatalog(GameModeConfigRepository rows, QueueMateMetrics metrics) {
        this.rows = rows;
        this.metrics = metrics;
    }

    /**
     * 기동 직후 한 번 적재한다.
     *
     * <p>실패해도 예외를 밖으로 던지지 않는다. 애플리케이션은 뜨고 매칭만 fail-closed 된다.
     * DB가 잠깐 늦게 뜬 것뿐인데 애플리케이션이 죽으면 다음 갱신에서 회복할 기회조차 없다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        reload();
    }

    @Scheduled(fixedDelayString = "${queuemate.gamemode.reload-ms:300000}")
    public void reload() {
        try {
            Map<GameKey, List<GameModeConfig>> loaded = read();
            snapshot = loaded;
            log.debug("모드 설정 적재 완료 games={} modes={}",
                    loaded.size(), loaded.values().stream().mapToInt(List::size).sum());
        } catch (RuntimeException e) {
            // 이전 스냅샷을 버리지 않는다. 낡은 설정이 없는 설정보다 낫다.
            metrics.gameModeReloadFailed();
            log.error("모드 설정 적재 실패. 이전 스냅샷을 유지한다 hasSnapshot={}", snapshot != null, e);
        }
    }

    private Map<GameKey, List<GameModeConfig>> read() {
        Map<GameKey, List<GameModeConfig>> loaded = new EnumMap<>(GameKey.class);
        for (GameModeConfigRow row : rows.findByActiveTrue()) {
            GameKey game = parseGame(row);
            if (game == null) {
                continue;
            }
            loaded.computeIfAbsent(game, key -> new ArrayList<>())
                    .add(new GameModeConfig(game, row.getModeKey(), row.getTargetPartySize(),
                            row.isRoleUniqueness(), true));
        }
        loaded.replaceAll((game, configs) -> List.copyOf(configs));
        return Map.copyOf(loaded);
    }

    /**
     * 모르는 게임 키는 건너뛴다.
     *
     * <p>표에는 CHECK 제약이 있지만 지원 게임이 줄어드는 방향의 변경에서는 어긋날 수 있다.
     * 그 행 하나 때문에 나머지 모드까지 못 쓰게 되는 것이 더 나쁘다.
     */
    private GameKey parseGame(GameModeConfigRow row) {
        try {
            return GameKey.valueOf(row.getGameKey());
        } catch (IllegalArgumentException e) {
            log.warn("모르는 게임 키를 건너뛴다 gameKey={} modeKey={}", row.getGameKey(), row.getModeKey());
            return null;
        }
    }

    @Override
    public Optional<GameModeConfig> findActive(GameKey game, String modeKey) {
        if (game == null || modeKey == null) {
            return Optional.empty();
        }
        String wanted = modeKey.trim();
        return activeModes(game).stream()
                .filter(config -> config.modeKey().equals(wanted))
                .findFirst();
    }

    @Override
    public List<GameModeConfig> activeModes(GameKey game) {
        Map<GameKey, List<GameModeConfig>> current = snapshot;
        if (current == null) {
            throw new ServiceUnavailableException("GAME_MODE_CONFIG_UNAVAILABLE",
                    "모드 설정을 읽지 못했다. 매칭을 받을 수 없다", null);
        }
        return current.getOrDefault(game, List.of());
    }
}
