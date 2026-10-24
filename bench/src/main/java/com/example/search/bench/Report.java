package com.example.search.bench;

import ai.onnxruntime.OrtEnvironment;
import com.example.search.MiniLmEmbedder;
import com.example.search.SemanticSearchService;
import com.example.search.SemanticSearchService.Hit;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.util.Statistics;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Writes bench/results/results.md and eval.md: cold start, heap vs RSS, precision@5 (full posting vs
 * requirements-only) and the JMH results.
 * Run from the repo root: {@code java -cp bench/target/benchmarks.jar com.example.search.bench.Report}.
 */
public class Report {

    record Query(String query, String kind, Set<String> relevant) {
    }

    /** precision@5 per query, in queries.json order. */
    record Eval(String name, List<Double> precision) {
        double mean(List<Query> queries, String kind) {
            return IntStream.range(0, queries.size())
                    .filter(i -> kind == null || queries.get(i).kind().equals(kind))
                    .mapToDouble(precision::get).average().orElse(0);
        }
    }

    static final Path OUT = Path.of("bench/results");
    static final Path QUERIES = Path.of("bench/data/queries.json");
    static final int K = 5;
    static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        long jvmToMainMs = ManagementFactory.getRuntimeMXBean().getUptime();
        List<String[]> memory = new ArrayList<>();
        memory.add(memory("JVM baseline, before model load"));

        // Cold start: what a freshly started app pays before it can answer its first query.
        long t0 = System.nanoTime();
        MiniLmEmbedder embedder = new MiniLmEmbedder(EmbedSearchBench.MODEL_DIR, 256);
        long t1 = System.nanoTime();
        embedder.embed("first query after startup");
        long t2 = System.nanoTime();
        memory.add(memory("model loaded + first embedding"));

        long t3 = System.nanoTime();
        SemanticSearchService full = new SemanticSearchService(embedder, EmbedSearchBench.DATA_PATH, EmbedSearchBench.FULL_POSTING);
        full.load();
        double indexSeconds = (System.nanoTime() - t3) / 1e9;
        memory.add(memory("after indexing " + full.size() + " postings"));

        SemanticSearchService requirements = new SemanticSearchService(embedder, EmbedSearchBench.DATA_PATH, List.of("requirements"));
        requirements.load();

        List<Query> queries = readQueries();
        List<Eval> evals = new ArrayList<>();
        evals.add(evaluate("local, full posting", queries, q -> full.search(q, K)));
        evals.add(evaluate("local, requirements-only", queries, q -> requirements.search(q, K)));
        embedder.close();

        Collection<RunResult> jmh = new Runner(new OptionsBuilder().include(EmbedSearchBench.class.getName()).build()).run();

