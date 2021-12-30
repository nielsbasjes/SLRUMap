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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test is intended to see what the eviction performance is.
 */
class TestEvictionPerformance {
    private static final Logger LOG = LogManager.getFormatterLogger(TestEvictionPerformance.class);

    public static Iterable<Integer> cacheSizes() {
        return List.of(
                 100
              ,1_000
             ,10_000
            ,100_000
//          ,1_000_000
//         ,10_000_000
        );
    }

    @ParameterizedTest(name = "Test Evict SLRUMap size {0}")
    @MethodSource("cacheSizes")
    void testSLRUMap(Integer cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = new SLRUCache<>(cacheSize);
        runTest(cacheInstance, cacheSize);
    }

    @ParameterizedTest(name = "Test Evict Synchronized LRUMap size {0}")
    @MethodSource("cacheSizes")
    void testSynchronizedLRUMap(Integer cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = Collections.synchronizedMap(new LRUMap<>(cacheSize));
        runTest(cacheInstance, cacheSize);
    }

    @ParameterizedTest(name = "Test Evict LRUMap size {0}")
    @MethodSource("cacheSizes")
    void testLRUMap(Integer cacheSize) throws InterruptedException {
        Map<String, String> cacheInstance = new LRUMap<>(cacheSize);
        runTest(cacheInstance, cacheSize);
    }

    void runTest(Map<String, String> cacheInstance, int cacheSize) throws InterruptedException {
        long iterations = 10_000;
        long nanosUsed;

        // Fill the entire cache to the limit (+10 to be sure it already overflows and evicts)
        for (int i = 0; i < cacheSize+10; i++) {
            cacheInstance.put("PreFill" + i, "Value");
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cacheInstance.put("Key" + i, "Value");
        }
        long stop = System.nanoTime();
        nanosUsed = stop-start;

        LOG.info("[%15s(%8d)] %d evictions: Took %12dns (%6dms) = %8dns(~%6.3fms) each.",
                cacheInstance.getClass().getSimpleName(), cacheSize,
                iterations,
                nanosUsed,                          (nanosUsed) / 1_000_000L,
                (nanosUsed/iterations),  ((float)nanosUsed/iterations) / 1_000_000L);
    }
}
