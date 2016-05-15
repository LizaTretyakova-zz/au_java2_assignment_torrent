import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class TrackerState {

    public final Object filesSync = new Object();

    private static final Logger logger = Logger.getLogger("TrackerState");
    // map: file_id ---> clients holding it
    private final HashMap<Integer, List<ClientDescriptor>> seeds = new HashMap<>();
    // map: IP ---> client
    private final HashMap<InetAddress, ClientDescriptor> clients = new HashMap<>();
    private final List<FileDescr> files = new ArrayList<>();

    public TrackerState(String path) {
        if(!Files.exists(Paths.get(path))) {
            logger.info("Starting from scratch");
            return;
        }

        try (DataInputStream src = new DataInputStream(new FileInputStream(path))) {
            int cnt = src.readInt();
            for (int i = 0; i < cnt; i++) {
                String filename = src.readUTF();
                int fileId = src.readInt();
                long size = src.readLong();

                files.add(new FileDescr(fileId, filename, size));
            }
            logger.info("Restored from file");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    synchronized public HashMap<Integer, List<ClientDescriptor>> getSeeds() {
        return seeds;
    }

    synchronized public HashMap<InetAddress, ClientDescriptor> getClients() {
        return clients;
    }

    synchronized public List<FileDescr> getFiles() {
        return files;
    }

    synchronized public void store(DataOutputStream output) throws IOException {
        for (FileDescr fd : files) {
            output.writeUTF(fd.getName());
            output.writeInt(fd.getId());
            output.writeLong(fd.getSize());

            output.flush();
        }
    }

    synchronized public void updateSeeds(int id) {
        for (Iterator<ClientDescriptor> it = seeds.get(id).iterator();
             it.hasNext(); ) {
            ClientDescriptor seed = it.next();
            if (System.currentTimeMillis() - seed.getLastUpdated() > Tracker.TIMEOUT) {
                it.remove();
            }
        }
    }

//    public static void updateSeeds(List<ClientDescriptor> fileSeeds) {
//        for (Iterator<ClientDescriptor> it = fileSeeds.iterator();
//             it.hasNext(); ) {
//            ClientDescriptor seed = it.next();
//            if (System.currentTimeMillis() - seed.getLastUpdated() > Tracker.TIMEOUT) {
//                it.remove();
//            }
//        }
//    }
}