/*
 * Yet Another UserAgent Analyzer
 * Copyright (C) 2013-2021 Niels Basjes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.basjes.collections;

import org.apache.commons.collections4.map.LRUMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * This test is intended to see if there is a performance difference for cached hits
 * if there are a lot of uncached hits also.
 */
class TestConcurrentPerformance {
    private static final Logger LOG = LogManager.getLogger(TestConcurrentPerformance.class);

    public static class Analyzer {
        private final Map<String, String> cache;

        public Analyzer(Map<String, String> cache) {
            this.cache = cache;
        }

        public String parse(String input) {
            String output = cache.get(input);
            if (output == null) {
                output = parseReally(input);
                cache.put(input, output);
            }
            return output;
        }

        public String parseReally(String input) {
            try {
                Thread.sleep(1);// Wait 1 millisecond
            } catch (InterruptedException e) {
                // Ignore
            }
            return "OUT-" + input;
        }

        public void clearCache() {
            cache.clear();
        }
    }

    public static class RunUNCachedTestCases extends Thread {
        private final Analyzer analyzer;
        private final int start;
        private final int end;

        public RunUNCachedTestCases(Analyzer analyzer, int start, int end) {
            this.analyzer = analyzer;
            this.start = start;
            this.end = end;
        }

        public void run() {
            IntStream
                    .range(start, end)
                    .forEach(value -> analyzer.parse("TEST:"+value));
        }
    }

    public static class RunCachedTestCase extends Thread {
        private final Analyzer analyzer;
        private final String testCase;
        private final long iterations;
        private long nanosUsed;

        RunCachedTestCase(Analyzer analyzer, String testCase, long iterations) {
            this.analyzer = analyzer;
            this.testCase = testCase;
            this.iterations = iterations;
        }

        public void run() {
            long start = System.nanoTime();
            for (long i = 0; i < iterations; i++) {
                analyzer.parse(testCase);
            }
            long stop = System.nanoTime();
            nanosUsed = stop-start;
        }

        public long getIterations() {
            return iterations;
        }

        public long getNanosUsed() {
            return nanosUsed;
        }
    }

    @Test
    void testMTPerformance_SLRUMap() throws InterruptedException {
        int cacheSize = 10000;

        Map<String, String> cacheInstance = new SLRUCache<>(cacheSize);

        runTest(cacheInstance);
    }


    @Test
    void testMTPerformance_LRUMap() throws InterruptedException {
        int cacheSize = 10000;

        Map<String, String> cacheInstance = Collections.synchronizedMap(new LRUMap<>(cacheSize));

        runTest(cacheInstance);
    }

    void runTest(Map<String, String> cacheInstance) throws InterruptedException {
        // This testcase does not occur in the rest of the testcases.
        String cachedTestCase = "Cached Test Case";

        Analyzer analyzer = new Analyzer(cacheInstance);

        long totalIterations = 0;
        long totalNanosUsed = 0;

        for (int i = 0; i < 5; i++) {
            LOG.info("Iteration {} : Start", i);

            RunUNCachedTestCases fireTests = new RunUNCachedTestCases(analyzer, 1, 1000);
            List<RunCachedTestCase> runCachedTestCases = Arrays.asList(
//                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
//                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
//                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
//                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
//                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000),
                    new RunCachedTestCase(analyzer, cachedTestCase, 10_000_000)
                );

            // Wipe the cache for the new run.
            analyzer.clearCache();

            // Now parse and cache the precached testcase.
            analyzer.parse(cachedTestCase);

            // Start both
            fireTests.start();
            runCachedTestCases.forEach(Thread::start);

            // Wait for both to finish
            fireTests.join();
            for (RunCachedTestCase ctc : runCachedTestCases) {
                ctc.join();
            }

            for (RunCachedTestCase runCachedTestCase : runCachedTestCases) {
                long iterations = runCachedTestCase.getIterations();
                long nanosUsed = runCachedTestCase.getNanosUsed();
                LOG.info("Iteration {} : Took {}ns ({}ms) = {}ns each", i, nanosUsed, (nanosUsed) / 1_000_000L, nanosUsed/iterations);
//              if (i > 3) {
                totalIterations += iterations;
                totalNanosUsed += nanosUsed;
//              }
            }

        }

        LOG.info("Average    : {}ns ({}ms) = {}ns each", totalNanosUsed, (totalNanosUsed) / 1_000_000L, totalNanosUsed/totalIterations);
    }
}
