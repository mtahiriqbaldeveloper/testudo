package com.github.sepgh.internal.storage;

import com.github.sepgh.internal.EngineConfig;
import com.github.sepgh.internal.storage.header.Header;
import com.github.sepgh.internal.storage.header.HeaderManager;
import com.github.sepgh.internal.tree.Pointer;
import com.github.sepgh.internal.tree.node.BaseTreeNode;
import com.github.sepgh.internal.tree.node.InternalTreeNode;
import com.google.common.io.BaseEncoding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.sepgh.internal.storage.FileIndexStorageManager.INDEX_FILE_NAME;

public class FileIndexStorageManagerTestCase {
    private static Path dbPath;
    private static EngineConfig engineConfig;
    private static Header header;
    private static final byte[] singleKeyLeafNodeRepresentation = {
            ((byte) (0x00 | BaseTreeNode.ROOT_BIT | BaseTreeNode.TYPE_LEAF_NODE_BIT)), // Leaf

            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F,  // Key 1

            // >> Start pointer to child 1
            Pointer.TYPE_DATA,  // type
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,  // position
            0x00, 0x00, 0x00, 0x01, // chunk
            // >> End pointer to child 1
    };
    private static final byte[] singleKeyInternalNodeRepresentation = {
            ((byte) (0x00 | BaseTreeNode.ROOT_BIT | BaseTreeNode.TYPE_INTERNAL_NODE_BIT)), // Not leaf

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
        engineConfig = EngineConfig.builder()
                .bTreeNodeMaxKey(1)
                .bTreeGrowthNodeAllocationCount(2)
                .build();
        engineConfig.setBTreeMaxFileSize(3L * engineConfig.getPaddedSize());

        byte[] writingBytes = new byte[engineConfig.getPaddedSize() * 2];
        System.arraycopy(singleKeyLeafNodeRepresentation, 0, writingBytes, 0, singleKeyLeafNodeRepresentation.length);
        System.arraycopy(singleKeyInternalNodeRepresentation, 0, writingBytes, engineConfig.getPaddedSize(), singleKeyInternalNodeRepresentation.length);
        Path indexPath = Path.of(dbPath.toString(), String.format("%s.%d", INDEX_FILE_NAME, 0));
        Files.write(indexPath, writingBytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        header = Header.builder()
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
                                        .initialized(true)
                                        .build()
                        )
                )
                .build();

        Assertions.assertTrue(header.getTableOfId(1).isPresent());
        Assertions.assertTrue(header.getTableOfId(1).get().getIndexChunk(0).isPresent());
    }

    @AfterAll
    public static void destroy() throws IOException {
        Path indexPath0 = Path.of(dbPath.toString(), String.format("%s.%d", INDEX_FILE_NAME, 0));
        Files.delete(indexPath0);
        try {
            Path indexPath1 = Path.of(dbPath.toString(), String.format("%s.%d", INDEX_FILE_NAME, 0));
            Files.delete(indexPath1);
        } catch (NoSuchFileException ignored){}
    }

    @Test
    public void canReadNodeSuccessfully() throws ExecutionException, InterruptedException, IOException {

        HeaderManager headerManager = new InMemoryHeaderManager(header);

        FileIndexStorageManager fileIndexStorageManager = new FileIndexStorageManager(dbPath, headerManager, engineConfig);
        try {
            CompletableFuture<IndexStorageManager.NodeData> future = fileIndexStorageManager.readNode(1, 0, 0);

            IndexStorageManager.NodeData nodeData = future.get();
            System.out.println(BaseEncoding.base16().lowerCase().encode(nodeData.bytes()));
            Assertions.assertEquals(engineConfig.getPaddedSize(), nodeData.bytes().length);

            BaseTreeNode treeNode = BaseTreeNode.fromBytes(nodeData.bytes());

            Iterator<Long> keys = treeNode.keys();

            Assertions.assertTrue(keys.hasNext());
            Assertions.assertEquals(15, keys.next());


            future = fileIndexStorageManager.readNode(1, engineConfig.getPaddedSize(), 0);
            nodeData = future.get();
            System.out.println(BaseEncoding.base16().lowerCase().encode(nodeData.bytes()));
            Assertions.assertEquals(engineConfig.getPaddedSize(), nodeData.bytes().length);

            treeNode = BaseTreeNode.fromBytes(nodeData.bytes());

            Iterator<Pointer> children = ((InternalTreeNode) treeNode).children();

            Assertions.assertTrue(children.hasNext());
            Pointer pointer = children.next();
            Assertions.assertTrue(pointer.isNodePointer());
            Assertions.assertEquals(1, pointer.position());
            Assertions.assertEquals(1, pointer.chunk());
        } finally {
            fileIndexStorageManager.close();
        }

    }

}
