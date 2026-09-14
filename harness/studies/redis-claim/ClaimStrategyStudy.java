package com.queuemate.study;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.DefaultClientResources;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit study, excluded from normal application sources and tests. */
class ClaimStrategyStudy {
    static final ObjectMapper JSON = new ObjectMapper();
    static final int WORKERS = 4;
    static final Path OUTPUT = Path.of(System.getProperty("study.output"));
    final List<Map<String, Object>> results = new ArrayList<>();

    @Test
    void compareProductionLuaWithJavaUnderRedisLocks() throws Exception {
        Files.createDirectories(OUTPUT);
        var resources = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        try (var container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
            container.start();
            String uri = "redis://" + container.getHost() + ":" + container.getMappedPort(6379);
            var client = RedisClient.create(resources, uri);
            client.setDefaultTimeout(Duration.ofSeconds(10));
            try (var control = client.connect()) {
                var redis = control.sync();
                redis.configSet("slowlog-log-slower-than", "1000");
                redis.configSet("slowlog-max-len", "1024");
                write("environment.json", Map.of(
                        "java", System.getProperty("java.version"), "os", System.getProperty("os.name"),
                        "arch", System.getProperty("os.arch"), "workers", WORKERS,
                        "threadsPerWorker", ClaimStudyWorker.THREADS,
                        "redisInfo", redis.info("server"),
                        "scope", "claim only; four JVMs, one local standalone Redis; no DB/HTTP/Sentinel",
                        "claimTtlSeconds", ClaimAlgorithms.CLAIM_SECONDS, "lockLeaseMs", 10_000));
                proveFailureCases(client);
                List<Worker> workers = new ArrayList<>();
                try {
                    for (int i = 0; i < WORKERS; i++) workers.add(new Worker(uri, i));
                    for (String strategy : List.of("lua", "lock")) {
                        run(client, control, container, workers,
                                new ClaimStudyWorker.Run("warm-" + strategy, strategy, false, 5, 500), false);
                    }
                    for (int round = 1; round <= 3; round++) {
                        for (int partySize : new int[]{2, 5}) {
                            for (boolean contention : new boolean[]{false, true}) {
                                for (String strategy : round % 2 == 1 ? List.of("lua", "lock") : List.of("lock", "lua")) {
                                    String id = "r" + round + "-n" + partySize + "-" + contention + "-" + strategy;
                                    run(client, control, container, workers,
                                            new ClaimStudyWorker.Run(id, strategy, contention, partySize, 1000), true);
                                }
                            }
                        }
                    }
                } finally {
                    for (Worker worker : workers) worker.close();
                }
            } finally { client.shutdown(); }
        } finally { resources.shutdown().get(10, TimeUnit.SECONDS); }
    }

