package no.ssb.vtl.script.operations.hierarchy;

import com.codepoetics.protonpack.StreamUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import no.ssb.vtl.model.AbstractUnaryDatasetOperation;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.Order;
import no.ssb.vtl.model.VTLObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.*;
import static java.util.stream.Collectors.*;
import static no.ssb.vtl.script.operations.hierarchy.HierarchyAccumulator.*;

public class HierarchyOperation extends AbstractUnaryDatasetOperation {

    private static final String FROM_COLUMN_NAME = "from";
    private static final String TO_COLUMN_NAME = "to";
    private static final String SIGN_COLUMN_NAME = "sign";
    private static final String COLUMN_NOT_FOUND = "could not find the column %s";
    private static final String UNKNOWN_SIGN_VALUE = "unknown sign component %s";
    private static final String CIRCULAR_DEPENDENCY = "the edge %s -(%s)-> %s introduced a loop (%s)";
    private static final Map<String, Composition> COMPOSITION_MAP = ImmutableMap.of(
            "", Composition.UNION,
            "+", Composition.UNION,
            "plus", Composition.UNION,
            "-", Composition.COMPLEMENT,
            "minus", Composition.COMPLEMENT
    );

    private final ImmutableValueGraph<VTLObject, Composition> graph;

    // The component
    private final Component component;

    public HierarchyOperation(Dataset dataset, ValueGraph<VTLObject, Composition> hierarchy, Component group) {
        super(dataset);

        this.component = checkNotNull(group, "component cannot be null");

        checkArgument(
                group.isIdentifier(),
                "%s was not an identifier",
                group
        );

        checkArgument(
                dataset.getDataStructure().containsValue(group),
                "%s was not part of %s",
                group, dataset
        );

        for (Component component : dataset.getDataStructure().values()) {
            if (component.isMeasure()) {
                checkArgument(
                        Number.class.isAssignableFrom(component.getType()),
                        "all measure components must be numeric (%s was not)",
                        component
                );
            }
        }

        // TODO: Hierarchy should be typed.
        checkNotNull(hierarchy);
        checkArgument(hierarchy.isDirected());
        checkArgument(!hierarchy.allowsSelfLoops());
        this.graph = ImmutableValueGraph.copyOf(hierarchy);
    }

    public HierarchyOperation(Dataset dataset, Dataset hierarchy, Component component) {
        this(dataset, convertToHierarchy(hierarchy), component);
    }

    /**
     * Create the directed acyclic graph from the dataset.
     * <p>
     * The dataset is required to have the following columns:
     * from, to, sign.
     *
     * @throws IllegalArgumentException if a circular dependency is found.
     * @throws IllegalArgumentException if from and to are not of the same type.
     */
    private static ValueGraph<VTLObject, Composition> convertToHierarchy(final Dataset hierarchy) {

        // Checks.
        final DataStructure structure = checkNotNull(hierarchy).getDataStructure();
        Component fromComponent = checkNotNull(structure.get(FROM_COLUMN_NAME), COLUMN_NOT_FOUND, FROM_COLUMN_NAME);
        Component toComponent = checkNotNull(structure.get(TO_COLUMN_NAME), COLUMN_NOT_FOUND, TO_COLUMN_NAME);
        Component signComponent = checkNotNull(structure.get(SIGN_COLUMN_NAME), COLUMN_NOT_FOUND, SIGN_COLUMN_NAME);

        // The graph.
        MutableValueGraph<VTLObject, Composition> graph = ValueGraphBuilder.directed()
                .allowsSelfLoops(false)
                .build();

        // Add all the points.
        for (DataPoint point : (Iterable<? extends DataPoint>) hierarchy.getData()::iterator) {

            Map<Component, VTLObject> asMap = structure.asMap(point);

            VTLObject from = asMap.get(fromComponent);
            VTLObject to = asMap.get(toComponent);
            VTLObject sign = asMap.get(signComponent);

            Composition composition = checkNotNull(COMPOSITION_MAP.get(sign.get()), UNKNOWN_SIGN_VALUE, sign);

            List<List<VTLObject>> paths = findPaths(graph, to, from);
            checkArgument(paths.isEmpty(), CIRCULAR_DEPENDENCY, from, composition, to, paths);

            graph.putEdgeValue(from, to, composition);
        }
        return graph;
    }

