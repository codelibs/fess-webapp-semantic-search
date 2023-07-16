Semantic Search WebApp Plugin for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml)
==========================

## Overview

This is a semantic-search plugin for Fess webapp.

## Download

See [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/fess/fess-webapp-semantic-search/).

## Installation

See [Plugin](https://fess.codelibs.org/14.9/admin/plugin-guide.html) of Administration guide.

## Getting Started

### Download docker-fess

```sh
git clone --branch v14.9.1 --single-branch https://github.com/codelibs/docker-fess.git
cd docker-fess/compose
```

### Add the following line in compose.yaml

```
      - "FESS_PLUGINS=fess-webapp-semantic-search:14.9.0"
```

### Start Fess and OpenSerach

```sh
docker compose -f compose.yaml -f compose-opensearch2.yaml up -d
```

### Run Setup Script

```sh
curl -o setup.sh https://github.com/codelibs/fess-webapp-semantic-search/blob/fess-webapp-semantic-search-14.9.0/tools/setup.sh
/bin/bash setup.sh
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

The above settings are printed by setup.sh.

### Create a new index

In Admin > Maintenance page, start reindexing.
Then, create a crawling config, start a crawler, and search them.
