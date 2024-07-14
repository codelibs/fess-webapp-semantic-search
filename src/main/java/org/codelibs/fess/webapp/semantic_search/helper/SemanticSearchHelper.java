/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.webapp.semantic_search.helper;

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_CHUNK_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_DIMENSION;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_ENGINE;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_METHOD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_MODEL_ID;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_NESTED_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_PARAM_EF_CONSTRUCTION;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_PARAM_M;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_SPACE_TYPE;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.PIPELINE;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.join.ScoreMode;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.lang.ThreadUtil;
import org.codelibs.curl.CurlResponse;
import org.codelibs.fess.Constants;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.es.client.SearchEngineClient;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.query.parser.QueryParser;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants;
import org.codelibs.fess.webapp.semantic_search.index.query.NeuralQueryBuilder;
import org.codelibs.opensearch.runner.net.OpenSearchCurl;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.util.LaRequestUtil;
import org.opensearch.index.query.InnerHitBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import com.google.common.base.CharMatcher;

public class SemanticSearchHelper {
    private static final Logger logger = LogManager.getLogger(SemanticSearchHelper.class);

    protected ThreadLocal<SemanticSearchContext> contextLocal = new ThreadLocal<>();

    protected Float minScore;

    protected Long minContentLength;

    protected int chunkSize;

    @PostConstruct
    public void init() {
        final SearchEngineClient client = ComponentUtil.getSearchEngineClient();
        client.usePipeline();
        client.addDocumentSettingRewriteRule(s -> {
            final String pipeline = System.getProperty(PIPELINE); // ex. neural_pipeline
            if (logger.isDebugEnabled()) {
                logger.debug("pipeline: {}", pipeline);
            }
            if (StringUtil.isNotBlank(pipeline)) {
                s = s.replace("\"index\":", "\"default_pipeline\": \"" + pipeline + "\",\"index\":");
            }
            return s.replace("\"codec\":", "\"knn\": true,\"codec\":");
        });
        client.addDocumentMappingRewriteRule(s -> {
            final String dimension = System.getProperty(CONTENT_DIMENSION); // ex. 384
            final String field = System.getProperty(CONTENT_FIELD); // ex. knn
            final String method = System.getProperty(CONTENT_METHOD); // ex. hnsw
            final String engine = System.getProperty(CONTENT_ENGINE); // ex. lucene
            if (logger.isDebugEnabled()) {
                logger.debug("field: {}, dimension: {}, method: {}, engine: {}", field, dimension, method, engine);
            }
            if (StringUtil.isBlank(dimension) || StringUtil.isBlank(field) || StringUtil.isBlank(method) || StringUtil.isBlank(engine)) {
                return s;
            }
            final String nestedField = System.getProperty(CONTENT_NESTED_FIELD); // ex. content_vector
            final String chunkField = System.getProperty(CONTENT_CHUNK_FIELD); // ex. content_chunk
            final String spaceType = System.getProperty(CONTENT_SPACE_TYPE, "l2"); // ex. l2
            final String m = System.getProperty(CONTENT_PARAM_M, "16"); // ex. 16
            final String ef = System.getProperty(CONTENT_PARAM_EF_CONSTRUCTION, "100"); // ex. 100
            final String fieldDef;
            if (StringUtil.isNotBlank(nestedField)) {
                fieldDef = "\"" + nestedField + "\": {\n" //
                        + "  \"type\": \"nested\",\n" //
                        + "  \"properties\": {\n" //
                        + "    \"" + field + "\": {\n" //
                        + "      \"type\": \"knn_vector\",\n" //
                        + "      \"dimension\": " + dimension + ",\n" //
                        + "      \"method\": {\n" //
                        + "        \"name\": \"" + method + "\",\n" //
                        + "        \"engine\": \"" + engine + "\",\n" //
                        + "        \"space_type\": \"" + spaceType + "\",\n" //
                        + "        \"parameters\": {\n" //
                        + "          \"m\": " + m + ",\n" //
                        + "          \"ef_construction\": " + ef + "\n" //
                        + "        }\n" //
                        + "      }\n" //
                        + "    }\n" //
                        + "  }\n" //
                        + "},\n" //
                        + "\"" + chunkField + "\": {\n" //
                        + "  \"type\": \"text\",\n" //
                        + "  \"index\": false\n" //
                        + "},";
            } else {
                fieldDef = "\"" + field + "\": {\n" //
                        + "  \"type\": \"knn_vector\",\n" //
                        + "  \"dimension\": " + dimension + ",\n" //
                        + "  \"method\": {\n" //
                        + "    \"name\": \"" + method + "\",\n" //
                        + "    \"engine\": \"" + engine + "\",\n" //
                        + "    \"space_type\": \"" + spaceType + "\",\n" //
                        + "    \"parameters\": {\n" //
                        + "      \"m\": \"" + m + "\",\n" //
                        + "      \"ef_construction\": \"" + ef + "\"\n" //
                        + "    }\n" //
                        + "  }\n" //
                        + "},";
            }
            if (logger.isDebugEnabled()) {
                logger.debug("fieldDef: {}", fieldDef);
            }
            return s.replace("\"content\":", fieldDef + "\n\"content\":");
        });

        if (ComponentUtil.hasQueryParser()) {
            final QueryParser queryParser = ComponentUtil.getQueryParser();
            queryParser.addFilter((query, chain) -> chain.parse(rewriteQuery(query)));
        }

        load();
        ComponentUtil.getSystemHelper().addUpdateConfigListener("SemanticSearch", this::load);
    }

