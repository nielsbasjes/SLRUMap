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

import lombok.Getter;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is intended to see if there is a performance difference for cached hits
 * if there are a lot of uncached hits also.
 */
@Disabled("These performance tests are too heavy to run automatically.")
class TestConcurrentPerformance {
    private static final Logger LOG = LogManager.getFormatterLogger(TestConcurrentPerformance.class);

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
                Thread.sleep(0, 500_000 ); // Don't parse, just wait 0.5 millisecond
            } catch (InterruptedException e) {
                // Ignore
            }
            return "OUT-" + input;
        }

        public void clearCache() {
            cache.clear();
        }
    }

    public static class StatsMap<K,V> implements Map<K, V> {

        private final Map<K, V> map;

        public StatsMap(Map<K, V> map) {
            this.map = map;
        }

        @Getter private long callsToSize          = 0; @Override public int                size()                                  { callsToSize          ++; return map.size();               }
        @Getter private long callsToIsEmpty       = 0; @Override public boolean            isEmpty()                               { callsToIsEmpty       ++; return map.isEmpty();            }
        @Getter private long callsToContainsKey   = 0; @Override public boolean            containsKey(Object key)                 { callsToContainsKey   ++; return map.containsKey(key);     }
        @Getter private long callsToContainsValue = 0; @Override public boolean            containsValue(Object value)             { callsToContainsValue ++; return map.containsValue(value); }
        @Getter private long callsToGet           = 0; @Override public V                  get(Object key)                         { callsToGet           ++; return map.get(key);             }
        @Getter private long callsToPut           = 0; @Override public V                  put(K key, V value)                     { callsToPut           ++; return map.put(key, value);      }
        @Getter private long callsToRemove        = 0; @Override public V                  remove(Object key)                      { callsToRemove        ++; return map.remove(key);          }
        @Getter private long callsToPutAll        = 0; @Override public void               putAll(Map<? extends K, ? extends V> m) { callsToPutAll        ++; map.putAll(m);                   }
        @Getter private long callsToClear         = 0; @Override public void               clear()                                 { callsToClear         ++; map.clear();                     }
        @Getter private long callsToKeySet        = 0; @Override public Set<K>             keySet()                                { callsToKeySet        ++; return map.keySet();             }
        @Getter private long callsToValues        = 0; @Override public Collection<V>      values()                                { callsToValues        ++; return map.values();             }
        @Getter private long callsToEntrySet      = 0; @Override public Set<Entry<K, V>>   entrySet()                              { callsToEntrySet      ++; return map.entrySet();           }
    }


    public static class RunUNCachedTestCases extends Thread {
        private final Analyzer analyzer;
        private final int start;
        private final int count;

        public RunUNCachedTestCases(Analyzer analyzer, int start, int count) {
            this.analyzer = analyzer;
            this.start = start;
            this.count = count;
        }

        public void run() {
            IntStream
                    .range(start, start + count)
                    .forEach(value -> analyzer.parse("TEST:"+value));
//            LOG.info("Task %s finished.", this.getClass().getSimpleName());
        }
    }

    public static abstract class TestCaseRunner extends Thread {
        protected boolean runOk = true;
        protected final int id;
        protected final Analyzer analyzer;
        protected final String testCase;
        protected final long iterations;
        protected long nanosUsed;

        TestCaseRunner(int id, Analyzer analyzer, String testCase, long iterations) {
            this.id = id;
            this.analyzer = analyzer;
            this.testCase = testCase;
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
            }
            finally {
                long stop = System.nanoTime();
                nanosUsed = stop-start;
            }
//            LOG.info("Task %s finished.", this.getClass().getSimpleName());
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

    public static class UpdateCachedTestCase extends TestCaseRunner {
        UpdateCachedTestCase(int id, Analyzer analyzer, String testCase, long iterations) {
            super(id, analyzer, testCase, iterations);
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
                analyzer.parse(testCase, true);
            }
        }
    }

    public static class RunCachedTestCase extends TestCaseRunner {
        String expectedResult;
        RunCachedTestCase(int id, Analyzer analyzer, String testCase, long iterations) {
            super(id, analyzer, testCase, iterations);
            expectedResult = analyzer.parse(testCase, true);// Force it to be in the cache
        }

        public void runner() {
            for (long i = 0; i < iterations; i++) {
//                if (i%10000==0) {
//                    LOG.info("Thread [{}] {} ({}): {}/{}", id, this.getClass().getSimpleName(), this.getId(), i, iterations);
//                }
                String result = analyzer.parse(testCase);
                assertEquals(expectedResult, result);
            }
        }
    }

    public static Iterable<Integer> cacheSizes() {
        return List.of(
            10000,
//            20000,
            50000
        );
    }


    @ParameterizedTest(name = "Test SLRUMap for cachesize {0}")
    @MethodSource("cacheSizes")
    void testMTPerformance_SLRUMap(int cacheSize) throws InterruptedException {
        StatsMap<String, String> cacheInstance = new StatsMap<>(new SLRUMap<>(cacheSize));
        runTest(cacheInstance,cacheSize);
    }

    @ParameterizedTest(name = "Test LRUMap for cachesize {0}")
    @MethodSource("cacheSizes")
    void testMTPerformance_LRUMap(int cacheSize) throws InterruptedException {
        StatsMap<String, String> cacheInstance = new StatsMap<>(Collections.synchronizedMap(new LRUMap<>(cacheSize)));
        runTest(cacheInstance,  cacheSize);
    }

    void runTest(StatsMap<String, String> cacheInstance, int cacheSize) throws InterruptedException {
        // This testcase does not occur in the rest of the testcases.
        String cachedTestCase = "Cached Test Case";

        Analyzer analyzer = new Analyzer(cacheInstance);

        long totalIterations = 0;
        long totalNanosUsed = 0;

        for (int i = 0; i < 1; i++) {
//            LOG.info("Iteration {} : Start", i);

            // Wipe the cache for the new run.
            analyzer.clearCache();

            List<Thread> runCacheUpdates = new ArrayList<>();
            for (int j = 0 ; j < 10 ; j++) {
                runCacheUpdates.add(new RunUNCachedTestCases(analyzer, (j*2000) + 1, 2000));
            }

            List<RunCachedTestCase> runCachedTestCases = new ArrayList<>();
            int cacheCases = 10;
            for (int j = 0 ; j < cacheCases ; j++) {
                runCachedTestCases.add(new RunCachedTestCase(j, analyzer, cachedTestCase+"-"+j, 50_000_000));
                // Now parse and cache the precached testcase.
                analyzer.parse(cachedTestCase+"-"+j);
            }

            // Start
            runCacheUpdates.forEach(Thread::start);
            runCachedTestCases.forEach(Thread::start);

            // Wait for all to finish
            for (Thread ctc : runCacheUpdates) {
                ctc.join();
            }
            for (RunCachedTestCase ctc : runCachedTestCases) {
                ctc.join();
                assertTrue(ctc.isRunOk(), "Run failed.");
            }

            for (RunCachedTestCase runCachedTestCase : runCachedTestCases) {
                long iterations = runCachedTestCase.getIterations();
                long nanosUsed = runCachedTestCase.getNanosUsed();
//                LOG.info("Iteration {} : Took {}ns ({}ms) = {}ns each", i, nanosUsed, (nanosUsed) / 1_000_000L, nanosUsed/iterations);
//              if (i > 3) {
                totalIterations += iterations;
                totalNanosUsed += nanosUsed;
//              }
            }

        }

        LOG.info("CacheSize: %6d --> Total %10dns (%5.1fms) = %8dns each (%6.3fms) Stats:(G=%d | P=%d)",
                cacheSize,
                totalNanosUsed,                 ((float)totalNanosUsed                ) / 1_000_000L,
                totalNanosUsed/totalIterations, ((float)totalNanosUsed/totalIterations) / 1_000_000L,
                cacheInstance.getCallsToGet(),
                cacheInstance.getCallsToPut()
                );
    }
}
