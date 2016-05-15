import java.net.InetAddress;

// client description: id, IP, port, time of last executeUpdate
public class ClientDescriptor {
    private InetAddress addr;
    private int port;
    private long lastUpdated;

    public ClientDescriptor(InetAddress addr, int port, long lastUpdated) {
        this.addr = addr;
        this.port = port;
        this.lastUpdated = lastUpdated;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddr() {
        return addr;
    }
}