    protected String load() {
        final StringBuilder buf = new StringBuilder();

        buf.append("min_score=");
        final String minScoreValue = System.getProperty(SemanticSearchConstants.MIN_SCORE);
        if (StringUtil.isNotBlank(minScoreValue)) {
            try {
                minScore = Float.valueOf(minScoreValue);
                buf.append(minScore);
            } catch (final NumberFormatException e) {
                logger.debug("Failed to parse {}.", minScoreValue, e);
                minScore = null;
            }
        } else {
            minScore = null;
        }

        buf.append(", min_content_length=");
        final String minContentLengthValue = System.getProperty(SemanticSearchConstants.MIN_CONTENT_LENGTH);
        if (StringUtil.isNotBlank(minContentLengthValue)) {
            try {
                minContentLength = Long.valueOf(minContentLengthValue);
                buf.append(minContentLength);
            } catch (final NumberFormatException e) {
                logger.debug("Failed to parse {}.", minContentLengthValue, e);
                minContentLength = null;
            }
        } else {
            minContentLength = null;
        }

        buf.append(",model=");
        final String modelId = System.getProperty(CONTENT_MODEL_ID);
        if (StringUtil.isNotBlank(modelId)) {
            buf.append(modelId);
            final Map<String, Object> model = getModel(modelId);
            final Object modelStatus = model.get("model_state");
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Loaded model status is {}. Details: {}", modelId, modelStatus, model);
            } else {
                logger.info("[{}] Loaded model status is {}.", modelId, modelStatus);
            }
            if (!"DEPLOYED".equals(modelStatus)) {
                if (!loadModel(modelId)) {
                    logger.warn("Failed to load model: {} => {}", modelId, getModel(modelId));
                } else if (logger.isDebugEnabled()) {
                    logger.info("Loaded model: {}", modelId);
                }
            }
        }

        buf.append("chunk_size=");
        final String chunkSizeValue = System.getProperty(SemanticSearchConstants.CONTENT_CHUNK_SIZE, "1");
        if (StringUtil.isNotBlank(chunkSizeValue)) {
            try {
                chunkSize = Integer.parseInt(chunkSizeValue);
                buf.append(chunkSize);
            } catch (final NumberFormatException e) {
                logger.debug("Failed to parse {}.", chunkSizeValue, e);
                chunkSize = 1;
            }
        } else {
            chunkSize = 1;
        }

