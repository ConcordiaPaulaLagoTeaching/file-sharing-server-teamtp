package ca.concordia.filesystem.datastructures;

public class FNode {

    private int blockIndex;
    private int next;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    
    public int getBlockIndex() {
        return blockIndex;
    }

    public int getNextBlock() {
        return next;
    }
        public void markFree() {
        this.blockIndex = -1;
        this.next = -1;
    }
    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public void setNextBlock(int next) {        //to mark as free
        this.next = next;
    }
}
}
