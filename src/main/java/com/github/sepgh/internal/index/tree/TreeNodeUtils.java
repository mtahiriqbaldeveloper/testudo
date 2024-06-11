package com.github.sepgh.internal.index.tree;

import com.github.sepgh.internal.index.Pointer;
import com.github.sepgh.internal.index.tree.node.AbstractTreeNode;
import com.github.sepgh.internal.index.tree.node.InternalTreeNode;
import com.github.sepgh.internal.index.tree.node.data.BinaryObjectWrapper;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

public class TreeNodeUtils {
    private static final int OFFSET_TREE_NODE_FLAGS_END = 1;
    private static final int OFFSET_INTERNAL_NODE_KEY_BEGIN = OFFSET_TREE_NODE_FLAGS_END + Pointer.BYTES;
    private static final int OFFSET_LEAF_NODE_KEY_BEGIN = OFFSET_TREE_NODE_FLAGS_END;
    private static final int SIZE_LEAF_NODE_SIBLING_POINTERS = 2 * Pointer.BYTES;

    /**
     *
     * @param treeNode node to read/write from/to
     * @param index to check child pointer
     * @return bool true, if the first byte of the current cursor position is not 0x00
     *         Note that index is the index of the pointer object we want to refer to, not the byte position in byte array
     */
    public static boolean hasChildPointerAtIndex(AbstractTreeNode<?> treeNode, int index, int keySize){
        if (OFFSET_TREE_NODE_FLAGS_END + (index * (Pointer.BYTES + keySize)) > treeNode.getData().length)
            return false;


        return treeNode.getData()[OFFSET_TREE_NODE_FLAGS_END + (index * (Pointer.BYTES + keySize))] == Pointer.TYPE_NODE;
    }


    /**
     * @param treeNode node to read/write from/to
     * @param index to check child pointer
     * @return Pointer to child node at index
     */public static Pointer getChildPointerAtIndex(AbstractTreeNode<?> treeNode, int index, int keySize){
        return Pointer.fromBytes(treeNode.getData(), OFFSET_TREE_NODE_FLAGS_END + (index * (Pointer.BYTES + keySize)));
    }

    // This will not pull remaining children (won't shift them one step behind)
    public static void removeChildAtIndex(AbstractTreeNode<?> treeNode, int index, int keySize) {
        System.arraycopy(
                new byte[Pointer.BYTES],
                0,
                treeNode.getData(),
                OFFSET_TREE_NODE_FLAGS_END + (index * (Pointer.BYTES + keySize)),
                Pointer.BYTES
        );
    }

    /**
     * @param treeNode node to read/write from/to
     * @param index index of the pointer to set
     * @param pointer object to set
     */
    public static void setPointerToChild(AbstractTreeNode<?> treeNode, int index, Pointer pointer, int keySize){
        if (index == 0){
            System.arraycopy(pointer.toBytes(), 0, treeNode.getData(), OFFSET_TREE_NODE_FLAGS_END, Pointer.BYTES);
        } else {
            System.arraycopy(
                    pointer.toBytes(),
                    0,
                    treeNode.getData(),
                    OFFSET_TREE_NODE_FLAGS_END + (index * (Pointer.BYTES + keySize)),
                    Pointer.BYTES
            );
        }
    }

    /**
     * @param treeNode to read/write from/to
     * @param index of the key we are looking for
     * @return the offset where the key is found at
     */
    private static int getKeyStartOffset(AbstractTreeNode<?> treeNode, int index, int keySize, int valueSize) {
        if (!treeNode.isLeaf()){
            return OFFSET_INTERNAL_NODE_KEY_BEGIN + (index * (keySize + valueSize));
        } else {
            return OFFSET_TREE_NODE_FLAGS_END + (index * (keySize + valueSize));
        }
    }

