# Fess Semantic Search Plugin

[![Java CI with Maven](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-webapp-semantic-search/actions/workflows/maven.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.codelibs.fess/fess-webapp-semantic-search/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.codelibs.fess/fess-webapp-semantic-search)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A powerful semantic search plugin for [Fess](https://fess.codelibs.org/), the open-source enterprise search server. This plugin extends Fess's search capabilities by integrating neural search using OpenSearch's machine learning features and vector similarity search.

## ✨ Features

- **Neural Search Integration**: Leverages OpenSearch ML Commons plugin for semantic vector search
- **Automatic Query Rewriting**: Converts traditional text queries to neural queries when appropriate
- **Rank Fusion Processing**: Combines traditional and semantic search results for improved relevance
- **Content Chunking**: Processes long documents in chunks for better semantic matching
- **Configurable Models**: Supports multiple pre-trained transformer models from HuggingFace
- **Seamless Integration**: Works as a drop-in plugin for existing Fess installations

## 🚀 Quick Start

### Prerequisites

- Fess 15.0+ (Full-text Enterprise Search Server)
- OpenSearch 2.x with ML Commons plugin enabled
- Docker and Docker Compose (recommended for setup)

### 1. Clone and Setup Docker Environment

```bash
git clone https://github.com/codelibs/docker-fess.git
cd docker-fess/compose
```

### 2. Configure Plugin in Docker Compose

Add the following line to your `compose.yaml`:

```yaml
environment:
  - "FESS_PLUGINS=fess-webapp-semantic-search:15.1.0"
```

### 3. Start Services

```bash
docker compose -f compose.yaml -f compose-opensearch2.yaml up -d
```

### 4. Initialize ML Models and Pipeline

Download and run the setup script:

```bash
curl -o setup.sh https://raw.githubusercontent.com/codelibs/fess-webapp-semantic-search/main/tools/setup.sh
chmod +x setup.sh
./setup.sh localhost:9200
```

The setup script will:
- Display available pre-trained models
- Register your selected model in OpenSearch
- Create the neural search pipeline
- Provide the configuration settings

### 5. Configure Fess

In Fess Admin Panel (Admin > General > System Properties), add the configuration provided by the setup script:

```properties
fess.semantic_search.pipeline=neural_pipeline
fess.semantic_search.content.field=content_vector
fess.semantic_search.content.dimension=384
fess.semantic_search.content.method=hnsw
fess.semantic_search.content.engine=lucene
fess.semantic_search.content.space_type=cosinesimil
fess.semantic_search.content.model_id=<your-model-id>
```

#### Optional: Performance Tuning (v15.3.0+)

For better performance, you can add these optional parameters:

```properties
# HNSW search-time parameter (higher = better recall, slower search)
fess.semantic_search.content.param.ef_search=100

# Enable performance monitoring for debugging
fess.semantic_search.performance.monitoring.enabled=true

# Enable batch inference (requires compatible ML model setup)
fess.semantic_search.batch_inference.enabled=true
```

#### Optional: Diversity with MMR (Experimental)

To improve result diversity using Maximal Marginal Relevance:

```properties
# Enable MMR
fess.semantic_search.mmr.enabled=true

# Lambda: 1.0 = only relevance, 0.0 = only diversity, 0.5 = balanced
fess.semantic_search.mmr.lambda=0.7
```

### 6. Create Index and Start Crawling

1. Go to Admin > Maintenance and start reindexing
2. Create your crawling configuration
3. Start the crawler
4. Begin semantic searching!

## 📖 Available Models

The plugin supports various pre-trained transformer models:

| Model | Dimension | Description |
|-------|-----------|-------------|
| all-MiniLM-L6-v2 | 384 | Fast and efficient, good for general use |
| all-mpnet-base-v2 | 768 | Higher quality, slower performance |
| all-distilroberta-v1 | 768 | RoBERTa-based, good performance |
| msmarco-distilbert-base-tas-b | 768 | Optimized for passage retrieval |
| multi-qa-MiniLM-L6-cos-v1 | 384 | Specialized for question answering |
| paraphrase-multilingual-MiniLM-L12-v2 | 384 | Multilingual support |

## ⚙️ Configuration Options

### Core Settings

| Property | Description | Default |
|----------|-------------|---------|
| `fess.semantic_search.pipeline` | Neural search pipeline name | - |
| `fess.semantic_search.content.model_id` | ML model ID in OpenSearch | - |
| `fess.semantic_search.content.field` | Vector field name | - |
| `fess.semantic_search.content.dimension` | Vector dimension size | - |

### Advanced Settings

| Property | Description | Default |
|----------|-------------|---------|
| `fess.semantic_search.content.method` | Vector search method | `hnsw` |
| `fess.semantic_search.content.engine` | Vector search engine | `lucene` |
| `fess.semantic_search.content.space_type` | Distance calculation method | `cosinesimil` |
| `fess.semantic_search.min_score` | Minimum similarity score | - |
| `fess.semantic_search.min_content_length` | Minimum content length for processing | - |
| `fess.semantic_search.content.chunk_size` | Number of chunks to return | `1` |

### HNSW Parameters

| Property | Description | Default |
|----------|-------------|---------|
| `fess.semantic_search.content.param.m` | HNSW M parameter (higher = better recall, more memory) | `16` |
| `fess.semantic_search.content.param.ef_construction` | HNSW ef_construction parameter (higher = better quality, slower indexing) | `100` |
| `fess.semantic_search.content.param.ef_search` | HNSW ef_search parameter (higher = better recall, slower search) | Not set (OpenSearch default) |

### Performance Tuning (v15.3.0+)

| Property | Description | Default |
|----------|-------------|---------|
| `fess.semantic_search.performance.monitoring.enabled` | Enable detailed performance logging | `false` |
| `fess.semantic_search.batch_inference.enabled` | Enable batch inference for better GPU utilization | `false` |

### Experimental Features (v15.3.0+)

| Property | Description | Default |
|----------|-------------|---------|
| `fess.semantic_search.mmr.enabled` | Enable Maximal Marginal Relevance for diversity | `false` |
| `fess.semantic_search.mmr.lambda` | MMR lambda (1.0=relevance, 0.0=diversity) | `0.5` |

## 🏗️ Architecture

### Core Components

- **SemanticSearchHelper**: Central component managing neural search configuration and model interactions
- **NeuralQueryBuilder**: Custom OpenSearch query builder for neural/vector search queries  
- **SemanticPhraseQueryCommand**: Converts phrase queries to neural queries when appropriate
- **SemanticTermQueryCommand**: Handles term-based semantic search queries
- **SemanticSearcher**: Extends Fess's DefaultSearcher for rank fusion processing

### Integration Points

- **Query Processing**: Integrates with Fess's QueryParser to rewrite queries for semantic search
- **Document Processing**: Adds rewrite rules for OpenSearch mapping and settings to support vector fields
- **Rank Fusion**: Registers as a searcher in Fess's rank fusion processor
- **DI Container**: Uses LastaDi for dependency injection

## 🔧 Development

### Building from Source

```bash
git clone https://github.com/codelibs/fess-webapp-semantic-search.git
cd fess-webapp-semantic-search
mvn clean package
```

### Running Tests

```bash
mvn test
```

### Code Quality

```bash
mvn clean compile javadoc:javadoc
```

## 📦 Installation Methods

### Maven Repository

The plugin is available from Maven Central:

```xml
<dependency>
    <groupId>org.codelibs.fess</groupId>
    <artifactId>fess-webapp-semantic-search</artifactId>
    <version>15.1.0</version>
</dependency>
```

### Manual Installation

1. Download the JAR from [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/fess/fess-webapp-semantic-search/)
2. Place it in your Fess webapp/WEB-INF/lib/ directory
3. Restart Fess

### Plugin Management

See the [Fess Plugin Guide](https://fess.codelibs.org/15.0/admin/plugin-guide.html) for detailed installation instructions.

## 🤝 Contributing

We welcome contributions! 

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Run the test suite (`mvn test`)
6. Commit your changes (`git commit -m 'Add some amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

### Code Style

This project uses:
- Maven for build management
- JUnit for testing
- CheckStyle for code formatting
- JavaDoc for documentation

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

- [Fess Official Website](https://fess.codelibs.org/)
- [OpenSearch ML Commons](https://opensearch.org/docs/latest/ml-commons-plugin/)
- [Docker Fess](https://github.com/codelibs/docker-fess)
- [Issue Tracker](https://github.com/codelibs/fess-webapp-semantic-search/issues)

## 🚀 OpenSearch 3.3 Optimization (v15.3.0+)

This plugin is optimized for OpenSearch 3.3 with significant performance improvements and new features:

### Key Improvements
- **Concurrent Segment Search**: Enabled by default, up to 2.5x faster k-NN queries
- **Improved HNSW**: Default `space_type` changed to `cosinesimil` for better semantic search accuracy
- **Performance Monitoring**: Optional detailed query performance tracking
- **Advanced Tuning**: Fine-grained control over HNSW parameters including `ef_search`

### Migration from Earlier Versions
If upgrading from v15.2.x or earlier:
1. The default `space_type` has changed from `l2` to `cosinesimil`
2. To maintain compatibility with existing indices, explicitly set: `fess.semantic_search.content.space_type=l2`
3. For new deployments, the new default `cosinesimil` is recommended

## 📊 Version Compatibility

| Plugin Version | Fess Version | OpenSearch Version |
|----------------|--------------|-------------------|
| 15.3.x | 15.3+ | 3.3.x (recommended) |
| 15.0.x | 15.0+ | 2.x |
| 14.9.x | 14.9+ | 2.x |

## 🆘 Support

- **Documentation**: [Fess Documentation](https://fess.codelibs.org/15.0/)
- **Issues**: [GitHub Issues](https://github.com/codelibs/fess-webapp-semantic-search/issues)
- **Discussions**: [GitHub Discussions](https://github.com/codelibs/fess-webapp-semantic-search/discussions)
- **Community**: [Fess Community](https://discuss.codelibs.org/)

## 🙏 Acknowledgments

- [CodeLibs](https://www.codelibs.org/) for developing and maintaining Fess
- [HuggingFace](https://huggingface.co/) for providing pre-trained transformer models
- [OpenSearch](https://opensearch.org/) team for ML Commons plugin
- All contributors who have helped improve this plugin

