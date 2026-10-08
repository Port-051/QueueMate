package com.queuemate.platform.account.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * LoL 챔피언의 <b>숫자 번호 → 이름</b>표 — {@code champion-mastery-v4} 의 응답에는 숫자 {@code championId} 만 있고 이름이 없어서 둔다
 * (2026-09-30 — 모스트 챔피언을 숙련도 상위 셋으로 바꾸며. {@code contracts/platform-api.md} P-39).
 *
 * <p><b>원본은 Riot 의 Data Dragon 이고 그것을 파일로 떠 두었다</b>({@code resources/lol/champions.json} — {@code en_US/champion.json} 의
 * {@code key} → {@code id}). 버전은 프런트의 챔피언 목록({@code frontend/src/domain/championCatalog.ts})과 같은 {@code 16.18.1} 이다 —
 * 이름은 그 목록의 첫 칸(Data Dragon ID)과 같은 글자라 프런트가 한글 이름 · 그림을 그대로 찾는다.
 * <b>Data Dragon 을 실행 중에 부르지 않는다</b> — Riot API 한도와 무관한 CDN 이지만 부를 곳 · 캐시를 새로 두는 것보다 표 하나가 싸다(Claude 가 정한 세부).
 *
 * <p><b>표에 없는 번호</b>(이 파일 뒤에 나온 새 챔피언)는 {@code null} 이다 — 부르는 쪽({@link LolStatsProvider})이 최근 경기의 {@code championName} 으로 메우고,
 * 거기에도 없으면 번호를 글자로 쓴다. 새 챔피언이 나오면 이 파일을 새 버전으로 다시 뜬다(프런트의 목록과 같이).
 *
 * <p>프로세스 안에 들고 있는 것은 <b>배포에 묶인 상수</b>다 — 사용자의 행동으로 바뀌는 상태가 아니라 stateless 규칙({@code CLAUDE.md} §5)과 부딪히지 않는다.
 */
@Slf4j
@Component
public class LolChampionNames {

    /** 표의 자리(classpath) */
    static final String RESOURCE = "lol/champions.json";

    private final Map<Long, String> byKey;

    public LolChampionNames(ObjectMapper objectMapper)
    {
        JsonNode root;
        try(InputStream in = new ClassPathResource(RESOURCE).getInputStream())
        {
            root = objectMapper.readTree(in);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("LoL 챔피언 이름표를 읽지 못했다 " + RESOURCE, e);
        }
        Map<Long, String> names = new HashMap<>();
        for(Map.Entry<String, JsonNode> entry : root.path("champions").properties())
        {
            names.put(Long.parseLong(entry.getKey()), entry.getValue().stringValue());
        }
        if(names.isEmpty())
        {
            throw new IllegalStateException("LoL 챔피언 이름표가 비어 있다 " + RESOURCE);
        }
        this.byKey = Map.copyOf(names);
        log.info("LoL 챔피언 이름표 — Data Dragon {} · {}개", root.path("version").stringValue(), byKey.size());
    }

    /** 그 번호의 이름({@code 145} → {@code "Kaisa"}). 표에 없으면 {@code null} */
    String name(long championKey)
    {
        return byKey.get(championKey);
    }

    /** 표의 크기 — 테스트가 본다 */
    int size()
    {
        return byKey.size();
    }
}
