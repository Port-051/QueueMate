package com.queuemate.api;

import com.queuemate.matching.app.MatchTrigger;
import com.queuemate.matching.recruitment.BoardStore;
import com.queuemate.user.domain.User;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Explicitly invoked experiment; not part of the default backend test source set. */
class MatchingContentionStudy extends ApiContractTestSupport {
    @Autowired DataSource dataSource;
    @Autowired MatchTrigger trigger;
    @MockitoSpyBean BoardStore store;

    @DynamicPropertySource
    static void studyProperties(DynamicPropertyRegistry registry) {
        registry.add("queuemate.matching.auto-trigger", () -> true);
        registry.add("queuemate.proposal.ttl-seconds", () -> 600);
        registry.add("queuemate.auth.access-token-ttl-seconds", () -> 3600);
        registry.add("spring.lifecycle.timeout-per-shutdown-phase", () -> "1s");
    }

    record Actor(UUID id, String token) {}
    record Response(int status, double ms, String body) {}
    final AtomicInteger sequence = new AtomicInteger();
    final AtomicBoolean pauseNextMatcher = new AtomicBoolean();
    final CountDownLatch matcherPaused = new CountDownLatch(1), releaseMatcher = new CountDownLatch(1);
    volatile Samples current;
    final List<Map<String, Object>> results = new ArrayList<>();
    final Path output = Path.of(System.getProperty("study.output"));

    @Test void compareModeContention() throws Exception {
        Files.createDirectories(output);
        writeResults();
        assertEquals(true, ReflectionTestUtils.getField(trigger, "enabled"));
        Files.writeString(output.resolve("environment.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "javaVersion", System.getProperty("java.version"),
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "hikariMaximumPoolSize", dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize(),
                "matcherThreads", workers().getCorePoolSize(),
                "monitorDelayMs", 10,
                "postgresImage", "postgres:16-alpine", "redisImage", "redis:7-alpine")));
        BoardStore instrumented = AopTestUtils.getUltimateTargetObject(store);
        assertTrue(org.mockito.Mockito.mockingDetails(instrumented).isSpy());
        doAnswer(call -> {
            long start = System.nanoTime();
            try {
                Object answer = call.callRealMethod();
                if (Thread.currentThread().getName().startsWith("match-trigger-")
                        && pauseNextMatcher.compareAndSet(true, false)) {
                    matcherPaused.countDown();
                    if (!releaseMatcher.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Study gate timed out");
                }
                return answer;
            } finally {
                Samples samples = current;
                if (samples != null) {
                    var durations = Thread.currentThread().getName().startsWith("match-trigger-")
                            ? samples.matcherLockCallMs : samples.registrationLockCallMs;
                    durations.add(msSince(start));
                }
            }
        }).when(instrumented).lock(any(), anyString());

        // Warm both paths; keep all warmup samples out of the report.
        burst("warmup-one", 20, 1, 10, false);
        burst("warmup-two", 20, 2, 10, false);
        for (int round = 1; round <= 3; round++) {
            int[] order = round % 2 == 1 ? new int[]{1, 2} : new int[]{2, 1};
            for (int modes : order) burst("round-" + round, 100, modes, 32, true);
        }
        proveRegistrationWaitsForMatcher();
        writeResults();
    }

    Actor actor() {
        int n = sequence.incrementAndGet();
        UUID id = users.save(User.create("study" + n + "@queuemate.test", "unused", "study" + n)).getId();
        return new Actor(id, tokenService.issueAccessToken(id));
    }

    Response register(Actor actor, String mode, boolean automatic) {
        String payload = """
                {"type":"REALTIME","condition":{"game":"VALORANT","modeKey":"%s",
                "keyCondition":{"type":"ROLE","value":"DUELIST"},"voicePreference":"OPTIONAL",
                "playPurpose":"RANK_UP"},"preferences":{"desiredKeys":[],"purposeRequired":false},
                "description":"contention study","autoMatch":%s}
                """.formatted(mode, automatic);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.token()); headers.setContentType(MediaType.APPLICATION_JSON);
        long start = System.nanoTime();
        ResponseEntity<String> response = http.exchange("/api/v1/recruitments", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        return new Response(response.getStatusCode().value(), msSince(start), response.getBody());
    }

