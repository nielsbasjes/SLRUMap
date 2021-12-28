package nl.basjes.collections;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SLRUCache<K, V> implements Map<K, V>, Serializable {

    /** The default load factor to use */
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** The maximum capacity allowed */
    protected static final int MAXIMUM_CAPACITY = 1 << 30;

    /** Load factor, normally 0.75 */
    private float loadFactor = DEFAULT_LOAD_FACTOR;

    /** The size of the map */
    private int size;

    // The maximum number of entries in the LRU
    private final int capacity;

    /** Hash based lookup for fast and unsynchronized retrieval */
    private Map<K, HashEntry<K, V>>[] hashLookup;

    private int hashIndex(Object key) {
        if (key == null) {
            return 0;
        }
        int hashCode = key.hashCode();
        if (hashCode == Integer.MIN_VALUE) {
            hashCode = 0;
        }
        return Math.abs(hashCode) % hashLookup.length;
    }

    /** Map of all elements. */
    private final Map<K, HashEntry<K, V>> data;

    private static class HashEntry<K, V> implements Map.Entry<K, V> {
        /** The hash code of the key */
        protected int hashCode;
        /** The key */
        protected K key;
        /** The value */
        protected V value;

        public HashEntry(K key, V value) {
            this.hashCode = key.hashCode();
            this.value = value;
            this.key = key;
        }

        @Override
        public K getKey() {
            if (key == null) {
                return null;
            }
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(final V newValue) {
            final V old = this.value;
            this.value = newValue;
            return old;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Map.Entry)) {
                return false;
            }
            final Map.Entry<?, ?> other = (Map.Entry<?, ?>) obj;
            return
                (getKey()   == null ? other.getKey()   == null : getKey().equals(other.getKey())) &&
                (getValue() == null ? other.getValue() == null : getValue().equals(other.getValue()));
        }

        @Override
        public int hashCode() {
            return (getKey() == null ? 0 : getKey().hashCode()) ^
                   (getValue() == null ? 0 : getValue().hashCode());
        }

        @Override
        public String toString() {
            return "" + getKey() + '=' + getValue();
        }
    }

    public SLRUCache(int newCapacity) {
        this(newCapacity, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked") // Because of Generic array creation
    public SLRUCache(int newCapacity, float newLoadFactor) {
        size = 0;
        capacity = newCapacity;
        loadFactor = newLoadFactor;
        hashLookup = new Map[(int) (capacity * loadFactor)];
        data = Collections.synchronizedMap(new HashMap<>(capacity, loadFactor));
    }

    @Override
    public int size() {
        return data.size();
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return data.containsValue(value);
    }

    private boolean sameKey(Object key, HashEntry<K, V> hashEntry) {
        if (key == null) {
            return hashEntry.key == null;
        }
        return key.equals(hashEntry.key);
    }

    private boolean sameValue(V value, HashEntry<K, V> hashEntry) {
        if (value == null) {
            return hashEntry.value == null;
        }
        return value.equals(hashEntry.value);
    }

    private HashEntry<K, V> findHashEntry(Object key) {
        int index = hashIndex(key);
        Map<K, HashEntry<K, V>> sameHashValueMap = hashLookup[index];
        if (sameHashValueMap != null) {
            // This one IS synchronized
            return sameHashValueMap.get(key);
        }
        return null;
    }

    @Override
    public V get(Object key) {
        HashEntry<K, V> hashEntry = findHashEntry(key);
        if (hashEntry == null) {
            return null;
        }
        return hashEntry.getValue();
    }

    @Override
    public synchronized V put(K key, V value) {
        int index = hashIndex(key);
        Map<K, HashEntry<K, V>> sameHashValueMap = hashLookup[index];

        if (sameHashValueMap == null) {
            // New entry with a previously unused hash value.
            sameHashValueMap = Collections.synchronizedMap(new HashMap<>());
            HashEntry<K, V> hashEntry = new HashEntry<>(key, value);
            sameHashValueMap.put(key, hashEntry);
            data.put(key, hashEntry);
            hashLookup[index] = sameHashValueMap;
            return null;
        }

        // So we have existing entry/ies for this hashValue.
        HashEntry<K, V> hashEntry = sameHashValueMap.get(key);
        if (hashEntry == null) {
            // We do not have this value yet
            hashEntry = new HashEntry<>(key, value);
            sameHashValueMap.put(key, hashEntry);
            data.put(key, hashEntry);
            return null;
        }
        V oldValue = hashEntry.getValue();
        hashEntry.setValue(value);
        return oldValue;
    }

    @Override
    public synchronized V remove(Object key) {
        int index = hashIndex(key);
        Map<K, HashEntry<K, V>> sameHashValueMap = hashLookup[index];

        if (sameHashValueMap == null) {
            return null;
        }

        HashEntry<K, V> hashEntry = sameHashValueMap.get(key);

        if (hashEntry == null) {
            // It does not exist in the map
            return null;
        }

        // Found it.
        sameHashValueMap.remove(key);
        data.remove(key);
        if (sameHashValueMap.isEmpty()) {
            hashLookup[index] = null;
        }
        return hashEntry.getValue();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> copy) {
        copy.forEach(this::put);
    }

    @Override
    public void clear() {
        // Wipe the map
        data.clear();
        // Full wipe of the array.
        Arrays.fill(hashLookup, null);
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
//        return false;
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
//        return false;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
//        return false;
    }
}
