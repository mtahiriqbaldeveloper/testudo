package com.github.sepgh.internal.index.tree;

import com.github.sepgh.internal.index.IndexManager;
import com.github.sepgh.internal.index.Pointer;
import com.github.sepgh.internal.index.tree.node.AbstractLeafTreeNode;
import com.github.sepgh.internal.index.tree.node.AbstractTreeNode;
import com.github.sepgh.internal.index.tree.node.InternalTreeNode;
import com.github.sepgh.internal.index.tree.node.NodeFactory;
import com.github.sepgh.internal.index.tree.node.cluster.LeafClusterTreeNode;
import com.github.sepgh.internal.index.tree.node.data.BinaryObjectWrapper;
import com.github.sepgh.internal.storage.IndexStorageManager;
import com.github.sepgh.internal.storage.IndexTreeNodeIO;
import com.github.sepgh.internal.storage.session.ImmediateCommitIndexIOSession;
import com.github.sepgh.internal.storage.session.IndexIOSession;
import com.github.sepgh.internal.storage.session.IndexIOSessionFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.github.sepgh.internal.index.tree.node.AbstractTreeNode.TYPE_LEAF_NODE_BIT;

public class BPlusTreeIndexManager<K extends Comparable<K>, V extends Comparable<V>> implements IndexManager<K, V> {
    private final IndexStorageManager indexStorageManager;
    private final IndexIOSessionFactory indexIOSessionFactory;
    private final int degree;
    private final BinaryObjectWrapper<K> keyBinaryObjectWrapper;
    private final BinaryObjectWrapper<V> valueBinaryObjectWrapper;
    private final NodeFactory<K> nodeFactory;

    public BPlusTreeIndexManager(int degree, IndexStorageManager indexStorageManager, IndexIOSessionFactory indexIOSessionFactory, BinaryObjectWrapper<K> keyBinaryObjectWrapper, BinaryObjectWrapper<V> valueBinaryObjectWrapper, NodeFactory<K> nodeFactory){
        this.degree = degree;
        this.indexStorageManager = indexStorageManager;
        this.indexIOSessionFactory = indexIOSessionFactory;
        this.keyBinaryObjectWrapper = keyBinaryObjectWrapper;
        this.valueBinaryObjectWrapper = valueBinaryObjectWrapper;
        this.nodeFactory = nodeFactory;
    }

    public BPlusTreeIndexManager(int degree, IndexStorageManager indexStorageManager, BinaryObjectWrapper<K> keyBinaryObjectWrapper, BinaryObjectWrapper<V> valueBinaryObjectWrapper, NodeFactory<K> nodeFactory){
        this(degree, indexStorageManager, ImmediateCommitIndexIOSession.Factory.getInstance(), keyBinaryObjectWrapper, valueBinaryObjectWrapper, nodeFactory);
    }

    public BPlusTreeIndexManager(int degree, IndexStorageManager indexStorageManager, IndexIOSessionFactory indexIOSessionFactory, BinaryObjectWrapper<K> keyBinaryObjectWrapper, BinaryObjectWrapper<V> valueBinaryObjectWrapper){
        this(degree, indexStorageManager, indexIOSessionFactory, keyBinaryObjectWrapper, valueBinaryObjectWrapper, new NodeFactory<K>() {
            @Override
            public AbstractTreeNode<K> fromBytes(byte[] bytes) {
                if ((bytes[0] & TYPE_LEAF_NODE_BIT) == TYPE_LEAF_NODE_BIT)
                    return new AbstractLeafTreeNode<>(bytes, keyBinaryObjectWrapper, valueBinaryObjectWrapper);
                return new InternalTreeNode<>(bytes, keyBinaryObjectWrapper);
            }

            @Override
            public AbstractTreeNode<K> fromBytes(byte[] bytes, AbstractTreeNode.Type type) {
                if (type.equals(AbstractTreeNode.Type.LEAF))
                    return new AbstractLeafTreeNode<>(bytes, keyBinaryObjectWrapper, valueBinaryObjectWrapper);
                return new InternalTreeNode<>(bytes, keyBinaryObjectWrapper);
            }
        });

    }

