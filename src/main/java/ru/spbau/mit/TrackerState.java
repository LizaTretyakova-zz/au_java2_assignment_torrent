package ru.spbau.mit;

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

    private static final Logger LOGGER = Logger.getLogger("TrackerState");
    // map: file_id ---> clients holding it
    private final HashMap<Integer, List<ClientDescriptor>> seeds = new HashMap<>();
    // map: IP ---> client
    private final HashMap<InetAddress, ClientDescriptor> clients = new HashMap<>();
    private final List<FileDescr> files = new ArrayList<>();
    private int cnt = 0;

    public TrackerState(String path) {
        if (!Files.exists(Paths.get(path))) {
            LOGGER.info("Starting from scratch");
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
            LOGGER.info("Restored from file");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public synchronized HashMap<Integer, List<ClientDescriptor>> getSeeds() {
        return seeds;
    }

    public synchronized HashMap<InetAddress, ClientDescriptor> getClients() {
        return clients;
    }

    public synchronized List<FileDescr> getFiles() {
        return files;
    }

    public synchronized void store(DataOutputStream output) throws IOException {
        for (FileDescr fd : files) {
            output.writeUTF(fd.getName());
            output.writeInt(fd.getId());
            output.writeLong(fd.getSize());

            output.flush();
        }
    }

    public synchronized void updateSeeds(int id) {
        for (Iterator<ClientDescriptor> it = seeds.get(id).iterator();
             it.hasNext();
        ) {
            ClientDescriptor seed = it.next();
            if (System.currentTimeMillis() - seed.getLastUpdated() > TorrentTrackerMain.TIMEOUT) {
                it.remove();
            }
        }
    }

    public synchronized void listFiles(DataOutputStream output) {
        try {
            output.writeInt(files.size());
            for (FileDescr fd : files) {
                output.writeInt(fd.getId());
                output.writeUTF(fd.getName());
                output.writeLong(fd.getSize());
            }
            output.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized int getId() {
        return cnt++;
    }
}
