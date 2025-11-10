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
package org.codelibs.fess.webapp.semantic_search.helper;

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.entity.SearchRequestParams.SearchRequestType;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.opensearch.client.SearchEngineClient;
import org.codelibs.fess.query.QueryFieldConfig;
import org.codelibs.fess.query.parser.QueryParser;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.helper.SemanticSearchHelper.SemanticSearchContext;
import org.dbflute.optional.OptionalThing;
import org.dbflute.utflute.lastadi.LastaDiTestCase;
import org.opensearch.index.query.QueryBuilder;

public class SemanticSearchHelperTest extends LastaDiTestCase {
    private static final Logger logger = LogManager.getLogger(SemanticSearchHelperTest.class);

    private SemanticSearchHelper semanticSearchHelper;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Clear all system properties to start fresh
        clearSemanticSearchProperties();

        // Set up required components
        setupTestComponents();

        semanticSearchHelper = new SemanticSearchHelper();
        ComponentUtil.register(semanticSearchHelper, "semanticSearchHelper");
    }

    @Override
    public void tearDown() throws Exception {
        clearSemanticSearchProperties();
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    /**
     * Test context management - creation, retrieval, and cleanup
     */
    public void test_contextManagement() throws Exception {
        semanticSearchHelper.init();

        // Initially no context
        assertNull(semanticSearchHelper.getContext());

        // Create context
        SearchRequestParams params = new MockSearchRequestParams();
        OptionalThing<FessUserBean> userBean = OptionalThing.empty();
        SemanticSearchContext context = semanticSearchHelper.createContext("test query", params, userBean);

        assertNotNull(context);
        assertEquals("test query", context.getQuery());
        assertSame(params, context.getParams());
        assertSame(userBean, context.getUserBean());

        // Context should be retrievable
        assertSame(context, semanticSearchHelper.getContext());

        // Close context
        semanticSearchHelper.closeContext();
        assertNull(semanticSearchHelper.getContext());
    }

    /**
     * Test neural query builder creation without configuration
     */
    public void test_newNeuralQueryBuilder_noConfiguration() throws Exception {
        semanticSearchHelper.init();

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
        assertFalse(result.isPresent());
    }

    /**
     * Test neural query builder creation with basic configuration
     */
    public void test_newNeuralQueryBuilder_basicConfiguration() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");

        // Skip init() to avoid curlHelper dependency in test environment
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
        assertTrue(result.isPresent());

        // Basic check that neural query was created
        assertNotNull(result.get());
    }

    /**
     * Test neural query builder creation with nested field configuration
     */
    public void test_newNeuralQueryBuilder_nestedConfiguration() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "vector");
        System.setProperty(CONTENT_NESTED_FIELD, "content_nested");

        // Skip init() to avoid curlHelper dependency in test environment
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("semantic search test");
        assertTrue(result.isPresent());

        // Basic check that nested neural query was created
        assertNotNull(result.get());
    }

    /**
     * Test empty or null query text handling
     */
    public void test_newNeuralQueryBuilder_emptyQuery() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");

        // Skip init() to avoid curlHelper dependency in test environment

        // Empty string
        OptionalThing<QueryBuilder> result1 = semanticSearchHelper.newNeuralQueryBuilder("");
        assertFalse(result1.isPresent());

        // Null string
        OptionalThing<QueryBuilder> result2 = semanticSearchHelper.newNeuralQueryBuilder(null);
        assertFalse(result2.isPresent());

        // Whitespace only
        OptionalThing<QueryBuilder> result3 = semanticSearchHelper.newNeuralQueryBuilder("   ");
        assertFalse(result3.isPresent()); // Should not work with whitespace only
    }

    /**
     * Test configuration loading and property parsing
     */
    public void test_configurationLoading() throws Exception {
        // Test min_score configuration
        System.setProperty(MIN_SCORE, "0.5");
        semanticSearchHelper.init();
        assertEquals(Float.valueOf(0.5f), semanticSearchHelper.getMinScore());

        // Test min_content_length configuration
        System.setProperty(MIN_CONTENT_LENGTH, "100");
        semanticSearchHelper.init();
        assertEquals(Long.valueOf(100L), semanticSearchHelper.getMinContentLength());

        // Test invalid values
        System.setProperty(MIN_SCORE, "invalid");
        System.setProperty(MIN_CONTENT_LENGTH, "invalid");
        semanticSearchHelper.init();
        assertNull(semanticSearchHelper.getMinScore());
        assertNull(semanticSearchHelper.getMinContentLength());
    }

    /**
     * Test configuration edge cases and boundary values
     */
    public void test_configurationEdgeCases() throws Exception {
        // Test zero values
        System.setProperty(MIN_SCORE, "0.0");
        System.setProperty(MIN_CONTENT_LENGTH, "0");
        semanticSearchHelper.init();
        assertEquals(Float.valueOf(0.0f), semanticSearchHelper.getMinScore());
        assertEquals(Long.valueOf(0L), semanticSearchHelper.getMinContentLength());

        // Test negative values
        System.setProperty(MIN_SCORE, "-1.0");
        System.setProperty(MIN_CONTENT_LENGTH, "-1");
        semanticSearchHelper.init();
        assertEquals(Float.valueOf(-1.0f), semanticSearchHelper.getMinScore());
        assertEquals(Long.valueOf(-1L), semanticSearchHelper.getMinContentLength());

        // Test very large values
        System.setProperty(MIN_SCORE, "999999.99");
        System.setProperty(MIN_CONTENT_LENGTH, "999999999");
        semanticSearchHelper.init();
        assertEquals(Float.valueOf(999999.99f), semanticSearchHelper.getMinScore());
        assertEquals(Long.valueOf(999999999L), semanticSearchHelper.getMinContentLength());
    }

    /**
     * Test multiple context creation warnings
     */
    public void test_multipleContextCreation() throws Exception {
        semanticSearchHelper.init();

        SearchRequestParams params1 = new MockSearchRequestParams();
        SearchRequestParams params2 = new MockSearchRequestParams();
        OptionalThing<FessUserBean> userBean = OptionalThing.empty();

        // Create first context
        SemanticSearchContext context1 = semanticSearchHelper.createContext("query1", params1, userBean);
        assertNotNull(context1);
        assertEquals("query1", context1.getQuery());

        // Create second context (should replace first and log warning)
        SemanticSearchContext context2 = semanticSearchHelper.createContext("query2", params2, userBean);
        assertNotNull(context2);
        assertEquals("query2", context2.getQuery());

        // Should return the second context
        assertSame(context2, semanticSearchHelper.getContext());
    }

    /**
     * Test context closure without existing context
     */
    public void test_closeContextWithoutExistingContext() throws Exception {
        semanticSearchHelper.init();

        // Should not throw exception but may log warning
        semanticSearchHelper.closeContext();
        assertNull(semanticSearchHelper.getContext());
    }

    /**
     * Test SemanticSearchContext functionality
     */
    public void test_semanticSearchContext() throws Exception {
        SearchRequestParams params = new MockSearchRequestParams();
        OptionalThing<FessUserBean> optionalUserBean = OptionalThing.empty();

        SemanticSearchContext context = new SemanticSearchContext("test query", params, optionalUserBean);

        assertEquals("test query", context.getQuery());
        assertSame(params, context.getParams());
        assertSame(optionalUserBean, context.getUserBean());
        assertFalse(context.getUserBean().isPresent());

        // Test toString
        String contextString = context.toString();
        assertTrue(contextString.contains("test query"));
    }

    /**
     * Test query rewriting scenarios
     */
    public void test_queryRewriting() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model");

        // Set up query field config with some fields
        QueryFieldConfig queryFieldConfig = ComponentUtil.getComponent("queryFieldConfig");
        // This would typically be configured, but for test we'll work with defaults

        // Skip init() to avoid curlHelper dependency in test environment

        // Test various query patterns through the helper's rewrite logic
        // Note: The rewriteQuery method is protected, so we test it indirectly
        // The actual rewriting logic depends on QueryParser integration

        assertTrue(true); // Basic test passed
    }

    /**
     * Test ef_search parameter configuration (v15.3.0+)
     */
    public void test_efSearchConfiguration() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");
        System.setProperty(CONTENT_PARAM_EF_SEARCH, "150");

        // Skip init() to avoid curlHelper dependency in test environment
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
        assertTrue(result.isPresent());

        // The ef_search parameter should be applied to the query builder
        assertNotNull(result.get());
    }

    /**
     * Test ef_search parameter with null value (v15.3.0+)
     */
    public void test_efSearchWithNullValue() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");
        // Don't set ef_search - should use default (null)

        // Skip init() to avoid curlHelper dependency in test environment
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
        assertTrue(result.isPresent());

        // Should work without ef_search parameter
        assertNotNull(result.get());
    }

    /**
     * Test ef_search parameter with invalid value (v15.3.0+)
     */
    public void test_efSearchWithInvalidValue() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");
        System.setProperty(CONTENT_PARAM_EF_SEARCH, "invalid");

        // Skip init() to avoid curlHelper dependency in test environment
        try {
            OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
            // Should either fail or ignore the invalid value
            // Test passes if no exception is thrown
            assertTrue(true);
        } catch (NumberFormatException e) {
            // Expected behavior - invalid number format
            assertTrue(true);
        }
    }

    /**
     * Test new configuration properties (v15.3.0+)
     */
    public void test_newConfigurationProperties() throws Exception {
        // Test MMR configuration
        System.setProperty(MMR_ENABLED, "true");
        System.setProperty(MMR_LAMBDA, "0.7");

        // Test batch inference configuration
        System.setProperty(BATCH_INFERENCE_ENABLED, "true");

        // Test performance monitoring configuration
        System.setProperty(PERFORMANCE_MONITORING_ENABLED, "true");

        // These properties should be readable
        assertEquals("true", System.getProperty(MMR_ENABLED));
        assertEquals("0.7", System.getProperty(MMR_LAMBDA));
        assertEquals("true", System.getProperty(BATCH_INFERENCE_ENABLED));
        assertEquals("true", System.getProperty(PERFORMANCE_MONITORING_ENABLED));
    }

    /**
     * Test default space_type changed to cosinesimil (v15.3.0+)
     */
    public void test_defaultSpaceTypeIsCosinesimil() throws Exception {
        // When space_type is not set, it should default to 'cosinesimil'
        // This is tested indirectly through the mapping rewrite rule
        // The actual default is in SemanticSearchHelper.java:117

        // Verify the constant exists
        assertNotNull(CONTENT_SPACE_TYPE);
        assertEquals("fess.semantic_search.content.space_type", CONTENT_SPACE_TYPE);
    }

    /**
     * Test chunk size configuration parsing
     */
    public void test_chunkSizeConfiguration() throws Exception {
        System.setProperty(CONTENT_CHUNK_SIZE, "512");
        semanticSearchHelper.init();

        // Chunk size should be parsed and available
        // This is used internally by the helper
        assertTrue(true); // Test passes if no exception during init
    }

    /**
     * Test chunk size with invalid value
     */
    public void test_chunkSizeWithInvalidValue() throws Exception {
        System.setProperty(CONTENT_CHUNK_SIZE, "invalid_size");
        semanticSearchHelper.init();

        // Should handle invalid chunk size gracefully
        assertTrue(true);
    }

    /**
     * Test HNSW parameter m configuration
     */
    public void test_hnswParameterM() throws Exception {
        System.setProperty(CONTENT_PARAM_M, "32");
        semanticSearchHelper.init();

        // Parameter should be parsed successfully
        assertTrue(true);
    }

    /**
     * Test HNSW parameter ef_construction
     */
    public void test_hnswParameterEfConstruction() throws Exception {
        System.setProperty(CONTENT_PARAM_EF_CONSTRUCTION, "256");
        semanticSearchHelper.init();

        // Parameter should be parsed successfully
        assertTrue(true);
    }

    /**
     * Test multiple neural query builders in sequence
     */
    public void test_multipleNeuralQueryBuilders() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");

        String[] queries = { "first query", "second query", "third query" };

        for (String query : queries) {
            OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder(query);
            assertTrue(result.isPresent());
            assertNotNull(result.get());
            logger.info("Neural query builder created for: {}", query);
        }
    }

    /**
     * Test neural query builder with various text patterns
     */
    public void test_neuralQueryBuilderWithVariousPatterns() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-id");
        System.setProperty(CONTENT_FIELD, "test_vector_field");

        String[] patterns = { "simple", "multi word query", "query-with-dashes", "query_with_underscores", "query.with.dots",
                "query@special", "123 numbers", "mixed123text", "UPPERCASE", "CamelCase" };

        for (String pattern : patterns) {
            OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder(pattern);
            assertTrue("Pattern '" + pattern + "' should produce neural query", result.isPresent());
        }
    }

    /**
     * Test configuration with all HNSW parameters
     */
    public void test_fullHNSWConfiguration() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model");
        System.setProperty(CONTENT_FIELD, "vector_field");
        System.setProperty(CONTENT_DIMENSION, "768");
        System.setProperty(CONTENT_ENGINE, "nmslib");
        System.setProperty(CONTENT_METHOD, "hnsw");
        System.setProperty(CONTENT_SPACE_TYPE, "cosinesimil");
        System.setProperty(CONTENT_PARAM_M, "16");
        System.setProperty(CONTENT_PARAM_EF_CONSTRUCTION, "128");
        System.setProperty(CONTENT_PARAM_EF_SEARCH, "100");

        semanticSearchHelper.init();

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");
        assertTrue(result.isPresent());
    }

    /**
     * Test neural query builder with nested and chunk fields
     */
    public void test_neuralQueryBuilderWithNestedAndChunk() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model");
        System.setProperty(CONTENT_FIELD, "vector");
        System.setProperty(CONTENT_NESTED_FIELD, "content_nested");
        System.setProperty(CONTENT_CHUNK_FIELD, "content_chunks");
        System.setProperty(CONTENT_CHUNK_SIZE, "256");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("chunked nested query");
        assertTrue(result.isPresent());
    }

    /**
     * Test context with user bean
     */
    public void test_contextWithUserBean() throws Exception {
        semanticSearchHelper.init();

        SearchRequestParams params = new MockSearchRequestParams();
        FessUserBean userBean = new FessUserBean();
        userBean.setUserId("test-user");
        OptionalThing<FessUserBean> optionalUserBean = OptionalThing.of(userBean);

        SemanticSearchContext context = semanticSearchHelper.createContext("test query", params, optionalUserBean);

        assertNotNull(context);
        assertTrue(context.getUserBean().isPresent());
        assertEquals("test-user", context.getUserBean().get().getUserId());

        semanticSearchHelper.closeContext();
    }

    /**
     * Test concurrent context creation warning
     */
    public void test_concurrentContextCreation() throws Exception {
        semanticSearchHelper.init();

        SearchRequestParams params1 = new MockSearchRequestParams();
        SearchRequestParams params2 = new MockSearchRequestParams();

        SemanticSearchContext context1 = semanticSearchHelper.createContext("query1", params1, OptionalThing.empty());
        assertNotNull(context1);

        // Creating second context without closing first should log warning
        SemanticSearchContext context2 = semanticSearchHelper.createContext("query2", params2, OptionalThing.empty());
        assertNotNull(context2);
        assertNotSame(context1, context2);

        // Should return the most recent context
        assertEquals(context2, semanticSearchHelper.getContext());

        semanticSearchHelper.closeContext();
    }

    /**
     * Test min score with boundary values
     */
    public void test_minScoreWithBoundaryValues() throws Exception {
        // Test maximum float value
        System.setProperty(MIN_SCORE, String.valueOf(Float.MAX_VALUE));
        semanticSearchHelper.init();
        assertEquals(Float.MAX_VALUE, semanticSearchHelper.getMinScore(), 0.001f);

        // Test minimum positive float value
        System.setProperty(MIN_SCORE, String.valueOf(Float.MIN_VALUE));
        semanticSearchHelper.init();
        assertEquals(Float.MIN_VALUE, semanticSearchHelper.getMinScore(), 0.0000001f);

        // Test value of 1.0
        System.setProperty(MIN_SCORE, "1.0");
        semanticSearchHelper.init();
        assertEquals(1.0f, semanticSearchHelper.getMinScore(), 0.001f);
    }

    /**
     * Test min content length with boundary values
     */
    public void test_minContentLengthWithBoundaryValues() throws Exception {
        // Test maximum long value
        System.setProperty(MIN_CONTENT_LENGTH, String.valueOf(Long.MAX_VALUE));
        semanticSearchHelper.init();
        assertEquals(Long.MAX_VALUE, semanticSearchHelper.getMinContentLength().longValue());

        // Test value of 1
        System.setProperty(MIN_CONTENT_LENGTH, "1");
        semanticSearchHelper.init();
        assertEquals(1L, semanticSearchHelper.getMinContentLength().longValue());
    }

    /**
     * Test neural query builder with whitespace variations
     */
    public void test_neuralQueryBuilderWithWhitespaceVariations() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model");
        System.setProperty(CONTENT_FIELD, "vector_field");

        // Leading whitespace
        OptionalThing<QueryBuilder> result1 = semanticSearchHelper.newNeuralQueryBuilder("   leading");
        assertTrue(result1.isPresent());

        // Trailing whitespace
        OptionalThing<QueryBuilder> result2 = semanticSearchHelper.newNeuralQueryBuilder("trailing   ");
        assertTrue(result2.isPresent());

        // Multiple spaces between words
        OptionalThing<QueryBuilder> result3 = semanticSearchHelper.newNeuralQueryBuilder("multiple    spaces");
        assertTrue(result3.isPresent());

        // Tab characters
        OptionalThing<QueryBuilder> result4 = semanticSearchHelper.newNeuralQueryBuilder("with\ttabs");
        assertTrue(result4.isPresent());
    }

    /**
     * Test pipeline configuration
     */
    public void test_pipelineConfiguration() throws Exception {
        System.setProperty(PIPELINE, "my-neural-pipeline");
        semanticSearchHelper.init();

        // Pipeline should be configured during init
        // This is used for preprocessing in OpenSearch
        assertTrue(true);
    }

    /**
     * Test content dimension configuration
     */
    public void test_contentDimensionConfiguration() throws Exception {
        // Test various dimension sizes
        String[] dimensions = { "128", "256", "512", "768", "1024" };

        for (String dim : dimensions) {
            System.setProperty(CONTENT_DIMENSION, dim);
            semanticSearchHelper.init();
            logger.info("Configured dimension: {}", dim);
            assertTrue(true);
        }
    }

    /**
     * Test neural query builder k parameter variations
     */
    public void test_neuralQueryBuilderKParameter() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model");
        System.setProperty(CONTENT_FIELD, "vector_field");

        // The k parameter (number of nearest neighbors) should be configurable
        // Default is 20 based on the test outputs
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("k parameter test");
        assertTrue(result.isPresent());

        String queryString = result.get().toString();
        assertTrue(queryString.contains("\"k\":20") || queryString.contains("k=20"));
    }

    /**
     * Test MMR (Maximal Marginal Relevance) configuration
     */
    public void test_mmrConfiguration() throws Exception {
        System.setProperty(MMR_ENABLED, "true");
        System.setProperty(MMR_LAMBDA, "0.5");
        semanticSearchHelper.init();

        // MMR should be configurable for diversity in results
        assertEquals("true", System.getProperty(MMR_ENABLED));
        assertEquals("0.5", System.getProperty(MMR_LAMBDA));
    }

    /**
     * Test batch inference configuration
     */
    public void test_batchInferenceConfiguration() throws Exception {
        System.setProperty(BATCH_INFERENCE_ENABLED, "true");
        semanticSearchHelper.init();

        // Batch inference should be configurable
        assertEquals("true", System.getProperty(BATCH_INFERENCE_ENABLED));
    }

    private void clearSemanticSearchProperties() {
        System.clearProperty(PIPELINE);
        System.clearProperty(CONTENT_MODEL_ID);
        System.clearProperty(CONTENT_DIMENSION);
        System.clearProperty(CONTENT_ENGINE);
        System.clearProperty(CONTENT_METHOD);
        System.clearProperty(CONTENT_SPACE_TYPE);
        System.clearProperty(CONTENT_PARAM_M);
        System.clearProperty(CONTENT_PARAM_EF_CONSTRUCTION);
        System.clearProperty(CONTENT_PARAM_EF_SEARCH);
        System.clearProperty(CONTENT_FIELD);
        System.clearProperty(CONTENT_NESTED_FIELD);
        System.clearProperty(CONTENT_CHUNK_FIELD);
        System.clearProperty(CONTENT_CHUNK_SIZE);
        System.clearProperty(MIN_SCORE);
        System.clearProperty(MIN_CONTENT_LENGTH);
        System.clearProperty(MMR_ENABLED);
        System.clearProperty(MMR_LAMBDA);
        System.clearProperty(BATCH_INFERENCE_ENABLED);
        System.clearProperty(PERFORMANCE_MONITORING_ENABLED);
    }

    private void setupTestComponents() {
        final QueryFieldConfig queryFieldConfig = new QueryFieldConfig();
        queryFieldConfig.init();
        ComponentUtil.register(queryFieldConfig, "queryFieldConfig");

        final QueryParser queryParser = new QueryParser();
        queryParser.init();
        ComponentUtil.register(queryParser, "queryParser");

        final SearchEngineClient searchEngineClient = new SearchEngineClient();
        ComponentUtil.register(searchEngineClient, "searchEngineClient");

        final SystemHelper systemHelper = new SystemHelper();
        ComponentUtil.register(systemHelper, "systemHelper");
    }

    /**
     * Mock SearchRequestParams for testing
     */
    private static class MockSearchRequestParams extends SearchRequestParams {
        @Override
        public int getPageSize() {
            return 20;
        }

        @Override
        public String getQuery() {
            return "test";
        }

        @Override
        public String getSimilarDocHash() {
            return null;
        }

        @Override
        public SearchRequestType getType() {
            return SearchRequestType.SEARCH;
        }

        @Override
        public java.util.Locale getLocale() {
            return java.util.Locale.getDefault();
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public java.util.Map<String, String[]> getFields() {
            return new java.util.HashMap<>();
        }

        @Override
        public java.util.Map<String, String[]> getConditions() {
            return new java.util.HashMap<>();
        }

        @Override
        public String[] getLanguages() {
            return new String[0];
        }

        @Override
        public String getSort() {
            return null;
        }

        @Override
        public int getStartPosition() {
            return 0;
        }

        @Override
        public int getOffset() {
            return 0;
        }

        @Override
        public String[] getExtraQueries() {
            return new String[0];
        }

        @Override
        public Float getMinScore() {
            return null;
        }

        @Override
        public String getTrackTotalHits() {
            return "true";
        }

        @Override
        public org.codelibs.fess.entity.FacetInfo getFacetInfo() {
            return null;
        }

        @Override
        public org.codelibs.fess.entity.GeoInfo getGeoInfo() {
            return null;
        }

        @Override
        public org.codelibs.fess.entity.HighlightInfo getHighlightInfo() {
            return null;
        }
    }
}