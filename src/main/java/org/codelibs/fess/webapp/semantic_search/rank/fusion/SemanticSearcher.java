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
package org.codelibs.fess.webapp.semantic_search.rank.fusion;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.rank.fusion.DefaultSearcher;
import org.codelibs.fess.rank.fusion.SearchResult;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants;
import org.codelibs.fess.webapp.semantic_search.helper.SemanticSearchHelper;
import org.dbflute.optional.OptionalThing;

public class SemanticSearcher extends DefaultSearcher {
    private static final Logger logger = LogManager.getLogger(SemanticSearcher.class);

    @PostConstruct
    public void register() {
        if (logger.isInfoEnabled()) {
            logger.info("Load {}", this.getClass().getSimpleName());
        }

        ComponentUtil.getRankFusionProcessor().register(this);
    }

    @Override
    protected SearchResult search(final String query, final SearchRequestParams params, final OptionalThing<FessUserBean> userBean) {
        final SemanticSearchHelper semanticSearchHelper = getSemanticSearchHelper();
        try {
            semanticSearchHelper.createContext(query, params, userBean);
            return super.search(query, params, userBean);
        } finally {
            semanticSearchHelper.closeContext();
        }
    }

    protected SemanticSearchHelper getSemanticSearchHelper() {
        return ComponentUtil.getComponent(SemanticSearchConstants.SEMANTIC_SEARCH_HELPER);
    }
}
