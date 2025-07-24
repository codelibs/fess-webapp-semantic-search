/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
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
package org.codelibs.fess.webapp.semantic_search;

/**
 * Constants for semantic search configuration properties.
 * Contains system property keys with the prefix 'fess.semantic_search.'.
 */
public class SemanticSearchConstants {

    /**
     * Private constructor to prevent instantiation.
     */
    private SemanticSearchConstants() {
    }

    private static final String PREFIX = "fess.semantic_search.";

    /** Configuration key for neural pipeline setting. */
    public static final String PIPELINE = PREFIX + "pipeline";

    /** Configuration key for ML model ID. */
    public static final String CONTENT_MODEL_ID = PREFIX + "content.model_id";

    /** Configuration key for vector dimension size. */
    public static final String CONTENT_DIMENSION = PREFIX + "content.dimension";

    /** Configuration key for vector search engine type. */
    public static final String CONTENT_ENGINE = PREFIX + "content.engine";

    /** Configuration key for vector search method. */
    public static final String CONTENT_METHOD = PREFIX + "content.method";

    /** Configuration key for vector space type. */
    public static final String CONTENT_SPACE_TYPE = PREFIX + "content.space_type";

    /** Configuration key for HNSW parameter M. */
    public static final String CONTENT_PARAM_M = PREFIX + "content.param.m";

    /** Configuration key for HNSW parameter ef_construction. */
    public static final String CONTENT_PARAM_EF_CONSTRUCTION = PREFIX + "content.param.ef_construction";

    /** Configuration key for content vector field name. */
    public static final String CONTENT_FIELD = PREFIX + "content.field";

    /** Configuration key for nested content field name. */
    public static final String CONTENT_NESTED_FIELD = PREFIX + "content.nested_field";

    /** Configuration key for content chunk field name. */
    public static final String CONTENT_CHUNK_FIELD = PREFIX + "content.chunk_field";

    /** Configuration key for content chunk size. */
    public static final String CONTENT_CHUNK_SIZE = PREFIX + "content.chunk_size";

    /** Configuration key for minimum search score threshold. */
    public static final String MIN_SCORE = PREFIX + "min_score";

    /** Configuration key for minimum content length requirement. */
    public static final String MIN_CONTENT_LENGTH = PREFIX + "min_content_length";

    /** Component name for SemanticSearchHelper in DI container. */
    public static final String SEMANTIC_SEARCH_HELPER = "semanticSearchHelper";
}