        String header = header();
        StringBuilder results = new StringBuilder(header)
                .append("## Cold start\n\n| Step | ms |\n|---|---:|\n")
                .append("| JVM start to main() | ").append(jvmToMainMs).append(" |\n")
                .append("| Model load | ").append(ms(t1 - t0)).append(" |\n")
                .append("| First embedding | ").append(ms(t2 - t1)).append(" |\n")
                .append("| **Total to first embedding** | **").append(jvmToMainMs + ms(t2 - t0)).append("** |\n\n")
                .append(String.format("Indexing %d full postings one at a time: %.2f s (%.1f docs/sec).%n%n",
                        full.size(), indexSeconds, full.size() / indexSeconds))
                .append("## Memory\n\nONNX Runtime keeps the model off the Java heap, so RSS grows while the heap barely moves.\n\n")
                .append("| Stage | Heap used (MiB) | RSS (MiB) |\n|---|---:|---:|\n");
        memory.forEach(row -> results.append("| ").append(String.join(" | ", row)).append(" |\n"));
        results.append("\n").append(jmhSection(jmh));
        write("results.md", results.toString());
        write("eval.md", header + evalSection(queries, evals));
    }

    // ---- precision@5 -----------------------------------------------------------------------------

    static List<Query> readQueries() {
        List<Query> queries = new ArrayList<>();
        for (JsonNode node : JSON.readTree(QUERIES.toFile())) {
            Set<String> relevant = new HashSet<>();
            node.get("relevant").forEach(id -> relevant.add(id.asString()));
            queries.add(new Query(node.get("query").asString(), node.get("kind").asString(), relevant));
        }
        return queries;
    }

    /** precision@5 = (relevant results in the top 5) / 5. */
    static Eval evaluate(String name, List<Query> queries, Function<String, List<Hit>> search) {
        List<Double> precision = queries.stream()
                .map(q -> search.apply(q.query()).stream().filter(hit -> q.relevant().contains(hit.id())).count() / (double) K)
                .toList();
        return new Eval(name, precision);
    }

    static String evalSection(List<Query> queries, List<Eval> evals) {
        double ceiling = queries.stream().mapToDouble(q -> Math.min(q.relevant().size(), K) / (double) K).average().orElse(0);
        StringBuilder md = new StringBuilder("## precision@5\n\n")
                .append(String.format("%d labeled queries from bench/data/queries.json. Best possible mean: %.3f "
                        + "(a query with 3 relevant postings scores at most 3/5).%n%n", queries.size(), ceiling))
                .append("| Setup | Mean | Skill queries | Role queries |\n|---|---:|---:|---:|\n");
        for (Eval e : evals) {
            md.append(String.format("| %s | %.3f | %.3f | %.3f |%n", e.name(), e.mean(queries, null), e.mean(queries, "skill"), e.mean(queries, "role")));
        }
        md.append("\n## Per query\n\n| Query | Kind | Relevant | ")
                .append(evals.stream().map(Eval::name).collect(Collectors.joining(" | ")))
                .append(" |\n|---|---|---:|").append("---:|".repeat(evals.size())).append("\n");
        for (int i = 0; i < queries.size(); i++) {
            Query q = queries.get(i);
            md.append("| ").append(q.query()).append(" | ").append(q.kind()).append(" | ").append(q.relevant().size());
            for (Eval e : evals) {
                md.append(String.format(" | %.1f", e.precision().get(i)));
            }
            md.append(" |\n");
        }
        return md.toString();
    }

    // ---- JMH -------------------------------------------------------------------------------------

    static String jmhSection(Collection<RunResult> results) {
        StringBuilder md = new StringBuilder("## Latency (JMH)\n\n`EmbedSearchBench`: 1 fork, 3x2 s warmup, 5x2 s measurement. "
                + "Search = top-5 over random 384-d unit vectors.\n\n| Benchmark | Documents | p50 (ms) | p99 (ms) |\n|---|---:|---:|---:|\n");
        for (RunResult r : results) {
            String name = r.getParams().getBenchmark().substring(r.getParams().getBenchmark().lastIndexOf('.') + 1);
            String size = r.getParams().getParam("size");
            Statistics stats = r.getPrimaryResult().getStatistics(); // microseconds
            md.append(String.format("| %s | %s | %.3f | %.3f |%n", name, size == null ? "-" : size,
                    stats.getPercentile(50) / 1000, stats.getPercentile(99) / 1000));
        }
        return md.append("\n").toString();
    }

    // ---- machine, memory, output -----------------------------------------------------------------

    static String header() {
        String power = powerSource();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        StringBuilder md = new StringBuilder();
        if (power.startsWith("BATTERY")) {
            md.append("> **WARNING: measured on battery.** Laptops slow the CPU down on battery; expect slower numbers than on AC power.\n\n");
        }
        return md.append("# Benchmark results (").append(LocalDate.now()).append(")\n\n")
                .append("| Machine | |\n|---|---|\n")
                .append("| CPU | ").append(cpuName()).append(" |\n")
                .append("| Logical processors | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n")
                .append(String.format("| RAM | %.1f GiB |%n", os.getTotalMemorySize() / (1024.0 * 1024 * 1024)))
                .append("| OS | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(" |\n")
                .append("| JVM | ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.runtime.version")).append(" |\n")
                .append("| ONNX Runtime | ").append(OrtEnvironment.getEnvironment().getVersion()).append(" |\n")
                .append("| Power | ").append(power).append(" |\n\n")
                .toString();
    }

    static String[] memory(String stage) {
        System.gc();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long rss = residentSetBytes();
        return new String[] {stage, mib(heap), rss < 0 ? "n/a" : mib(rss)};
    }

    /** Resident set size (working set on Windows), or -1 if this OS isn't supported. */
    static long residentSetBytes() {
        String os = System.getProperty("os.name").toLowerCase();
        long pid = ProcessHandle.current().pid();
        try {
            if (os.contains("linux")) {
                return Files.readAllLines(Path.of("/proc/self/status")).stream().filter(l -> l.startsWith("VmRSS:"))
                        .findFirst().map(l -> Long.parseLong(l.replaceAll("\\D", "")) * 1024).orElse(-1L);
            } else if (os.contains("win")) {
                return Long.parseLong(run("powershell", "-NoProfile", "-Command", "(Get-Process -Id " + pid + ").WorkingSet64"));
            } else if (os.contains("mac")) {
                return Long.parseLong(run("ps", "-o", "rss=", "-p", String.valueOf(pid))) * 1024;
            }
        } catch (IOException | RuntimeException e) {
            // unsupported, reported as n/a
        }
        return -1;
    }

    static String powerSource() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String status = run("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Battery).BatteryStatus");
                return status.isBlank() ? "AC (no battery)" : status.equals("1") ? "BATTERY (discharging)" : "AC";
            } else if (os.contains("linux") && Files.exists(Path.of("/sys/class/power_supply/AC/online"))) {
                return Files.readString(Path.of("/sys/class/power_supply/AC/online")).trim().equals("1") ? "AC" : "BATTERY";
            } else if (os.contains("mac")) {
                return run("pmset", "-g", "batt").contains("Battery Power") ? "BATTERY" : "AC";
            }
        } catch (IOException | RuntimeException e) {
            // unknown
        }
        return "unknown";
    }

    static String cpuName() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("linux")) {
                return Files.readAllLines(Path.of("/proc/cpuinfo")).stream().filter(l -> l.startsWith("model name"))
                        .findFirst().map(l -> l.substring(l.indexOf(':') + 1).trim()).orElse("unknown");
            } else if (os.contains("win")) {
                return run("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name");
            } else if (os.contains("mac")) {
                return run("sysctl", "-n", "machdep.cpu.brand_string");
            }
        } catch (IOException | RuntimeException e) {
            // unknown
        }
        return "unknown";
    }

    static String run(String... command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    static void write(String file, String content) throws IOException {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve(file), content);
        System.out.println("wrote " + OUT.resolve(file));
    }

    static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    static String mib(long bytes) {
        return String.format("%.0f", bytes / (1024.0 * 1024));
    }
}