    @VisibleForTesting
    static <T> LinkedList<T> sortTopologically(ValueGraph<T, Composition> graph) {
        // Kahn's algorithm
        MutableValueGraph<T, Composition> g = Graphs.copyOf(graph);
        LinkedList<T> sorted = Lists.newLinkedList();
        Deque<T> leaves = Lists.newLinkedList(g.nodes()
                .stream()
                .filter(n -> g.inDegree(n) == 0)
                .collect(toList())
        );
        while (!leaves.isEmpty()) {
            T node = leaves.pop();
            sorted.push(node);
            Set<T> successors = ImmutableSet.copyOf(g.successors(node));
            for (T successor : successors) {
                g.removeEdge(node, successor);
                if (g.inDegree(successor) == 0) {
                    leaves.addLast(successor);
                }
            }
        }
        checkArgument(g.edges().isEmpty(), "the graph contains a circular dependency %s", g);
        Collections.reverse(sorted);
        return sorted;
    }

    /**
     * Try to find a path in the graph between from and to.
     *
     * @return a list (possibly empty) of paths
     */
    @VisibleForTesting
    static <T> List<List<T>> findPaths(Graph<T> graph, T from, T to) {
        List<List<T>> paths = Lists.newArrayList();
        if (graph.nodes().contains(from) && graph.nodes().contains(to)) {
            // DAG means no loop.
            // Each time we add nodes, check if there is a path
            // already. We don't check if we never saw one of them.

            //LinkedHashSet<String> path = Sets.newLinkedHashSet();
            Deque<T> stack = Lists.newLinkedList();
            stack.push(from);

            while (!stack.isEmpty()) {
                T current = stack.pop();

                if (current.equals(to)) {
                    // return false;
                    ArrayList<T> path = Lists.newArrayList();
                    path.add(from);
                    path.addAll(stack);
                    path.add(to);
                    paths.add(path);
                    continue;
                }
                for (T t : graph.successors(current)) {
                    if (!stack.contains(t)) {
                        stack.push(t);
                    }
                }
            }
        }
        return paths;
    }

    private Order computePredicate() {

        // Same as the groupOrder, but we exclude the hierarchy component.

        DataStructure structure = getDataStructure();
        Order.Builder builder = Order.create(structure);
        for (Component component : structure.values()) {
            if (component.isIdentifier() && !component.equals(this.component)) {
                builder.put(component, Order.Direction.ASC); // TODO: Could be ASC or DESC
            }
        }
        return builder.build();
    }

    private Order computeOrder() {

        // Sort by all the identifiers we are grouping on but the hierarchy element.
        // The hierarchy element has to be the last one.

        DataStructure structure = getDataStructure();
        Order.Builder builder = Order.create(structure);
        for (Component component : structure.values()) {
            if (component.isIdentifier() && !component.equals(this.component)) {
                builder.put(component, Order.Direction.ASC); // TODO: Could be ASC or DESC
            }
        }
        builder.put(component, Order.Direction.ASC); // TODO: Could be ASC or DESC
        return builder.build();
    }

    @Override
    protected DataStructure computeDataStructure() {
        return getChild().getDataStructure();
    }

