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

package nl.basjes.collections.performance;

import com.sun.management.OperatingSystemMXBean;
import nl.basjes.collections.SLRUMap;
import nl.basjes.collections.SLRUMapBackgroundFlush;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test is intended to see if there is a performance difference for cached hits
 * if there are a lot of uncached hits also.
 */
abstract class PerformanceBase {
    private static final Logger LOG = LogManager.getFormatterLogger(PerformanceBase.class);

    protected static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    public static class Analyzer {
        private final Map<String, String> cache;

        private final int delayNs;

        public Analyzer(Map<String, String> cache) {
            this(cache, 500_000);
        }

        public Analyzer(Map<String, String> cache, int delayNs) {
            this.cache = cache;
            this.delayNs = delayNs;
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
            if (delayNs > 0) {
                try {
                    Thread.sleep(0, delayNs); // Don't parse, just wait.
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            return "OUT-" + input;
        }

        public void clearCache() {
            cache.clear();
        }
    }

    public static abstract class TestCaseRunner extends Thread {
        protected boolean runOk = true;
        protected final int id;
        protected final Analyzer analyzer;
        protected final long iterations;
        protected long nanosUsed;

        TestCaseRunner(int id, Analyzer analyzer, long iterations) {
            this.id = id;
            this.analyzer = analyzer;
            this.iterations = iterations;
        }

        public abstract void runner();

        public void run() {
            long start = System.nanoTime();
            try {
                runner();
            } catch (ConcurrentModificationException cme) {
                runOk = false;
                throw cme;
            } finally {
                long stop = System.nanoTime();
                nanosUsed = stop - start;
            }
        }

        public long getIterations() {
            return iterations;
        }

        public long getNanosUsed() {
            return nanosUsed;
        }

        public boolean isRunOk() {
            return runOk;
        }
    }

    public static class RunUNCachedTestCases extends TestCaseRunner {
        private final Analyzer analyzer;
        private final int start;
        private final int count;

        public RunUNCachedTestCases(int id, Analyzer analyzer, int start, int count) {
            super(id, analyzer, count);
            this.analyzer = analyzer;
            this.start = start;
            this.count = count;
        }

        public void runner() {
            IntStream
                .range(start, start + count)
                .forEach(value -> analyzer.parse("TEST:" + value));
        }
    }

    public static class UpdateCachedTestCase extends TestCaseRunner {
        private final String testCase;
        private final String expectedResult;

        UpdateCachedTestCase(int id, Analyzer analyzer, String testCase, long iterations) {
            super(id, analyzer, iterations);
            this.testCase = testCase;
            expectedResult = analyzer.parse(testCase, true); // Force it to be in the cache
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
                String result = analyzer.parse(testCase, true);
                assertEquals(expectedResult, result);
            }
        }
    }

    public static class RunCachedSingleTestCase extends TestCaseRunner {
        String expectedResult;
        private final String testCase;

        RunCachedSingleTestCase(int id, Analyzer analyzer, String testCase, long iterations) {
            super(id, analyzer, iterations);
            this.testCase = testCase;
            expectedResult = analyzer.parse(testCase, true); // Force it to be in the cache
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
                String result = analyzer.parse(testCase);
//                assertEquals(expectedResult, result);
            }
        }
    }

    public static Iterable<Integer> cacheSizes() {
        return List.of(
            10000,
            20000,
            50000
        );
    }

    @ParameterizedTest(name = "Test SLRUMap for cachesize {0}")
    @MethodSource("cacheSizes")
    void testSLRUMap(int cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = new SLRUMap<>(cacheSize);
        runTest("SLRUMap", cacheInstance, cacheSize);
    }

    @ParameterizedTest(name = "Test SLRUMapBackgroundFlush for cachesize {0}")
    @MethodSource("cacheSizes")
    void testSLRUMapBackgroundFlush(int cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = new SLRUMapBackgroundFlush<>(cacheSize, 0);
        runTest("SLRUMap BG", cacheInstance, cacheSize);
    }

    @ParameterizedTest(name = "Test LRUMap for cachesize {0}")
    @MethodSource("cacheSizes")
    void testSynchronizedLRUMap(int cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = Collections.synchronizedMap(new LRUMap<>(cacheSize));
        runTest("LRUMap", cacheInstance, cacheSize);
    }

    abstract void runTest(String name, Map<String, String> cacheInstance, int cacheSize) throws InterruptedException;

    public void logStats(String name, int cacheSize, long totalIterations, long totalNanosUsed) {
        LOG.info("%-10s(%6d) --> Total %12dns (%8.1fms) = %8dns each (%6.3fms) CPU load: ~%3.0f%%",
            name,cacheSize,
            totalNanosUsed,                     ((float)totalNanosUsed                )/1_000_000L,
            totalNanosUsed/totalIterations,     ((float)totalNanosUsed/totalIterations)/1_000_000L,
            OS_BEAN.getProcessCpuLoad()*100
        );
    }

}
