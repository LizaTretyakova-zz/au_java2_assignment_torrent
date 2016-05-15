import java.net.InetAddress;

// client description: id, IP, port, time of last executeUpdate
public class ClientDescriptor {
    private InetAddress addr;
    private long lastUpdated;

    public ClientDescriptor(InetAddress addr, int port, long lastUpdated) {
        this.addr = addr;
        this.lastUpdated = lastUpdated;
    }

    synchronized public long getLastUpdated() {
        return lastUpdated;
    }

    synchronized public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    synchronized public InetAddress getAddr() {
        return addr;
    }
}