    void run(RedisClient client, StatefulRedisConnection<String, String> control,
             GenericContainer<?> container, List<Worker> workers, ClaimStudyWorker.Run run, boolean record) throws Exception {
        var redis = control.sync();
        redis.flushdb();
        seed(control, run);
        redis.configResetstat();
        redis.slowlogReset();
        Map<String, Double> cpuBefore = numbers(redis.info("cpu"));
        List<ClaimStudyWorker.Result> responses = new ArrayList<>();
        double elapsed;
        Map<String, Double> cpuAfter;
        String stats;
        List<Double> pingSamples;
        try (PingProbe probe = new PingProbe(client)) {
            long start = System.nanoTime();
            for (Worker worker : workers) worker.send(run);
            for (Worker worker : workers) responses.add(worker.receive());
            elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
            probe.stop();
            cpuAfter = numbers(redis.info("cpu"));
            stats = redis.info("commandstats");
            pingSamples = List.copyOf(probe.samples);
        }
        var slowlog = container.execInContainer("redis-cli", "--json", "SLOWLOG", "GET", "1024");
        assertEquals(0, slowlog.getExitCode(), slowlog.getStderr());
        var rows = JSON.readTree(slowlog.getStdout());
        List<Long> slowEvalMicros = new ArrayList<>();
        for (var entry : rows) {
            if (entry.get(3).get(0).asText().equalsIgnoreCase("evalsha")) slowEvalMicros.add(entry.get(2).asLong());
        }
        List<ClaimStudyWorker.Sample> samples = responses.stream().flatMap(r -> r.samples().stream()).toList();
        long successes = samples.stream().filter(ClaimStudyWorker.Sample::success).count();
        int expected = run.operations() * (run.contended() ? 1 : WORKERS);
        assertEquals(expected, successes, "Exactly one winner per shared participant, or all disjoint attempts");
        verifyState(control, run, responses);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("run", run);
        row.put("elapsedSeconds", elapsed);
        row.put("attempts", samples.size()); row.put("successes", successes);
        row.put("conflicts", samples.size() - successes); row.put("errors", 0);
        row.put("attemptsPerSecond", samples.size() / elapsed);
        row.put("successesPerSecond", successes / elapsed);
        row.put("allLatencyMs", percentiles(samples.stream().map(ClaimStudyWorker.Sample::millis).toList()));
        row.put("successLatencyMs", percentiles(samples.stream().filter(ClaimStudyWorker.Sample::success).map(ClaimStudyWorker.Sample::millis).toList()));
        row.put("conflictLatencyMs", percentiles(samples.stream().filter(s -> !s.success()).map(ClaimStudyWorker.Sample::millis).toList()));
        row.put("unrelatedPingMs", percentiles(pingSamples));
        double cpuSeconds = cpuAfter.get("used_cpu_sys") + cpuAfter.get("used_cpu_user")
                - cpuBefore.get("used_cpu_sys") - cpuBefore.get("used_cpu_user");
        row.put("redisCpuSeconds", cpuSeconds);
        row.put("redisCpuMicrosPerAttempt", cpuSeconds * 1_000_000 / samples.size());
        row.put("redisCpuPercentOfOneCore", cpuSeconds * 100 / elapsed);
        row.put("commandstats", stats);
        row.put("slowEvalOver1msCount", slowEvalMicros.size());
        row.put("slowEvalMaxMicros", slowEvalMicros.stream().mapToLong(Long::longValue).max().orElse(0));
        row.put("verified", "winner ownership, exact member sets, loser no writes, queue removal, positive TTL, no remaining mutexes");
        if (record) {
            results.add(row); write("results.json", results);
            write(run.id() + "-samples.json", responses);
        }
        System.out.println("CLAIM_STUDY " + run.id() + " ops/s=" + row.get("attemptsPerSecond")
                + " p99=" + row.get("allLatencyMs") + " ping=" + row.get("unrelatedPingMs"));
    }

    void seed(StatefulRedisConnection<String, String> connection, ClaimStudyWorker.Run run) throws Exception {
        Set<String> seen = new HashSet<>();
        List<RedisFuture<?>> futures = new ArrayList<>();
        connection.setAutoFlushCommands(false);
        try {
            var async = connection.async();
            for (int worker = 0; worker < WORKERS; worker++) {
                for (int index = 0; index < run.operations(); index++) {
                    for (var m : ClaimAlgorithms.attempt(run.id(), run.contended(), worker, index, run.partySize()).members()) {
                        if (seen.add(m.user())) {
                            futures.add(async.set(m.guard(), m.request()));
                            futures.add(async.zadd(m.bucket(), index, m.request()));
                        }
                    }
                }
            }
            connection.flushCommands();
        } finally { connection.setAutoFlushCommands(true); }
        for (var future : futures) future.get(15, TimeUnit.SECONDS);
    }

    void verifyState(StatefulRedisConnection<String, String> connection, ClaimStudyWorker.Run run,
                     List<ClaimStudyWorker.Result> results) throws Exception {
        Map<String, String> owners = new HashMap<>();
        for (var result : results) for (var sample : result.samples()) {
            if (sample.success()) {
                var attempt = ClaimAlgorithms.attempt(run.id(), run.contended(), result.worker(), sample.index(), run.partySize());
                for (var m : attempt.members()) assertNull(owners.put(m.user(), attempt.proposal()), "Duplicate winner");
            }
        }
        // Verification is outside the timed interval. Batch it so fixture TTL is not consumed by
        // tens of thousands of sequential client/server round trips.
        List<Check> checks = new ArrayList<>();
        connection.setAutoFlushCommands(false);
        try {
            var redis = connection.async();
            for (var result : results) for (var sample : result.samples()) {
                var attempt = ClaimAlgorithms.attempt(run.id(), run.contended(), result.worker(), sample.index(), run.partySize());
                Set<String> expected = sample.success()
                        ? new HashSet<>(attempt.members().stream().map(ClaimAlgorithms.Member::user).toList()) : Set.of();
                checks.add(new Check(redis.smembers(attempt.membersKey()), expected, "members"));
                if (sample.success()) checks.add(new Check(redis.ttl(attempt.membersKey()), Positive.VALUE, "members TTL"));
                for (var m : attempt.members()) {
                    checks.add(new Check(redis.get(m.active()), owners.get(m.user()), "owner"));
                    checks.add(new Check(redis.zscore(m.bucket(), m.request()),
                            owners.containsKey(m.user()) ? null : (double) sample.index(), "queue score"));
                    if (owners.containsKey(m.user())) checks.add(new Check(redis.ttl(m.active()), Positive.VALUE, "claim TTL"));
                    checks.add(new Check(redis.get(m.lock()), null, "mutex released"));
                }
                if (checks.size() >= 2000) checkBatch(connection, checks);
            }
            checkBatch(connection, checks);
        } finally { connection.setAutoFlushCommands(true); }
    }

