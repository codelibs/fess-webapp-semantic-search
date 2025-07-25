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
package org.codelibs.fess.webapp.semantic_search.index.query;

import java.io.IOException;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.lucene.search.Query;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryShardContext;

/**
 * Custom OpenSearch query builder for neural/vector search queries.
 * Integrates with OpenSearch's neural search plugin to perform semantic search operations.
 */
public class NeuralQueryBuilder extends AbstractQueryBuilder<NeuralQueryBuilder> {

    private static final String NAME = "neural";

    private static final ParseField QUERY_TEXT_FIELD = new ParseField("query_text");

    private static final ParseField MODEL_ID_FIELD = new ParseField("model_id");

    private static final ParseField K_FIELD = new ParseField("k");

    private static final ParseField FILTER_FIELD = new ParseField("filter");

    private static final int DEFAULT_K = 10;

    /** The field name to search against. */
    protected String fieldName;

    /** The query text to be converted to vector representation. */
    protected String queryText;

    /** The ML model ID used for vector conversion. */
    protected String modelId;

    /** Number of nearest neighbors to return. */
    protected int k = DEFAULT_K;

    /** Optional filter to apply to the neural search results. */
    protected QueryBuilder filter;

    /**
     * Constructs a NeuralQueryBuilder from stream input.
     *
     * @param in the stream input to read from
     * @throws IOException if an I/O error occurs
     */
    public NeuralQueryBuilder(final StreamInput in) throws IOException {
        super(in);
        this.fieldName = in.readString();
        this.queryText = in.readString();
        this.modelId = in.readString();
        this.k = in.readVInt();
        this.filter = in.readOptionalNamedWriteable(QueryBuilder.class);
    }

    private NeuralQueryBuilder() {
    }

    /**
     * Builder class for creating NeuralQueryBuilder instances.
     */
    public static class Builder {

        /**
         * Default constructor.
         */
        public Builder() {
        }

        private int k;
        private String modelId;
        private String queryText;
        private String fieldName;
        private QueryBuilder filter;

        /**
         * Sets the field name to search against.
         *
         * @param fieldName the field name
         * @return this builder instance
         */
        public Builder field(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        /**
         * Sets the query text to be converted to vector representation.
         *
         * @param queryText the query text
         * @return this builder instance
         */
        public Builder query(final String queryText) {
            this.queryText = queryText;
            return this;
        }

        /**
         * Sets the ML model ID used for vector conversion.
         *
         * @param modelId the model ID
         * @return this builder instance
         */
        public Builder modelId(final String modelId) {
            this.modelId = modelId;
            return this;
        }

        /**
         * Sets the number of nearest neighbors to return.
         *
         * @param k the number of neighbors
         * @return this builder instance
         */
        public Builder k(final int k) {
            this.k = k;
            return this;
        }

        /**
         * Sets an optional filter to apply to the neural search results.
         *
         * @param filter the filter query
         * @return this builder instance
         */
        public Builder filter(final QueryBuilder filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds and returns a new NeuralQueryBuilder instance.
         *
         * @return the constructed NeuralQueryBuilder
         */
        public NeuralQueryBuilder build() {
            final NeuralQueryBuilder query = new NeuralQueryBuilder();
            query.k = k;
            query.modelId = modelId;
            query.queryText = queryText;
            query.fieldName = fieldName;
            query.filter = filter;
            return query;
        }
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected void doWriteTo(final StreamOutput out) throws IOException {
        out.writeString(this.fieldName);
        out.writeString(this.queryText);
        out.writeString(this.modelId);
        out.writeVInt(this.k);
        out.writeOptionalNamedWriteable(this.filter);
    }

    @Override
    protected void doXContent(final XContentBuilder xContentBuilder, final Params params) throws IOException {
        xContentBuilder.startObject(NAME);
        xContentBuilder.startObject(fieldName);
        xContentBuilder.field(QUERY_TEXT_FIELD.getPreferredName(), queryText);
        xContentBuilder.field(MODEL_ID_FIELD.getPreferredName(), modelId);
        xContentBuilder.field(K_FIELD.getPreferredName(), k);
        if (filter != null) {
            xContentBuilder.field(FILTER_FIELD.getPreferredName(), filter);
        }
        printBoostAndQueryName(xContentBuilder);
        xContentBuilder.endObject();
        xContentBuilder.endObject();
    }

    @Override
    protected Query doToQuery(final QueryShardContext context) throws IOException {
        throw new UnsupportedOperationException("doToQuery is not supported.");
    }

    @Override
    protected boolean doEquals(final NeuralQueryBuilder obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(fieldName, obj.fieldName);
        equalsBuilder.append(queryText, obj.queryText);
        equalsBuilder.append(modelId, obj.modelId);
        equalsBuilder.append(k, obj.k);
        equalsBuilder.append(filter, obj.filter);
        return equalsBuilder.isEquals();
    }

    @Override
    protected int doHashCode() {
        return new HashCodeBuilder().append(fieldName).append(queryText).append(modelId).append(k).toHashCode();
    }
}
