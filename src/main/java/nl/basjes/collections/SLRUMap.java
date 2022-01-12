package nl.basjes.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class SLRUMap<K extends Serializable, V extends Serializable> implements Map<K, V>, Serializable {

    /** The default load factor to use */
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** The maximum capacity allowed */
//    public static final int MAXIMUM_CAPACITY = 1 << 30;

    // The maximum number of entries in the LRU
    private final int capacity;

    /** Hash based lookup for fast and unsynchronized retrieval */
    private final SameHashIndexMap<K, V>[] hashLookup;

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
        return Math.abs(hashCode) % hashLookup.length;
    }

    private int hashIndex(Object key) {
        return hashIndex(cleanHashCode(key));
    }

    /** Raw map of all elements. */
    private final HashMap<K, LRUEntry<K, V>> allEntries;



    static class LRUEntry<K extends Serializable, V extends Serializable> implements Serializable {
        /** The key */
        @Getter private final K key;
        /** The value */
        @Getter private V value;

        private long lastTouchTimestamp;

        @Getter private final SameHashIndexMap<K,V> mySameHashIndexMap;

        public LRUEntry(SameHashIndexMap<K,V> mySameHashIndexMap, K key, V value) {
            this.mySameHashIndexMap = mySameHashIndexMap;
            this.mySameHashIndexMap.put(key, this);
            this.value = value;
            this.key = key;
            touch();
        }

        public void touch() {
            lastTouchTimestamp = System.nanoTime();
            mySameHashIndexMap.touch();
        }

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
            if (!(obj instanceof LRUEntry)) {
                return false;
            }
            final LRUEntry<?, ?> other = (LRUEntry<?, ?>) obj;
            return
                (getKey()   == null ? other.getKey()   == null : getKey().equals(other.getKey())) &&
                (getValue() == null ? other.getValue() == null : getValue().equals(other.getValue()));
        }

        @Override
        public int hashCode() {
            return (getKey()   == null ? 0 : getKey().hashCode()) ^
                   (getValue() == null ? 0 : getValue().hashCode());
        }

        @Override
        public String toString() {
            return "{" + getKey() + '=' + getValue() + " : ["+lastTouchTimestamp+"]}";
        }
    }

    private static class SameHashIndexMap<K extends Serializable, V extends Serializable> extends HashMap<K, LRUEntry<K, V>> {
        /** The hash code of the key */
        protected int index;

        protected long oldestTouchTimestamp;

        public SameHashIndexMap(int index) {
            super();
            this.index = index;
        }

        @Override
        public synchronized LRUEntry<K, V> get(Object key) {
            return super.get(key);
        }

        @Override
        public synchronized LRUEntry<K, V> put(K key, LRUEntry<K, V> value) {
            return super.put(key, value);
        }

        @Override
        public synchronized LRUEntry<K, V> remove(Object key) {
            return super.remove(key);
        }

        @Override
        public synchronized void clear() {
            super.clear();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SLRUMap.SameHashIndexMap)) return false;
            if (!super.equals(o)) return false;
            SameHashIndexMap<?, ?> that = (SameHashIndexMap<?, ?>) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), index);
        }

        boolean oldestTouchTimestampIsDirty = true;

        public synchronized void touch() {
            oldestTouchTimestampIsDirty = true;
        }

        public long getOldestTouchTimestamp() {
            if (oldestTouchTimestampIsDirty) {
                synchronized (this) {
                    oldestTouchTimestamp = super
                            .values()
                            .stream()
                            .map(lruEntry -> lruEntry.lastTouchTimestamp)
                            .min(Long::compareTo)
                            .orElse(0L);
                }
                oldestTouchTimestampIsDirty = false;
            }
            return oldestTouchTimestamp;
        }
    }

    public SLRUMap(int newCapacity) {
        this(newCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_FLUSH_SIZE);
    }

    public SLRUMap(int newCapacity, int minFlushSize) {
        this(newCapacity, DEFAULT_LOAD_FACTOR, minFlushSize);
    }

    @SuppressWarnings("unchecked") // Because of Generic array creation
    public SLRUMap(int newCapacity, float loadFactor, int minFlushSize) {
        capacity = newCapacity;
        hashLookup = new SameHashIndexMap[(int) (capacity / loadFactor)];
        allEntries = new HashMap<>(capacity, loadFactor);
        this.minFlushSize = minFlushSize;
    }

    @Override
    public int size() {
        return allEntries.size();
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isEmpty() {
        return allEntries.isEmpty();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return allEntries.containsKey(key);
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        return allEntries.containsValue(value);
    }

    private LRUEntry<K, V> findHashEntry(Object key) {
        SameHashIndexMap<K, V> sameHashIndexMap = hashLookup[hashIndex(key)];
        if (sameHashIndexMap != null) {
            // This one IS synchronized
            return sameHashIndexMap.get(key);
        }
        return null;
    }

    @Override
    public V get(Object key) {
        LRUEntry<K, V> lruEntry = findHashEntry(key);
        if (lruEntry == null) {
            return null;
        }
        lruEntry.touch();
        return lruEntry.getValue();
    }

    @Override
    public synchronized V put(K key, V value) {
        int index = hashIndex(key);
        SameHashIndexMap<K, V> sameHashIndexMap = hashLookup[index];

        if (sameHashIndexMap == null) {
            // New entry with a previously unused hash value.
            sameHashIndexMap = new SameHashIndexMap<>(index);
            LRUEntry<K, V> lruEntry = new LRUEntry<>(sameHashIndexMap, key, value);
            allEntries.put(key, lruEntry);
            hashLookup[index] = sameHashIndexMap;
            startFlushLRU();
            return null;
        }

        // So we have existing entry/ies for this hashValue.
        LRUEntry<K, V> lruEntry = sameHashIndexMap.get(key);
        if (lruEntry == null) {
            // We do not have this specific key yet
            lruEntry = new LRUEntry<>(sameHashIndexMap, key, value);
//            sameHashValueMap.put(key, hashEntry);
            allEntries.put(key, lruEntry);
            startFlushLRU();
            return null;
        }

        // We already have this key, so we only need to replace the value.
        return lruEntry.setValue(value);
    }

    @Override
    public synchronized V remove(Object key) {
        int index = hashIndex(key);
        Map<K, LRUEntry<K, V>> sameHashValueMap = hashLookup[index];

        if (sameHashValueMap == null) {
            return null;
        }

        LRUEntry<K, V> lruEntry = sameHashValueMap.get(key);

        if (lruEntry == null) {
            // It does not exist in the map
            return null;
        }

        // Found it.
        sameHashValueMap.remove(key);
        allEntries.remove(key);
        if (sameHashValueMap.isEmpty()) {
            hashLookup[index] = null;
        }
        return lruEntry.getValue();
    }

    public int startFlushLRU() {
        return flushLRU();
    }

    public static final int DEFAULT_FLUSH_SIZE = 100;
    int minFlushSize;

    /**
     * Make sure the LRU follows the configured maximum number of entries.
     * @return How may were removed.
     */
    public int flushLRU() {
        int removed = 0;
        while (size() > capacity + minFlushSize) {
            synchronized (this) {
                PriorityQueue<LRUEntry<K, V>> toRemove = new PriorityQueue<>(Comparator.comparingLong(o -> - o.lastTouchTimestamp));
                int entriesToRemove = size() - capacity;

                for (LRUEntry<K, V> lruEntry : allEntries.values()) {
                    toRemove.add(lruEntry);
                    if (toRemove.size() > entriesToRemove) {
                        toRemove.remove();
                    }
                }
//                TreeSet<LRUEntry<K, V>> toRemove = new TreeSet<>(Comparator.comparingLong(o -> o.lastTouchTimestamp));

//                toRemove.addAll(allEntries.values());
                for (LRUEntry<K, V> entry : toRemove) {
                    K key = entry.getKey();
                    SameHashIndexMap<K, V> sameHashIndexMap = entry.getMySameHashIndexMap();
                    sameHashIndexMap.remove(key);
                    allEntries.remove(key);
                    if (sameHashIndexMap.isEmpty()) {
                        hashLookup[sameHashIndexMap.index] = null;
                    }

                    removed++;
                    if (--entriesToRemove == 0) {
                        break;
                    }
                }
            }
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
        allEntries.clear();
        // Full wipe of the array.
        Arrays.fill(hashLookup, null);
    }

    /**
     * Don't use this. Much too slow.
     */
    @Override
    public synchronized Set<K> keySet() {
        return allEntries.keySet();
    }

    /**
     * Don't use this. Much too slow.
     */
    @Override
    public synchronized Collection<V> values() {
        return allEntries.values().stream().map(LRUEntry::getValue).collect(Collectors.toList());
    }

    @AllArgsConstructor
    private static final class TmpEntry<K, V> implements Entry<K,V> {
        @Getter private K key;
        @Getter private V value;
        @Getter private long timestamp;

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("Read only instance");
        }

        @Override
        public String toString() {
            return "\nTmpEntry{" +
                "key=" + key +
                ", value=" + value +
                ", timestamp=" + timestamp +
                '}';
        }
    }

    /**
     * Don't use this. Much too slow.
     */
    @Override
    public synchronized Set<Entry<K, V>> entrySet() {
        return allEntries
            .entrySet()
            .stream()
            .map(e -> new TmpEntry<>(e.getKey(), e.getValue().getValue(), e.getValue().lastTouchTimestamp))
            .collect(Collectors.toSet());
    }

    @Override
    public synchronized String toString() {
        return "SLRUMap{" +
            "capacity=" + capacity +
            ", hashLookup=" + Arrays.toString(hashLookup) +
            ", allEntries=" + allEntries +
            ", minFlushSize=" + minFlushSize +
            '}';
    }
}
