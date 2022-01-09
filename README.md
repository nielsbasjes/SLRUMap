# Sloppy LRU map
An **experimental** _sloppy_ LRU implementation that is 'sort of' LRU.

# Why?
Because this does not lock on a `get` operation.

So in a multithreaded scenario with a lot of (successful) retrieves the `get` operations _should_ perform better.

So this is usually only useful in a scenario with a very high cache hit ratio.

## Advantage: 
- A `get` is lock free and thus in highly concurrent situations a `get` is a lot faster than having all `get` operations synchronized.

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