    enum Positive { VALUE }
    record Check(RedisFuture<?> future, Object expected, String description) {}
    static void checkBatch(StatefulRedisConnection<String, String> connection, List<Check> checks) throws Exception {
        connection.flushCommands();
        for (Check check : checks) {
            Object actual = check.future().get(15, TimeUnit.SECONDS);
            if (check.expected() == Positive.VALUE) assertTrue(((Number) actual).longValue() > 0, check.description());
            else assertEquals(check.expected(), actual, check.description());
        }
        checks.clear();
    }

    void proveFailureCases(RedisClient client) throws Exception {
        List<String> checks = new ArrayList<>();
        try (var a = new ClaimAlgorithms(client); var b = new ClaimAlgorithms(client)) {
            var attempt = ClaimAlgorithms.attempt("correctness", false, 0, 0, 2);
            for (String mode : List.of("lua", "lock")) {
                a.redis.flushdb(); seedOne(a.redis, attempt);
                var occupied = attempt.members().get(1);
                a.redis.set(occupied.active(), "other");
                assertFalse(mode.equals("lua") ? a.lua(attempt) : a.locked(attempt));
                assertNull(a.redis.get(attempt.members().getFirst().active()));
                assertEquals("other", a.redis.get(occupied.active()));
                checks.add(mode + ": occupied member rejects entire claim");
                a.redis.del(occupied.active());
                a.redis.set(occupied.guard(), "replacement");
                assertFalse(mode.equals("lua") ? a.lua(attempt) : a.locked(attempt));
                assertNull(a.redis.get(attempt.membersKey()));
                checks.add(mode + ": stale request rejected");
            }
            a.redis.flushdb(); seedOne(a.redis, attempt);
            // Deterministically expire a held lock after validation but before EXEC.
            String expiredKey = attempt.members().getFirst().lock();
            assertFalse(a.locked(attempt, 10_000, () -> {}, () -> {
                b.redis.pexpire(expiredKey, 0);
                assertEquals("OK", b.redis.set(expiredKey, "new-owner", SetArgs.Builder.nx().px(10_000)));
            }));
            assertEquals("new-owner", b.redis.get(expiredKey), "Old holder must not delete the new lock");
            for (var m : attempt.members()) assertNull(b.redis.get(m.active()));
            checks.add("lock: expiration before EXEC aborts writes and preserves new owner");
            a.redis.flushdb(); seedOne(a.redis, attempt);
            assertFalse(a.locked(attempt, 10_000, () -> {},
                    () -> b.redis.set(attempt.members().getFirst().guard(), "replacement")));
            for (var m : attempt.members()) assertNull(b.redis.get(m.active()));
            checks.add("lock: request replacement between Java check and EXEC aborts writes");
            a.redis.flushdb(); seedOne(a.redis, attempt);
            assertThrows(IllegalStateException.class, () -> a.locked(attempt, 10_000, () -> {},
                    () -> { throw new IllegalStateException("injected before EXEC"); }));
            for (var m : attempt.members()) {
                assertNull(b.redis.get(m.active())); assertNull(b.redis.get(m.lock()));
                assertNotNull(b.redis.zscore(m.bucket(), m.request()));
            }
            checks.add("lock: exception before EXEC discards queued writes and releases mutexes");
            a.redis.flushdb(); seedOne(a.redis, attempt);
            var sorted = attempt.members().stream().sorted(Comparator.comparing(ClaimAlgorithms.Member::lock)).toList();
            b.redis.set(sorted.getLast().lock(), "other", SetArgs.Builder.px(10_000));
            assertFalse(a.locked(attempt));
            assertNull(b.redis.get(sorted.getFirst().lock()));
            assertEquals("other", b.redis.get(sorted.getLast().lock()));
            checks.add("lock: partial acquisition failure releases only owned locks");
            // Actual disconnected client before EXEC. Remaining mutex expires; no claim was applied.
            a.redis.flushdb(); seedOne(a.redis, attempt);
            try (var disconnected = client.connect()) {
                var c = disconnected.sync();
                c.set(expiredKey, "dead-client", SetArgs.Builder.nx().px(100));
                c.multi(); c.set(attempt.members().getFirst().active(), "uncommitted");
            }
            Thread.sleep(150);
            assertNull(b.redis.get(expiredKey));
            assertNull(b.redis.get(attempt.members().getFirst().active()));
            checks.add("lock: disconnected client before EXEC leaves no claim; lease expires");
        }
        write("correctness.json", checks);
    }

