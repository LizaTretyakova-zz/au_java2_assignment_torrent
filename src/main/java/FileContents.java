import java.io.*;

// file description with contents splitted into byte arrays
public class FileContents {
    public static final int PART_SIZE = 1024;
    private String path;
    // TODO: use RandomAccessFile
//    private byte[][] contents;
    private boolean[] parts;
    private RandomAccessFile ra;

    public FileContents(String path, long size) throws IOException {
        this.path = path;
//        contents = new byte[(int) ((size + Client.PART_SIZE - 1) / Client.PART_SIZE)][];
        parts = new boolean[(int) ((size + PART_SIZE - 1) / PART_SIZE)];
        ra = new RandomAccessFile(path, "rw");

//        DataInputStream file = new DataInputStream(new FileInputStream(path));
//        for (int i = 0; i < contents.length; i++) {
//            contents[i] = new byte[Client.PART_SIZE];
//            file.read(contents[i]);
//        }

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