        return buf.toString();
    }

    protected Map<String, Object> getModel(final String modelId) {
        try (CurlResponse response = ComponentUtil.getCurlHelper().get("/_plugins/_ml/models/" + modelId).execute()) {
            if (response.getHttpStatusCode() == 200) {
                return response.getContent(OpenSearchCurl.jsonParser());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("model:{} does not exists: {}", modelId, response.getContentAsString());
            }
        } catch (final IOException e) {
            logger.warn("Failed to get model info for {}", modelId, e);
        }
        return Collections.emptyMap();
    }

    protected boolean loadModel(final String modelId) {
        try (CurlResponse response = ComponentUtil.getCurlHelper().post("/_plugins/_ml/models/" + modelId + "/_load").execute()) {
            if (response.getHttpStatusCode() == 200) {
                final Map<String, Object> contentMap = response.getContent(OpenSearchCurl.jsonParser());
                if (logger.isDebugEnabled()) {
                    logger.debug("loading model:{}: {}", modelId, contentMap);
                }
                if (contentMap.get("task_id") instanceof final String taskId) {
                    for (int i = 0; i < 60; i++) {
                        ThreadUtil.sleepQuietly(1000L);
                        final Map<String, Object> taskInfo = getTask(taskId);
                        final Object taskState = taskInfo.get("state");
                        if (logger.isDebugEnabled()) {
                            logger.debug("task: {}, state: {}, count: {}", taskInfo, taskState, i);
                        }
                        if ("CREATED".equals(taskState) || "RUNNING".equals(taskState)) {
                            continue;
                        }
                        return true;
                    }
                }
            } else if (logger.isDebugEnabled()) {
                logger.debug("Failed to load model:{}: {}", modelId, response.getContentAsString());
            }
        } catch (final IOException e) {
            logger.warn("Failed to load model:{}", modelId, e);
        }
        return false;
    }

    protected Map<String, Object> getTask(final String taskId) {
        try (CurlResponse response = ComponentUtil.getCurlHelper().get("/_plugins/_ml/tasks/" + taskId).execute()) {
            if (response.getHttpStatusCode() == 200) {
                return response.getContent(OpenSearchCurl.jsonParser());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to load task: {} => {}", taskId, response.getContentAsString());
            }
        } catch (final IOException e) {
            logger.warn("Failed to load task: {}", taskId, e);
        }
        return Collections.emptyMap();
    }

    protected String rewriteQuery(final String query) {
        if (StringUtil.isBlank(query) || (query.indexOf('"') != -1) || !CharMatcher.whitespace().matchesAnyOf(query)) {
            return query;
        }

        for (final String field : ComponentUtil.getQueryFieldConfig().getSearchFields()) {
            if (query.indexOf(field + ":") != -1) {
                return query;
            }
        }

        final String modelId = System.getProperty(CONTENT_MODEL_ID);
        if (StringUtil.isBlank(modelId)) {
            return query;
        }

        return "\"" + query + "\"";
    }

    public OptionalThing<QueryBuilder> newNeuralQueryBuilder(final String text) {
        final String modelId = System.getProperty(CONTENT_MODEL_ID);
        final String field = System.getProperty(CONTENT_FIELD); // ex. knn
        if (StringUtil.isNotBlank(modelId) && StringUtil.isNotBlank(field) && StringUtil.isNotBlank(text)) {
            final String nestedField = System.getProperty(CONTENT_NESTED_FIELD); // ex. content_vector
            if (StringUtil.isNotBlank(nestedField)) {
                final String vectorField = nestedField + "." + field;
                final InnerHitBuilder innerHit =
                        new InnerHitBuilder(nestedField).setSize(chunkSize).setFetchSourceContext(new FetchSourceContext(false));
                return OptionalThing.of(QueryBuilders.nestedQuery(nestedField, new NeuralQueryBuilder.Builder().modelId(modelId)
                        .field(vectorField).query(text).k(LaRequestUtil.getOptionalRequest().map(req -> {
                            final Object pageSize = req.getAttribute(Constants.REQUEST_PAGE_SIZE);
                            if (pageSize != null) {
                                return Integer.parseInt(pageSize.toString());
                            }
                            return Constants.DEFAULT_PAGE_SIZE;
                        }).orElse(Constants.DEFAULT_PAGE_SIZE)).build(), ScoreMode.Max).innerHit(innerHit));
            }
            return OptionalThing.of(new NeuralQueryBuilder.Builder().modelId(modelId).field(field).query(text)
                    .k(LaRequestUtil.getOptionalRequest().map(req -> {
                        final Object pageSize = req.getAttribute(Constants.REQUEST_PAGE_SIZE);
                        if (pageSize != null) {
                            return Integer.parseInt(pageSize.toString());
                        }
                        return Constants.DEFAULT_PAGE_SIZE;
                    }).orElse(Constants.DEFAULT_PAGE_SIZE)).build());
        }
        return OptionalThing.empty();
    }

    public SemanticSearchContext createContext(final String query, final SearchRequestParams params,
            final OptionalThing<FessUserBean> userBean) {
        if (contextLocal.get() != null) {
            logger.warn("The context exists: {}", contextLocal.get());
            contextLocal.remove();
        }
        final SemanticSearchContext context = new SemanticSearchContext(query, params, userBean);
        contextLocal.set(context);
        return context;
    }

    public void closeContext() {
        if (contextLocal.get() == null) {
            logger.warn("The context does not exist.");
        } else {
            contextLocal.remove();
        }
    }

    public SemanticSearchContext getContext() {
        return contextLocal.get();
    }

    public Float getMinScore() {
        return minScore;
    }

    public Long getMinContentLength() {
        return minContentLength;
    }

    public static class SemanticSearchContext {

        private final String query;
        private final SearchRequestParams params;
        private final OptionalThing<FessUserBean> userBean;

        public SemanticSearchContext(final String query, final SearchRequestParams params, final OptionalThing<FessUserBean> userBean) {
            this.query = query;
            this.params = params;
            this.userBean = userBean;
        }

        public String getQuery() {
            return query;
        }

        public SearchRequestParams getParams() {
            return params;
        }

        public OptionalThing<FessUserBean> getUserBean() {
            return userBean;
        }

        @Override
        public String toString() {
            return "SemanticSearchContext [query=" + query + ", params=" + params + ", userBean=" + userBean.orElse(null) + "]";
        }

    }
}
