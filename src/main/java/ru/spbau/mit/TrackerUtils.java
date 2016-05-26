package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class TrackerUtils {

    private static final Logger LOGGER = Logger.getLogger("TrackerUtils");

    private TrackerUtils() {
    }

    // executeList request
    public static void executeList(DataOutputStream output, TrackerState state) throws IOException {
        state.listFiles(output);
    }

    // executeUpload request
    public static void executeUpload(
            Socket clientSocket, DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        String name = input.readUTF();
        long size = input.readLong();
        int id = state.getId();
        FileDescr fd = new FileDescr(id, name, size);
        // possibly synchronize?
        state.getFiles().add(fd);
        state.getSeeds().put(id, new ArrayList<>());
// TODO
//        int id;
//        FileDescr fd;
//        synchronized (state.filesSync) {
//            id = state.getFiles().size();
//            fd = new FileDescr(id, name, size);
//            state.getFiles().add(fd);
//        }

        LOGGER.info("Upload handler -- read data: name=" +
                name + " size=" + Long.toString(size) + " id=" + Integer.toString(id));
        // how to distinguish one client from another if they all seem to have the same address?
        // apparently by file id

// NOT NEEDED AT ALL
//        ClientDescriptor clientDescriptor = new ClientDescriptor(clientSocket);

//        InetAddress clientAddr = clientSocket.getInetAddress();
//        LOGGER.warning("EXECUTE_UPLOAD: clientSocket.address=" + clientAddr.toString());
//        int port = clientSocket.getPort();
//        LOGGER.warning("EXECUTE_UPLOAD: clientSocket.port=" + Integer.toString(port));
//        ClientDescriptor client = state.getClients().get(clientAddr);
//        if (client != null) {
//            client.setLastUpdated(System.currentTimeMillis());
//        } else {
//            client = new ClientDescriptor(clientAddr, port, System.currentTimeMillis());
//            state.getClients().put(clientAddr, client);
//        }
//
//        ArrayList<ClientDescriptor> tmp = new ArrayList<>();
//        tmp.add(client);

// NOT NEEDED AT ALL
//        state.getSeeds().put(id, clientDescriptor);

// TODO
//        possibly synchronized?
//        state.insertSeed(id, clientDescriptor);

        output.writeInt(id);
        output.flush();
    }

    // executeSources request
    public static void executeSources(DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        int id = input.readInt();
        state.updateSeeds(id);
        List<ClientDescriptor> fileSeeds = state.getSeeds().get(id);
        if (fileSeeds == null) {
            output.writeInt(0);
            output.flush();
            LOGGER.warning("Wrong file id!");
            return;
        }
        output.writeInt(fileSeeds.size());
        for (ClientDescriptor seed : fileSeeds) {
            byte[] addrBytes = seed.getAddr().getAddress();
            output.write(addrBytes);
            // output.writeShort(TorrentTrackerMain.PORT);
            output.writeShort(seed.getPort());
        }
        output.flush();
    }

    // executeUpdate request
    public static void executeUpdate(
            Socket clientSocket, DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        LOGGER.warning("enter executeUpdate TRACKER_UTILS");
        try {
            int seedPort = input.readInt();
            String from = "from port=" + Integer.toString(seedPort);
            LOGGER.warning("TRACKER_UTILS getting update from client " + from);
            int count = input.readInt();
            LOGGER.warning("TRACKER_UTILS executeUpdate read count=" + Integer.toString(count) + from);
            for (int i = 0; i < count; i++) {
                int id = input.readInt();
                LOGGER.warning("TRACKER_UTILS executeUpdate read id=" + Integer.toString(id) + from);
                boolean updated = false;
                // since Update and Client runs in the same place I suppose they have the similar InetAddress
                // but port still may differ, so it's necessary to pass it
                for (ClientDescriptor seed : state.getSeeds().get(id)) {
                    if (seed.getAddr().equals(clientSocket.getInetAddress()) &&
                            seed.getPort() == seedPort) {
                        seed.setLastUpdated(System.currentTimeMillis());
                        updated = true;
                    }
                }
                if (!updated) {
                    LOGGER.warning("TRACKER_UTILS executeUpdate !updated" + from);
                    state.getSeeds().get(id).add(new ClientDescriptor(clientSocket.getInetAddress(),
                            seedPort, System.currentTimeMillis()));
//                    InetAddress addr = clientSocket.getInetAddress();
//                    ClientDescriptor seed = state.getClients().get(addr);
//                    if (seed == null) {
//                        seed = new ClientDescriptor(addr, seedPort, System.currentTimeMillis());
//                        state.getClients().put(addr, seed);
//                    } else {
//                        seed.setLastUpdated(System.currentTimeMillis());
//                    }
//                    state.getSeeds().get(id).add(seed);
                }
            }
        } catch (IOException e) {
            output.writeBoolean(false);
            output.flush();
            return;
        }
        output.writeBoolean(true);
        output.flush();
        LOGGER.warning("exit executeUpdate TRACKER_UTILS");

    }
}
