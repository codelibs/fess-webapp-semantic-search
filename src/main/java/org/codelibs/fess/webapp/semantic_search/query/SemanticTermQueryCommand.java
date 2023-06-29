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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.TermQuery;
import org.codelibs.fess.Constants;
import org.codelibs.fess.entity.QueryContext;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.query.TermQueryCommand;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants;
import org.codelibs.fess.webapp.semantic_search.helper.SemanticSearchHelper;
import org.opensearch.index.query.QueryBuilder;

public class SemanticTermQueryCommand extends TermQueryCommand {

    private static final Logger logger = LogManager.getLogger(SemanticTermQueryCommand.class);

    @Override
    protected QueryBuilder convertDefaultTermQuery(final FessConfig fessConfig, final QueryContext context, final TermQuery termQuery,
            final float boost, final String field, final String text) {
        final SemanticSearchHelper semanticSearchHelper = getSemanticSearchHelper();

        if (!Constants.DEFAULT_FIELD.equals(field) || semanticSearchHelper.getContext() == null) {
            return super.convertDefaultTermQuery(fessConfig, context, termQuery, boost, field, text);
        }

        return semanticSearchHelper.newNeuralQueryBuilder(text).map(builder -> {
            context.addFieldLog(field, text);
            context.addHighlightedQuery(text);
            if (logger.isDebugEnabled()) {
                logger.debug("NeuralQueryBuilder: {}", builder);
            }
            return builder;
        }).orElseGet(() -> super.convertDefaultTermQuery(fessConfig, context, termQuery, boost, field, text));
    }

    protected SemanticSearchHelper getSemanticSearchHelper() {
        return ComponentUtil.getComponent(SemanticSearchConstants.SEMANTIC_SEARCH_HELPER);
    }
}
