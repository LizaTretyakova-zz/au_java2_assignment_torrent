import java.io.*;

// file description with contents splitted into byte arrays
public class FileContents {
    public static final int PART_SIZE = 1024;
    private String path;
    private boolean[] parts;
    private RandomAccessFile ra;

    public FileContents(String path, long size) throws IOException {
        this.path = path;
        parts = new boolean[(int) ((size + PART_SIZE - 1) / PART_SIZE)];
        ra = new RandomAccessFile(path, "rw");
    }

    public String getPath() {
        return path;
    }

    public long getContentsSize() {
        return parts.length;
    }

    synchronized public void writePart(int index, DataInputStream src) throws IOException {
        if(parts[index]) {
            return;
        }

        byte[] buffer = new byte[PART_SIZE];

        if(src.read(buffer) > 0) {
            ra.seek(index * PART_SIZE);
            ra.write(buffer);
            parts[index] = true;
        }
    }

    synchronized public void readPart(int index, DataOutputStream output) throws IOException {
        byte[] buffer = new byte[PART_SIZE];

        ra.seek(index * PART_SIZE);
        ra.read(buffer);
        output.write(buffer);
        output.flush();
    }

    synchronized public boolean isPartDownloaded(int index) {
        return parts[index];
    }
}
