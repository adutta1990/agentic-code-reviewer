package com.ai.agents.ingestion;

import com.ai.agents.config.IncidentAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the runbook corpus into pgvector on startup.
 *
 * <h2>Why there is no chunking here</h2>
 * The reflex in a RAG pipeline is to run a {@code TokenTextSplitter} over everything. That
 * would be wrong for this corpus. Each runbook is a small (~1–3KB), tightly-structured
 * document — Meaning, Impact, Diagnosis, Mitigation — and those sections only make sense
 * together. Splitting produces chunks like a bare "## Mitigation" fragment that retrieves
 * well on the word "mitigation" but is useless without the alert it belongs to, and it
 * lets a single runbook occupy every top-k slot with its own fragments.
 *
 * <p>So the retrieval unit is the whole runbook: one document per alert. The corpus is
 * ~108 documents averaging well under the embedding model's 8191-token limit, so nothing
 * is lost. Chunking would be the right call for a corpus of long-form postmortems; it is
 * the wrong call here. If you add larger documents later, split those — not these.
 */
@Service
public class RunbookIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunbookIngestionService.class);

    /** Hugo front matter block at the top of each runbook. */
    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("^title:\\s*(.+)$", Pattern.MULTILINE);
    /** Filenames are flattened to <component>__<AlertName>.md by scripts/download-corpus.sh. */
    private static final Pattern FILENAME = Pattern.compile("^(.+?)__(.+)\\.md$");

    /** Postgres identifier, so the table name can be interpolated into DDL without injection risk. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final IncidentAgentProperties props;
    private final String tableName;

    public RunbookIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, IncidentAgentProperties props,
                                   @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        // Read from the same property Spring AI uses, rather than hardcoding: changing
        // table-name in application.yaml would otherwise silently break ingestion.
        if (!SAFE_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Unsafe pgvector table-name: " + tableName);
        }
        this.tableName = tableName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var cfg = props.getIngestion();
        if (!cfg.isEnabled()) {
            log.info("Runbook ingestion disabled; skipping.");
            return;
        }

        long existing = countExisting();
        if (existing > 0 && !cfg.isForce()) {
            log.info("Vector store already holds {} runbooks; skipping ingestion. "
                    + "Set incident-agent.ingestion.force=true to rebuild.", existing);
            return;
        }
        if (existing > 0) {
            log.warn("force=true — clearing {} existing runbook vectors before re-ingesting.", existing);
            jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        }

        List<Document> docs = loadCorpus(cfg.getCorpusLocation());
        if (docs.isEmpty()) {
            log.error("No runbooks found at '{}'. Run ./scripts/download-corpus.sh first.", cfg.getCorpusLocation());
            return;
        }

        log.info("Embedding and storing {} runbooks (this makes {} embedding API calls)...", docs.size(), docs.size());
        long start = System.currentTimeMillis();
        vectorStore.add(docs);
        log.info("Ingested {} runbooks in {}ms.", docs.size(), System.currentTimeMillis() - start);
    }

    private long countExisting() {
        try {
            Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            // Table not created yet — Spring AI creates it lazily with initialize-schema: true.
            log.debug("Could not count existing vectors (table likely not created yet): {}", e.getMessage());
            return 0;
        }
    }

    private List<Document> loadCorpus(String location) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(location);
        List<Document> docs = new ArrayList<>(resources.length);

        for (Resource r : resources) {
            String filename = r.getFilename();
            if (filename == null) continue;

            String raw;
            try (var in = r.getInputStream()) {
                raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            String component = "general";
            String alertName = filename.replace(".md", "");
            Matcher fm = FILENAME.matcher(filename);
            if (fm.matches()) {
                component = fm.group(1);
                alertName = fm.group(2);
            }

            String title = alertName;
            String body = raw;
            Matcher matter = FRONT_MATTER.matcher(raw);
            if (matter.find()) {
                Matcher t = TITLE.matcher(matter.group(1));
                if (t.find()) {
                    title = t.group(1).trim();
                }
                // Drop the front matter: it is Hugo rendering config (weight, etc.) and
                // embedding it only adds noise to the vector.
                body = raw.substring(matter.end());
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("alertName", alertName);
            metadata.put("component", component);
            metadata.put("title", title);
            metadata.put("source", "prometheus-operator/runbooks");

            // Prepend the alert name to the embedded text. Retrieval queries lead with
            // "Alert: KubePodCrashLooping", so having that token in the document text itself
            // measurably improves the match over relying on prose alone.
            String text = "Alert: %s\nComponent: %s\n\n%s".formatted(alertName, component, body.strip());

            docs.add(new Document(text, metadata));
        }
        return docs;
    }
}
