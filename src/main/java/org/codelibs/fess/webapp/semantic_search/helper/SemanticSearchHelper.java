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

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.KNN_VECTOR_DIMENSION;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.KNN_VECTOR_ENGINE;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.KNN_VECTOR_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.KNN_VECTOR_METHOD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.MODEL_ID;

import javax.annotation.PostConstruct;

import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.es.client.SearchEngineClient;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.index.query.NeuralQueryBuilder;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.util.LaRequestUtil;
import org.opensearch.index.query.QueryBuilder;

public class SemanticSearchHelper {

    @PostConstruct
    public void init() {
        final SearchEngineClient client = ComponentUtil.getSearchEngineClient();
        client.addDocumentSettingRewriteRule(s -> s.replace("\"index\":", "\"default_pipeline\": \"neural_pipeline\",\"index\":")
                .replace("\"codec\":", "\"knn\": true,\"codec\":"));
        client.addDocumentMappingRewriteRule(s -> {
            final String dimension = System.getProperty(KNN_VECTOR_DIMENSION); // ex. 384
            final String field = System.getProperty(KNN_VECTOR_FIELD); // ex. content_vector
            final String method = System.getProperty(KNN_VECTOR_METHOD); // ex. hnsw
            final String engine = System.getProperty(KNN_VECTOR_ENGINE); // ex. lucene
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
    }

    public OptionalThing<QueryBuilder> newNeuralQueryBuilder(final String text) {
        final String modelId = System.getProperty(MODEL_ID); // ex. 384
        final String field = System.getProperty(KNN_VECTOR_FIELD); // ex. content_vector
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
}
