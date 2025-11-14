package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.util.Vector;
import java.io.RandomAccessFile;
import java.security.KeyStore.Entry;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
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
    this.fNodes        = new FNode[MAXBLOCKS];   // you added this earlier
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
}


    private FEntry findFileEntry(String name) throws Exception {
    for (FEntry e : inodeTable)
        if (e.getFilename().equals(name))
            return e;

    throw new Exception("ERROR: file " + name + " does not exist");
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
