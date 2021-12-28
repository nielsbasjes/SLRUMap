package nl.basjes.collections;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
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

    /** Hash based lookup for fast retrieval */
    private HashEntry<K, V>[] hashLookup;

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

    /** List of all elements.
     * The entry in the hashLookup is the first element in
     * the chain of all elements with the same hashvalue.
     */
    private final Map<K, HashEntry<K, V>> data;

    private static class HashEntry<K, V> implements Map.Entry<K, V> {
        /** The hash code of the key */
        protected int hashCode;
        /** The key */
        protected K key;
        /** The value */
        protected V value;
        /** In case of a hash collision we can find the next one with the same hashCode faster */
        protected HashEntry<K, V> next;

        public HashEntry(K key, V value) {
            this.hashCode = key.hashCode();
            this.value = value;
            this.key = key;
            this.next = null;
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
        hashLookup = new HashEntry[(int)(capacity * loadFactor)];
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
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException();
//        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
//        return false;
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
        HashEntry<K, V> hashEntry = hashLookup[index];
        while(hashEntry != null && !sameKey(key, hashEntry)) {
            hashEntry = hashEntry.next;
        }
        return hashEntry;
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
        HashEntry<K, V> hashEntry = hashLookup[index];
        HashEntry<K, V> prevHashEntry = null;
        while(hashEntry != null && !sameKey(key, hashEntry)) {
            prevHashEntry = hashEntry;
            hashEntry = hashEntry.next;
        }

        if (hashEntry == null) {
            // New entry with a previously unused hash value.
            hashEntry = new HashEntry<>(key, value);
            data.put(key, hashEntry);
            hashLookup[index] = hashEntry;
            if (prevHashEntry != null) {
                prevHashEntry.next = hashEntry;
            }
            return null;
        }

        // So we have an existing entry for this key.
        V oldValue = hashEntry.getValue();
        hashEntry.setValue(value);
        return oldValue;
    }

    @Override
    public synchronized V remove(Object key) {
        int index = hashIndex(key);
        HashEntry<K, V> hashEntry = hashLookup[index];
        HashEntry<K, V> prevHashEntry = null;
        while(hashEntry != null && !sameKey(key, hashEntry)) {
            prevHashEntry = hashEntry;
            hashEntry = hashEntry.next;
        }

        if (hashEntry == null) {
            // It does not exist in the map
            return null;
        }

        // Found it.
        if (prevHashEntry == null) {
            // This means this one is the FIRST in the hash list.
            // Make the second entry the one in the hashmap (if any)
            hashLookup[index] = hashEntry.next;
        } else {
            prevHashEntry.next = hashEntry.next;
        }
        data.remove(key);
        return hashEntry.getValue();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> copy) {
        copy.forEach(this::put);
    }

    @Override
    public void clear() {
        // Destroy the links
        data.forEach((k,v) -> v.next = null);
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
