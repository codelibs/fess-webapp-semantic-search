/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_DIMENSION;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_ENGINE;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_METHOD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_MODEL_ID;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.PIPELINE;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.lang.ThreadUtil;
import org.codelibs.curl.CurlResponse;
import org.codelibs.fess.Constants;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.es.client.SearchEngineClient;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.query.parser.QueryParser;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.index.query.NeuralQueryBuilder;
import org.codelibs.opensearch.runner.net.OpenSearchCurl;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.util.LaRequestUtil;
import org.opensearch.index.query.QueryBuilder;

import com.google.common.base.CharMatcher;

public class SemanticSearchHelper {
    private static final Logger logger = LogManager.getLogger(SemanticSearchHelper.class);

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
            final String field = System.getProperty(CONTENT_FIELD); // ex. content_vector
            final String method = System.getProperty(CONTENT_METHOD); // ex. hnsw
            final String engine = System.getProperty(CONTENT_ENGINE); // ex. lucene
            if (logger.isDebugEnabled()) {
                logger.debug("field: {}, dimension: {}, method: {}, engine: {}", field, dimension, method, engine);
            }
            if (StringUtil.isBlank(dimension) || StringUtil.isBlank(field) || StringUtil.isBlank(method) || StringUtil.isBlank(engine)) {
                return s;
            }
            return s.replace("\"content\":", "\"" + field + "\": {\n" //
                    + "  \"type\": \"knn_vector\",\n" //
                    + "  \"dimension\": " + dimension + ",\n" //
                    + "  \"method\": {\n" //
                    + "    \"name\": \"" + method + "\",\n" //
                    + "    \"engine\": \"" + engine + "\"\n" //
                    + "  }\n" //
                    + "},\n" //
                    + "\"content\":");
        });

        if (ComponentUtil.hasQueryParser()) {
            final QueryParser queryParser = ComponentUtil.getQueryParser();
            queryParser.addFilter((query, chain) -> chain.parse(rewriteQuery(query)));
        }

        final String modelId = System.getProperty(CONTENT_MODEL_ID);
        if (StringUtil.isNotBlank(modelId)) {
            final Map<String, Object> model = getModel(modelId);
            if (logger.isDebugEnabled()) {
                logger.debug("model: {}", model);
            }
            if ("LOAD_FAILED".equals(model.get("model_state"))) {
                if (!loadModel(modelId)) {
                    logger.warn("Failed to load model:{}", modelId);
                } else if (logger.isDebugEnabled()) {
                    logger.warn("Loaded model:{}", modelId);
                }
            }
        }
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
                    for (int i = 0; i < 10; i++) {
                        ThreadUtil.sleepQuietly(1000L);
                        final Map<String, Object> taskInfo = getTask(taskId);
                        if (logger.isDebugEnabled()) {
                            logger.debug("task: {}", taskInfo);
                        }
                        if ("COMPLETED".equals(taskInfo.get("state"))) {
                            return true;
                        }
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
                logger.debug("Failed to load task:{}: {}", taskId, response.getContentAsString());
            }
        } catch (final IOException e) {
            logger.warn("Failed to load task:{}", taskId, e);
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
        final String field = System.getProperty(CONTENT_FIELD); // ex. content_vector
        if (StringUtil.isNotBlank(modelId) && StringUtil.isNotBlank(field) && StringUtil.isNotBlank(text)) {
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

    protected ThreadLocal<SemanticSearchContext> contextLocal = new ThreadLocal<>();

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
