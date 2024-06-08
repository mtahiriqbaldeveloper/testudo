package com.github.sepgh.internal.index.tree.node.cluster;

import com.github.sepgh.internal.index.Pointer;
import com.github.sepgh.internal.index.tree.TreeNodeUtils;
import com.github.sepgh.internal.index.tree.node.data.NodeInnerObj;
import com.github.sepgh.internal.index.tree.node.data.PointerInnerObject;
import com.github.sepgh.internal.utils.CollectionUtils;
import com.google.common.collect.ImmutableList;
import lombok.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InternalClusterTreeNode<K extends Comparable<K>> extends BaseClusterTreeNode<K> {
    public InternalClusterTreeNode(byte[] data, ClusterIdentifier.Strategy<K> strategy) {
        super(data, strategy);
        setType(Type.INTERNAL);
    }

    public Iterator<ChildPointers<K>> getChildPointers(int degree){
        return new ChildPointersIterator(this, degree);
    }

    public List<ChildPointers<K>> getChildPointersList(int degree){
        return ImmutableList.copyOf(getChildPointers(degree));
    }

    public void setChildPointers(List<ChildPointers<K>> childPointers, int degree, boolean cleanRest){
        modified();
        if (cleanRest)
            TreeNodeUtils.cleanChildrenPointers(this, degree, clusterIdentifierStrategy.size(), PointerInnerObject.BYTES);
        int i = 0;
        for (ChildPointers<K> keyPointer : childPointers) {
            keyPointer.setIndex(i);
            TreeNodeUtils.setKeyAtIndex(this, keyPointer.index, clusterIdentifierStrategy.fromObject(keyPointer.key), PointerInnerObject.BYTES);
            if (i == 0){
                TreeNodeUtils.setPointerToChild(this, 0, keyPointer.left, clusterIdentifierStrategy.size());
                TreeNodeUtils.setPointerToChild(this, 1, keyPointer.right, clusterIdentifierStrategy.size());
            } else {
                TreeNodeUtils.setPointerToChild(this, keyPointer.index + 1, keyPointer.right, clusterIdentifierStrategy.size());
            }
            i++;
        }

    }

    public int addKey(K identifier, int degree) {
        List<K> keyList = new ArrayList<>(this.getKeyList(degree));
        int idx = CollectionUtils.indexToInsert(keyList, identifier);
        keyList.add(idx, identifier);

        for (int j = idx; j < keyList.size() && j < degree - 1; j++){
            TreeNodeUtils.setKeyAtIndex(this, j, clusterIdentifierStrategy.fromObject(keyList.get(j)), Pointer.BYTES);
        }

        return idx;
    }

    public void addChildPointers(K identifier, @Nullable Pointer left, @Nullable Pointer right, int degree, boolean clearForNull){
        modified();
        int i = this.addKey(identifier, degree);
        if (left != null){
            TreeNodeUtils.setPointerToChild(this, i, left, clusterIdentifierStrategy.size());
        }
        else if (clearForNull)
            TreeNodeUtils.removeChildAtIndex(this, i, clusterIdentifierStrategy.size());
        if (right != null)
            TreeNodeUtils.setPointerToChild(this, i+1, right, clusterIdentifierStrategy.size());
        else if (clearForNull)
            TreeNodeUtils.removeChildAtIndex(this, i + 1, clusterIdentifierStrategy.size());
    }

    public void addChildPointers(ChildPointers<K> childPointers, int degree) {
        modified();
        this.addChildPointers(childPointers.key, childPointers.left, childPointers.right, degree, false);
    }

    public void addChildPointers(ChildPointers<K> childPointers, int degree, boolean clearForNull){
        modified();
        this.addChildPointers(childPointers.key, childPointers.left, childPointers.right, degree, clearForNull);
    }

    public Iterator<Pointer> getChildren() {
        return new ChildrenIterator(this);
    }

    public List<Pointer> getChildrenList(){
        return ImmutableList.copyOf(getChildren());
    }

    public void addChildAtIndex(int index, Pointer pointer){
        List<Pointer> childrenList = new ArrayList<>(this.getChildrenList());
        childrenList.add(index, pointer);

        for (int i = index; i < childrenList.size(); i++){
            this.setChildAtIndex(i, childrenList.get(i));
        }

    }

    public void setChildAtIndex(int index, Pointer pointer){
        TreeNodeUtils.setPointerToChild(this, index, pointer, clusterIdentifierStrategy.size());
    }

    public Pointer getChildAtIndex(int index) {
        return TreeNodeUtils.getChildPointerAtIndex(this, index, clusterIdentifierStrategy.size());
    }

    public int getIndexOfChild(Pointer pointer){
        return this.getChildrenList().indexOf(pointer);
    }


    /*
     *   When is this called? when an internal node wanted to add a new child pointer but there is no space left
     *   Wherever the new identifier should be added, we add a new ChildPointers,
     *            where the left would point to current left of the existing node at that index
     *            and right would be the new pointer
     *            and if newly added ChildPointers was not last item, we change the next item left to the pointer (new one's right)
     *
     *   The returned list first node should be passed to parent and the remaining should be stored in a new node
     */
    public List<ChildPointers<K>> addAndSplit(K identifier, Pointer pointer, int degree){
        modified();
        int mid = (degree - 1) / 2;

        List<K> keyList = new ArrayList<>(getKeyList(degree));
        int i = CollectionUtils.indexToInsert(keyList, identifier);
        keyList.add(i, identifier);

        List<ChildPointers<K>> childPointersList = new ArrayList<>(getChildPointersList(degree));

        childPointersList.add(i, new ChildPointers<>(
                        0,
                        identifier,
                        childPointersList.get(i-1).getRight(),
                        pointer  // Setting right pointer at index
                )
        );
        if (i + 1 < childPointersList.size()){
            childPointersList.get(i+1).setLeft(pointer);  // Setting left pointer of next key if not last key
        }

        List<ChildPointers<K>> toKeep = childPointersList.subList(0, mid + 1);
        this.setChildPointers(toKeep, degree, true);

        List<ChildPointers<K>> toPass = childPointersList.subList(mid + 1, keyList.size());
        return toPass;
    }

    public void setKeys(List<K> childKeyList) {
        for (int i = 0; i < childKeyList.size(); i++){
            this.setKey(i, childKeyList.get(i));
        }
    }

    public void setChildren(ArrayList<Pointer> childPointers) {
        for (int i = 0; i < childPointers.size(); i++){
            this.setChildAtIndex(i, childPointers.get(i));
        }
    }

    public void removeChild(int idx, int degree) {
        List<Pointer> pointerList = this.getChildrenList();
        TreeNodeUtils.removeChildAtIndex(this, idx, clusterIdentifierStrategy.size());
        List<Pointer> subList = pointerList.subList(idx + 1, pointerList.size());
        int lastIndex = -1;
        for (int i = 0; i < subList.size(); i++) {
            lastIndex = idx + i;
            TreeNodeUtils.setPointerToChild(this, lastIndex, subList.get(i), clusterIdentifierStrategy.size());
        }
        if (lastIndex != -1){
            for (int i = lastIndex + 1; i < degree; i++){
                TreeNodeUtils.removeChildAtIndex(this, i, clusterIdentifierStrategy.size());
            }
        }
    }

    private static class ChildrenIterator implements Iterator<Pointer> {

        private final BaseClusterTreeNode<?> node;
        private int cursor = 0;

        private ChildrenIterator(BaseClusterTreeNode<?> node) {
            this.node = node;
        }

        @Override
        public boolean hasNext() {
            return TreeNodeUtils.hasChildPointerAtIndex(this.node, cursor, node.clusterIdentifierStrategy.size());
        }

        @Override
        public Pointer next() {
            Pointer pointer = TreeNodeUtils.getChildPointerAtIndex(this.node, cursor, node.clusterIdentifierStrategy.size());
            cursor++;
            return pointer;
        }
    }

    private class ChildPointersIterator implements Iterator<ChildPointers<K>> {

        private int cursor = 0;
        private Pointer lastRightPointer;

        private final BaseClusterTreeNode<K> node;
        private final int degree;

        private ChildPointersIterator(BaseClusterTreeNode<K> node, int degree) {
            this.node = node;
            this.degree = degree;
        }


        @SneakyThrows
        @Override
        public boolean hasNext() {
            return TreeNodeUtils.hasKeyAtIndex(node, cursor, degree, clusterIdentifierStrategy.getNodeInnerObjClass(), clusterIdentifierStrategy.size(), Pointer.BYTES);
        }

        @SneakyThrows
        @Override
        public ChildPointers<K> next() {
            NodeInnerObj<K> nodeInnerObj = (NodeInnerObj<K>) TreeNodeUtils.getKeyAtIndex(node, cursor, clusterIdentifierStrategy.getNodeInnerObjClass(), clusterIdentifierStrategy.size(), PointerInnerObject.BYTES);
            ChildPointers childPointers = null;
            if (cursor == 0){
                childPointers = new ChildPointers<K>(
                        cursor,
                        nodeInnerObj.data(),
                        TreeNodeUtils.getChildPointerAtIndex(node, 0, clusterIdentifierStrategy.size()),
                        TreeNodeUtils.getChildPointerAtIndex(node, 1, clusterIdentifierStrategy.size())
                );
            } else {
                childPointers = new ChildPointers(
                        cursor,
                        nodeInnerObj.data(),
                        lastRightPointer,
                        TreeNodeUtils.getChildPointerAtIndex(node, cursor + 1, clusterIdentifierStrategy.size())
                );
            }
            lastRightPointer = childPointers.getRight();

            cursor++;
            return childPointers;
        }
    }


    @AllArgsConstructor
    @Getter
    @Setter
    @ToString
    public static class ChildPointers<E extends Comparable<E>> implements Comparable<ChildPointers<E>> {

        private int index;
        private E key;
        private Pointer left;
        private Pointer right;

        @Override
        public int compareTo(ChildPointers<E> o) {
            return this.key.compareTo(o.getKey());
        }
    }
}
