package com.github.sepgh.testudo.index.tree.node;

import com.github.sepgh.testudo.ds.KVSize;
import com.github.sepgh.testudo.ds.KeyValue;
import com.github.sepgh.testudo.ds.Pointer;
import com.github.sepgh.testudo.exception.IndexBinaryObjectCreationException;
import com.github.sepgh.testudo.index.data.IndexBinaryObjectFactory;
import com.github.sepgh.testudo.index.tree.TreeNodeUtils;
import com.github.sepgh.testudo.utils.CollectionUtils;
import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;

import java.util.*;


// Todo: some methods like getKeyList or getKeys can support a caching mechanism that invalidates only on change. We can use decorators for this behavior. This also applies to InternalTreeNode
public class AbstractLeafTreeNode<K extends Comparable<K>, V> extends AbstractTreeNode<K> {
    protected final IndexBinaryObjectFactory<V> valueIndexBinaryObjectFactory;

    public AbstractLeafTreeNode(byte[] data, IndexBinaryObjectFactory<K> keyIndexBinaryObjectFactory, IndexBinaryObjectFactory<V> valueIndexBinaryObjectFactory) {
        super(data, keyIndexBinaryObjectFactory);
        this.valueIndexBinaryObjectFactory = valueIndexBinaryObjectFactory;
        setType(Type.LEAF);
    }

    public Iterator<K> getKeys(int degree) {
        return super.getKeys(degree, valueIndexBinaryObjectFactory.size());
    }

    public List<K> getKeyList(int degree) {
        return super.getKeyList(degree, valueIndexBinaryObjectFactory.size());
    }

    @SneakyThrows
    public void setKey(int index, K key) {
        super.setKey(index, key, valueIndexBinaryObjectFactory.size());
    }

    public void removeKey(int idx, int degree) {
        super.removeKey(idx, degree, valueIndexBinaryObjectFactory.size());
    }

    public void setNextSiblingPointer(Pointer pointer, int degree){
        modified();
        TreeNodeUtils.setNextPointer(this, degree, pointer, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
    }

    public void setPreviousSiblingPointer(Pointer pointer, int degree){
        modified();
        TreeNodeUtils.setPreviousPointer(this, degree, pointer, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
    }

    public Optional<Pointer> getPreviousSiblingPointer(int degree){
        return TreeNodeUtils.getPreviousPointer(this, degree, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
    }

    public Optional<Pointer> getNextSiblingPointer(int degree){
        return TreeNodeUtils.getNextPointer(this, degree, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
    }

    public Iterator<KeyValue<K, V>> getKeyValues(int degree){
        return new KeyValueIterator(this, degree);
    }

    public List<KeyValue<K, V>> getKeyValueList(int degree) {
        return ImmutableList.copyOf(getKeyValues(degree));
    }

    public void setKeyValues(List<KeyValue<K, V>> keyValueList, int degree) throws IndexBinaryObjectCreationException {
        modified();
        for (int i = 0; i < keyValueList.size(); i++){
            KeyValue<K, V> keyValue = keyValueList.get(i);
            this.setKeyValue(i, keyValue);
        }
        for (int i = keyValueList.size(); i < (degree - 1); i++){
            TreeNodeUtils.removeKeyValueAtIndex(this, i, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
        }
    }

    public void setKeyValue(int index, KeyValue<K, V> keyValue) throws IndexBinaryObjectCreationException {
        TreeNodeUtils.setKeyValueAtIndex(this, index, kIndexBinaryObjectFactory.create(keyValue.key()), valueIndexBinaryObjectFactory.create(keyValue.value()));
    }

    public List<KeyValue<K, V>> addAndSplit(K identifier, V v, int degree) throws IndexBinaryObjectCreationException {
        int mid = (degree - 1) / 2;

        List<KeyValue<K, V>> allKeyValues = new ArrayList<>(getKeyValueList(degree));
        KeyValue<K, V> keyValue = new KeyValue<>(identifier, v);
        int i = CollectionUtils.indexToInsert(allKeyValues, keyValue);
        allKeyValues.add(i, keyValue);

        List<KeyValue<K, V>> toKeep = allKeyValues.subList(0, mid + 1);
        this.setKeyValues(toKeep, degree);
        return allKeyValues.subList(mid + 1, allKeyValues.size());
    }

    public int addKeyValue(K identifier, V v, int degree) throws IndexBinaryObjectCreationException {
        int i = CollectionUtils.indexToInsert(getKeyList(degree), identifier);
        TreeNodeUtils.addKeyValue(this, degree, kIndexBinaryObjectFactory, identifier, valueIndexBinaryObjectFactory, v, i);
        return i;
    }

    public int addKeyValue(KeyValue<K, V> keyValue, int degree) throws IndexBinaryObjectCreationException {
        return this.addKeyValue(keyValue.key(), keyValue.value(), degree);
    }

    public void removeKeyValue(int index) {
        TreeNodeUtils.removeKeyValueAtIndex(this, index, kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
    }

    public boolean removeKeyValue(K key, int degree) throws IndexBinaryObjectCreationException {
        List<KeyValue<K, V>> keyValueList = new ArrayList<>(this.getKeyValueList(degree));
        int i = Collections.binarySearch(getKeyList(degree), key);
        if (i >= 0){
            keyValueList.remove(i);
            setKeyValues(keyValueList, degree);
            return true;
        }
        return false;
    }

    public boolean removeKeyValue(K key, V value, int degree) throws IndexBinaryObjectCreationException {
        List<KeyValue<K, V>> keyValueList = new ArrayList<>(this.getKeyValueList(degree));
        int i = Collections.binarySearch(getKeyList(degree), key);
        if (i >= 0 && keyValueList.get(i).key().equals(key)){
            keyValueList.remove(i);
            setKeyValues(keyValueList, degree);
            return true;
        }
        return false;
    }

    @Override
    public KVSize getKVSize() {
        return new KVSize(kIndexBinaryObjectFactory.size(), valueIndexBinaryObjectFactory.size());
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
            return TreeNodeUtils.hasKeyAtIndex(node, cursor, degree, kIndexBinaryObjectFactory, valueIndexBinaryObjectFactory.size());
        }

        @SneakyThrows
        @Override
        public KeyValue<K, V> next() {
            Map.Entry<K, V> kvAtIndex = TreeNodeUtils.getKeyValueAtIndex(
                    node,
                    cursor,
                    kIndexBinaryObjectFactory,
                    valueIndexBinaryObjectFactory
            );
            cursor++;
            return new KeyValue<>(kvAtIndex.getKey(), kvAtIndex.getValue());
        }
    }


    @Override
    public String toString() {
        return "AbstractLeafTreeNode{" +
                "Root: " + isRoot() +
                ", Pointer: " + getPointer() +
                "}";
    }
}
