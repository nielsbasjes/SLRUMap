package nl.basjes.collections;

import org.apache.commons.collections4.map.LRUMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBasicLRUMapOperations {

    private static final int CAPACITY = 5;

    private static final class TestParameter {
        String name;
        Map<String, String> map;

        public TestParameter(String name, Map<String, String> map) {
            this.name = name;
            this.map = map;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static Iterable<TestParameter> maps() {
        return List.of(
//            new HashMap<>(100),
                new TestParameter("LRUMap", new LRUMap<>(CAPACITY)),
                new TestParameter("SLRUCache", new SLRUCache<>(CAPACITY))
        );
    }

    @ParameterizedTest(name = "Test PutGet for {0}")
    @MethodSource("maps")
    void testPutGet(TestParameter testParameter) {
        Map<String, String> map = testParameter.map;

        assertTrue(map.isEmpty());
        map.put("K1", "V1");
        assertEquals("V1", map.get("K1"));
        assertEquals("V1", map.get("K1")); // Double check as the 'get' modifies.

        map.put("K2", "V2");
        assertEquals("V1", map.get("K1"));
        assertEquals("V2", map.get("K2"));

        assertEquals("V1", map.remove("K1"));
        assertNull(map.get("K1"));
        assertNull(map.remove("K1"));
        assertFalse(map.isEmpty());

        assertEquals("V2", map.remove("K2"));
        assertNull(map.get("K2"));
        assertNull(map.remove("K2"));
        assertTrue(map.isEmpty());
    }

    @ParameterizedTest(name = "Test LRU drop oldest {0}")
    @MethodSource("maps")
    void testLRU1(TestParameter testParameter) {
        Map<String, String> map = testParameter.map;

        assertTrue(map.isEmpty());
        map.put("K1", "V1");
        map.put("K2", "V2");
        map.put("K3", "V3");
        map.put("K4", "V4");
        map.put("K5", "V5");
        map.put("K6", "V6");

        assertEquals(CAPACITY, map.size());
        assertNull(map.get("K1")); // K1 must be gone now
    }

    @ParameterizedTest(name = "Test LRU drop oldest {0}")
    @MethodSource("maps")
    void testLRU2(TestParameter testParameter) {
        Map<String, String> map = testParameter.map;

        assertTrue(map.isEmpty());
        map.put("K1", "V1");
        map.put("K2", "V2");
        map.put("K3", "V3");

        // This 'get' must promote K1 and make K2 the oldest
        assertEquals("V1", map.get("K1"));

        map.put("K4", "V4");
        map.put("K5", "V5");
        assertEquals("V2", map.get("K2"));
        map.put("K6", "V6");

        assertEquals(CAPACITY, map.size());
        assertNull(map.get("K3")); // K3 must be gone now
    }

}
