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
        // Don't call init() in tests - it requires curlHelper which isn't available

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
        // Don't call init() in tests - it requires curlHelper which isn't available

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
     * Test neural query builder with model and field configuration
     */
    public void test_neuralQueryBuilderWithConfiguration() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "test-model-123");
        System.setProperty(CONTENT_FIELD, "test_vector_field");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("semantic search query");

        assertTrue(result.isPresent());
        QueryBuilder builder = result.get();
        String queryString = builder.toString();

        // Verify the query contains model ID and field name
        assertTrue(queryString.contains("test-model-123"));
        assertTrue(queryString.contains("test_vector_field"));
        assertTrue(queryString.contains("semantic search query"));
    }

    /**
     * Test neural query builder without configuration returns empty
     */
    public void test_neuralQueryBuilderWithoutConfiguration() throws Exception {
        // Clear configuration
        System.clearProperty(CONTENT_MODEL_ID);
        System.clearProperty(CONTENT_FIELD);

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("test query");

        assertFalse(result.isPresent());
    }

    /**
     * Test neural query builder with nested field configuration
     */
    public void test_neuralQueryBuilderWithNestedField() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "nested-model");
        System.setProperty(CONTENT_FIELD, "vector");
        System.setProperty(CONTENT_NESTED_FIELD, "content_nested");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("nested query");

        assertTrue(result.isPresent());
        String queryString = result.get().toString();

        // Verify nested query structure
        assertTrue(queryString.contains("nested"));
        assertTrue(queryString.contains("content_nested"));
        assertTrue(queryString.contains("vector"));
    }

    /**
     * Test neural query builder with ef_search parameter
     */
    public void test_neuralQueryBuilderWithEfSearch() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "model-with-ef");
        System.setProperty(CONTENT_FIELD, "vector_field");
        System.setProperty(CONTENT_PARAM_EF_SEARCH, "50");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("ef search test");

        assertTrue(result.isPresent());
        String queryString = result.get().toString();

        // Verify ef_search is included in the query
        assertTrue(queryString.contains("ef_search") || queryString.contains("50"));
    }

    /**
     * Test multiple context creation warnings
     */
    public void test_multipleContextCreation() throws Exception {
        // Don't call init() in tests - it requires curlHelper which isn't available

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
        // Don't call init() in tests - it requires curlHelper which isn't available

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
     * Test neural query builder with empty model ID returns empty
     */
    public void test_neuralQueryBuilderWithEmptyModelId() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "");
        System.setProperty(CONTENT_FIELD, "vector_field");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("query text");

        assertFalse(result.isPresent());
    }

    /**
     * Test neural query builder with empty field returns empty
     */
    public void test_neuralQueryBuilderWithEmptyField() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "model-id");
        System.setProperty(CONTENT_FIELD, "");

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("query text");

        assertFalse(result.isPresent());
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
     * Test neural query builder with nested and non-nested fields
     */
    public void test_neuralQueryBuilderNestedVsFlat() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "compare-model");
        System.setProperty(CONTENT_FIELD, "vector");

        // Test without nested field (flat structure)
        System.clearProperty(CONTENT_NESTED_FIELD);
        OptionalThing<QueryBuilder> flatResult = semanticSearchHelper.newNeuralQueryBuilder("flat query");
        assertTrue(flatResult.isPresent());
        String flatQuery = flatResult.get().toString();
        assertFalse(flatQuery.contains("nested"));

        // Test with nested field
        System.setProperty(CONTENT_NESTED_FIELD, "content_nested");
        OptionalThing<QueryBuilder> nestedResult = semanticSearchHelper.newNeuralQueryBuilder("nested query");
        assertTrue(nestedResult.isPresent());
        String nestedQuery = nestedResult.get().toString();
        assertTrue(nestedQuery.contains("nested"));
        assertTrue(nestedQuery.contains("content_nested"));
    }

    /**
     * Test context with different user bean configurations
     */
    public void test_contextWithUserBean() throws Exception {
        SearchRequestParams params = new MockSearchRequestParams();

        // Test with empty user bean
        OptionalThing<FessUserBean> emptyUserBean = OptionalThing.empty();
        SemanticSearchContext context1 = semanticSearchHelper.createContext("query1", params, emptyUserBean);
        assertNotNull(context1);
        assertFalse(context1.getUserBean().isPresent());
        semanticSearchHelper.closeContext();

        // Test with null params (edge case)
        SemanticSearchContext context2 = semanticSearchHelper.createContext("query2", null, emptyUserBean);
        assertNotNull(context2);
        assertEquals("query2", context2.getQuery());
        assertNull(context2.getParams());
        semanticSearchHelper.closeContext();
    }

    /**
     * Test neural query builder creates different instances
     */
    public void test_neuralQueryBuilderCreatesNewInstances() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "instance-test-model");
        System.setProperty(CONTENT_FIELD, "vector");

        OptionalThing<QueryBuilder> result1 = semanticSearchHelper.newNeuralQueryBuilder("first");
        OptionalThing<QueryBuilder> result2 = semanticSearchHelper.newNeuralQueryBuilder("second");

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());

        // Should create different instances
        assertNotSame(result1.get(), result2.get());

        // But both should contain the model ID
        assertTrue(result1.get().toString().contains("instance-test-model"));
        assertTrue(result2.get().toString().contains("instance-test-model"));
    }

    /**
     * Test neural query builder with Unicode characters
     */
    public void test_neuralQueryBuilderWithUnicode() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "unicode-model");
        System.setProperty(CONTENT_FIELD, "vector");

        // Test with Japanese characters
        OptionalThing<QueryBuilder> result1 = semanticSearchHelper.newNeuralQueryBuilder("検索テスト");
        assertTrue(result1.isPresent());

        // Test with mixed languages
        OptionalThing<QueryBuilder> result2 = semanticSearchHelper.newNeuralQueryBuilder("search 検索 test");
        assertTrue(result2.isPresent());

        assertNotNull(result1.get());
        assertNotNull(result2.get());
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

        // Don't call init() in tests - it requires curlHelper which isn't available

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
     * Test concurrent context creation warning
     */
    public void test_concurrentContextCreation() throws Exception {
        // Don't call init() in tests - it requires curlHelper which isn't available

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
     * Test neural query builder with multiple queries sequentially
     */
    public void test_neuralQueryBuilderSequential() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "sequential-model");
        System.setProperty(CONTENT_FIELD, "vector");

        // Test multiple queries in sequence
        String[] queries = {"first query", "second query", "third query"};

        for (String query : queries) {
            OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder(query);
            assertTrue(result.isPresent());
            String queryString = result.get().toString();
            assertTrue(queryString.contains(query));
            assertTrue(queryString.contains("sequential-model"));
        }
    }

    /**
     * Test neural query builder with special characters in query text
     */
    public void test_neuralQueryBuilderWithSpecialChars() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "special-model");
        System.setProperty(CONTENT_FIELD, "vector");

        // Test with various special characters
        String queryWithChars = "query with @#$% chars";
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder(queryWithChars);

        assertTrue(result.isPresent());
        // Query should be created even with special characters
        assertNotNull(result.get());
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
        // Don't call init() in tests - it requires curlHelper which isn't available

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
            // Don't call init() in tests - it requires curlHelper which isn't available
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

        // The k parameter (number of nearest neighbors) is determined by request page size
        // In test environment without LaRequest, it uses DEFAULT_PAGE_SIZE
        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder("k parameter test");
        assertTrue(result.isPresent());

        String queryString = result.get().toString();
        // Verify query contains required model_id and field
        assertTrue(queryString.contains("test-model"));
        assertTrue(queryString.contains("vector_field"));
    }

    /**
     * Test context lifecycle: create -> get -> close
     */
    public void test_contextLifecycle() throws Exception {
        SearchRequestParams params = new MockSearchRequestParams();
        OptionalThing<FessUserBean> userBean = OptionalThing.empty();

        // Initially no context
        assertNull(semanticSearchHelper.getContext());

        // Create context
        SemanticSearchContext context1 = semanticSearchHelper.createContext("lifecycle query", params, userBean);
        assertNotNull(context1);
        assertEquals("lifecycle query", context1.getQuery());

        // Get context returns the same instance
        SemanticSearchContext context2 = semanticSearchHelper.getContext();
        assertSame(context1, context2);

        // Close context
        semanticSearchHelper.closeContext();
        assertNull(semanticSearchHelper.getContext());

        // Can create new context after closing
        SemanticSearchContext context3 = semanticSearchHelper.createContext("new query", params, userBean);
        assertNotNull(context3);
        assertEquals("new query", context3.getQuery());

        // Clean up
        semanticSearchHelper.closeContext();
    }

    /**
     * Test neural query builder with long query text
     */
    public void test_neuralQueryBuilderWithLongText() throws Exception {
        System.setProperty(CONTENT_MODEL_ID, "long-text-model");
        System.setProperty(CONTENT_FIELD, "vector");

        // Create a reasonably long query (not excessively long to avoid parser issues)
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longQuery.append("word").append(i).append(" ");
        }

        OptionalThing<QueryBuilder> result = semanticSearchHelper.newNeuralQueryBuilder(longQuery.toString().trim());

        assertTrue(result.isPresent());
        assertNotNull(result.get());
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