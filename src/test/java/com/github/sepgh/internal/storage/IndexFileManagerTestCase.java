package com.github.sepgh.internal.storage;

import com.github.sepgh.internal.EngineConfig;
import com.github.sepgh.internal.storage.header.Header;
import com.github.sepgh.internal.storage.header.HeaderManager;
import com.github.sepgh.internal.tree.Pointer;
import com.github.sepgh.internal.tree.TreeNode;
import com.google.common.io.BaseEncoding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.github.sepgh.internal.storage.IndexFileManager.INDEX_FILE_NAME;

public class IndexFileManagerTestCase {
    private static Path dbPath;
    private static EngineConfig engineConfig;
    private static final byte[] singleKeyLeafNodeRepresentation = {
            TreeNode.TYPE_LEAF_NODE, // Leaf

            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F,  // Key 1

            // >> Start pointer to child 1
            Pointer.TYPE_DATA,  // type
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,  // position
            0x00, 0x00, 0x00, 0x01, // chunk
            // >> End pointer to child 1
    };
    private static final byte[] singleKeyInternalNodeRepresentation = {
            TreeNode.TYPE_INTERNAL_NODE, // Not leaf

            // >> Start pointer to child 1
            Pointer.TYPE_NODE,  // type
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,  // position
            0x00, 0x00, 0x00, 0x01, // chunk
            // >> End pointer to child 1

            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F,  // Key

            // >> Start pointer to child 2
            Pointer.TYPE_NODE,  // type
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,  // position
            0x00, 0x00, 0x00, 0x02, // chunk
            // >> End pointer to child 2
    };

    @BeforeAll
    public static void setUp() throws IOException {
        dbPath = Files.createTempDirectory("TEST_IndexFileManagerTestCase");
        engineConfig = EngineConfig.builder().bTreeNodeMaxKey(1).build();
        System.out.println(engineConfig.getPaddedSize());
        byte[] writingBytes = new byte[engineConfig.getPaddedSize() * 2];
        System.arraycopy(singleKeyLeafNodeRepresentation, 0, writingBytes, 0, singleKeyLeafNodeRepresentation.length);
        System.arraycopy(singleKeyInternalNodeRepresentation, 0, writingBytes, engineConfig.getPaddedSize(), singleKeyInternalNodeRepresentation.length);
        Path indexPath = Path.of(dbPath.toString(), String.format("%s.%d", INDEX_FILE_NAME, 0));
        Files.write(indexPath, writingBytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    @AfterAll
    public static void destroy() {

    }

    @Test
    public void readNode() throws ExecutionException, InterruptedException {
        Header header = Header.builder()
                .database("sample")
                .tables(
                        Collections.singletonList(
                                Header.Table.builder()
                                        .id(1)
                                        .name("test")
                                        .chunks(
                                                Collections.singletonList(
                                                        Header.IndexChunk.builder()
                                                                .chunk(0)
                                                                .offset(0)
                                                                .build()
                                                )
                                        )
                                        .build()
                        )
                )
                .build();

        Assertions.assertTrue(header.getTableOfId(1).isPresent());
        Assertions.assertTrue(header.getTableOfId(1).get().getIndexChunk(0).isPresent());

        HeaderManager headerManager = new InMemoryHeaderManager(header);

        IndexFileManager indexFileManager = new IndexFileManager(dbPath, engineConfig, headerManager);
        try {
            Future<byte[]> future = indexFileManager.readNode(1, 0, 0);

            byte[] bytes = future.get();
            System.out.println(BaseEncoding.base16().lowerCase().encode(bytes));
            Assertions.assertEquals(engineConfig.getPaddedSize(), bytes.length);

            TreeNode treeNode = new TreeNode(bytes);
            Assertions.assertTrue(treeNode.isLeaf());

            Iterator<Long> keys = treeNode.keys();

            Assertions.assertTrue(keys.hasNext());
            Assertions.assertEquals(15, keys.next());


            future = indexFileManager.readNode(1, engineConfig.getPaddedSize(), 0);
            bytes = future.get();
            System.out.println(BaseEncoding.base16().lowerCase().encode(bytes));
            Assertions.assertEquals(engineConfig.getPaddedSize(), bytes.length);

            treeNode = new TreeNode(bytes);
            Assertions.assertFalse(treeNode.isLeaf());

            Iterator<Pointer> children = treeNode.children();

            Assertions.assertTrue(children.hasNext());
            Pointer pointer = children.next();
            Assertions.assertTrue(pointer.isNodePointer());
            Assertions.assertEquals(1, pointer.position());
            Assertions.assertEquals(1, pointer.chunk());
        } finally {
            indexFileManager.close();
        }

    }

}
