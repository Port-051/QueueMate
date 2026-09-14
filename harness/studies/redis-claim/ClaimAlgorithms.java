package com.queuemate.study;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental alternative only: not wired into the application. One connection per thread. */
final class ClaimAlgorithms implements AutoCloseable {
    static final int CLAIM_SECONDS = 120;
    // Lock release still needs compare-and-delete. This is not a claim/business-logic script.
    static final String UNLOCK = """
            local n = 0
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) == ARGV[1] then
                n = n + redis.call('DEL', KEYS[i])
              end
            end
            return n
            """;
    record Member(String user, String request, String bucket) {
        String active() { return "qm:user:active-proposal:" + user; }
        String guard() { return "qm:user:active-request:" + user; }
        String lock() { return "qm:lock:claim:" + user; }
    }
    record Attempt(String proposal, List<Member> members) {
        String membersKey() { return "qm:proposal:members:" + proposal; }
    }
    final StatefulRedisConnection<String, String> connection;
    final RedisCommands<String, String> redis;
    final String claimSha;
    final String unlockSha;

    ClaimAlgorithms(RedisClient client) throws IOException {
        connection = client.connect();
        redis = connection.sync();
        try (var stream = ClaimAlgorithms.class.getResourceAsStream("/study-baseline/atomic-proposal-claim.lua")) {
            if (stream == null) throw new IOException("Historical claim Lua resource missing");
            claimSha = redis.scriptLoad(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        unlockSha = redis.scriptLoad(UNLOCK);
    }

    boolean lua(Attempt attempt) {
        List<String> keys = new ArrayList<>(List.of(attempt.membersKey()));
        List<String> args = new ArrayList<>(List.of(attempt.proposal(), Integer.toString(CLAIM_SECONDS)));
        for (Member m : attempt.members()) {
            keys.addAll(List.of(m.active(), m.guard(), m.bucket()));
            args.addAll(List.of(m.request(), m.user()));
        }
        Long result = redis.evalsha(claimSha, ScriptOutputType.INTEGER,
                keys.toArray(String[]::new), args.toArray(String[]::new));
        return result == 1;
    }

    boolean locked(Attempt attempt) { return locked(attempt, 10_000, () -> {}, () -> {}); }

    /**
     * SET NX PX locks -> Java checks -> WATCH/MULTI/EXEC -> owner-checked unlock.
     * WATCH prevents an expired lock holder or a concurrent cancellation from committing stale reads.
     * MULTI keeps partial writes invisible if the client dies before EXEC. Neither mechanism is
     * a Sentinel durability guarantee; production would still require the existing DB constraints.
     */
    boolean locked(Attempt attempt, long leaseMs, Runnable afterLocks, Runnable beforeExec) {
        String token = UUID.randomUUID().toString();
        List<String> acquired = new ArrayList<>();
        boolean watching = false;
        boolean transaction = false;
        try {
            for (String key : attempt.members().stream().map(Member::lock).sorted().toList()) {
                if (!"OK".equals(redis.set(key, token, SetArgs.Builder.nx().px(leaseMs)))) return false;
                acquired.add(key);
            }
            afterLocks.run();
            List<String> checkedKeys = new ArrayList<>(acquired);
            attempt.members().forEach(m -> checkedKeys.addAll(List.of(m.active(), m.guard())));
            redis.watch(checkedKeys.toArray(String[]::new));
            watching = true;
            List<KeyValue<String, String>> values = redis.mget(checkedKeys.toArray(String[]::new));
            for (int i = 0; i < acquired.size(); i++) {
                if (!token.equals(values.get(i).getValueOrElse(null))) return false;
            }
            for (int i = 0; i < attempt.members().size(); i++) {
                int offset = acquired.size() + i * 2;
                if (values.get(offset).hasValue()
                        || !attempt.members().get(i).request().equals(values.get(offset + 1).getValueOrElse(null))) {
                    return false;
                }
            }
            redis.multi();
            transaction = true;
            // Pipeline queued writes; do not add a network round trip for every participant update.
            connection.setAutoFlushCommands(false);
            try {
                var async = connection.async();
                for (Member m : attempt.members()) {
                    async.set(m.active(), attempt.proposal(), SetArgs.Builder.ex(CLAIM_SECONDS));
                    async.zrem(m.bucket(), m.request());
                    async.sadd(attempt.membersKey(), m.user());
                }
                async.expire(attempt.membersKey(), CLAIM_SECONDS);
                connection.flushCommands();
            } finally {
                connection.setAutoFlushCommands(true);
            }
            beforeExec.run();
            TransactionResult result = redis.exec();
            transaction = false;
            watching = false;
            if (result.wasDiscarded()) return false;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof Throwable error) throw new IllegalStateException("EXEC failed", error);
            }
            return true;
        } finally {
            if (transaction) redis.discard();
            else if (watching) redis.unwatch();
            if (!acquired.isEmpty()) redis.evalsha(unlockSha, ScriptOutputType.INTEGER,
                    acquired.toArray(String[]::new), token);
        }
    }

    static Attempt attempt(String run, boolean contended, int worker, int index, int size) {
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String user = run + ":" + index + ":" + (contended && i == 0 ? "shared" : worker + ":" + i);
            members.add(new Member(user, "request:" + user, "qm:queue:study:" + run + ":" + i));
        }
        return new Attempt(run + ":proposal:" + worker + ":" + index, members);
    }

    @Override public void close() { connection.close(); }
}
