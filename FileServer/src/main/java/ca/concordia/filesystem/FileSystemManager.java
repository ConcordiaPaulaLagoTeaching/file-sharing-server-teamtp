package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import java.util.Arrays;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import ca.concordia.filesystem.datastructures.FNode;

import java.util.Vector;
import java.io.RandomAccessFile;
import java.security.KeyStore.Entry;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static final int BLOCK_SIZE = 128;

    private final RandomAccessFile disk;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final FEntry[] inodeTable;
    private final boolean[] freeBlockList;

    public FileSystemManager(String filename, int totalSizeBytes) {
        try {
            this.disk = new RandomAccessFile(filename, "rw");
            this.disk.setLength(totalSizeBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open disk file", e);
        }

        this.inodeTable = new FEntry[MAXFILES];
        this.freeBlockList = new boolean[MAXBLOCKS];
        Arrays.fill(freeBlockList, true);
    }

    private int findFileIndex(String name) {
        for (int i = 0; i < inodeTable.length; i++) {
            FEntry e = inodeTable[i];
            if (e != null && e.getFilename().equals(name)) {
                return i;
            }
        }
        return -1;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    private FNode[] fNodes;

public FileSystemManager(String filename, int totalSizeBytes) {
    try {
        this.disk = new RandomAccessFile(filename, "rw");
        // make sure the backing file exists and has the requested size
        this.disk.setLength(totalSizeBytes);
    } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to open simulated disk file", e);
    }

    // init in-memory metadata
    this.inodeTable    = new FEntry[MAXFILES];
    this.freeBlockList = new boolean[MAXBLOCKS];
    this.fNodes        = new FNode[MAXBLOCKS];
}

    public void createFile(String fileName) throws Exception {  
        //check if less than 11 characters or invalid name
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("invalid filename");
            }
            if (fileName.length() > 11) {
                throw new IllegalArgumentException("name has more than 11 characters");
            }

            globalLock.lock(); //mutex, makes the create operation atomic
            try {
                //if already exists
                for (FEntry e : inodeTable) {
                    if (e != null && e.getFilename() != null && !e.getFilename().isEmpty() && e.getFilename().equals(fileName)) {
                        return; //file already exists
                    }
                }

                //finding the first empty FEntry slot
                for (int i =0 ; i <inodeTable.length; i++) {
                        FEntry e=inodeTable[i];
                        if (e==null || e.getFilename() == null || e.getFilename().isEmpty()) {
                            inodeTable[i]= new FEntry(fileName, (short)0, (short)-1);
                        
                        return;
                        }
                    }
                

                //when no free entry available
                throw new IllegalStateException("no more free entries");
                
            } finally {
                globalLock.unlock();
            }
  
    }


    public void createFile(String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("invalid filename");

        if (fileName.length() > 11)
            throw new IllegalArgumentException("filename too long");

        writeLock.lock();
        try {
            if (findFileIndex(fileName) != -1) {
                return; // already exists
            }

            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] == null) {
                    inodeTable[i] = new FEntry(fileName, (short) 0, (short) -1);
                    return;
                }
            }

            throw new IllegalStateException("no more free entries");

        } finally {
            writeLock.unlock();
        }
   public void deleteFile(String fileName) throws Exception {
    if (fileName == null || fileName.isEmpty()) {
        throw new IllegalArgumentException("invalid filename");
    }
    if (fileName.length() > 11) {
        throw new IllegalArgumentException("filename too large");
    }

    globalLock.lock();
    try {
        //Find the file entry
        int entryIdx = -1;
        for (int i = 0; i < inodeTable.length; i++) {
            FEntry e = inodeTable[i];
            if (e != null && e.getFilename() != null && !e.getFilename().isEmpty()
                    && e.getFilename().equals(fileName)) {
                entryIdx = i;
                break;
            }
        }
        if (entryIdx == -1) {
            throw new IllegalStateException("file " + fileName + " doesnt exist");
        }

        FEntry entry = inodeTable[entryIdx];

        //go through the FNode chain and free each data block
        int currentNodeIndex = entry.getFirstBlock();
        while (currentNodeIndex != -1) {
            FNode node = fNodes[currentNodeIndex]; 

            int dataBlockIndex = node.getBlockIndex();
            if (dataBlockIndex >= 0) {
                long offset = (long) dataBlockIndex * BLOCK_SIZE;
                byte[] zeros = new byte[BLOCK_SIZE];
                disk.seek(offset);
                disk.write(zeros);

                //marking the block free in the bitmap
                freeBlockList[dataBlockIndex] = false;     //false = free
            }

            int next = node.getNextBlock();

            node.setBlockIndex((int) -1);
            node.setNextBlock((int) -1);

            currentNodeIndex = next;
        }

        //clear the FEntry (make slot reusable)
        inodeTable[entryIdx] = new FEntry("", (short) 0, (short) -1);

    } finally {
        globalLock.unlock();
    }
}

    public void deleteFile(String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("invalid filename");

        if (fileName.length() > 11)
            throw new IllegalArgumentException("filename too long");

        int firstBlock = -1;
        int blockCount = 0;
        int idx = -1;

        writeLock.lock();
        try {
            idx = findFileIndex(fileName);
            if (idx == -1) {
                return; // safe ignore missing file
            }

            FEntry entry = inodeTable[idx];
            firstBlock = entry.getFirstBlock();
            blockCount = (int) Math.ceil(entry.getFilesize() / (double) BLOCK_SIZE);

            // free blocks in metadata
            if (firstBlock >= 0) {
                for (int i = 0; i < blockCount; i++) {
                    if (firstBlock + i < MAXBLOCKS) {
                        freeBlockList[firstBlock + i] = true;
                    }
                }
            }

            inodeTable[idx] = null;

        } finally {
            writeLock.unlock();
        }

        if (firstBlock >= 0) {
            for (int i = 0; i < blockCount; i++) {
                int blk = firstBlock + i;
                if (blk < MAXBLOCKS) {
                    disk.seek(blk * BLOCK_SIZE);
                    disk.write(new byte[BLOCK_SIZE]);
                }
            }
        }
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {
        int fileIdx = -1;
        int oldStart = -1;
        int oldCount = 0;
        int newStart = -1;
        int newCount = (int) Math.ceil(contents.length / (double) BLOCK_SIZE);

        writeLock.lock();
        try {
            fileIdx = findFileIndex(fileName);
            if (fileIdx == -1)
                throw new Exception("file does not exist");

            FEntry entry = inodeTable[fileIdx];

            oldStart = entry.getFirstBlock();
            oldCount = (int) Math.ceil(entry.getFilesize() / (double) BLOCK_SIZE);

            // Free old blocks in metadata
            if (oldStart >= 0) {
                for (int i = 0; i < oldCount; i++) {
                    if (oldStart + i < MAXBLOCKS) {
                        freeBlockList[oldStart + i] = true;
                    }
                }
            }

            // Find new contiguous space
            for (int i = 0; i <= MAXBLOCKS - newCount; i++) {
                boolean ok = true;
                for (int j = 0; j < newCount; j++) {
                    if (!freeBlockList[i + j]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    newStart = i;
                    break;
                }
            }

            if (newStart == -1)
                throw new Exception("file too large");

            // Mark new blocks as used in metadata
            for (int i = 0; i < newCount; i++) {
                freeBlockList[newStart + i] = false;
            }

            // Update entry metadata
            entry.setFilesize((short) contents.length);

            // manually set firstBlock (since no setter)
            entry.setFirstBlock((short) newStart);

        } finally {
            writeLock.unlock();
        }

        int offset = 0;
        for (int i = 0; i < newCount; i++) {
            disk.seek((newStart + i) * BLOCK_SIZE);

            int chunk = Math.min(BLOCK_SIZE, contents.length - offset);
            disk.write(contents, offset, chunk);

            if (chunk < BLOCK_SIZE)
                disk.write(new byte[BLOCK_SIZE - chunk]);

            offset += chunk;
        }
    }

    public byte[] readFile(String fileName) throws Exception {
        int first = -1;
        int size = 0;

        // read metadata inside lock
        readLock.lock();
        try {
            int idx = findFileIndex(fileName);
            if (idx == -1)
                throw new Exception("file does not exist");

            FEntry entry = inodeTable[idx];
            first = entry.getFirstBlock();
            size = entry.getFilesize();

        } finally {
            readLock.unlock();
        }

        if (first < 0)
            return new byte[0];

        byte[] data = new byte[size];
        int blocks = (int) Math.ceil(size / (double) BLOCK_SIZE);
        int offset = 0;

        // disk I/O outside lock
        for (int i = 0; i < blocks; i++) {
            disk.seek((first + i) * BLOCK_SIZE);
            int chunk = Math.min(BLOCK_SIZE, size - offset);
            disk.readFully(data, offset, chunk);
            offset += chunk;
        }

        return data;
    }
    
    public String[] listFiles() {
        readLock.lock();
        try {
            return Arrays.stream(inodeTable)
                    .filter(e -> e != null && e.getFilename() != null)
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            readLock.unlock();
        }
    }
}

public String[] listFiles() {
    //if we later switch to a ReadWriteLock, use readLock here
    globalLock.lock();
    try {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (FEntry e : inodeTable) {
            if (e != null) {
                String n = e.getFilename();
                if (n != null && !n.isEmpty()) {
                    names.add(n);
                }
            }
        }
        return names.toArray(new String[0]);
    } finally {
        globalLock.unlock();
    }
}
    // TODO: Add readFile, writeFile and other required methods,
}