    ThreadPoolExecutor workers() {
        return (ThreadPoolExecutor) ReflectionTestUtils.getField(trigger, "workers");
    }

    void awaitIdle() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (workers().getActiveCount() != 0 || !workers().getQueue().isEmpty()) {
            if (System.nanoTime() > deadline) fail("Matcher did not drain");
            Thread.sleep(10);
        }
    }

    void clear() throws InterruptedException {
        awaitIdle();
        jdbc.sql("TRUNCATE match_proposals, users CASCADE").update();
        redis.execute((RedisCallback<Void>) connection -> { connection.serverCommands().flushDb(); return null; });
    }

    void burst(String label, int count, int modeCount, int concurrency, boolean report) throws Exception {
        clear();
        List<Actor> actors = new ArrayList<>();
        for (int i = 0; i < count; i++) actors.add(actor());
        Samples samples = new Samples();
        current = samples;
        long start = System.nanoTime();
        List<Response> responses = new ArrayList<>();
        try (Monitor monitor = new Monitor(samples); ExecutorService clients = Executors.newFixedThreadPool(concurrency)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Actor actor = actors.get(i);
                String mode = modeCount == 1 || i % 2 == 0 ? "COMPETITIVE" : "UNRATED";
                futures.add(clients.submit(() -> {
                    gate.await();
                    samples.sentAt.put(actor.id(), System.nanoTime());
                    return register(actor, mode, true);
                }));
            }
            gate.countDown();
            for (Future<Response> future : futures) responses.add(future.get(30, TimeUnit.SECONDS));
            awaitIdle();
            monitor.sample();
        } finally { current = null; }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("round", label); row.put("users", count); row.put("modes", modeCount);
        row.put("clientConcurrency", concurrency); row.put("elapsedMs", msSince(start));
        row.put("http201", responses.stream().filter(r -> r.status() == 201).count());
        row.put("errors", responses.stream().filter(r -> r.status() != 201).toList());
        row.put("registration", percentiles(responses.stream().map(Response::ms).toList()));
        row.put("proposalObservedAfterRequestStart", percentiles(samples.proposalMs.values()));
        row.put("registrationLockCall", percentiles(samples.registrationLockCallMs));
        row.put("matcherLockCall", percentiles(samples.matcherLockCallMs));
        row.put("pgAdvisoryWaitersMax", samples.advisoryMax.get());
        row.put("pgAdvisoryWaiterSamples", samples.advisorySum.get());
        row.put("monitorSamples", samples.sampleCount.get());
        row.put("hikariPendingMax", samples.hikariPendingMax.get());
        row.put("proposedUsers", number("select count(*) from match_requests where status='PROPOSED'"));
        row.put("queuedUsers", number("select count(*) from match_requests where status='QUEUED'"));
        row.put("proposals", number("select count(*) from match_proposals"));
        row.put("duplicateProposalUsers", number("select count(*) from (select user_id from proposal_members group by user_id having count(*)>1) x"));
        row.put("wrongPartySize", number("select count(*) from (select proposal_id from proposal_members group by proposal_id having count(*)<>5) x"));
        if (report) { results.add(row); writeResults(); }
        System.out.println("STUDY " + JSON.writeValueAsString(row));
        assertEquals(count, ((Number) row.get("http201")).intValue(), responses.toString());
        assertEquals(0, row.get("duplicateProposalUsers"));
        assertEquals(0, row.get("wrongPartySize"));
        assertEquals(count, samples.registrationLockCallMs.size(), "Every registration must be instrumented");
        assertFalse(samples.matcherLockCallMs.isEmpty(), "Matcher instrumentation must be active");
        assertEquals(((Number) row.get("proposedUsers")).intValue() / 5, row.get("proposals"));
        // Report remaining users instead of assuming the event path always drains all possible matches.
    }

    void proveRegistrationWaitsForMatcher() throws Exception {
        clear();
        Actor seed = actor(), same = actor(), other = actor();
        pauseNextMatcher.set(true);
        assertEquals(201, register(seed, "COMPETITIVE", true).status());
        assertTrue(matcherPaused.await(5, TimeUnit.SECONDS));
        Map<String, Object> row = new LinkedHashMap<>();
        try (ExecutorService clients = Executors.newFixedThreadPool(2); Connection observer = observer()) {
            Future<Response> blocked = clients.submit(() -> register(same, "COMPETITIVE", false));
            Future<Response> independent = clients.submit(() -> register(other, "UNRATED", false));
            Response otherResponse = independent.get(5, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            int waiters = 0;
            while (waiters == 0 && System.nanoTime() < deadline) {
                waiters = advisoryWaiters(observer);
                if (waiters == 0) Thread.sleep(10);
            }
            row.put("experiment", "matcher-paused-after-acquiring-mode-lock");
            row.put("otherModeHttpStatus", otherResponse.status());
            row.put("otherModeRegistrationMs", otherResponse.ms());
            row.put("sameModeFinishedBeforeRelease", blocked.isDone());
            row.put("advisoryWaitersBeforeRelease", waiters);
            releaseMatcher.countDown();
            Response sameResponse = blocked.get(5, TimeUnit.SECONDS);
            row.put("sameModeHttpStatus", sameResponse.status());
            row.put("sameModeRegistrationMs", sameResponse.ms());
            results.add(row); writeResults();
            assertEquals(201, otherResponse.status()); assertEquals(201, sameResponse.status());
            assertEquals(false, row.get("sameModeFinishedBeforeRelease"));
            assertTrue(waiters > 0);
        } finally { releaseMatcher.countDown(); }
        awaitIdle();
    }

    int number(String sql) { return jdbc.sql(sql).query(Integer.class).single(); }
    static double msSince(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    static Map<String, Object> percentiles(Collection<Double> values) {
        var sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) return Map.of("count", 0);
        return Map.of("count", sorted.size(), "p50Ms", percentile(sorted, .5),
                "p95Ms", percentile(sorted, .95), "maxMs", sorted.getLast());
    }
    static double percentile(List<Double> sorted, double p) { return sorted.get(Math.max(0, (int)Math.ceil(p * sorted.size()) - 1)); }
    void writeResults() throws Exception { Files.writeString(output.resolve("results.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(results)); }
    Connection observer() throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        return DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
    }
    static int advisoryWaiters(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "select count(*) from pg_locks where locktype='advisory' and not granted")) {
            rs.next(); return rs.getInt(1);
        }
    }
    static class Samples {
        final Map<UUID, Long> sentAt = new ConcurrentHashMap<>();
        final Map<UUID, Double> proposalMs = new ConcurrentHashMap<>();
        final Queue<Double> registrationLockCallMs = new ConcurrentLinkedQueue<>(), matcherLockCallMs = new ConcurrentLinkedQueue<>();
        final AtomicInteger advisoryMax = new AtomicInteger(), advisorySum = new AtomicInteger(), sampleCount = new AtomicInteger(), hikariPendingMax = new AtomicInteger();
    }
    class Monitor implements AutoCloseable {
        final Samples samples;
        final Connection connection;
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final AtomicReference<Exception> failure = new AtomicReference<>();
        Monitor(Samples samples) throws SQLException {
            this.samples = samples; connection = observer();
            executor.scheduleWithFixedDelay(() -> { try { sample(); } catch (Exception e) { failure.set(e); } }, 0, 10, TimeUnit.MILLISECONDS);
        }
        synchronized void sample() throws SQLException {
            int waiting = advisoryWaiters(connection);
            samples.advisoryMax.accumulateAndGet(waiting, Math::max);
            samples.advisorySum.addAndGet(waiting); samples.sampleCount.incrementAndGet();
            samples.hikariPendingMax.accumulateAndGet(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("select user_id from match_requests where status='PROPOSED'")) {
                while (rs.next()) {
                    UUID id = rs.getObject(1, UUID.class); Long start = samples.sentAt.get(id);
                    if (start != null) samples.proposalMs.putIfAbsent(id, msSince(start));
                }
            }
        }
        public void close() throws Exception {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            connection.close();
            if (failure.get() != null) throw failure.get();
        }
    }
}
