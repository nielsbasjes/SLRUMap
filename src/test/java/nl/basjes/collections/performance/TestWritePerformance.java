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

import java.util.Map;

/**
 * This test is intended to see if there is a performance difference for cached hits
 * if there are a lot of uncached hits also.
 */
//@Disabled("These performance tests are too heavy to run automatically.")
class TestWritePerformance extends PerformanceBase {

    void runTest(String name, Map<String, String> cacheInstance, int cacheSize) throws InterruptedException {
        Analyzer analyzer = new Analyzer(cacheInstance, 0);

        // Wipe the cache for the new run.
        analyzer.clearCache();

        // Ensure the LRU is full with values that all must be evicted
        for (int i = 0; i < cacheSize + 10; i++) {
            analyzer.parse("Dummy-"+i);
        }

        int totalIterations = 100_000;
        TestCaseRunner runner = new RunUNCachedTestCases(0, analyzer, 1, totalIterations);

        runner.start();
        runner.join();

        logStats(name, cacheSize, totalIterations, runner.getNanosUsed());
    }
}
