# Sloppy LRU map
An **experimental** _sloppy_ LRU implementation that is 'sort of' LRU.

The 'sort of' part stems from the fact that there ARE race conditions and the effects of these are accepted. 🤯

# Why?
I got a [bug report](https://github.com/nielsbasjes/yauaa/issues/385) that intrigued me.

Simply put: The standard LRU cache needs to be synchronized and this causes a lot of waiting for the lock on the cache, even if there are only `get` operations.

The rootcause is that _any sensible LRU implementation will reorder the list_ of elements on a get to guarantee the LRU property.

# You did what?
So I thought: Can I make a LRU implementation that is usable yet does NOT lock on a `get` operation?

If the `get` is lock free then in a multithreaded scenario with a lot of (successful) retrieves the `get` operations _should_ perform better on those `get` operations.

The predicted (and confirmed) downside is that a `put` operation performs terribly.

# How?
Is has an array of `Map`s per set of values that have the same hashcode. A `get` in this array is not synchronized. For now those inner `Map`s are synchronized.
As a consequence the `get` operations do not lock the entire Map (only the hashcode entry) which makes that there is a lot less waiting.

The ordering in this map is administrated by recording the latest time stamp (in nano seconds) when a value was touched last without synchronization.

So this causes ordering problems (and thus a `sort-of` LRU) in at least two ways:
- If two threads retrieve the same value close together it is unpredictable which of the two timestamps will be kept. Given that we only care about the question if this entry should remain longer in the cache this is not important.
- The [System.nanoTime()](https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#nanoTime()) documentation says
  > This method provides nanosecond precision, but not necessarily nanosecond resolution (that is, how frequently the value changes) - no guarantees are made except that the resolution is at least as good as that of currentTimeMillis().

  So it is quite possible that multiple `get` operations to different keys will set the same timestamp. If this happens it is impossible to determine within that group the real ordering. Here the impact is also limited as I do not care about the exact ordering, only about "drop if too old".
  Note: This did not yet happen in my Linux machine and apparently it will happen on Windows https://stackoverflow.com/questions/11452597/precision-vs-accuracy-of-system-nanotime .

It is because of these ordering problems I say this is `sort-of` LRU.

## Advantage:
- A `get` does not lock on the entire Map and thus in highly concurrent situations a `get` is a lot faster than having all `get` operations synchronized.

## Disadvantages:
- It does not guarantee exact LRU anymore. It is close enough for practical purposes, and it is NOT exact.
- A put is a LOT slower. Like really a lot. The reason is that the eviction of the oldest record is now REALLY REALLY hard.
- And it no longer scales. So a cache size should really be small (like <100_000). Above that it becomes useless really fast.

To compare:

The performance of evictions of a fully synchronized LRU Map from Apache Commons Collections does an eviction in less than 0.001ms for all cache sizes.

This SLRUmap as benchmarked on my i7 based laptop:

|  Cache Size | Average<br/>time per<br/>eviction |
|------------:|----------------------------------:|
|         100 |                           0.011ms |
|         200 |                           0.008ms |
|         500 |                           0.011ms |
|        1000 |                           0.019ms |
|        2000 |                           0.020ms |
|        5000 |                           0.014ms |
|       10000 |                           0.023ms |
|       20000 |                           0.042ms |
|       50000 |                           0.101ms |
|      100000 |                           0.307ms |
|      200000 |                           1.096ms |
|      500000 |                           2.363ms |

# Usecase
If you have a highly concurrent need for an LRU cache (where the 'sort of' LRU is fine) where you have a very high hit ratio. Like I have with [Yauaa](https://yauaa.basjes.nl).