    public BPlusTreeIndexManager(int degree, IndexStorageManager indexStorageManager, BinaryObjectWrapper<K> keyBinaryObjectWrapper, BinaryObjectWrapper<V> valueBinaryObjectWrapper){
        this(degree, indexStorageManager, ImmediateCommitIndexIOSession.Factory.getInstance(), keyBinaryObjectWrapper, valueBinaryObjectWrapper);
    }

    @Override
    public AbstractTreeNode<K> addIndex(int table, K identifier, V value) throws ExecutionException, InterruptedException, IOException, BinaryObjectWrapper.InvalidBinaryObjectWrapperValue {
        IndexIOSession<K> indexIOSession = this.indexIOSessionFactory.create(indexStorageManager, table, nodeFactory);
        AbstractTreeNode<K> root = getRoot(indexIOSession);
        return new BPlusTreeIndexCreateOperation<>(degree, indexIOSession, keyBinaryObjectWrapper, valueBinaryObjectWrapper).addIndex(root, identifier, value);
    }

    @Override
    public Optional<V> getIndex(int table, K identifier) throws ExecutionException, InterruptedException, IOException {
        IndexIOSession<K> indexIOSession = this.indexIOSessionFactory.create(indexStorageManager, table, nodeFactory);

        AbstractLeafTreeNode<K, V> baseTreeNode = BPlusTreeUtils.getResponsibleNode(indexStorageManager, getRoot(indexIOSession), identifier, table, degree, nodeFactory, valueBinaryObjectWrapper);
        for (AbstractLeafTreeNode.KeyValue<K, V> entry : baseTreeNode.getKeyValueList(degree)) {
            if (entry.key() == identifier)
                return Optional.of(entry.value());
        }

        return Optional.empty();
    }

    @Override
    public boolean removeIndex(int table, K identifier) throws ExecutionException, InterruptedException, IOException {
        IndexIOSession<K> indexIOSession = this.indexIOSessionFactory.create(indexStorageManager, table, nodeFactory);
        AbstractTreeNode<K> root = getRoot(indexIOSession);
        return new BPlusTreeIndexDeleteOperation<>(degree, table, indexIOSession, valueBinaryObjectWrapper, nodeFactory).removeIndex(root, identifier);
    }

    @Override
    public int size(int table) throws InterruptedException, ExecutionException, IOException {
        Optional<IndexStorageManager.NodeData> optionalNodeData = this.indexStorageManager.getRoot(table).get();
        if (optionalNodeData.isEmpty())
            return 0;

        AbstractTreeNode<K> root = nodeFactory.fromNodeData(optionalNodeData.get());
        if (root.isLeaf()){
            return root.getKeyList(degree, valueBinaryObjectWrapper.size()).size();
        }

        AbstractTreeNode<K> curr = root;
        while (!curr.isLeaf()) {
            curr = IndexTreeNodeIO.read(indexStorageManager, table, ((InternalTreeNode<K>) curr).getChildrenList().getFirst(), nodeFactory);
        }

        int size = curr.getKeyList(degree, valueBinaryObjectWrapper.size()).size();
        Optional<Pointer> optionalNext = ((LeafClusterTreeNode<K>) curr).getNextSiblingPointer(degree);
        while (optionalNext.isPresent()){
            AbstractTreeNode<K> nextNode = IndexTreeNodeIO.read(indexStorageManager, table, optionalNext.get(), nodeFactory);
            size += nextNode.getKeyList(degree, valueBinaryObjectWrapper.size()).size();
            optionalNext = ((LeafClusterTreeNode<K>) nextNode).getNextSiblingPointer(degree);
        }

        return size;
    }

    private AbstractTreeNode<K> getRoot(IndexIOSession<K> indexIOSession) throws ExecutionException, InterruptedException, IOException {
        Optional<AbstractTreeNode<K>> optionalRoot = indexIOSession.getRoot();
        if (optionalRoot.isPresent()){
            return optionalRoot.get();
        }

        byte[] emptyNode = indexStorageManager.getEmptyNode();
        AbstractLeafTreeNode<K, ?> leafTreeNode = (AbstractLeafTreeNode<K, ?>) nodeFactory.fromBytes(emptyNode, AbstractTreeNode.Type.LEAF);
        leafTreeNode.setAsRoot();

        IndexStorageManager.NodeData nodeData = indexIOSession.write(leafTreeNode);
        leafTreeNode.setPointer(nodeData.pointer());
        return leafTreeNode;
    }

}