    /**
     * @param treeNode to read/write from/to
     * @param index of the key to check existence
     * @return boolean state of existence of a key in index
     */
    public static <K extends Comparable<K>> boolean hasKeyAtIndex(AbstractTreeNode<?> treeNode, int index, int degree, BinaryObjectWrapper<K> kBinaryObjectWrapper, int valueSize) {
        if (index >= degree - 1)
            return false;

        int keyStartIndex = getKeyStartOffset(treeNode, index, kBinaryObjectWrapper.size(), valueSize);
        if (keyStartIndex + kBinaryObjectWrapper.size() > treeNode.getData().length)
            return false;
        return kBinaryObjectWrapper.load(treeNode.getData(), keyStartIndex).hasValue();
    }


    public static <E extends Comparable<E>> void setKeyAtIndex(AbstractTreeNode<?> treeNode, int index, BinaryObjectWrapper<E> binaryObjectWrapper, int valueSize) {
        int keyStartIndex = getKeyStartOffset(treeNode, index, binaryObjectWrapper.size(), valueSize);
        System.arraycopy(
                binaryObjectWrapper.getBytes(),
                0,
                treeNode.getData(),
                keyStartIndex,
                binaryObjectWrapper.size()
        );
    }

    /**
     * @param treeNode to read/write from/to
     * @param index to read they key at
     * @return key value at index
     */
    public static <K extends Comparable<K>> BinaryObjectWrapper<K> getKeyAtIndex(AbstractTreeNode<?> treeNode, int index, BinaryObjectWrapper<K> kBinaryObjectWrapper, int valueSize) {
        int keyStartIndex = getKeyStartOffset(treeNode, index, kBinaryObjectWrapper.size(), valueSize);
        return kBinaryObjectWrapper.load(treeNode.getData(), keyStartIndex);
    }

    public static void removeKeyAtIndex(AbstractTreeNode<?> treeNode, int index, int keySize, int valueSize) {
        System.arraycopy(
                new byte[keySize],
                0,
                treeNode.getData(),
                getKeyStartOffset(treeNode, index, keySize, valueSize),
                keySize
        );
    }

    public static <K extends Comparable<K>, V extends Comparable<V>> Map.Entry<K, V> getKeyValueAtIndex(
            AbstractTreeNode<K> treeNode,
            int index,
            BinaryObjectWrapper<K> kBinaryObjectWrapper,
            BinaryObjectWrapper<V> vBinaryObjectWrapper
    ){
        int keyStartIndex = getKeyStartOffset(treeNode, index, kBinaryObjectWrapper.size(), vBinaryObjectWrapper.size());
        return new AbstractMap.SimpleImmutableEntry<K, V>(
                kBinaryObjectWrapper.load(treeNode.getData(), keyStartIndex).asObject(),
                vBinaryObjectWrapper.load(treeNode.getData(), keyStartIndex + kBinaryObjectWrapper.size()).asObject()
        );
    }

    public static <K extends Comparable<K>, V extends Comparable<V>> void setKeyValueAtIndex(AbstractTreeNode<?> treeNode, int index, BinaryObjectWrapper<K> keyInnerObj, BinaryObjectWrapper<V> valueInnerObj) {
        System.arraycopy(
                keyInnerObj.getBytes(),
                0,
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + (index * (keyInnerObj.size() + valueInnerObj.size())),
                keyInnerObj.size()
        );

        System.arraycopy(
                valueInnerObj.getBytes(),
                0,
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + (index * (keyInnerObj.size() + valueInnerObj.size())) + keyInnerObj.size(),
                valueInnerObj.size()
        );

    }


