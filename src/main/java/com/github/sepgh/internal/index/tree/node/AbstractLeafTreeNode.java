package com.github.sepgh.internal.index.tree.node;

import com.github.sepgh.internal.index.Pointer;
import com.github.sepgh.internal.index.tree.TreeNodeUtils;
import com.github.sepgh.internal.index.tree.node.data.NodeInnerObj;
import com.github.sepgh.internal.utils.CollectionUtils;
import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class AbstractLeafTreeNode<K extends Comparable<K>, V extends Comparable<V>> extends AbstractTreeNode<K> {
    protected final NodeInnerObj.Strategy<V> valueStrategy;

    public AbstractLeafTreeNode(byte[] data, NodeInnerObj.Strategy<K> keyStrategy, NodeInnerObj.Strategy<V> valueStrategy) {
        super(data, keyStrategy);
        this.valueStrategy = valueStrategy;
        setType(Type.LEAF);
    }

    public Iterator<K> getKeys(int degree) {
        return super.getKeys(degree, valueStrategy.size());
    }

    public List<K> getKeyList(int degree) {
        return super.getKeyList(degree, valueStrategy.size());
    }

    public void setKey(int index, K key) {
        super.setKey(index, key, valueStrategy.size());
    }

    public void removeKey(int idx, int degree) {
        super.removeKey(idx, degree, valueStrategy.size());
    }

    public void setNextSiblingPointer(Pointer pointer, int degree){
        modified();
        TreeNodeUtils.setNextPointer(this, degree, pointer, keyStrategy.size(), valueStrategy.size());
    }

    public void setPreviousSiblingPointer(Pointer pointer, int degree){
        modified();
        TreeNodeUtils.setPreviousPointer(this, degree, pointer, keyStrategy.size(), valueStrategy.size());
    }

    public Optional<Pointer> getPreviousSiblingPointer(int degree){
        return TreeNodeUtils.getPreviousPointer(this, degree, keyStrategy.size(), valueStrategy.size());
    }

    public Optional<Pointer> getNextSiblingPointer(int degree){
        return TreeNodeUtils.getNextPointer(this, degree, keyStrategy.size(), valueStrategy.size());
    }

    public Iterator<KeyValue<K, V>> getKeyValues(int degree){
        return new KeyValueIterator(this, degree);
    }

    public List<KeyValue<K, V>> getKeyValueList(int degree) {
        return ImmutableList.copyOf(getKeyValues(degree));
    }

    public void setKeyValues(List<KeyValue<K, V>> keyValueList, int degree) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        modified();
        for (int i = 0; i < keyValueList.size(); i++){
            KeyValue<K, V> keyValue = keyValueList.get(i);
            this.setKeyValue(i, keyValue);
        }
        for (int i = keyValueList.size(); i < (degree - 1); i++){
            TreeNodeUtils.removeKeyValueAtIndex(this, i, keyStrategy.size(), valueStrategy.size());
        }
    }

    public void setKeyValue(int index, KeyValue<K, V> keyValue) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        if (!keyStrategy.isValid(keyValue.key)) {
            throw new NodeInnerObj.InvalidValueForNodeInnerObj(keyValue.key, keyStrategy.getNodeInnerObjClass());
        }
        TreeNodeUtils.setKeyValueAtIndex(this, index, keyStrategy.fromObject(keyValue.key()), valueStrategy.fromObject(keyValue.value()));
    }

    public List<KeyValue<K, V>> split(K identifier, V v, int degree) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        int mid = (degree - 1) / 2;

        List<KeyValue<K, V>> allKeyValues = new ArrayList<>(getKeyValueList(degree));
        KeyValue<K, V> keyValue = new KeyValue<>(identifier, v);
        int i = CollectionUtils.indexToInsert(allKeyValues, keyValue);
        allKeyValues.add(i, keyValue);

        List<KeyValue<K, V>> toKeep = allKeyValues.subList(0, mid + 1);
        this.setKeyValues(toKeep, degree);
        return allKeyValues.subList(mid + 1, allKeyValues.size());
    }

    public int addKeyValue(K identifier, V v, int degree) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        if (!keyStrategy.isValid(identifier)) {
            throw new NodeInnerObj.InvalidValueForNodeInnerObj(identifier, keyStrategy.getNodeInnerObjClass());
        }
        try {
            return TreeNodeUtils.addKeyValueAndGetIndex(this, degree, keyStrategy, identifier, keyStrategy.size(), valueStrategy, v, valueStrategy.size());
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public int addKeyValue(KeyValue<K, V> keyValue, int degree) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        return this.addKeyValue(keyValue.key, keyValue.value, degree);
    }

    public void removeKeyValue(int index) {
        TreeNodeUtils.removeKeyValueAtIndex(this, index, keyStrategy.size(), valueStrategy.size());
    }

    public boolean removeKeyValue(K key, int degree) throws NodeInnerObj.InvalidValueForNodeInnerObj {
        List<KeyValue<K, V>> keyValueList = new ArrayList<>(this.getKeyValueList(degree));
        boolean removed = keyValueList.removeIf(keyValue -> keyValue.key.compareTo(key) == 0);
        setKeyValues(keyValueList, degree);
        return removed;
    }


    public record KeyValue<K extends Comparable<K>, V extends Comparable<V>>(K key,
                                                                             V value) implements Comparable<KeyValue<K, V>> {

        @Override
            public int compareTo(KeyValue<K, V> o) {
                return this.key.compareTo(o.key);
            }
        }

    private class KeyValueIterator implements Iterator<KeyValue<K, V>>{
        private int cursor = 0;

        private final AbstractLeafTreeNode<K, V> node;
        private final int degree;

        public KeyValueIterator(AbstractLeafTreeNode<K, V> node, int degree) {
            this.node = node;
            this.degree = degree;
        }

        @SneakyThrows
        @Override
        public boolean hasNext() {
            return TreeNodeUtils.hasKeyAtIndex(node, cursor, degree, keyStrategy.getNodeInnerObjClass(), keyStrategy.size(), valueStrategy.size());
        }

        @SneakyThrows
        @Override
        public KeyValue<K, V> next() {
            Map.Entry<K, V> kvAtIndex = TreeNodeUtils.getKeyValueAtIndex(
                    node,
                    cursor,
                    keyStrategy.getNodeInnerObjClass(),
                    keyStrategy.size(),
                    valueStrategy.getNodeInnerObjClass(),
                    valueStrategy.size()
            );
            cursor++;
            return new KeyValue<>(kvAtIndex.getKey(), kvAtIndex.getValue());
        }
    }

}
