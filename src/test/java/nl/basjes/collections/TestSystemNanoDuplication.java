package nl.basjes.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSystemNanoDuplication {

    private static final Logger LOG = LogManager.getLogger(TestSystemNanoDuplication.class);

    @Test
    void testSystemNano() {
        int expectedUniques = 1_000_000;
        long[] times = new long[expectedUniques];
        for (int i = 0; i < expectedUniques; i++) {
            times[i] = System.nanoTime();
        }

        List<Long> timesList = new ArrayList<>(expectedUniques);
        for (long time : times) {
            timesList.add(time);
        }

        long count = timesList.stream().distinct().count();
        LOG.info("System.nanoTime() uniques: {}/{}", count, expectedUniques);
        long previous = 0;
        for (int i = 1; i <= 30; i++) { // Skip the first on (index 0)
            if (previous != 0) {
                LOG.info("Timestamp: {} --> Δ {}ns", times[i], times[i] - previous);
            }
            previous = times[i];
        }

        // https://stackoverflow.com/questions/11452597/precision-vs-accuracy-of-system-nanotime
        // I expect this to pass/fail depending on the underlying operating system.
        // This fails on Github Actions:
        // Error:    TestSystemNanoDuplication.testSystemNano:41 expected: <1000000> but was: <312784>
        if (expectedUniques != count) {
            LOG.fatal("Your system will see a less accurate LRU ordering because of the way System.nanoTime() works!!");
        }
    }

}
