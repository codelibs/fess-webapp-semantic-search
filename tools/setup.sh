#!/bin/bash
#
# Registers and deploys an OpenSearch ML Commons text-embedding model for use
# with the fess-webapp-semantic-search plugin (OpenSearchEmbeddingClient).
#
# Usage:
#   ./setup.sh [opensearch_url] [extra curl options...]
#
#   ./setup.sh                                  # http://localhost:9200
#   ./setup.sh http://localhost:9200
#   ./setup.sh https://search.example.com:9200 -u admin:password
#
# Any arguments after the URL are passed to every curl call, so basic-auth
# clusters work via "-u user:password" (add "-k" for self-signed TLS).
#
# Non-interactive overrides (skip the prompts):
#   MODEL_NAME, MODEL_VERSION, MODEL_FORMAT, DIMENSION
#   e.g. MODEL_NAME=huggingface/sentence-transformers/all-MiniLM-L6-v2 \
#        MODEL_VERSION=1.0.2 DIMENSION=384 ./setup.sh

opensearch_host=$1
if [[ $# -gt 0 ]] ; then
  shift
fi
curl_opts=("$@")
tmp_file=/tmp/output.$$

model_name=${MODEL_NAME:-}
model_version=${MODEL_VERSION:-1.0.2}
model_format=${MODEL_FORMAT:-TORCH_SCRIPT}
dimension=${DIMENSION:-}

if ! which curl > /dev/null; then
  echo "curl command is not found."
  exit 1
fi

if ! which jq > /dev/null; then
  echo "jq command is not found."
  exit 1
fi

if [[ "$opensearch_host" = "" ]] ; then
  opensearch_host=http://localhost:9200
fi

os_curl() {
  curl -o ${tmp_file} -s -H "Content-Type:application/json" "${curl_opts[@]}" "$@"
}

if [[ "${model_name}" = "" ]] ; then
  # https://docs.opensearch.org/latest/ml-commons-plugin/pretrained-models/
  cat <<EOS
Models:
[1]  huggingface/sentence-transformers/all-distilroberta-v1
[2]  huggingface/sentence-transformers/all-MiniLM-L6-v2
[3]  huggingface/sentence-transformers/all-MiniLM-L12-v2
[4]  huggingface/sentence-transformers/all-mpnet-base-v2
[5]  huggingface/sentence-transformers/msmarco-distilbert-base-tas-b
[6]  huggingface/sentence-transformers/multi-qa-MiniLM-L6-cos-v1
[7]  huggingface/sentence-transformers/multi-qa-mpnet-base-dot-v1
[8]  huggingface/sentence-transformers/paraphrase-MiniLM-L3-v2
[9]  huggingface/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
[10] huggingface/sentence-transformers/paraphrase-mpnet-base-v2
[11] huggingface/sentence-transformers/distiluse-base-multilingual-cased-v1
EOS

  echo -n "Which model would you like to use? [2] "
  read input
  case "${input}" in
    "1")
      model_name=huggingface/sentence-transformers/all-distilroberta-v1
      dimension=768
      ;;
    "3")
      model_name=huggingface/sentence-transformers/all-MiniLM-L12-v2
      dimension=384
      ;;
    "4")
      model_name=huggingface/sentence-transformers/all-mpnet-base-v2
      dimension=768
      ;;
    "5")
      model_name=huggingface/sentence-transformers/msmarco-distilbert-base-tas-b
      dimension=768
      ;;
    "6")
      model_name=huggingface/sentence-transformers/multi-qa-MiniLM-L6-cos-v1
      dimension=384
      ;;
    "7")
      model_name=huggingface/sentence-transformers/multi-qa-mpnet-base-dot-v1
      dimension=768
      ;;
    "8")
      model_name=huggingface/sentence-transformers/paraphrase-MiniLM-L3-v2
      dimension=384
      ;;
    "9")
      model_name=huggingface/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
      dimension=384
      ;;
    "10")
      model_name=huggingface/sentence-transformers/paraphrase-mpnet-base-v2
      dimension=768
      ;;
    "11")
      model_name=huggingface/sentence-transformers/distiluse-base-multilingual-cased-v1
      dimension=512
      ;;
    *)
      model_name=huggingface/sentence-transformers/all-MiniLM-L6-v2
      dimension=384
      ;;
  esac

  # Model versions differ per model in the pretrained registry; see
  # https://docs.opensearch.org/latest/ml-commons-plugin/pretrained-models/
  echo -n "Model version? [${model_version}] "
  read input
  if [[ "${input}" != "" ]] ; then
    model_version=${input}
  fi
