package io.github.closeup1202.lofi.core.persistence;

public final class LofiSqlQueries {

    public static final String STATS_BY_METHOD = """
            WITH ranked AS (
                SELECT
                    class_name,
                    method_name,
                    elapsed_ns,
                    COUNT(*) OVER (PARTITION BY class_name, method_name)                         AS total_cnt,
                    ROW_NUMBER() OVER (PARTITION BY class_name, method_name ORDER BY elapsed_ns) AS rn
                FROM method_metric
                WHERE commit_hash = ?
            ),
            p_targets AS (
                SELECT DISTINCT class_name, method_name, total_cnt,
                    (total_cnt * 95 + 99) / 100 AS p95_rn,
                    (total_cnt * 99 + 99) / 100 AS p99_rn
                FROM ranked
            )
            SELECT
                r.class_name,
                r.method_name,
                AVG(r.elapsed_ns)                                     AS avg_ns,
                t.total_cnt                                           AS cnt,
                MAX(CASE WHEN r.rn = t.p95_rn THEN r.elapsed_ns END) AS p95_ns,
                MAX(CASE WHEN r.rn = t.p99_rn THEN r.elapsed_ns END) AS p99_ns
            FROM ranked r
            JOIN p_targets t ON r.class_name = t.class_name AND r.method_name = t.method_name
            GROUP BY r.class_name, r.method_name
            """;

    private LofiSqlQueries() {}
}
