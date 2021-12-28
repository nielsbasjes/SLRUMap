package nl.basjes.collections;

import org.apache.commons.collections4.map.LRUMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBasicMapOperations {


    public static Iterable<Map<String, String>> maps() {
        return List.of(
            new HashMap<>(100),
            new LRUMap<>(100),
            new SLRUCache<>(100)
        );
    }

    @ParameterizedTest(name = "Test PutGet {index}")
    @MethodSource("maps")
    void testPutGet(Map<String, String> map) {
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
}
