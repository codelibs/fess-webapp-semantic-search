#!/bin/bash

opensearch_host=$1
pipeline_name=neural_pipeline
tmp_file=/tmp/output.$$

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

echo "Checking ${opensearch_host}..."
if ! curl -o ${tmp_file} -s -XGET -H "Content-Type:application/json" "${opensearch_host}" ; then
  echo "${opensearch_host} is not available."
  exit 1
fi

echo "Uploading language model..."
curl -o ${tmp_file} -s -XPOST -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/models/_upload" \
--data-raw '{
  "name": "multi-qa-MiniLM-L6-cos-v1",
  "version": "1.0.1",
  "description": "language model",
  "model_format": "TORCH_SCRIPT",
  "model_config": {
    "model_type": "bert",
    "embedding_dimension": 384,
    "framework_type": "sentence_transformers"
  },
  "url": "https://artifacts.opensearch.org/models/ml-models/huggingface/sentence-transformers/multi-qa-MiniLM-L6-cos-v1/1.0.1/torch_script/sentence-transformers_multi-qa-MiniLM-L6-cos-v1-1.0.1-torch_script.zip"
}'

task_id=$(cat ${tmp_file} | jq -r .task_id)
if [[ ${task_id} = "null" ]] ; then
  echo "Failed to run a task: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

echo -n "Checking task:${task_id}"
ret=RUNNING
while [ $ret = "CREATED" ] || [ $ret = "RUNNING" ] ; do
  sleep 1
  curl -o ${tmp_file} -s -XGET -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/tasks/${task_id}"
  ret=$(cat ${tmp_file} | jq -r .state)
  model_id=$(cat ${tmp_file} | jq -r .model_id)
  echo -n "."
done
echo

echo "Loading model:${model_id}..."
curl -o ${tmp_file} -s -XPOST -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/models/${model_id}/_load"

task_id=$(cat ${tmp_file} | jq -r .task_id)
if [[ ${task_id} = "null" ]] ; then
  echo "Failed to run a task: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

echo -n "Checking task:${task_id}"
ret=RUNNING
while [ $ret = "CREATED" ] || [ $ret = "RUNNING" ] ; do
  sleep 1
  curl -o ${tmp_file} -s -XGET -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/tasks/${task_id}"
  ret=$(cat ${tmp_file} | jq -r .state)
  model_id=$(cat ${tmp_file} | jq -r .model_id)
  echo -n "."
done
echo

echo "Setting pipeline:${pipeline_name}..."
curl -o ${tmp_file} -s -XPUT -H "Content-Type:application/json" "${opensearch_host}/_ingest/pipeline/${pipeline_name}" \
--data-raw '{
  "description": "A neural search pipeline",
  "processors" : [
    {
      "text_embedding": {
        "model_id": "'"${model_id}"'",
        "field_map": {
           "content": "content_vector"
        }
      }
    }
  ]
}'

acknowledged=$(cat ${tmp_file} | jq -r .acknowledged)
if [[ ${acknowledged} != "true" ]] ; then
  echo "Failed to craete ${pipeline_name}"$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

cat << EOS
--- system properties: start ---
fess.semantic_search.pipeline=${pipeline_name}
fess.semantic_search.content.field=content_vector
fess.semantic_search.content.dimension=384
fess.semantic_search.content.method=hnsw
fess.semantic_search.content.engine=lucene
fess.semantic_search.content.model_id=${model_id}
--- system properties: end ---
EOS

rm -f $tmp_file