    /**
     * Todo: performance improvements may be possible
     *       linear search is used to sort the keys
     *       binary search could be used
     *       alternatively, we can hold a space for metadata which keeps track of the number of keys or values stored
     */
    public static <K extends Comparable<K>, V extends Comparable<V>> int addKeyValueAndGetIndex(
            AbstractTreeNode<?> treeNode,
            int degree,
            BinaryObjectWrapper<K> binaryObjectWrapper,
            K key,
            int keySize,
            BinaryObjectWrapper<V> valueBinaryObjectWrapper,
            V value,
            int valueSize
    ) throws BinaryObjectWrapper.InvalidBinaryObjectWrapperValue {
        int indexToFill = -1;
        BinaryObjectWrapper<K> keyAtIndex;
        for (int i = 0; i < degree - 1; i++){
            keyAtIndex = getKeyAtIndex(treeNode, i, binaryObjectWrapper, valueSize);
            K data = keyAtIndex.asObject();
            if (!keyAtIndex.hasValue() || data.compareTo(key) > 0){
                indexToFill = i;
                break;
            }
        }

        if (indexToFill == -1){
            throw new RuntimeException("F..ed up"); // Todo
        }


        int max = degree - 1;
        int bufferSize = ((max - indexToFill - 1) * (keySize + valueSize));

        byte[] temp = new byte[bufferSize];
        System.arraycopy(
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + (indexToFill * (keySize + valueSize)),
                temp,
                0,
                temp.length
        );

        setKeyValueAtIndex(
                treeNode,
                indexToFill,
                binaryObjectWrapper.load(key),
                valueBinaryObjectWrapper.load(value)
        );

        System.arraycopy(
                temp,
                0,
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + ((indexToFill + 1) * (keySize + valueSize)),
                temp.length
        );

        return indexToFill;

    }

    // Todo: this function will shift the remaining space after next KV to current KV (which we wanted to remove) despite other KV existing.
    //       The performance could improve (reduce copy call) by checking if next KV exists at all first.
    public static void removeKeyValueAtIndex(AbstractTreeNode<?> treeNode, int index, int keySize, int valueSize) {
        int nextIndexOffset = getKeyStartOffset(treeNode, index + 1, keySize, valueSize);
        if (nextIndexOffset < treeNode.getData().length - SIZE_LEAF_NODE_SIBLING_POINTERS){
            System.arraycopy(
                    new byte[keySize + valueSize],
                    0,
                    treeNode.getData(),
                    getKeyStartOffset(treeNode, index, keySize, valueSize),
                    keySize + valueSize
            );
        } else {
            System.arraycopy(
                    treeNode.getData(),
                    nextIndexOffset,
                    treeNode.getData(),
                    getKeyStartOffset(treeNode, index, keySize, valueSize),
                    nextIndexOffset - SIZE_LEAF_NODE_SIBLING_POINTERS
            );
            System.arraycopy(
                    new byte[keySize + valueSize],
                    0,
                    treeNode.getData(),
                    treeNode.getData().length - SIZE_LEAF_NODE_SIBLING_POINTERS - (keySize + valueSize),
                    keySize + valueSize
            );
        }
    }

    public static Optional<Pointer> getPreviousPointer(AbstractTreeNode<?> treeNode, int degree, int keySize, int valueSize){
        if (treeNode.getData()[OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize))] == (byte) 0x0){
            return Optional.empty();
        }

        return Optional.of(
                Pointer.fromBytes(treeNode.getData(), OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize)))
        );
    }

    public static void setPreviousPointer(AbstractTreeNode<?> treeNode, int degree, Pointer pointer, int keySize, int valueSize) {
        System.arraycopy(
                pointer.toBytes(),
                0,
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize)),
                Pointer.BYTES
        );
    }

    public static Optional<Pointer> getNextPointer(AbstractTreeNode<?> treeNode, int degree, int keySize, int valueSize) {
        if (treeNode.getData()[OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize)) + Pointer.BYTES] == (byte) 0x0){
            return Optional.empty();
        }

        return Optional.of(
                Pointer.fromBytes(treeNode.getData(), OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize)) + Pointer.BYTES)
        );
    }

    public static void setNextPointer(AbstractTreeNode<?> treeNode, int degree, Pointer pointer, int keySize, int valueSize) {
        System.arraycopy(
                pointer.toBytes(),
                0,
                treeNode.getData(),
                OFFSET_LEAF_NODE_KEY_BEGIN + ((degree - 1) * (keySize + valueSize)) + Pointer.BYTES,
                Pointer.BYTES
        );
    }

    public static void cleanChildrenPointers(InternalTreeNode<?> treeNode, int degree, int keySize, int valueSize) {
        int len = ((degree - 1) * ((keySize + valueSize)) + Pointer.BYTES);
        System.arraycopy(
                new byte[len],
                0,
                treeNode.getData(),
                OFFSET_TREE_NODE_FLAGS_END,
                len
        );
    }
}
