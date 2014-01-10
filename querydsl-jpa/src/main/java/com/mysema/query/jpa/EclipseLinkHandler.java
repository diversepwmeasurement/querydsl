/*
 * Copyright 2013, Mysema Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.jpa;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import javax.persistence.Query;

import org.eclipse.persistence.config.QueryHints;
import org.eclipse.persistence.config.ResultSetType;
import org.eclipse.persistence.jpa.JpaQuery;
import org.eclipse.persistence.queries.Cursor;

import com.mysema.commons.lang.CloseableIterator;
import com.mysema.commons.lang.IteratorAdapter;
import com.mysema.query.types.Expression;
import com.mysema.query.types.FactoryExpression;

/**
 *
 * @author tiwe
 *
 */
public class EclipseLinkHandler implements QueryHandler {

    @Override
    public void addEntity(Query query, Expression<?> expression) {
        // do nothing
    }

    @Override
    public <T> CloseableIterator<T> iterate(Query query, FactoryExpression<?> projection) {
        Iterator<T> iterator = null;
        Closeable closeable = null;
        if (query instanceof JpaQuery) {
            JpaQuery<T> elQuery = (JpaQuery<T>) query;
            elQuery.setHint(QueryHints.RESULT_SET_TYPE, ResultSetType.ForwardOnly);
            elQuery.setHint(QueryHints.SCROLLABLE_CURSOR, true);
            final Cursor cursor = elQuery.getResultCursor();
            closeable = new Closeable() {
                @Override
                public void close() throws IOException {
                    cursor.close();
                }
            };
            iterator = cursor;
        } else {
            iterator = query.getResultList().iterator();
        }
        if (projection != null) {
            return new TransformingIterator<T>(iterator, closeable, projection);
        } else {
            return new IteratorAdapter<T>(iterator, closeable);
        }
    }

    @Override
    public boolean transform(Query query, FactoryExpression<?> projection) {
        return false;
    }

}