fi

echo "Selected model: ${model_name} ${model_version} (${model_format}${dimension:+, ${dimension}-dimensional})"

echo "Checking ${opensearch_host}..."
if ! os_curl -XGET "${opensearch_host}" ; then
  echo "${opensearch_host} is not available."
  exit 1
fi

# Allow ML tasks on non-ML nodes (required on clusters without dedicated ML
# nodes, e.g. a single-node Fess setup).
echo "Updating cluster settings (plugins.ml_commons.only_run_on_ml_node=false)..."
os_curl -XPUT "${opensearch_host}/_cluster/settings" \
--data-raw '{
  "persistent": {
    "plugins.ml_commons.only_run_on_ml_node": false
  }
}'

acknowledged=$(cat ${tmp_file} | jq -r .acknowledged)
if [[ ${acknowledged} != "true" ]] ; then
  echo "Failed to update cluster settings: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

wait_for_task() {
  task_id=$1
  echo -n "Checking task:${task_id}"
  ret=RUNNING
  waited=0
  max_wait=${MAX_WAIT:-600}
  while [ "$ret" = "CREATED" ] || [ "$ret" = "RUNNING" ] ; do
    if [ ${waited} -ge ${max_wait} ] ; then
      echo
      echo "Task ${task_id} still ${ret} after ${max_wait}s; giving up (set MAX_WAIT to wait longer)."
      rm -f $tmp_file
      exit 1
    fi
    sleep 1
    waited=$((waited + 1))
    os_curl -XGET "${opensearch_host}/_plugins/_ml/tasks/${task_id}"
    ret=$(cat ${tmp_file} | jq -r .state)
    model_id=$(cat ${tmp_file} | jq -r .model_id)
    echo -n "."
  done
  echo
  if [[ ${ret} != "COMPLETED" ]] ; then
    echo "Task ${task_id} did not complete (state=${ret}): "$(cat ${tmp_file})
    echo "Hint: a DEPLOY_FAILED state with \"Memory Circuit Breaker is open\" means the"
    echo "cluster is low on heap; free memory or raise plugins.ml_commons.jvm_heap_memory_threshold."
    rm -f $tmp_file
    exit 1
  fi
}

echo "Registering model ${model_name}..."
os_curl -XPOST "${opensearch_host}/_plugins/_ml/models/_register" \
--data-raw '{
  "name": "'"${model_name}"'",
  "version": "'"${model_version}"'",
  "model_format": "'"${model_format}"'"
}'

task_id=$(cat ${tmp_file} | jq -r .task_id)
if [[ ${task_id} = "null" ]] || [[ ${task_id} = "" ]] ; then
  echo "Failed to run a task: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

wait_for_task ${task_id}

echo "Deploying model:${model_id}..."
os_curl -XPOST "${opensearch_host}/_plugins/_ml/models/${model_id}/_deploy"

task_id=$(cat ${tmp_file} | jq -r .task_id)
if [[ ${task_id} = "null" ]] || [[ ${task_id} = "" ]] ; then
  echo "Failed to run a task: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

wait_for_task ${task_id}

os_curl -XGET "${opensearch_host}/_plugins/_ml/models/${model_id}"
model_state=$(cat ${tmp_file} | jq -r .model_state)
echo "Model ${model_id} is ${model_state}."
if [ "$model_state" != "DEPLOYED" ] && [ "$model_state" != "PARTIALLY_DEPLOYED" ] ; then
  echo "WARNING: the model is not deployed; Fess will report the embedding provider as unavailable."
fi

cat << EOS
==============================================
Fess Configuration
==============================================
1) System Properties (Admin > General > System Properties, or system.properties):

content_chunker.enabled=true
content_chunker.embedding.name=opensearch
content_chunker.embedding.dimension=${dimension:-<model dimension>}

# Optional: blend chunk-vector results into search (requires an index
# created/reindexed after the settings above are in place):
# content_chunker.search.enabled=true

2) fess_config.properties:

content_chunker.embedding.opensearch.model.id=${model_id}

Then restart Fess, crawl, and enable/run the "Content Chunk Vector Indexer"
job in Admin > Scheduler. See the plugin README for details.
==============================================
EOS

rm -f $tmp_file
