package nl.basjes.collections;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public class SLRUMapBackgroundFlush<K extends Serializable, V extends Serializable> extends SLRUMap<K, V>{
    public SLRUMapBackgroundFlush(int newCapacity) {
        super(newCapacity);
    }

    public SLRUMapBackgroundFlush(int newCapacity, int flushSize) {
        super(newCapacity, flushSize);
    }

    public SLRUMapBackgroundFlush(int newCapacity, float loadFactor, int flushSize) {
        super(newCapacity, loadFactor, flushSize);
    }

    AtomicBoolean flushIsRunning = new AtomicBoolean(false);

    @Override
    public int aChangeHappened() {
        int flushSize = getFlushSize();
        if (size() > getCapacity() + flushSize) {
            if (flushIsRunning.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        flushLRU(flushSize);
                    } finally {
                        flushIsRunning.set(false);
                    }
                }).start();
            }
        }
        return 0;
    }
}
