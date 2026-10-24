# Benchmark results (2026-10-24)

| Machine | |
|---|---|
| CPU | Intel(R) Core(TM) i7-8850H CPU @ 2.60GHz |
| Logical processors | 12 |
| RAM | 31.7 GiB |
| OS | Windows 11 10.0 |
| JVM | OpenJDK 64-Bit Server VM 21.0.12.1+1-LTS |
| ONNX Runtime | 1.31.0 |
| Power | AC |

## Cold start

| Step | ms |
|---|---:|
| JVM start to main() | 279 |
| Model load | 572 |
| First embedding | 26 |
| **Total to first embedding** | **878** |

Indexing 330 full postings one at a time: 4.33 s (76.2 docs/sec).

## Memory

ONNX Runtime keeps the model off the Java heap, so RSS grows while the heap barely moves.

| Stage | Heap used (MiB) | RSS (MiB) |
|---|---:|---:|
| JVM baseline, before model load | 3 | 55 |
| model loaded + first embedding | 7 | 178 |
| after indexing 330 postings | 8 | 201 |

## Latency (JMH)

`EmbedSearchBench`: 1 fork, 3x2 s warmup, 5x2 s measurement. Search = top-5 over random 384-d unit vectors.

| Benchmark | Documents | p50 (ms) | p99 (ms) |
|---|---:|---:|---:|
| embedPosting | - | 15.958 | 21.186 |
| embedQuery | - | 2.900 | 4.028 |
| search | 500 | 0.223 | 0.618 |
| search | 10000 | 6.021 | 7.401 |
| search | 100000 | 65.012 | 79.991 |
