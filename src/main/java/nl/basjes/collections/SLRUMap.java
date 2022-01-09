package nl.basjes.collections;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SLRUMap<K, V> implements Map<K, V>, Serializable {

    /** The default load factor to use */
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** The maximum capacity allowed */
//    public static final int MAXIMUM_CAPACITY = 1 << 30;

    // The maximum number of entries in the LRU
    private final int capacity;

    /** Hash based lookup for fast and unsynchronized retrieval */
    private final SameHashValueMap<K, V>[] hashLookup;

    private static int cleanHashCode(Object key) {
        if (key == null) {
            return 0;
        }
        return key.hashCode();
    }

    private int hashIndex(int hashCode) {
        if (hashCode == Integer.MIN_VALUE) {
            hashCode = 0;
        }
        return Math.abs(cleanHashCode(hashCode)) % hashLookup.length;
    }

    private int hashIndex(Object key) {
        return hashIndex(cleanHashCode(key));
    }

    /** Raw map of all elements. */
    private HashMap<K, HashEntry<K, V>> data;

    private static class HashEntry<K, V> implements Map.Entry<K, V>, Serializable {
        /** The hash code of the key */
        protected int hashCode;
        /** The key */
        protected K key;
        /** The value */
        protected V value;

        protected long lastTouchTimestamp;

        private final SameHashValueMap<K,V> parent;

        public HashEntry(SameHashValueMap<K,V> parent, K key, V value) {
            this.parent = parent;
            this.parent.put(key, this);
            this.hashCode = key.hashCode();
            this.value = value;
            this.key = key;
            touch();
        }

        public synchronized void touch() {
            lastTouchTimestamp = System.nanoTime();
            parent.touch();
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

    private static class SameHashValueMap<K, V> extends HashMap<K, HashEntry<K, V>> {
        /** The hash code of the key */
        protected int hashCode;

        protected long oldestTouchTimestamp;

        public SameHashValueMap(int hashCode) {
            super();
            this.hashCode = hashCode;
        }

        @Override
        public synchronized HashEntry<K, V> get(Object key) {
            if (hashCode != cleanHashCode(key)) {
                return null;
            }
            return super.get(key);
        }

        @Override
        public synchronized HashEntry<K, V> put(K key, HashEntry<K, V> value) {
            return super.put(key, value);
        }

        @Override
        public synchronized HashEntry<K, V> remove(Object key) {
            return super.remove(key);
        }

        @Override
        public synchronized void clear() {
            super.clear();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SameHashValueMap)) return false;
            if (!super.equals(o)) return false;
            SameHashValueMap<?, ?> that = (SameHashValueMap<?, ?>) o;
            return hashCode == that.hashCode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), hashCode);
        }

        public synchronized void touch() {
            oldestTouchTimestamp = super
                    .values()
                    .stream()
                    .map(hashEntry -> hashEntry.lastTouchTimestamp)
                    .min(Long::compareTo)
                    .orElse(0L);
        }
    }

    public SLRUMap(int newCapacity) {
        this(newCapacity, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked") // Because of Generic array creation
    public SLRUMap(int newCapacity, float loadFactor) {
        capacity = newCapacity;
        hashLookup = new SameHashValueMap[(int) (capacity / loadFactor)];
        data = new HashMap<>(capacity, loadFactor);
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
    public synchronized boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        return data.containsValue(value);
    }

    private HashEntry<K, V> findHashEntry(Object key) {
        int index = hashIndex(key);
        SameHashValueMap<K, V> sameHashValueMap = hashLookup[index];
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
        hashEntry.touch();
        return hashEntry.getValue();
    }

    @Override
    public synchronized V put(K key, V value) {
        int index = hashIndex(key);
        SameHashValueMap<K, V> sameHashValueMap = hashLookup[index];

        if (sameHashValueMap == null) {
            // New entry with a previously unused hash value.
            sameHashValueMap = new SameHashValueMap<>(key.hashCode());
            HashEntry<K, V> hashEntry = new HashEntry<>(sameHashValueMap, key, value);
//            sameHashValueMap.put(key, hashEntry);
            data.put(key, hashEntry);
            hashLookup[index] = sameHashValueMap;
            flushLRU();
            return null;
        }

        // So we have existing entry/ies for this hashValue.
        HashEntry<K, V> hashEntry = sameHashValueMap.get(key);
        if (hashEntry == null) {
            // We do not have this specific key yet
            hashEntry = new HashEntry<>(sameHashValueMap, key, value);
//            sameHashValueMap.put(key, hashEntry);
            data.put(key, hashEntry);
            flushLRU();
            return null;
        }

        // We already have this key, so we only need to replace the value.
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

    /**
     * Make sure the LRU follows the configured maximum number of entries.
     * @return How may were removed.
     */
    public int flushLRU() {
        int removed = 0;
        while (size() > capacity) {
            SameHashValueMap<K, V> oldestSameHashValueMap = null;
            for (SameHashValueMap<K, V> sameHashValueMap : hashLookup) {
                if (sameHashValueMap == null) {
                    continue;
                }
                if (oldestSameHashValueMap == null) {
                    oldestSameHashValueMap = sameHashValueMap;
                }
                if (oldestSameHashValueMap.oldestTouchTimestamp > sameHashValueMap.oldestTouchTimestamp) {
                    oldestSameHashValueMap = sameHashValueMap;
                }
            }

            if (oldestSameHashValueMap != null) {
                long oldestTouchTimestamp = oldestSameHashValueMap.oldestTouchTimestamp;
                List<HashEntry<K,V>> remove =
                        oldestSameHashValueMap
                        .values().stream()
                        .filter(hv -> hv.lastTouchTimestamp == oldestTouchTimestamp)
                        .collect(Collectors.toList());

                synchronized (this) {
                    for (HashEntry<K, V> removeEntry : remove) {
                        oldestSameHashValueMap.remove(removeEntry.key);
                        data.remove(removeEntry.key);
                    }
                    if (oldestSameHashValueMap.isEmpty()) {
                        hashLookup[hashIndex(oldestSameHashValueMap.hashCode)] = null;
                    } else {
                        oldestSameHashValueMap.touch();
                    }
                }
            }
            removed++;
        }
        return removed;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> copy) {
        copy.forEach(this::put);
    }

    @Override
    public synchronized void clear() {
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