    static void seedOne(RedisCommands<String, String> redis, ClaimAlgorithms.Attempt attempt) {
        for (var m : attempt.members()) { redis.set(m.guard(), m.request()); redis.zadd(m.bucket(), 0, m.request()); }
    }
    static Map<String, Double> numbers(String info) {
        Map<String, Double> result = new HashMap<>();
        for (String line : info.split("\\r?\\n")) {
            if (!line.startsWith("#") && line.contains(":")) {
                String[] parts = line.split(":", 2);
                try { result.put(parts[0], Double.parseDouble(parts[1])); } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }
    static Map<String, Object> percentiles(List<Double> values) {
        if (values.isEmpty()) return Map.of("count", 0);
        List<Double> sorted = values.stream().sorted().toList();
        return Map.of("count", sorted.size(), "p50", percentile(sorted, .5), "p95", percentile(sorted, .95),
                "p99", percentile(sorted, .99), "max", sorted.getLast());
    }
    static double percentile(List<Double> sorted, double q) { return sorted.get((int) Math.ceil(sorted.size() * q) - 1); }
    static void write(String name, Object value) throws IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.resolve(name).toFile(), value);
    }

    static final class Worker implements AutoCloseable {
        final Process process;
        final BufferedReader output;
        final BufferedWriter input;
        final ExecutorService reader = Executors.newSingleThreadExecutor();
        Worker(String uri, int id) throws Exception {
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-XX:ActiveProcessorCount=2", "-Xms64m", "-Xmx256m", "-cp", System.getProperty("study.classpath"),
                    ClaimStudyWorker.class.getName(), uri, Integer.toString(id))
                    .redirectError(OUTPUT.resolve("worker-" + id + ".log").toFile()).start();
            output = process.inputReader(); input = process.outputWriter();
            try { readUntil("READY"); } catch (Exception e) { close(); throw e; }
        }
        void send(ClaimStudyWorker.Run run) throws IOException {
            input.write(JSON.writeValueAsString(run)); input.newLine(); input.flush();
        }
        String readUntil(String prefix) throws Exception {
            return reader.submit(() -> {
                for (String line; (line = output.readLine()) != null;) if (line.startsWith(prefix)) return line;
                throw new EOFException("Worker exited; see worker log");
            }).get(90, TimeUnit.SECONDS);
        }
        ClaimStudyWorker.Result receive() throws Exception {
            return JSON.readValue(readUntil("RESULT ").substring(7), ClaimStudyWorker.Result.class);
        }
        @Override public void close() {
            try {
                input.write("STOP\n"); input.flush();
                if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (Exception ignored) { process.destroyForcibly(); }
            reader.shutdownNow();
        }
    }
    static final class PingProbe implements AutoCloseable {
        final StatefulRedisConnection<String, String> connection;
        final AtomicBoolean running = new AtomicBoolean(true);
        final List<Double> samples = new ArrayList<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> job;
        PingProbe(RedisClient client) {
            connection = client.connect();
            job = executor.submit(() -> {
                while (running.get()) {
                    long start = System.nanoTime();
                    assertEquals("PONG", connection.sync().ping());
                    samples.add((System.nanoTime() - start) / 1_000_000.0);
                    try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            });
        }
        void stop() throws Exception { running.set(false); job.get(15, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { try { stop(); } finally { executor.shutdownNow(); connection.close(); } }
    }
}
