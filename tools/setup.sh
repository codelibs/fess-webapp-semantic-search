#!/bin/bash

opensearch_host=$1
pipeline_name=neural_pipeline
tmp_file=/tmp/output.$$

# https://opensearch.org/docs/latest/ml-commons-plugin/pretrained-models/
cat <<EOS
Models:
[1]  huggingface/sentence-transformers/all-distilroberta-v1
[2]  huggingface/sentence-transformers/all-MiniLM-L6-v2"
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

echo -n "Which model would you like to use? "
read input
case "${input}" in
  "1")
    model_name=huggingface/sentence-transformers/all-distilroberta-v1
    dimension=768
    space_type=cosinesimil
    ;;
  "2")
    model_name=huggingface/sentence-transformers/all-MiniLM-L6-v2
    dimension=384
    space_type=cosinesimil
    ;;
  "3")
    model_name=huggingface/sentence-transformers/all-MiniLM-L12-v2
    dimension=384
    space_type=cosinesimil
    ;;
  "4")
    model_name=huggingface/sentence-transformers/all-mpnet-base-v2
    dimension=768
    space_type=cosinesimil
    ;;
  "5")
    model_name=huggingface/sentence-transformers/msmarco-distilbert-base-tas-b
    dimension=768
    space_type=cosinesimil
    ;;
  "6")
    model_name=huggingface/sentence-transformers/multi-qa-MiniLM-L6-cos-v1
    dimension=384
    space_type=cosinesimil
    ;;
  "7")
    model_name=huggingface/sentence-transformers/multi-qa-mpnet-base-dot-v1
    dimension=768
    space_type=cosinesimil
    ;;
  "8")
    model_name=huggingface/sentence-transformers/paraphrase-MiniLM-L3-v2
    dimension=384
    space_type=cosinesimil
    ;;
  "9")
    model_name=huggingface/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
    dimension=384
    space_type=cosinesimil
    ;;
  "10")
    model_name=huggingface/sentence-transformers/paraphrase-mpnet-base-v2
    dimension=768
    space_type=cosinesimil
    ;;
  "11")
    model_name=huggingface/sentence-transformers/distiluse-base-multilingual-cased-v1
    dimension=512
    space_type=cosinesimil
    ;;
  *)
    input=4
    model_name=huggingface/sentence-transformers/all-mpnet-base-v2
    dimension=768
    space_type=cosinesimil
    ;;
esac

echo "Selected model: [${input}] ${model_name} (${dimension}-dimensional dense vector space)"

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

echo "Registering a language model..."
curl -o ${tmp_file} -s -XPOST -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/model_groups/_register" \
--data-raw '{
  "name": "'${model_name}'",
  "description": "A language model for semantic search."
}'

model_group_id=$(cat ${tmp_file} | jq -r .model_group_id)
if [[ ${model_group_id} = "null" ]] ; then
  echo "Failed to run a task: "$(cat ${tmp_file})
  rm -f $tmp_file
  exit 1
fi

echo "Uploading language model..."
curl -o ${tmp_file} -s -XPOST -H "Content-Type:application/json" "${opensearch_host}/_plugins/_ml/models/_register" \
--data-raw '{
  "name": "'${model_name}'",
  "version": "1.0.1",
  "model_format": "TORCH_SCRIPT",
  "model_group_id": "'${model_group_id}'"
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
      "text_chunking": {
        "algorithm": {
          "fixed_token_length": {
            "token_limit": 100,
            "overlap_rate": 0.1,
            "tokenizer": "standard"
          }
        },
        "field_map": {
          "content": "content_chunk"
        }
      }
    },
    {
      "text_embedding": {
        "model_id": "'"${model_id}"'",
        "field_map": {
           "content_chunk": "content_vector"
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
==============================================
System Properties (Required)
==============================================
Copy these properties to Fess Admin Panel (Admin > General > System Properties):

fess.semantic_search.pipeline=${pipeline_name}
fess.semantic_search.content.nested_field=content_vector
fess.semantic_search.content.chunk_field=content_chunk
fess.semantic_search.content.field=knn
fess.semantic_search.content.dimension=${dimension}
fess.semantic_search.content.method=hnsw
fess.semantic_search.content.engine=lucene
fess.semantic_search.content.space_type=${space_type}
fess.semantic_search.content.model_id=${model_id}
fess.semantic_search.min_score=0.5

==============================================
Optional: Performance Tuning (v15.3.0+)
==============================================
# Uncomment and add these for better performance:

# HNSW search-time parameter (higher = better recall, slower search)
# fess.semantic_search.content.param.ef_search=100

# Enable performance monitoring for debugging
# fess.semantic_search.performance.monitoring.enabled=true

# Enable batch inference (requires compatible ML model setup)
# fess.semantic_search.batch_inference.enabled=true

==============================================
Optional: Diversity with MMR (Experimental)
==============================================
# Uncomment to enable Maximal Marginal Relevance for diverse results:

# Enable MMR
# fess.semantic_search.mmr.enabled=true

# Lambda: 1.0 = only relevance, 0.0 = only diversity, 0.5 = balanced
# fess.semantic_search.mmr.lambda=0.7

==============================================
EOS

rm -f $tmp_file
