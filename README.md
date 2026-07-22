Semantic Search Plugin for Fess
===============================

[![Java CI with Maven](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.codelibs.fess/fess-webapp-semantic-search/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.codelibs.fess/fess-webapp-semantic-search)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Overview

This plugin provides `OpenSearchEmbeddingClient`, an embedding provider for the
content-chunk pipeline built into [Fess](https://fess.codelibs.org/) 15.8 and later.
It generates text embeddings by calling the OpenSearch ML Commons Predict API
against a pre-deployed text-embedding model — by default on the **same OpenSearch
cluster Fess already uses as its search engine**, so no additional inference
service is needed.

Everything else lives in Fess itself: Fess core chunks document content, calls
the embedding provider selected by `content_chunker.embedding.name`, indexes the
chunk vectors (Content Chunk Vector Indexer job), uses them for RAG-chat chunk
selection, and blends semantic results into search via its rank-fusion searcher
(`SemanticChunkSearcher`, with automatic exact/ANN k-NN selection). This plugin's
sole job is to turn text into vectors; **it does nothing at search time itself**.

Since exactly one embedding client is active at a time, setting
`content_chunker.embedding.name=opensearch` selects this plugin (over e.g. the
Ollama provider from fess-llm-ollama).

> [!IMPORTANT]
> Version 15.8.0 is a complete rewrite. The former neural-search implementation
> (query rewriting, `neural_pipeline` ingest pipeline, `content_vector` mappings,
> `fess.semantic_search.*` properties) has been removed. If you are upgrading
> from 15.7.x or earlier, read the [Migration](#migration-from-157x-or-earlier)
> section — the old index keeps running the old ingest pipeline until you detach
> it or reindex.

## Download

See [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/fess/fess-webapp-semantic-search/).

## Requirements

- Fess 15.8 or later (the `content_chunker` pipeline and the embedding SPI are part of Fess core as of 15.8)
- OpenSearch 3.x with the `opensearch-ml` (ML Commons) plugin installed as the Fess search engine
- A registered and **deployed** ML Commons text-embedding model (see [Model Setup](#model-setup))
- Java 21 or later

## Installation

1. Download the plugin JAR from the Maven Repository
2. Install it via Admin > Plugin, or place it in `webapp/WEB-INF/plugin/` (Docker: use the `FESS_PLUGINS` environment variable, e.g. `FESS_PLUGINS=fess-webapp-semantic-search:15.8.0`)
3. Restart Fess

For detailed instructions, see the [Plugin Administration Guide](https://fess.codelibs.org/15.8/admin/plugin-guide.html).

## Model Setup

The plugin does not manage models: the model must be registered and deployed on
the OpenSearch cluster before Fess can embed anything. `tools/setup.sh` in this
repository automates all of the steps below (check out the tag matching your
plugin version and run `./tools/setup.sh http://localhost:9200`; append curl
options such as `-u admin:password` for a secured cluster).

### 1. Cluster settings

On a cluster without dedicated ML nodes (the common single-node Fess setup),
allow ML tasks on data nodes:

```
PUT /_cluster/settings
{"persistent": {"plugins.ml_commons.only_run_on_ml_node": false}}
```

Note: model deployment is subject to the ML Commons memory circuit breaker. On a
heap-pressured cluster (e.g. a node co-located with heavy indexing) the deploy
step can fail with `DEPLOY_FAILED: "Memory Circuit Breaker is open"`. If that
happens, free heap or raise `plugins.ml_commons.jvm_heap_memory_threshold` /
`plugins.ml_commons.native_memory_threshold` (default 85) temporarily.

### 2. Register the model

Register a pretrained model from the [OpenSearch pretrained model registry](https://docs.opensearch.org/latest/ml-commons-plugin/pretrained-models/):

```
POST /_plugins/_ml/models/_register
{
  "name": "huggingface/sentence-transformers/all-MiniLM-L6-v2",
  "version": "1.0.2",
  "model_format": "TORCH_SCRIPT"
}
```

This returns a `task_id`. Poll `GET /_plugins/_ml/tasks/{task_id}` until
`"state": "COMPLETED"`; the completed task carries the `model_id`.

### 3. Deploy the model

```
POST /_plugins/_ml/models/{model_id}/_deploy
```

Again poll the returned task until `COMPLETED`, then verify with
`GET /_plugins/_ml/models/{model_id}` that `model_state` is `DEPLOYED`.
A model that is merely `REGISTERED` is rejected by the Predict API
("Model not ready yet") — ML Commons does not auto-deploy local models on
predict. To have models redeployed automatically after a cluster restart,
consider enabling `plugins.ml_commons.model_auto_redeploy.enable`.

## Configuration

Configuration is split across two places, matching Fess core conventions.

### System properties (Admin > General > System Properties, or `system.properties`)

Shared content-chunker settings, provider-independent:

| Property | Default | Description |
|----------|---------|-------------|
| `content_chunker.enabled` | `false` | Set to `true` to enable the content-chunk pipeline. |
| `content_chunker.embedding.name` | `ollama` | **Must be set to `opensearch`** to use this plugin — the default selects the Ollama provider. |
| `content_chunker.embedding.dimension` | (none — **required**) | Embedding vector dimension. Must match the deployed model; there is no auto-detection. E.g. `384` for `all-MiniLM-L6-v2`, `768` for `all-mpnet-base-v2`. |
| `content_chunker.search.enabled` | `false` | Set to `true` to let Fess's rank-fusion searcher (`SemanticChunkSearcher`) blend chunk-vector k-NN results into search. Requires an index created/reindexed with the chunk-vector mapping. |

### `fess_config.properties`

Provider-specific settings for this plugin:

| Property | Default | Description |
|----------|---------|-------------|
| `content_chunker.embedding.opensearch.api.url` | (blank) | OpenSearch base URL. When blank, falls back to the search-engine address Fess is already using (`fess.search_engine.http_address`), then `http://localhost:9200`. Usually leave it unset. |
| `content_chunker.embedding.opensearch.model.id` | (none — **required**) | ML Commons `model_id` of the deployed text-embedding model (printed by `tools/setup.sh`). Re-read on each call, so the model can be swapped without a restart. |
| `content_chunker.embedding.opensearch.username` | (blank) | Basic-auth username. When blank, falls back to `search_engine.username` — a secured cluster that Fess can already reach needs no extra auth config here. Note: the fallback also applies when `api.url` points at a *different* cluster; set explicit credentials in that case so the search engine's credentials are not sent elsewhere. |
| `content_chunker.embedding.opensearch.password` | (blank) | Basic-auth password. When blank, falls back to `search_engine.password`. |
| `content_chunker.embedding.opensearch.timeout` | `60000` | Response/read timeout (ms). |
| `content_chunker.embedding.opensearch.connect.timeout` | `5000` | TCP connect timeout (ms). |
| `content_chunker.embedding.opensearch.retry.max` | `3` | Maximum total attempts on retryable HTTP errors (429/500/502/503/504) and connect-time IOExceptions. 429 typically means the ML memory circuit breaker is open. |
| `content_chunker.embedding.opensearch.retry.base.delay.ms` | `2000` | Base delay (ms) for exponential backoff with ±20% jitter. |
| `content_chunker.embedding.opensearch.availability.check.interval` | `60` | Interval (seconds) for checking that the model is `DEPLOYED`. |
| `content_chunker.embedding.opensearch.document.prefix` | (empty) | Prefix prepended to document/chunk texts before embedding. Pretrained OpenSearch models need none; set e.g. `passage: ` for e5-style models. |
| `content_chunker.embedding.opensearch.query.prefix` | (empty) | Prefix prepended to query texts before embedding (e.g. `query: ` for e5-style models). |

Minimal configuration:

```properties
# System properties (Admin > General > System Properties)
content_chunker.enabled=true
content_chunker.embedding.name=opensearch
content_chunker.embedding.dimension=384

# fess_config.properties
content_chunker.embedding.opensearch.model.id=<your-model-id>
```

## Verification Flow

1. Configure the properties above and restart Fess.
2. Crawl your content as usual.
3. Enable and run the **Content Chunk Vector Indexer** job (Admin > Scheduler; it is registered but disabled by default). It chunks indexed documents, embeds the chunks through this plugin, and stores the vectors.
4. To use the vectors at search time, set `content_chunker.search.enabled=true`. Note the chunk-vector k-NN mapping is spliced in only at **index creation** — on an index created before this configuration was in place, run a reindex via Admin > Maintenance first.
5. Check `fess.log`/`fess-chunk.log` for embedding activity; a misconfigured dimension or an undeployed model is reported there.

## Remote (Connector-Based) Models and Non-Goals

- **Remote models are supported only conditionally.** The plugin calls the model-scoped Predict API (`POST /_plugins/_ml/models/{id}/_predict`) with a `text_docs` input. Remote connector models whose connectors use the standard embedding pre/post-process functions (e.g. `connector.pre_process.openai.embedding`, `.cohere.embedding`, `.default.embedding`) return the same `sentence_embedding` tensor shape and should work unchanged, but this is not guaranteed: connectors with custom Painless scripts or no post-process function return connector-specific output that the plugin rejects. The plugin does not send connector `parameters` bodies.
- **No model management**: no register/deploy/undeploy or auto-deploy. The model must be pre-deployed by the operator.
- **No asymmetric-embedding `content_type` parameter**; use the `document.prefix`/`query.prefix` settings for e5-style models instead.
- **No custom TLS truststore and no auth beyond basic auth** (no AWS SigV4, no API-key headers). HTTPS endpoints work with the JVM default truststore.
- **No dimension auto-discovery**: `content_chunker.embedding.dimension` is authoritative; a mismatch with the model's reported dimension is only warned about.
- **One `model.id` per configuration**; no per-request model switching.

## Migration from 15.7.x or Earlier

Plugin versions up to 15.7.x implemented semantic search inside the plugin
(query rewriting to `neural` queries, a `neural_pipeline` ingest pipeline, and
`content_vector`/`content_chunk` index mappings). All of that is gone; Fess core
now owns chunking, ingestion, and semantic rank fusion. To migrate:

1. **Remove the old configuration.** The old plugin read `fess.semantic_search.*`
   keys as raw JVM options, so remove all `-Dfess.semantic_search.*` flags
   (pipeline, content.field/nested_field/chunk_field, dimension, method,
   engine, space_type, model_id, min_score, param.*, mmr.*, batch_inference.*,
   performance.monitoring.*) from `FESS_JAVA_OPTS`/`fess.in.sh`, plus any copies
   someone placed in Admin > General > System Properties. They are dead keys
   and only cause confusion.
2. **Detach the old ingest pipeline from the index.** The old plugin set
   `default_pipeline=neural_pipeline` as an *index setting*, so the pipeline
   keeps running on every write even after the plugin jar is removed — and
   fails the write if the old model is ever undeployed or deleted. Either
   recreate the index (recommended, see step 3) or unset it explicitly:
   ```
   PUT /fess.YYYYMMDD/_settings
   {"index": {"default_pipeline": null}}
   ```
3. **Reindex (recommended: recreate).** The old `content_vector`,
   `content_chunk`, and `index.knn` artifacts remain as dead weight in the old
   index, and the new chunk-vector k-NN mapping is only added at index creation.
   With the new configuration in place (plugin 15.8.0 installed,
   `content_chunker.*` properties set), run a reindex via Admin > Maintenance so
   a fresh index is created with the new mapping, then run the Content Chunk
   Vector Indexer job.
4. **Optionally clean up cluster-side leftovers**: delete the `neural_pipeline`
   ingest pipeline (`DELETE /_ingest/pipeline/neural_pipeline`) once no index
   references it. The registered/deployed embedding model itself is still used
   by this plugin — keep it.
5. **Expect different search behavior.** Semantic results are now blended by
   Fess core's rank-fusion searcher instead of the plugin rewriting your query
   into a `neural` query. Ranking will differ. Side effects of the old
   implementation no longer apply: multi-word queries are no longer
   auto-quoted, and the old "multi-word query + field filter returns HTTP 400,
   quote the query as a workaround" issue is gone along with the workaround.

## Development

### Building from Source

```bash
mvn clean package
```

Note: this plugin depends on Fess 15.8.0-SNAPSHOT (the `org.codelibs.fess.embedding` SPI), resolved from the Maven snapshot repository until Fess 15.8.0 is released.

### Running Tests

```bash
mvn test
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Links

- [Fess Official Website](https://fess.codelibs.org/)
- [OpenSearch ML Commons](https://docs.opensearch.org/latest/ml-commons-plugin/)
- [OpenSearch Pretrained Models](https://docs.opensearch.org/latest/ml-commons-plugin/pretrained-models/)
- [Issue Tracker](https://github.com/codelibs/fess-webapp-semantic-search/issues)
- [Fess Community](https://discuss.codelibs.org/)
