package com.github.sepgh.internal.index;

import com.github.sepgh.internal.index.tree.node.cluster.BaseClusterTreeNode;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface IndexManager<K extends Comparable<K>> {
    BaseClusterTreeNode<K> addIndex(int table, K identifier, Pointer pointer) throws ExecutionException, InterruptedException, IOException;
    Optional<Pointer> getIndex(int table, K identifier) throws ExecutionException, InterruptedException, IOException;
    boolean removeIndex(int table, K identifier) throws ExecutionException, InterruptedException, IOException;
    int size(int table) throws InterruptedException, ExecutionException, IOException;
}
