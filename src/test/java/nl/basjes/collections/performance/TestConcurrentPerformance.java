package nl.basjes.collections.performance;

import org.junit.jupiter.api.Disabled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("These performance tests are too heavy to run automatically.")
public class TestConcurrentPerformance extends PerformanceBase {

    @Override
    void runTest(String name, Map<String, String> cacheInstance, int cacheSize) throws InterruptedException {
        // This testcase does not occur in the rest of the testcases.
        String cachedTestCase = "Cached Test Case";

        // Apparently needed to get a sensible number a bit later
        OS_BEAN.getProcessCpuLoad();

        Analyzer analyzer = new Analyzer(cacheInstance);

        long totalIterations = 0;
        long totalNanosUsed = 0;

        // Wipe the cache for the new run.
        analyzer.clearCache();

        List<Thread> runCacheUpdates = new ArrayList<>();
        for (int j = 0 ; j < 5 ; j++) {
            runCacheUpdates.add(new RunUNCachedTestCases(j, analyzer, (j*2000) + 1, 2000));
        }

        List<RunCachedSingleTestCase> runCachedSingleTestCases = new ArrayList<>();
        for (int j = 0 ; j < 10 ; j++) {
            runCachedSingleTestCases.add(new RunCachedSingleTestCase(j, analyzer, cachedTestCase+"-"+j, 50_000_000));
            // Now parse and precached the testcase.
            analyzer.parse(cachedTestCase+"-"+j);
        }

        // Start
        runCacheUpdates.forEach(Thread::start);
        runCachedSingleTestCases.forEach(Thread::start);

        // Wait for all to finish
        for (Thread ctc : runCacheUpdates) {
            ctc.join();
        }
        for (RunCachedSingleTestCase ctc : runCachedSingleTestCases) {
            ctc.join();
            assertTrue(ctc.isRunOk(), "Run failed.");
        }

        for (RunCachedSingleTestCase runCachedSingleTestCase : runCachedSingleTestCases) {
            long iterations = runCachedSingleTestCase.getIterations();
            long nanosUsed = runCachedSingleTestCase.getNanosUsed();
            totalIterations += iterations;
            totalNanosUsed += nanosUsed;
        }

        logStats(name, cacheSize,totalIterations, totalNanosUsed);
    }

}
