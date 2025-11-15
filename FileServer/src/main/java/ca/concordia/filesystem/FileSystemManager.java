package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import java.util.Arrays;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static final int BLOCK_SIZE = 128;

    private final RandomAccessFile disk;

    // Multiple readers allowed, single writer exclusive
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Metadata
    private final FEntry[] inodeTable;
    private final boolean[] freeBlockList; // true = free, false = used

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
        lock.readLock().lock();
        try {
            for (int i = 0; i < inodeTable.length; i++) {
                FEntry e = inodeTable[i];
                if (e != null && e.getFilename().equals(name)) {
                    return i;
                }
            }
            return -1;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void createFile(String fileName) throws Exception {

        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("invalid filename");
        if (fileName.length() > 11)
            throw new IllegalArgumentException("Filename too long");

        // Check existence BEFORE acquiring write lock (safe, efficient)
        if (findFileIndex(fileName) != -1)
            return;

        lock.writeLock().lock();
        try {
            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] == null) {
                    inodeTable[i] = new FEntry(fileName, (short) 0, (short) -1);
                    return;
                }
            }

            throw new IllegalStateException("no more free entries");

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {

        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("invalid filename");
        if (fileName.length() > 11)
            throw new IllegalArgumentException("filename too long");

        int idx = findFileIndex(fileName);
        if (idx == -1) return; // ignore missing file

        int start, count;

        lock.writeLock().lock();
        try {
            FEntry entry = inodeTable[idx];
            start = entry.getFirstBlock();
            count = (int) Math.ceil(entry.getFilesize() / (double) BLOCK_SIZE);

            // Free metadata blocks
            if (start >= 0) {
                for (int i = 0; i < count; i++)
                    freeBlockList[start + i] = true;
            }

            // Remove inode entry
            inodeTable[idx] = null;

        } finally {
            lock.writeLock().unlock();
        }

        // Clear blocks on disk AFTER freeing metadata
        try {
            if (start >= 0) {
                for (int i = 0; i < count; i++) {
                    disk.seek((start + i) * BLOCK_SIZE);
                    disk.write(new byte[BLOCK_SIZE]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Disk write error during delete", e);
        }
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {
        int fileIdx = findFileIndex(fileName);
        if (fileIdx == -1)
            throw new Exception("file does not exist");

        int newCount = (int) Math.ceil(contents.length / (double) BLOCK_SIZE);
        int newStart = -1;

        lock.writeLock().lock();
        try {
            FEntry entry = inodeTable[fileIdx];

            // Free old blocks
            int oldStart = entry.getFirstBlock();
            int oldCount = (int) Math.ceil(entry.getFilesize() / (double) BLOCK_SIZE);

            if (oldStart >= 0) {
                for (int i = 0; i < oldCount; i++)
                    freeBlockList[oldStart + i] = true;
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

            // Mark new blocks used
            for (int i = 0; i < newCount; i++)
                freeBlockList[newStart + i] = false;

            // Update metadata
            entry.setFilesize((short) contents.length);
            entry.setFirstBlock((short) newStart);

        } finally {
            lock.writeLock().unlock();
        }

        // Write to disk (outside lock)
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
        int start, size;

        lock.readLock().lock();
        try {
            int idx = findFileIndex(fileName);
            if (idx == -1)
                throw new Exception("file does not exist");

            FEntry entry = inodeTable[idx];
            start = entry.getFirstBlock();
            size = entry.getFilesize();

        } finally {
            lock.readLock().unlock();
        }

        if (start < 0)
            return new byte[0];

        byte[] data = new byte[size];
        int blocks = (int) Math.ceil(size / (double) BLOCK_SIZE);

        int offset = 0;
        for (int i = 0; i < blocks; i++) {
            disk.seek((start + i) * BLOCK_SIZE);
            int chunk = Math.min(BLOCK_SIZE, size - offset);
            disk.readFully(data, offset, chunk);
            offset += chunk;
        }

        return data;
    }

    public String[] listFiles() {
        lock.readLock().lock();
        try {
            return Arrays.stream(inodeTable)
                    .filter(e -> e != null && e.getFilename() != null && !e.getFilename().isEmpty())
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            lock.readLock().unlock();
        }
    }
}
   

