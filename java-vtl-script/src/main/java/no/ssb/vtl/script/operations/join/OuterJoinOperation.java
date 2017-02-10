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

import com.google.common.collect.Lists;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.script.support.JoinSpliterator;

import java.util.*;

public class OuterJoinOperation extends AbstractJoinOperation {

    OuterJoinOperation(Map<String, Dataset> namedDatasets) {
        super(namedDatasets, Collections.emptySet());
    }

    public OuterJoinOperation(Map<String, Dataset> namedDatasets, Set<Component> identifiers) {
        super(namedDatasets, identifiers);
    }

    @Override
    protected JoinSpliterator.TriFunction<JoinTuple, JoinTuple, Integer, List<JoinTuple>> getMerger() {
        return (left, right, compare) -> {
            if (compare == 0) {
                return Collections.emptyList();
            } else {

                // Pad left with nulls.
                JoinTuple leftPadded = new JoinTuple(left.ids());
                leftPadded.addAll(left.values());
                for (DataPoint point : left.values()) {
                    leftPadded.add(new DataPoint(point.getComponent()) {
                        @Override
                        public Object get() {
                            return null;
                        }
                    });
                }

                // Update id reference.
                List<DataPoint> rightIds = Lists.newArrayList();
                Iterator<DataPoint> leftId = left.ids().iterator();
                Iterator<DataPoint> idsIt = right.ids().iterator();
                while (leftId.hasNext() && idsIt.hasNext()) {
                    final Object value = idsIt.next().get();
                    rightIds.add(new DataPoint(leftId.next().getComponent()) {
                        @Override
                        public Object get() {
                            return value;
                        }
                    });
                }

                JoinTuple rightPadded = new JoinTuple(rightIds);
                for (DataPoint point : right.values()) {
                    rightPadded.add(new DataPoint(point.getComponent()) {
                        @Override
                        public Object get() {
                            return null;
                        }
                    });
                }

                // Update reference.
                leftId = left.values().iterator();
                Iterator<DataPoint> valuesIt = right.values().iterator();
                while (leftId.hasNext() && valuesIt.hasNext()) {
                    final Object value = valuesIt.next().get();
                    rightPadded.add(new DataPoint(leftId.next().getComponent()) {
                        @Override
                        public Object get() {
                            return value;
                        }
                    });
                }

                if (compare > 0) {
                    return Arrays.asList(rightPadded, leftPadded);
                } else {
                    return Arrays.asList(leftPadded, rightPadded);
                }
            }
        };
    }

    @Override
    public WorkingDataset workDataset() {
        // TODO: Remove this method.
        return this;
    }

}
