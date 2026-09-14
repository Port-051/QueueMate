package com.queuemate.study;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.resource.DefaultClientResources;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Persistent separate JVM, with eight dedicated Redis connections and no shared Java locks. */
public final class ClaimStudyWorker {
    public record Run(String id, String strategy, boolean contended, int partySize, int operations) {}
    public record Sample(int index, boolean success, double millis) {}
    public record Result(int worker, List<Sample> samples) {}
    static final ObjectMapper JSON = new ObjectMapper();
    static final int THREADS = 8;

    public static void main(String[] args) throws Exception {
        int worker = Integer.parseInt(args[1]);
        var resources = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        var client = RedisClient.create(resources, args[0]);
        client.setDefaultTimeout(Duration.ofSeconds(10));
        List<ClaimAlgorithms> algorithms = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) algorithms.add(new ClaimAlgorithms(client));
        try (var pool = Executors.newFixedThreadPool(THREADS);
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("READY");
            System.out.flush();
            for (String line; (line = input.readLine()) != null;) {
                if (line.equals("STOP")) break;
                Run run = JSON.readValue(line, Run.class);
                CountDownLatch gate = new CountDownLatch(1);
                List<Future<List<Sample>>> futures = new ArrayList<>();
                for (int thread = 0; thread < THREADS; thread++) {
                    final int slot = thread;
                    futures.add(pool.submit(() -> {
                        var api = algorithms.get(slot);
                        List<Sample> samples = new ArrayList<>();
                        gate.await();
                        for (int index = slot; index < run.operations(); index += THREADS) {
                            var attempt = ClaimAlgorithms.attempt(run.id(), run.contended(), worker, index, run.partySize());
                            long started = System.nanoTime();
                            boolean won = run.strategy().equals("lua") ? api.lua(attempt) : api.locked(attempt);
                            samples.add(new Sample(index, won, (System.nanoTime() - started) / 1_000_000.0));
                        }
                        return samples;
                    }));
                }
                gate.countDown();
                List<Sample> samples = new ArrayList<>();
                for (var future : futures) samples.addAll(future.get(60, TimeUnit.SECONDS));
                System.out.println("RESULT " + JSON.writeValueAsString(new Result(worker, samples)));
                System.out.flush();
            }
        } finally {
            algorithms.forEach(ClaimAlgorithms::close);
            client.shutdown();
            resources.shutdown().get(10, TimeUnit.SECONDS);
        }
    }
}
