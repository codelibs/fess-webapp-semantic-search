Semantic Search WebApp Plugin for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml)
==========================

## Overview

This is a semantic-search plugin for Fess webapp.

## Download

See [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/fess/fess-webapp-semantic-search/).

## Installation

See [Plugin](https://fess.codelibs.org/14.15/admin/plugin-guide.html) of Administration guide.

## Getting Started

### Download docker-fess

```sh
git clone https://github.com/codelibs/docker-fess.git
cd docker-fess/compose 
```

### Add the following line in compose.yaml

```
      - "FESS_PLUGINS=fess-webapp-semantic-search:14.6.0"
```

### Start Fess and OpenSerach

```sh
docker compose -f compose.yaml -f compose-opensearch2.yaml up -d
```

### Upload a model into OpenSearch

```sh
curl -XPOST -H "Content-Type:application/json" 'http://localhost:9200/_plugins/_ml/models/_upload' \
--data-raw '{
  "name": "all-MiniLM-L6-v2",
  "version": "1.0.0",
  "description": "test model",
  "model_format": "TORCH_SCRIPT",
  "model_config": {
    "model_type": "bert",
    "embedding_dimension": 384,
    "framework_type": "sentence_transformers"
  },
  "url": "https://github.com/opensearch-project/ml-commons/raw/2.x/ml-algorithms/src/test/resources/org/opensearch/ml/engine/algorithms/text_embedding/all-MiniLM-L6-v2_torchscript_sentence-transformer.zip?raw=true"
}'
```
Output:
```
{"task_id":"<task1-id>","status":"CREATED"}
```

### Check the task status

```sh
curl -XGET -H "Content-Type:application/json" 'http://localhost:9200/_plugins/_ml/tasks/<task1-id>?pretty'
```
Output:
```
{
  "model_id" : "<model-id>",
  "task_type" : "UPLOAD_MODEL",
  "function_name" : "TEXT_EMBEDDING",
  "state" : "COMPLETED",
  "worker_node" : "...",
  "create_time" : 1672883268742,
  "last_update_time" : 1672883278189,
  "is_async" : true
}
```

### Load the model on OpenSearch

```sh
curl -XPOST -H "Content-Type:application/json" 'http://localhost:9200/_plugins/_ml/models/<model-id>/_load'
```
Output:
```
{"task_id":"<task2-id>","status":"CREATED"}
```

### Check the task status

```sh
curl -XGET -H "Content-Type:application/json" 'http://localhost:9200/_plugins/_ml/tasks/<task2-id>?pretty'
```
Output:
```
{
  "model_id" : "<model-id>",
  "task_type" : "LOAD_MODEL",
  "function_name" : "TEXT_EMBEDDING",
  "state" : "COMPLETED",
  "worker_node" : "...",
  "create_time" : 1672883382428,
  "last_update_time" : 1672883388750,
  "is_async" : true
}
```

### Create a pipeline for indexing

```sh
curl -XPUT -H "Content-Type:application/json" 'http://localhost:9200/_ingest/pipeline/neural_pipeline' \
--data-raw '{
  "description": "An example neural search pipeline",
  "processors" : [
    {
      "text_embedding": {
        "model_id": "<model-id>",
        "field_map": {
           "content": "content_vector"
        }
      }
    }
  ]
}'
```
Output:
```
{"acknowledged":true}
```

### Add settings on Fess

In Admin > General page, add the following value to `System Properties`.

```
fess.semantic_search.pipeline=neural_pipeline
fess.semantic_search.content.field=content_vector
fess.semantic_search.content.dimension=384
fess.semantic_search.content.method=hnsw
fess.semantic_search.content.engine=lucene
fess.semantic_search.content.model_id=<model-id>
```

### Create a new index

In Admin > Maintenance page, start reindexing.

...and then create a crawling config, start a crawler and search them.
