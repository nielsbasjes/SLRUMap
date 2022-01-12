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
class TestReadPerformance extends PerformanceBase {

    void runTest(String name, Map<String, String> cacheInstance, int cacheSize) throws InterruptedException {
        // This testcase does not occur in the rest of the testcases.
        String cachedTestCase = "Cached Test Case";

        Analyzer analyzer = new Analyzer(cacheInstance);

        // Wipe the cache for the new run.
        analyzer.clearCache();

        TestCaseRunner runner = new RunCachedSingleTestCase(1, analyzer, cachedTestCase, 100_000_000);

        // Now parse and precached the testcase.
        analyzer.parse(cachedTestCase);

        runner.start();
        runner.join();

        long totalIterations = runner.getIterations();
        long totalNanosUsed = runner.getNanosUsed();

        logStats(name, cacheSize, totalIterations, totalNanosUsed);
    }
}
