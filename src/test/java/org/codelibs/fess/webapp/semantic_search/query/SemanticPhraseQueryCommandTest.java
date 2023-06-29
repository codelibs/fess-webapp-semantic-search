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
package org.codelibs.fess.webapp.semantic_search.query;

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_MODEL_ID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;
import org.codelibs.fess.entity.QueryContext;
import org.codelibs.fess.es.client.SearchEngineClient;
import org.codelibs.fess.query.QueryFieldConfig;
import org.codelibs.fess.query.parser.QueryParser;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.helper.SemanticSearchHelper;
import org.dbflute.optional.OptionalThing;
import org.dbflute.utflute.lastadi.LastaDiTestCase;
import org.opensearch.index.query.QueryBuilder;

public class SemanticPhraseQueryCommandTest extends LastaDiTestCase {
    private static final Logger logger = LogManager.getLogger(SemanticPhraseQueryCommandTest.class);

    private SemanticPhraseQueryCommand queryCommand;

    private SemanticSearchHelper semanticSearchHelper;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        System.setProperty(CONTENT_MODEL_ID, "");
        System.setProperty(CONTENT_FIELD, "");

        final QueryFieldConfig queryFieldConfig = new QueryFieldConfig();
        queryFieldConfig.init();
        ComponentUtil.register(queryFieldConfig, "queryFieldConfig");

        final QueryParser queryParser = new QueryParser();
        queryParser.init();
        ComponentUtil.register(queryParser, "queryParser");

        final SearchEngineClient searchEngineClient = new SearchEngineClient();
        ComponentUtil.register(searchEngineClient, "searchEngineClient");

        semanticSearchHelper = new SemanticSearchHelper();
        semanticSearchHelper.init();
        ComponentUtil.register(semanticSearchHelper, "semanticSearchHelper");

        queryCommand = new SemanticPhraseQueryCommand();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    public void test_execute() throws Exception {
        assertQueryBuilder(
                "{\"bool\":{\"should\":[{\"match_phrase\":{\"title\":{\"query\":\"ThisisFess.\",\"slop\":0,\"zero_terms_query\":\"NONE\",\"boost\":0.5}}},{\"match_phrase\":{\"content\":{\"query\":\"ThisisFess.\",\"slop\":0,\"zero_terms_query\":\"NONE\",\"boost\":0.05}}}],\"adjust_pure_negative\":true,\"boost\":1.0}}",
                "\"This is Fess.\"");

        System.setProperty(CONTENT_MODEL_ID, "modelx");
        System.setProperty(CONTENT_FIELD, "content_vector");

        assertQueryBuilder(
                "{\"neural\":{\"content_vector\":{\"query_text\":\"ThisisFess.\",\"model_id\":\"modelx\",\"k\":20,\"boost\":1.0}}}",
                "This is Fess.");
        assertQueryBuilder(
                "{\"neural\":{\"content_vector\":{\"query_text\":\"ThisisFess.\",\"model_id\":\"modelx\",\"k\":20,\"boost\":1.0}}}",
                "\"This is Fess.\"");
    }

    private void assertQueryBuilder(final String expect, final String text) throws Exception {
        semanticSearchHelper.createContext(text, null, OptionalThing.empty());
        try {
        final QueryContext context = new QueryContext(text, false);
        final Query query = ComponentUtil.getQueryParser().parse(context.getQueryString());
        final QueryBuilder builder = queryCommand.execute(context, query, 1.0f);
        logger.info("{} => {}", text, builder.toString());
        assertEquals(expect, builder.toString().replaceAll("[\s\n]", ""));
        }finally {
            semanticSearchHelper.closeContext();
        }
    }

}