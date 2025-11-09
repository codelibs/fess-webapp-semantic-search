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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import junit.framework.TestCase;

public class SemanticSearchConstantsTest extends TestCase {
    private static final Logger logger = LogManager.getLogger(SemanticSearchConstantsTest.class);

    /**
     * Test that all constants have the expected prefix
     */
    public void test_constantsPrefix() throws Exception {
        Field[] fields = SemanticSearchConstants.class.getFields();

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == String.class
                    && !field.getName().equals("SEMANTIC_SEARCH_HELPER")) {

                String value = (String) field.get(null);
                assertTrue("Constant " + field.getName() + " should start with 'fess.semantic_search.'",
                        value.startsWith("fess.semantic_search."));
            }
        }
    }

    /**
     * Test specific constant values
     */
    public void test_specificConstantValues() throws Exception {
        assertEquals("fess.semantic_search.pipeline", SemanticSearchConstants.PIPELINE);
        assertEquals("fess.semantic_search.content.model_id", SemanticSearchConstants.CONTENT_MODEL_ID);
        assertEquals("fess.semantic_search.content.dimension", SemanticSearchConstants.CONTENT_DIMENSION);
        assertEquals("fess.semantic_search.content.engine", SemanticSearchConstants.CONTENT_ENGINE);
        assertEquals("fess.semantic_search.content.method", SemanticSearchConstants.CONTENT_METHOD);
        assertEquals("fess.semantic_search.content.space_type", SemanticSearchConstants.CONTENT_SPACE_TYPE);
        assertEquals("fess.semantic_search.content.param.m", SemanticSearchConstants.CONTENT_PARAM_M);
        assertEquals("fess.semantic_search.content.param.ef_construction", SemanticSearchConstants.CONTENT_PARAM_EF_CONSTRUCTION);
        assertEquals("fess.semantic_search.content.param.ef_search", SemanticSearchConstants.CONTENT_PARAM_EF_SEARCH);
        assertEquals("fess.semantic_search.content.field", SemanticSearchConstants.CONTENT_FIELD);
        assertEquals("fess.semantic_search.content.nested_field", SemanticSearchConstants.CONTENT_NESTED_FIELD);
        assertEquals("fess.semantic_search.content.chunk_field", SemanticSearchConstants.CONTENT_CHUNK_FIELD);
        assertEquals("fess.semantic_search.content.chunk_size", SemanticSearchConstants.CONTENT_CHUNK_SIZE);
        assertEquals("fess.semantic_search.min_score", SemanticSearchConstants.MIN_SCORE);
        assertEquals("fess.semantic_search.min_content_length", SemanticSearchConstants.MIN_CONTENT_LENGTH);
        assertEquals("semanticSearchHelper", SemanticSearchConstants.SEMANTIC_SEARCH_HELPER);
    }

    /**
     * Test v15.3.0+ new constant values
     */
    public void test_v15_3_0_newConstants() throws Exception {
        // HNSW ef_search parameter
        assertEquals("fess.semantic_search.content.param.ef_search", SemanticSearchConstants.CONTENT_PARAM_EF_SEARCH);

        // MMR (Maximal Marginal Relevance) constants
        assertEquals("fess.semantic_search.mmr.enabled", SemanticSearchConstants.MMR_ENABLED);
        assertEquals("fess.semantic_search.mmr.lambda", SemanticSearchConstants.MMR_LAMBDA);

        // Batch inference
        assertEquals("fess.semantic_search.batch_inference.enabled", SemanticSearchConstants.BATCH_INFERENCE_ENABLED);

        // Performance monitoring
        assertEquals("fess.semantic_search.performance.monitoring.enabled", SemanticSearchConstants.PERFORMANCE_MONITORING_ENABLED);
    }

    /**
     * Test that all constants are public, static, and final
     */
    public void test_constantModifiers() throws Exception {
        Field[] fields = SemanticSearchConstants.class.getFields();

        for (Field field : fields) {
            if (field.getType() == String.class) {
                int modifiers = field.getModifiers();
                assertTrue("Field " + field.getName() + " should be public", Modifier.isPublic(modifiers));
                assertTrue("Field " + field.getName() + " should be static", Modifier.isStatic(modifiers));
                assertTrue("Field " + field.getName() + " should be final", Modifier.isFinal(modifiers));
            }
        }
    }

    /**
     * Test that constant values are unique (no duplicates)
     */
    public void test_constantUniqueness() throws Exception {
        Field[] fields = SemanticSearchConstants.class.getFields();
        Set<String> values = new HashSet<>();

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == String.class) {

                String value = (String) field.get(null);
                assertFalse("Duplicate constant value found: " + value, values.contains(value));
                values.add(value);
            }
        }

        logger.info("Found {} unique constant values", values.size());
    }

    /**
     * Test constant naming conventions
     */
    public void test_constantNamingConventions() throws Exception {
        Field[] fields = SemanticSearchConstants.class.getFields();

        for (Field field : fields) {
            if (field.getType() == String.class) {
                String name = field.getName();

                // Should be uppercase with underscores
                assertTrue("Constant name " + name + " should be uppercase", name.equals(name.toUpperCase()));

                // Should not start or end with underscore
                assertFalse("Constant name " + name + " should not start with underscore", name.startsWith("_"));
                assertFalse("Constant name " + name + " should not end with underscore", name.endsWith("_"));

                // Should not contain consecutive underscores
                assertFalse("Constant name " + name + " should not contain consecutive underscores", name.contains("__"));
            }
        }
    }

    /**
     * Test content-related constants grouping
     */
    public void test_contentConstantsGrouping() throws Exception {
        // All content-related constants should start with CONTENT_
        String[] contentConstants = { "CONTENT_MODEL_ID", "CONTENT_DIMENSION", "CONTENT_ENGINE", "CONTENT_METHOD", "CONTENT_SPACE_TYPE",
                "CONTENT_PARAM_M", "CONTENT_PARAM_EF_CONSTRUCTION", "CONTENT_PARAM_EF_SEARCH", "CONTENT_FIELD", "CONTENT_NESTED_FIELD",
                "CONTENT_CHUNK_FIELD", "CONTENT_CHUNK_SIZE" };

        for (String constantName : contentConstants) {
            Field field = SemanticSearchConstants.class.getField(constantName);
            String value = (String) field.get(null);
            assertTrue("Content constant " + constantName + " should contain 'content' in its value", value.contains("content"));
        }
    }

    /**
     * Test parameter constants structure
     */
    public void test_parameterConstants() throws Exception {
        String paramM = SemanticSearchConstants.CONTENT_PARAM_M;
        String paramEfConstruction = SemanticSearchConstants.CONTENT_PARAM_EF_CONSTRUCTION;
        String paramEfSearch = SemanticSearchConstants.CONTENT_PARAM_EF_SEARCH;

        assertTrue("CONTENT_PARAM_M should contain 'param'", paramM.contains("param"));
        assertTrue("CONTENT_PARAM_EF_CONSTRUCTION should contain 'param'", paramEfConstruction.contains("param"));
        assertTrue("CONTENT_PARAM_EF_SEARCH should contain 'param'", paramEfSearch.contains("param"));

        assertTrue("CONTENT_PARAM_M should end with '.m'", paramM.endsWith(".m"));
        assertTrue("CONTENT_PARAM_EF_CONSTRUCTION should end with '.ef_construction'", paramEfConstruction.endsWith(".ef_construction"));
        assertTrue("CONTENT_PARAM_EF_SEARCH should end with '.ef_search'", paramEfSearch.endsWith(".ef_search"));
    }

    /**
     * Test non-prefixed constants
     */
    public void test_nonPrefixedConstants() throws Exception {
        // SEMANTIC_SEARCH_HELPER should not have the fess.semantic_search prefix
        assertEquals("semanticSearchHelper", SemanticSearchConstants.SEMANTIC_SEARCH_HELPER);
        assertFalse("SEMANTIC_SEARCH_HELPER should not start with prefix",
                SemanticSearchConstants.SEMANTIC_SEARCH_HELPER.startsWith("fess.semantic_search."));
    }

    /**
     * Test class is not instantiable (has private constructor)
     */
    public void test_classNotInstantiable() throws Exception {
        try {
            SemanticSearchConstants.class.getDeclaredConstructor().newInstance();
            fail("SemanticSearchConstants should not be instantiable");
        } catch (IllegalAccessException e) {
            // Expected - constructor should be private
            assertTrue(true);
        } catch (Exception e) {
            // Other exceptions might also indicate non-instantiability
            logger.debug("Constructor access exception: {}", e.getMessage());
            assertTrue(true);
        }
    }

    /**
     * Test constant count to ensure all expected constants are present
     */
    public void test_constantCount() throws Exception {
        Field[] fields = SemanticSearchConstants.class.getFields();
        int stringConstantCount = 0;

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == String.class) {
                stringConstantCount++;
            }
        }

        // v15.3.0 added 5 new constants: CONTENT_PARAM_EF_SEARCH, MMR_ENABLED, MMR_LAMBDA, BATCH_INFERENCE_ENABLED, PERFORMANCE_MONITORING_ENABLED
        // Expect at least 20 string constants (15 original + 5 new)
        assertTrue("Should have at least 20 string constants, found: " + stringConstantCount, stringConstantCount >= 20);

        logger.info("Found {} string constants in SemanticSearchConstants", stringConstantCount);
    }

    /**
     * Test that constants can be used as system property keys
     */
    public void test_constantsAsSystemProperties() throws Exception {
        // Test that constants can be used to set and retrieve system properties
        String testValue = "test_value_12345";

        System.setProperty(SemanticSearchConstants.CONTENT_MODEL_ID, testValue);
        assertEquals(testValue, System.getProperty(SemanticSearchConstants.CONTENT_MODEL_ID));

        System.setProperty(SemanticSearchConstants.MIN_SCORE, "0.75");
        assertEquals("0.75", System.getProperty(SemanticSearchConstants.MIN_SCORE));

        // Clean up
        System.clearProperty(SemanticSearchConstants.CONTENT_MODEL_ID);
        System.clearProperty(SemanticSearchConstants.MIN_SCORE);
    }
}