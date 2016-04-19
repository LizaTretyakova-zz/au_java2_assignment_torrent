import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Tracker {

    // constants
    public static final byte LIST = 1;
    public static final byte UPLOAD = 2;
    public static final byte SOURCES = 3;
    public static final byte UPDATE = 4;
    public static final byte OTHER = 5;
    public static final Integer PORT = 8081;
    public static final int TIMEOUT = 60000;
    public static final int IP_SIZE = 4;
    public static final String CONFIG_FILE = "./configTracker";

    private static final Logger LOGGER = Logger.getLogger("TRACKER");
    // map: file_id ---> clients holding it
    private final HashMap<Integer, ArrayList<ClientDescriptor>> seeds = new HashMap<>();
    // map: IP ---> client
    private final HashMap<InetAddress, ClientDescriptor> clients = new HashMap<>();
    // list: all known files
    private final ArrayList<FileDescr> files = new ArrayList<>();
    private ServerSocket serverSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();


    public Tracker() {
        try (DataInputStream src = new DataInputStream(new FileInputStream(CONFIG_FILE))) {
            int cnt = src.readInt();
            for (int i = 0; i < cnt; i++) {
                String filename = src.readUTF();
                int fileId = src.readInt();
                long size = src.readLong();

                files.add(new FileDescr(fileId, filename, size));

                seeds.put(fileId, new ArrayList<>());
                int numberOfSeeds = src.readInt();
                for (int j = 0; j < numberOfSeeds; j++) {
                    byte[] addr = new byte[IP_SIZE];
                    if (src.read(addr) != IP_SIZE) {
                        LOGGER.warning("Incorrect address of a seed, fileId: " + Integer.toString(fileId));
                    }
                    int port = src.readInt();
                    long lastUpdated = src.readLong();
                    ClientDescriptor client = new ClientDescriptor(
                            InetAddress.getByAddress(addr),
                            port,
                            lastUpdated
                    );

                    seeds.get(fileId).add(client);
                    clients.put(InetAddress.getByAddress(addr), client);
                }
            }
            LOGGER.info("Restoring from file");
        } catch (FileNotFoundException e) {
            LOGGER.info("Starting from scratch");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        new Tracker().startTracker();
    }


    public String getTrackerAddr() {
        return serverSocket.getInetAddress().toString();
    }

    // wake up tracker
    public void startTracker() throws IOException {
        DataOutputStream backup = new DataOutputStream(new FileOutputStream(CONFIG_FILE));
        serverSocket = new ServerSocket(PORT);
        threadPool.submit(() -> {
            while (!Thread.interrupted()) {
                LOGGER.info("New loop");
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Client accepted: " + clientSocket.toString());
                    threadPool.submit((Runnable) () -> {
                        try {
                            processClient(clientSocket);
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    });
                } catch (IOException e) {
                    LOGGER.info("Connection closed");
                }
            }

            try {
                store(backup);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }


    // tracker part

    private void store(DataOutputStream output) throws IOException {
        for (FileDescr fd : files) {
            output.writeUTF(fd.getName());
            output.writeInt(fd.getId());
            output.writeLong(fd.getSize());

            ArrayList<ClientDescriptor> fileSeeds = seeds.get(fd.getId());
            output.writeInt(fileSeeds.size());
            for (ClientDescriptor seed : fileSeeds) {
                output.write(seed.getAddr().getAddress());
                output.writeInt(seed.getPort());
                output.writeLong(seed.getLastUpdated());
            }

            output.flush();
        }
    }

    // list request
    private void list(DataOutputStream output) throws IOException {
        output.writeInt(files.size());
        for (FileDescr fd : files) {
            output.writeInt(fd.getId());
            output.writeUTF(fd.getName());
            output.writeLong(fd.getSize());
        }
        output.flush();
    }

    // upload request
    private void upload(Socket clientSocket, DataInputStream input, DataOutputStream output) throws IOException {
        String name = input.readUTF();
        long size = input.readLong();
        int id = new UUID(0, 0).hashCode();
        FileDescr fd = new FileDescr(id, name, size);
        files.add(fd);

        LOGGER.info("Upload handler -- read data: name=" + name + " size=" + Long.toString(size));

        InetAddress clientAddr = clientSocket.getInetAddress();
        int port = clientSocket.getPort();
        ClientDescriptor client = clients.get(clientAddr); //.get(port);
        if (client != null) {
            client.setLastUpdated(System.currentTimeMillis());
        } else {
            client = new ClientDescriptor(clientAddr, port, System.currentTimeMillis());
            clients.put(clientAddr, client); //.get(clientAddr).put(port, client);
        }

        ArrayList<ClientDescriptor> tmp = new ArrayList<>();
        tmp.add(client);
        seeds.put(fd.getId(), tmp);

        output.writeInt(id);
        output.flush();
    }

    // sources request
    private void sources(DataInputStream input, DataOutputStream output) throws IOException {
        int id = input.readInt();
        ArrayList<ClientDescriptor> fileSeeds = seeds.get(id);
        if (fileSeeds == null) {
            //throw new RuntimeException("Wrong file id!");
            LOGGER.warning("Wring file id!");
            return;
        }
        for (Iterator<ClientDescriptor> it = fileSeeds.iterator();
             it.hasNext();) {
            ClientDescriptor seed = it.next();
            if (System.currentTimeMillis() - seed.getLastUpdated() > TIMEOUT) {
                it.remove();
            }
        }
        output.writeInt(fileSeeds.size());
        for (ClientDescriptor seed : fileSeeds) {
            byte[] addrBytes = seed.getAddr().getAddress();
            output.write(addrBytes);
            output.writeByte(PORT);
        }
        output.flush();
    }

    // update request
    private void update(Socket clientSocket, DataInputStream input, DataOutputStream output) throws IOException {
        try {
            int seedPort = input.readInt();
            int count = input.readInt();
            for (int i = 0; i < count; i++) {
                int id = input.readInt();
                boolean updated = false;
                for (ClientDescriptor seed : seeds.get(id)) {
                    if (seed.getAddr() == clientSocket.getInetAddress()) {
                        seed.setLastUpdated(System.currentTimeMillis());
                        updated = true;
                    }
                }
                if (!updated) {
                    InetAddress addr = clientSocket.getInetAddress();
                    ClientDescriptor seed = clients.get(addr); //.get(seed_port);
                    if (seed == null) {
                        seed = new ClientDescriptor(addr, seedPort, System.currentTimeMillis());
                        clients.put(addr, seed); //.get(addr).put(seed_port, seed);
                    } else {
                        seed.setLastUpdated(System.currentTimeMillis());
                    }
                    seeds.get(id).add(seed);
                }
            }
        } catch (IOException e) {
            output.writeBoolean(false);
            output.flush();
            return;
        }
        output.writeBoolean(true);
        output.flush();
    }

    // processing of client have come
    private void processClient(Socket clientSocket) throws IOException {
        DataInputStream input;
        DataOutputStream output;
        try {
            input = new DataInputStream(clientSocket.getInputStream());
            output = new DataOutputStream(clientSocket.getOutputStream());

            byte requestType = input.readByte();
            if (requestType == LIST) {
                list(output);
            } else if (requestType == UPLOAD) {
                upload(clientSocket, input, output);
            } else if (requestType == SOURCES) {
                sources(input, output);
            } else if (requestType == UPDATE) {
                update(clientSocket, input, output);
            } else {
                int id = input.readInt();
                for (FileDescr file : files) {
                    if (file.getId() == id) {
                        long size = file.getSize();
                        output.writeLong(size);
                        output.writeUTF(file.getName());
                        break;
                    }
                }
                output.flush();
            }
        } catch (IOException e) {
            LOGGER.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // shut down
    public void stopTracker() throws IOException {
        serverSocket.close();
        threadPool.shutdown();
    }

    // file description: id, name and size
    public static class FileDescr {

        private int id;
        private String name;
        private long size;

        public FileDescr(int id, String name, long size) {
            this.id = id;
            this.name = name;
            this.size = size;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }
    }

    // client description: id, IP, port, time of last update
    public class ClientDescriptor {
        private InetAddress addr;
        private int port;
        private long lastUpdated;
        private UUID id;

        public ClientDescriptor(InetAddress addr, int port, long lastUpdated) {
            this.addr = addr;
            this.port = port;
            this.lastUpdated = lastUpdated;
            id = new UUID(0, 0);
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
}
