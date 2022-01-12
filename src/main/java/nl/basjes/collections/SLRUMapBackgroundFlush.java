package nl.basjes.collections;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public class SLRUMapBackgroundFlush<K extends Serializable, V extends Serializable> extends SLRUMap<K, V>{
    public SLRUMapBackgroundFlush(int newCapacity) {
        super(newCapacity);
    }

    public SLRUMapBackgroundFlush(int newCapacity, int minFlushSize) {
        super(newCapacity, minFlushSize);
    }

    public SLRUMapBackgroundFlush(int newCapacity, float loadFactor, int minFlushSize) {
        super(newCapacity, loadFactor, minFlushSize);
    }

    AtomicBoolean flushIsRunning = new AtomicBoolean(false);

    @Override
    public int startFlushLRU() {
        if (size() > getCapacity() + minFlushSize) {
            if (flushIsRunning.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        flushLRU();
                    } finally {
                        flushIsRunning.set(false);
                    }
                }).start();
            }
        }
        return 0;
    }
}