    @Override
    public Stream<DataPoint> getData() {

        final DataStructure structure = getDataStructure();
        final Order groupOrder = computeOrder();
        final Order groupPredicate = computePredicate();

        // TODO: Save the graph in the correct order.
        final LinkedList<VTLObject> sorted = sortTopologically(this.graph);

        final Map<Component, HierarchyAccumulator> accumulators = createAccumulatorMap();

        // Get the data sorted.
        Stream<DataPoint> sortedData = getChild().getData(groupOrder)
                .orElse(getChild().getData().sorted(groupOrder));

        Stream<ComposedDataPoint> streamToAggregate = StreamUtils.aggregate(
                sortedData,
                (prev, current) -> groupPredicate.compare(prev, current) == 0
        ).map(dataPoints -> {

            // Organize the data points in "buckets" for each component. Here we add "sign" information
            // to the data points so that we can use it later when we aggregate.
            Multimap<VTLObject, ComposedDataPoint> buckets = ArrayListMultimap.create();
            for (DataPoint dataPoint : dataPoints) {
                Map<Component, VTLObject> map = structure.asMap(dataPoint);
                VTLObject group = map.get(this.component);
                buckets.put(group, new ComposedDataPoint(dataPoint, Composition.UNION));
            }

            // TODO: Filter the nodes by the keys of the bucket (and check that it is faster)
            // For each component put the content in every successors. If the edge was a complement (-) then we invert
            // the sign of each datapoint (ie. a - (b - c + d) = a - b + c - d)
            for (VTLObject node : sorted) {
                for (VTLObject successor : graph.successors(node)) {
                    Composition sign = graph.edgeValue(node, successor);
                    for (ComposedDataPoint point : buckets.get(node)) {
                        if (Composition.COMPLEMENT.equals(sign)) {
                            // Invert if complement.
                            buckets.put(successor, ComposedDataPoint.invert(point));
                        } else {
                            buckets.put(successor, new ComposedDataPoint(point, point.sign));
                        }
                    }
                }
            }

            // Put the new "mapped" component
            List<ComposedDataPoint> result = Lists.newArrayList();
            for (Map.Entry<VTLObject, ComposedDataPoint> entry : buckets.entries()) {
                VTLObject group = entry.getKey();
                ComposedDataPoint point = entry.getValue();
                result.add(point);
                structure.asMap(point).put(this.component, group);
            }

            // Not needed since we are constructing the result by component.
            // Collections.sort(result, groupOrder);

            return result;

        }).flatMap(Collection::stream);


        return StreamUtils.aggregate(
                streamToAggregate,
                (dataPoint, dataPoint2) -> groupOrder.compare(dataPoint, dataPoint2) == 0
        ).map(dataPoints -> {

            DataPoint aggregate;
            // Optimization.
            if (dataPoints.size() > 1) {



                // Won't fail since we check size.
                aggregate = DataPoint.create(dataPoints.get(0));
                Map<Component, VTLObject> result = structure.asMap(aggregate);

                for (Map.Entry<Component, HierarchyAccumulator> entry : accumulators.entrySet()) {
                    result.put(entry.getKey(), entry.getValue().identity());
                }

                Iterator<ComposedDataPoint> iterator = dataPoints.iterator();
                while (iterator.hasNext()) {
                    ComposedDataPoint composedDataPoint = iterator.next();
                    Map<Component, VTLObject> next = structure.asMap(composedDataPoint);

                    for (Map.Entry<Component, HierarchyAccumulator> accumulator : accumulators.entrySet()) {
                        Component component = accumulator.getKey();
                        VTLObject objectValue = next.get(component);
                        HierarchyAccumulator value = accumulator.getValue();
                        result.merge(component, objectValue, value.accumulator(composedDataPoint.getSign()));
                    }

                }
            } else {
                aggregate = dataPoints.get(0);
            }

            return aggregate;
        });
    }
    
    private Map<Component, HierarchyAccumulator> createAccumulatorMap() {
        DataStructure structure = getDataStructure();
        ImmutableMap.Builder<Component, HierarchyAccumulator> builder = ImmutableMap.builder();
        for (Component component : structure.values()) {
            if (component.isMeasure()) {
                builder.put(component, sumAccumulatorFor(component.getType()));
            }
        }
        return builder.build();
    }


    @Override
    public Optional<Map<String, Integer>> getDistinctValuesCount() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getSize() {
        return Optional.empty();
    }

    static private class ComposedDataPoint extends DataPoint {
        private final Composition sign;

        private ComposedDataPoint(Collection<? extends VTLObject> c, Composition sign) {
            super(c);
            this.sign = checkNotNull(sign);
        }

        private static ComposedDataPoint invert(ComposedDataPoint original) {
            switch (original.getSign()) {
                case COMPLEMENT:
                    return new ComposedDataPoint(original, Composition.UNION);
                case UNION:
                    return new ComposedDataPoint(original, Composition.COMPLEMENT);
                default:
                    throw new IllegalArgumentException("unknown sign");
            }
        }

        private Composition getSign() {
            return sign;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(super.toString())
                    .addValue(sign)
                    .toString();
        }
    }
}
