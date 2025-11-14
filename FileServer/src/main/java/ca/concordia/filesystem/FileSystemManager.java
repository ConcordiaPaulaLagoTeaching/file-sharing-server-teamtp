package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import java.util.Vector;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
    if (instance != null)
        throw new IllegalStateException("FileSystemManager is already initialized.");
    
    instance = this;

    try {
        this.disk = new RandomAccessFile(filename, "rw");
        this.disk.setLength(totalSize);

        inodeTable = new FEntry[MAXFILES];
        for (int i = 0; i < MAXFILES; i++) {
            inodeTable[i] = new FEntry("", (short)0, (short)-1);
        }

        freeBlockList = new boolean[MAXBLOCKS];
        for (int i = 0; i < MAXBLOCKS; i++)
            freeBlockList[i] = true; 

    } catch (Exception e) {
        throw new RuntimeException("Failed to initialize filesystem: " + e.getMessage());
    }
}


    private FEntry findFileEntry(String name) throws Exception {
    for (FEntry e : inodeTable)
        if (e.getFilename().equals(name))
            return e;

    throw new Exception("ERROR: file " + name + " does not exist");
}




    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

   public void writeFile(String fileName, byte[] contents) throws Exception {
    globalLock.lock();

    try {
    
        FEntry entry = findFileEntry(fileName); 

        int numBlocks = (int)Math.ceil(contents.length / (double) BLOCK_SIZE);

        int start = -1;

        for (int i = 0; i <= MAXBLOCKS - numBlocks; i++) {
            boolean ok = true;

            for (int j = 0; j < numBlocks; j++) {
                if (!freeBlockList[i + j]) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                start = i;
                break;
            }
        }

        if (start == -1)
            throw new Exception("ERROR: file too large");

        int oldStart = entry.getFirstBlock();
        if (oldStart != -1) {
            int oldBlocks = (int)Math.ceil(entry.getFilesize() / (double) BLOCK_SIZE);

            for (int i = 0; i < oldBlocks; i++) {
                freeBlockList[oldStart + i] = true;
                disk.seek((oldStart + i) * BLOCK_SIZE);
                disk.write(new byte[BLOCK_SIZE]); 
            }
        }

        for (int i = 0; i < numBlocks; i++)
            freeBlockList[start + i] = false;

        int offset = 0;
        for (int i = 0; i < numBlocks; i++) {
            disk.seek((start + i) * BLOCK_SIZE);

            int chunk = Math.min(BLOCK_SIZE, contents.length - offset);
            disk.write(contents, offset, chunk);

            if (chunk < BLOCK_SIZE)
                disk.write(new byte[BLOCK_SIZE - chunk]);

            offset += chunk;
        }

        entry.setFilesize((short) contents.length);

        var field = FEntry.class.getDeclaredField("firstBlock");
        field.setAccessible(true);
        field.setShort(entry, (short) start);

    } finally {
        globalLock.unlock();
    }
}

    public byte[] readFile(String fileName) throws Exception {
    globalLock.lock();
    try {
        FEntry entry = findFileEntry(fileName);

        int size = entry.getFilesize();
        byte[] data = new byte[size];

        int firstBlock = entry.getFirstBlock();
        if (firstBlock == -1)
            return data;

        int numBlocks = (int)Math.ceil(size / (double)BLOCK_SIZE);

        int offset = 0;

        for (int i = 0; i < numBlocks; i++) {
            disk.seek((firstBlock + i) * BLOCK_SIZE);

            int chunk = Math.min(BLOCK_SIZE, size - offset);
            disk.read(data, offset, chunk);

            offset += chunk;
        }

        return data;

    } finally {
        globalLock.unlock();
    }
}

}
