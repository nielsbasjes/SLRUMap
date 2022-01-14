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
        LOG.info("System.nano() uniques: {}/{}", count, expectedUniques);
        long previous = 0;
        for (int i = 10; i < 20; i++) {
            if (previous != 0) {
                LOG.info("Timestamp: {} --> Δ {}ns", times[i], times[i] - previous);
            }
            previous = times[i];
        }

        // https://stackoverflow.com/questions/11452597/precision-vs-accuracy-of-system-nanotime
        // I expect this to pass/fail depending on the underlying operating system.
        assertEquals(expectedUniques, count);
    }

}
