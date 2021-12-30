An experimental sloppy LRU implementation that is 'sort of' LRU.

Why?

Because this does not lock on a get operation.

Advantage: 
- A get is lock free and thus in highly concurrent situations a get is a lot faster than having all `get` operations synchronized.

Disadvantages: 
- A put is a LOT slower. Like really a lot. The reason is that the eviction of the oldest record is now REALLY REALLY hard.
- And it no longer scales. So a cache size should really be small (like <100_000). Above that it becomes useless really fast.

Usecase: If you have a highly concurrent need for an LRU cache (where the 'sort of' LRU is fine) wher you have a very high hit ratio. Like I have with Yauaa.

