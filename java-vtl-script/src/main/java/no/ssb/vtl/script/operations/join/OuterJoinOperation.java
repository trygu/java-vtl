package no.ssb.vtl.script.operations.join;
/*-
 * #%L
 * java-vtl-script
 * %%
 * Copyright (C) 2016 Hadrien Kohl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.script.support.JoinSpliterator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OuterJoinOperation extends AbstractJoinOperation {

    OuterJoinOperation(Map<String, Dataset> namedDatasets) {
        super(namedDatasets, Collections.emptySet());
    }

    public OuterJoinOperation(Map<String, Dataset> namedDatasets, Set<Component> identifiers) {
        super(namedDatasets, identifiers);
    }

    @Override
    protected JoinSpliterator.TriFunction<JoinTuple, JoinTuple, Integer, List<JoinTuple>> getMerger() {
        Set<Component> identifiers = getIdentifiers();
        return (left, right, compare) -> {

            JoinTuple merged;
            if (compare <= 0) {
                merged = new JoinTuple(left.ids());
            } else {
                merged = new JoinTuple(right.ids());
            }

            if (compare == 0) {
                for (DataPoint point : left) {
                    if (!identifiers.contains(point.getComponent())) {
                        merged.add(point);
                    }
                }
                for (DataPoint point : right) {
                    if (!identifiers.contains(point.getComponent()))
                        merged.add(point);
                }
            } else {
                if (compare < 0) {
                    for (DataPoint point : left) {
                        if (!identifiers.contains(point.getComponent())) {
                            merged.add(point);
                        }
                    }
                    for (DataPoint point : right) {
                        if (!identifiers.contains(point.getComponent())) {
                            merged.add(createNull(point));
                        }
                    }
                } else { // (compare > 0) {
                    for (DataPoint point : left) {
                        if (!identifiers.contains(point.getComponent())) {
                            merged.add(createNull(point));
                        }
                    }
                    for (DataPoint point : right) {
                        if (!identifiers.contains(point.getComponent())) {
                            merged.add(point);
                        }
                    }
                }
            }
            return Collections.singletonList(merged);
        };
    }

    private DataPoint createNull(final DataPoint point) {
        return new DataPoint(point.getComponent()) {
            @Override
            public Object get() {
                return null;
            }
        };
    }

    @Override
    public WorkingDataset workDataset() {
        // TODO: Remove this method.
        return this;
    }

}
