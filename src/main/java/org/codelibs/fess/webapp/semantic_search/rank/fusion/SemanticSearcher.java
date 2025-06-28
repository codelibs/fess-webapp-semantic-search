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
package org.codelibs.fess.webapp.semantic_search.rank.fusion;

import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_CHUNK_FIELD;
import static org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants.CONTENT_NESTED_FIELD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.entity.FacetInfo;
import org.codelibs.fess.entity.GeoInfo;
import org.codelibs.fess.entity.HighlightInfo;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.opensearch.client.SearchEngineClient.SearchCondition;
import org.codelibs.fess.opensearch.client.SearchEngineClient.SearchConditionBuilder;
import org.codelibs.fess.rank.fusion.DefaultSearcher;
import org.codelibs.fess.rank.fusion.SearchResult;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.util.DocumentUtil;
import org.codelibs.fess.webapp.semantic_search.SemanticSearchConstants;
import org.codelibs.fess.webapp.semantic_search.helper.SemanticSearchHelper;
import org.dbflute.optional.OptionalThing;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHit.NestedIdentity;
import org.opensearch.search.SearchHits;

import jakarta.annotation.PostConstruct;

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
            final SearchRequestParams reqParams = new SearchRequestParamsWrapper(params, semanticSearchHelper.getMinScore());
            final StringBuilder queryBuf = new StringBuilder(query.length() + 40);
            queryBuf.append(query);
            final Long minContentLength = semanticSearchHelper.getMinContentLength();
            if (minContentLength != null && minContentLength.longValue() >= 0) {
                final String contentLengthField = ComponentUtil.getFessConfig().getIndexFieldContentLength();
                if (isSearchableField(contentLengthField)) {
                    if (query.indexOf('"') == -1) {
                        queryBuf.setLength(0);
                        queryBuf.append('"').append(query).append('"');
                    }
                    queryBuf.append(' ').append(contentLengthField).append(":[").append(minContentLength.toString()).append(" TO *]");
                    if (logger.isDebugEnabled()) {
                        logger.debug("append {} range query: {} => {}", contentLengthField, query, queryBuf);
                    }
                }
            }
            semanticSearchHelper.createContext(query, reqParams, userBean);
            return super.search(queryBuf.toString(), reqParams, userBean);
        } finally {
            semanticSearchHelper.closeContext();
        }
    }

    @Override
    protected SearchCondition<SearchRequestBuilder> createSearchCondition(final String query, final SearchRequestParams params,
            final OptionalThing<FessUserBean> userBean) {
        final String chunkField = System.getProperty(CONTENT_CHUNK_FIELD); // ex. content_chunk
        if (StringUtil.isBlank(chunkField)) {
            return super.createSearchCondition(query, params, userBean);
        }
        final String[] responseFields =
                Stream.concat(Arrays.stream(params.getResponseFields()), Stream.of(chunkField)).toArray(String[]::new);
        if (logger.isDebugEnabled()) {
            logger.debug("responseFields={}", Arrays.toString(responseFields));
        }
        return searchRequestBuilder -> {
            ComponentUtil.getQueryHelper().processSearchPreference(searchRequestBuilder, userBean, query);
            return SearchConditionBuilder.builder(searchRequestBuilder).query(query).offset(params.getStartPosition())
                    .size(params.getPageSize()).facetInfo(params.getFacetInfo()).geoInfo(params.getGeoInfo())
                    .highlightInfo(params.getHighlightInfo()).similarDocHash(params.getSimilarDocHash()).responseFields(responseFields)
                    .searchRequestType(params.getType()).trackTotalHits(params.getTrackTotalHits()).minScore(params.getMinScore()).build();
        };
    }

    @Override
    protected Map<String, Object> parseSearchHit(final FessConfig fessConfig, final String hlPrefix, final SearchHit searchHit) {
        final Map<String, Object> docMap = super.parseSearchHit(fessConfig, hlPrefix, searchHit);
        final Map<String, SearchHits> innerHits = searchHit.getInnerHits();
        if (innerHits != null) {
            final String chunkField = System.getProperty(CONTENT_CHUNK_FIELD); // ex. content_chunk
            if (StringUtil.isNotBlank(chunkField)) {
                final String nestedField = System.getProperty(CONTENT_NESTED_FIELD); // ex. content_vector
                final SearchHits innerSearchHits = innerHits.get(nestedField);
                if (logger.isDebugEnabled()) {
                    logger.debug("nestedField={}, innerSearchHits={}", nestedField, innerSearchHits);
                }
                final String[] chunks = DocumentUtil.getValue(docMap, chunkField, String[].class);
                docMap.remove(chunkField);
                if (innerSearchHits != null) {
                    final List<String> chunkList = new ArrayList<>();
                    String contentDesc = null;
                    for (final SearchHit hit : innerSearchHits.getHits()) {
                        final NestedIdentity nestedIdentity = hit.getNestedIdentity();
                        if (nestedIdentity != null) {
                            final int offset = nestedIdentity.getOffset();
                            if (logger.isDebugEnabled()) {
                                logger.debug("offset={}, chunks={}", offset, chunks);
                            }
                            if (chunks != null && chunks.length > offset) {
                                if (contentDesc == null) {
                                    contentDesc = chunks[offset];
                                }
                                chunkList.add(chunks[offset]);
                            }
                        }
                    }
                    if (StringUtil.isNotBlank(contentDesc)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("matched chunk: {}={}", fessConfig.getResponseFieldContentDescription(), contentDesc);
                        }
                        docMap.put(fessConfig.getResponseFieldContentDescription(), contentDesc);
                    }
                    docMap.put(chunkField, chunkList.toArray(n -> new String[n]));
                }
            }
        }
        return docMap;
    }

    protected boolean isSearchableField(final String field) {
        for (final String f : ComponentUtil.getQueryFieldConfig().getSearchFields()) {
            if (field.equals(f)) {
                return true;
            }
        }
        return false;
    }

    protected SemanticSearchHelper getSemanticSearchHelper() {
        return ComponentUtil.getComponent(SemanticSearchConstants.SEMANTIC_SEARCH_HELPER);
    }

    protected static class SearchRequestParamsWrapper extends SearchRequestParams {
        private final SearchRequestParams parent;
        private final Float minScore;

        protected SearchRequestParamsWrapper(final SearchRequestParams params, final Float minScore) {
            this.parent = params;
            this.minScore = minScore;
        }

        @Override
        public String getQuery() {
            return parent.getQuery();
        }

        @Override
        public Map<String, String[]> getFields() {
            return parent.getFields();
        }

        @Override
        public Map<String, String[]> getConditions() {
            return parent.getConditions();
        }

        @Override
        public String[] getLanguages() {
            return parent.getLanguages();
        }

        @Override
        public GeoInfo getGeoInfo() {
            return null;
        }

        @Override
        public FacetInfo getFacetInfo() {
            return null;
        }

        @Override
        public HighlightInfo getHighlightInfo() {
            return null;
        }

        @Override
        public String getSort() {
            return parent.getSort();
        }

        @Override
        public int getStartPosition() {
            return parent.getStartPosition();
        }

        @Override
        public int getPageSize() {
            return parent.getPageSize();
        }

        @Override
        public int getOffset() {
            return parent.getOffset();
        }

        @Override
        public String[] getExtraQueries() {
            return parent.getExtraQueries();
        }

        @Override
        public Object getAttribute(final String name) {
            return parent.getAttribute(name);
        }

        @Override
        public Locale getLocale() {
            return parent.getLocale();
        }

        @Override
        public SearchRequestType getType() {
            return parent.getType();
        }

        @Override
        public String getSimilarDocHash() {
            return parent.getSimilarDocHash();
        }

        @Override
        public Float getMinScore() {
            return minScore;
        }
    }

}
