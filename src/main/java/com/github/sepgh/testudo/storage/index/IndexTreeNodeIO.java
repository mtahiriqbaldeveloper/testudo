package com.github.sepgh.testudo.storage.index;

import com.github.sepgh.testudo.ds.KVSize;
import com.github.sepgh.testudo.ds.Pointer;
import com.github.sepgh.testudo.exception.InternalOperationException;
import com.github.sepgh.testudo.index.tree.node.AbstractTreeNode;
import com.github.sepgh.testudo.index.tree.node.NodeFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class IndexTreeNodeIO {
    public static CompletableFuture<IndexStorageManager.NodeData> write(IndexStorageManager indexStorageManager, int indexId, AbstractTreeNode<?> node) throws InternalOperationException {
        IndexStorageManager.NodeData nodeData = new IndexStorageManager.NodeData(node.getPointer(), node.getData());
        /*if (!node.isModified() && node.getPointer() != null){
            return CompletableFuture.completedFuture(nodeData);
        }*/
        CompletableFuture<IndexStorageManager.NodeData> output = new CompletableFuture<>();

        if (node.getPointer() == null){
            indexStorageManager.writeNewNode(indexId, node.getData(), node.isRoot(), node.getKVSize()).whenComplete((nodeData1, throwable) -> {
                if (throwable != null){
                    output.completeExceptionally(throwable);
                    return;
                }
                node.setPointer(nodeData1.pointer());
                output.complete(nodeData1);
            });
        } else {
            indexStorageManager.updateNode(indexId, node.getData(), node.getPointer(), node.isRoot()).whenComplete((integer, throwable) -> {
                if (throwable != null){
                    output.completeExceptionally(throwable);
                    return;
                }
                output.complete(nodeData);
            });
        }
        return output;
    }

    // Todo: apparently `indexStorageManager.readNode(indexId, pointer).get()` can return empty byte[] in case file doesnt exist,
    //       in that case fromBytes() method of the node factory throw "ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0" during construction
    public static <K extends Comparable<K>> AbstractTreeNode<K> read(IndexStorageManager indexStorageManager, int indexId, Pointer pointer, NodeFactory<K> nodeFactory, KVSize kvSize) throws InternalOperationException {
        try {
            return nodeFactory.fromNodeData(indexStorageManager.readNode(indexId, pointer, kvSize).get());
        } catch (InterruptedException | ExecutionException e) {
            throw new InternalOperationException(e);
        }
    }

    public static <K extends Comparable<K>> void update(IndexStorageManager indexStorageManager, int indexId, AbstractTreeNode<K> node) throws InternalOperationException {
        try {
            indexStorageManager.updateNode(indexId, node.getData(), node.getPointer(), node.isRoot()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new InternalOperationException(e);
        }
    }

    public static <E extends Comparable<E>> void remove(IndexStorageManager indexStorageManager, int indexId, AbstractTreeNode<E> node, KVSize kvSize) throws ExecutionException, InterruptedException, InternalOperationException {
        remove(indexStorageManager, indexId, node.getPointer(), kvSize);
    }

    public static void remove(IndexStorageManager indexStorageManager, int indexId, Pointer pointer, KVSize kvSize) throws InternalOperationException {
        try {
            indexStorageManager.removeNode(indexId, pointer, kvSize).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new InternalOperationException(e);
        }
    }
}
