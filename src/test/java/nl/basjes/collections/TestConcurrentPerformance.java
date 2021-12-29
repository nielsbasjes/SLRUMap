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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            return parse(input, false);
        }

        public String parse(String input, boolean forceUpdateCache) {
            String output = cache.get(input);
            if (output == null || forceUpdateCache) {
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

    public static abstract class TestCaseRunner extends Thread {
        protected final Analyzer analyzer;
        protected final String testCase;
        protected final long iterations;
        protected long nanosUsed;

        TestCaseRunner(Analyzer analyzer, String testCase, long iterations) {
            this.analyzer = analyzer;
            this.testCase = testCase;
            this.iterations = iterations;
        }

        public abstract void runner();

        public void run() {
            long start = System.nanoTime();
            runner();
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

    public static class UpdateCachedTestCase extends TestCaseRunner {
        UpdateCachedTestCase(Analyzer analyzer, String testCase, long iterations) {
            super(analyzer, testCase, iterations);
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
                analyzer.parse(testCase, true);
            }
        }
    }

    public static class RunCachedTestCase extends TestCaseRunner {
        String expectedResult;
        RunCachedTestCase(Analyzer analyzer, String testCase, long iterations) {
            super(analyzer, testCase, iterations);
            expectedResult = analyzer.parse(testCase, true);// Force it to be in the cache
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
                String result = analyzer.parse(testCase);
                assertEquals(expectedResult, result);
            }
        }
    }

    int cacheSize = 100;

    @Test
    void testMTPerformance_SLRUMap() throws InterruptedException {
        Map<String, String> cacheInstance = new SLRUCache<>(cacheSize);
        runTest(cacheInstance);
    }

    @Disabled
    @Test
    void testMTPerformance_LRUMap() throws InterruptedException {
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

            List<Thread> runCacheUpdates = new ArrayList<>();
            for (int j = 0 ; j < 10 ; j++) {
                runCacheUpdates.add(new RunUNCachedTestCases(analyzer, (j*1000) + 1, (j+1)*1000));
            }

            List<RunCachedTestCase> runCachedTestCases = new ArrayList<>();
            for (int j = 0 ; j < 10 ; j++) {
                runCachedTestCases.add(new RunCachedTestCase(analyzer, cachedTestCase+"-"+j, 10_000_000));
            }

            // Wipe the cache for the new run.
            analyzer.clearCache();

            // Now parse and cache the precached testcase.
            analyzer.parse(cachedTestCase);

            // Start
            runCacheUpdates.forEach(Thread::start);
            runCachedTestCases.forEach(Thread::start);

            // Wait for all to finish
            for (Thread ctc : runCacheUpdates) {
                ctc.join();
            }
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
