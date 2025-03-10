package com.github.sepgh.testudo.operation.query;

import com.github.sepgh.testudo.operation.CollectionIndexProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeCondition implements Condition {
    private final CompositeOperator operator;
    private final List<Condition> conditions = new ArrayList<>();
    private final IterationCacheFactory iterationCacheFactory;

    public CompositeCondition(CompositeOperator operator, IterationCacheFactory iterationCacheFactory, Condition... initialConditions) {
        this.operator = operator;
        this.iterationCacheFactory = iterationCacheFactory;
        for (Condition condition : initialConditions) {
            addCondition(condition);
        }
    }

    public CompositeCondition(CompositeOperator operator, Condition... initialConditions) {
        this(operator, new HashsetIterationCacheFactory(), initialConditions);
    }


    public void addCondition(Condition condition) {
        conditions.add(condition);
    }

    @Override
    public <V extends Number & Comparable<V>> Iterator<V> evaluate(CollectionIndexProvider collectionIndexProvider, Order order) {
        List<Iterator<V>> iterators = conditions.stream()
                .map(cond -> (Iterator<V>) cond.evaluate(collectionIndexProvider, order))
                .collect(Collectors.toList());
        return operator.equals(CompositeOperator.OR) ? new OrIterator<>(iterators, order) : new AndIterator<>(iterators, iterationCacheFactory);
    }

    @Override
    public String getField() {
        throw new UnsupportedOperationException("Composite conditions do not have a single field");    
    }

    public enum CompositeOperator {
        AND, OR
    }
}
