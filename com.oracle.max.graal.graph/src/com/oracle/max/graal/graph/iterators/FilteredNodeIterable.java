/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.graal.graph.iterators;

import java.util.*;

import com.oracle.max.graal.graph.*;

public class FilteredNodeIterable<T extends Node> extends NodeIterable<T> {
    private final NodeIterable<T> nodeIterable;
    private NodePredicate predicate = new NodePredicate() {
        @Override
        public boolean apply(Node n) {
            return true;
        }
    };
    public FilteredNodeIterable(NodeIterable<T> nodeIterable) {
        this.nodeIterable = nodeIterable;
        this.until = nodeIterable.until;
    }
    @SuppressWarnings("unchecked")
    public <F extends T> FilteredNodeIterable<F> and(Class<F> clazz) {
        this.predicate = predicate.and(new TypePredicate(clazz));
        return (FilteredNodeIterable<F>) this;
    }
    @SuppressWarnings("unchecked")
    public FilteredNodeIterable<Node> or(Class<? extends Node> clazz) {
        this.predicate = predicate.or(new TypePredicate(clazz));
        return (FilteredNodeIterable<Node>) this;
    }
    @Override
    public Iterator<T> iterator() {
        final Iterator<T> iterator = nodeIterable.iterator();
        return new PredicatedProxyNodeIterator<T>(until, iterator, predicate);
    }
}
