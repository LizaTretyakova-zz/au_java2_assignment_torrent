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

    public synchronized int getId() {
        return id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized long getSize() {
        return size;
    }
}
