import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

// file description with contents splitted into byte arrays
public class FileContents {
    private String path;
    // TODO: use RandomAccessFile
    private byte[][] contents;

    public FileContents(String path, long size) throws IOException {
        this.path = path;
        contents = new byte[(int) ((size + Client.PART_SIZE - 1) / Client.PART_SIZE)][];

        DataInputStream file = new DataInputStream(new FileInputStream(path));
        for (int i = 0; i < contents.length; i++) {
            contents[i] = new byte[Client.PART_SIZE];
            file.read(contents[i]);
        }

    }

    public String getPath() {
        return path;
    }

    public int getContentsSize() {
        return contents.length;
    }

    public byte[][] getContents() {
        return contents;
    }
}
