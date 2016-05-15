// file description: id, name and size
public class FileDescr {

    private final int id;
    private final String name;
    private final long size;

    public FileDescr(int id, String name, long size) {
        this.id = id;
        this.name = name;
        this.size = size;
    }

    synchronized public int getId() {
        return id;
    }

    synchronized public String getName() {
        return name;
    }

    synchronized public long getSize() {
        return size;
    }
}
