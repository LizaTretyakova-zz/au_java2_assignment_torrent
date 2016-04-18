import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Client {

    // constants
    public static final int PART_SIZE = 1024;
    public static final byte STAT = 1;
    public static final byte GET = 2;
    public static final String CONFIG_FILE = "/configClient";
    public final Logger LOG = Logger.getLogger("CLIENT");
    // thread pool
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private ClientsServer clientsServer = null;
    private Updater updater = null;
    private Boolean running = false;

    // file description with contents splitted into byte arrays
    public class FileContents {
        String path;
        private byte[][] contents;

        public FileContents(String path, long size) throws IOException {
            this.path = path;
            contents = new byte[(int) ((size + PART_SIZE - 1) / PART_SIZE)][];

            DataInputStream file = new DataInputStream(new FileInputStream(path));
            for(int i = 0; i < contents.length; i++) {
                contents[i] = new byte[PART_SIZE];
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

    // a description of a file we will download in a time: id & tracker address
    public class FileRequest {
        private int id;

        public FileRequest(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    // list of files we need to download
    private ArrayList<FileRequest> wishList = new ArrayList<>();
    // list of files we have
    private HashMap<Integer, FileContents> ownedFiles = new HashMap<>();


    // a server part of client
    private class ClientsServer {
        private ServerSocket server;
        private ExecutorService threadPool = Executors.newCachedThreadPool();
        private Runnable processClient = new Runnable() {
            Socket client;
            DataInputStream input;
            DataOutputStream output;
            @Override
            public void run() {
                try {
                    client = server.accept();
                    input = new DataInputStream(client.getInputStream());
                    output = new DataOutputStream(client.getOutputStream());

                    byte type = input.readByte();
                    if (type == STAT) {
                        threadPool.submit((Runnable) () -> {
                            try {
                                stat(input, output);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
//                        stat(input, output);
                    } else if (type == GET) {
                        threadPool.submit((Runnable) () -> {
                            try {
                                get(input, output);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
//                        get(input, output);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        };

        // start listening for incoming connections
        public void start() throws IOException {
            LOG.info("Starting clientsServer");

            server = new ServerSocket(0);
            threadPool.submit(processClient);

            LOG.info("ClientsServer started");
        }

        // stop the client-client server
        public void stop() throws InterruptedException, IOException {
            LOG.info("Stopping clientsServer");

            threadPool.shutdown();

            LOG.info("ClientsServer stopped");
        }

        // stat request
        public void stat(DataInputStream input, DataOutputStream output) throws IOException {
            int id = input.readInt();
            ArrayList<Integer> available = new ArrayList<>();
            synchronized (this) {
                FileContents fc = ownedFiles.get(id);
                byte[][] contents = fc.getContents();
                for(int i = 0; i < fc.getContentsSize(); i++) {
                    if(contents[i] != null) {
                        available.add(i);
                    }
                }
            }

            output.writeInt(available.size());
            for (Integer anAvailable : available) {
                output.writeInt(anAvailable);
            }
            output.flush();

            LOG.info("Stated");
        }

        // give the file to the other client
        public void get(DataInputStream input, DataOutputStream output) throws IOException {
            int id = input.readInt();
            int partId = input.readInt();

            FileContents fileContents = ownedFiles.get(id);
            if(fileContents == null) {
                output.flush();
                LOG.warning("Requested a missing file");
                return;
            }
            byte[] content = fileContents.getContents()[partId];
            if(content == null) {
                output.flush();
                LOG.warning("Requested a missing part of a file");
                return;
            }

            output.write(content);
            output.flush();
            LOG.info("File contents sent");
        }
    }

    // remind tracker about our existence
    private class Updater {
        ScheduledExecutorService executorService = null;
        Socket client;
        DataInputStream input;
        DataOutputStream output;

        public Updater(String trackerAddr) {
            executorService = Executors.newSingleThreadScheduledExecutor();

            try {
                client = new Socket(trackerAddr, Tracker.PORT);
                input = new DataInputStream(client.getInputStream());
                output = new DataOutputStream(client.getOutputStream());

                executorService.scheduleAtFixedRate(() -> {
                    try {
                        output.writeByte(Tracker.UPDATE);
                        output.writeShort((short)client.getPort());
                        output.writeInt(ownedFiles.size());
                        for(Map.Entry entry: ownedFiles.entrySet()) {
                            output.writeInt((int)entry.getKey());
                        }
                        output.flush();
                        boolean succeed = input.readBoolean();

                        LOG.info("Update success: " + Boolean.toString(succeed));
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }, 0, 3 * Tracker.TIMEOUT / 4, TimeUnit.MILLISECONDS);

            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public void stop() {
            executorService.shutdown();
        }
    }

    public Client(String dirPath) {
        try (DataInputStream src = new DataInputStream(new FileInputStream(dirPath + CONFIG_FILE))) {
            int wishListSize = src.readInt();
            for(int i = 0; i < wishListSize; i++) {
                int id = src.readInt();
                wishList.add(new FileRequest(id));
            }

            int ownedFilesSize = src.readInt();
            for(int i = 0; i < ownedFilesSize; i++) {
                String path = src.readUTF();
                long size = src.readLong();
                int id = src.readInt();
                ownedFiles.put(id, new FileContents(path, size));
            }
        } catch (FileNotFoundException e) {
            LOG.info("Client's starting from scratch");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public byte[][] getFileContents(int id) {
        FileContents tmp = ownedFiles.get(id);
        if(tmp == null) {
            return null;
        }
        return tmp.getContents();
    }

    public void store() {
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(CONFIG_FILE))) {
            output.writeInt(wishList.size());
            for(int i = 0; i < wishList.size(); i++) {
                output.writeInt(wishList.get(i).getId());
            }
            output.writeInt(ownedFiles.size());
            for(Map.Entry entry: ownedFiles.entrySet()) {
                String path = ((FileContents)entry.getValue()).getPath();
                output.writeUTF(path);
                output.writeLong(new File(path).length());
                output.writeInt((Integer)entry.getKey());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public static void main(String[] args) throws IOException {
        Client inner = new Client("./");
        if(Objects.equals(args[0], "list")) {
            inner.list(args[1]);
        } else if(Objects.equals(args[0], "get")) {
            inner.get(args[1], args[2]);
        } else if(Objects.equals(args[0], "newfile")) {
            inner.newfile(args[1], args[2]);
        } else if(Objects.equals(args[0], "run")) {
            inner.run(args[1]);
        }
        inner.store();
    }

    // list command
    public void list(String trackerAddr) throws IOException {
        Socket client = new Socket(trackerAddr, Tracker.PORT);
        DataInputStream input = new DataInputStream(client.getInputStream());
        DataOutputStream output = new DataOutputStream(client.getOutputStream());

        output.writeByte(Tracker.LIST);
        output.flush();

        LOG.info("LIST request");

        int count = input.readInt();
        LOG.info("count: " + Integer.toString(count));

        for(int i = 0; i < count; i++) {
            int id = input.readInt();
            String name = input.readUTF();
            long size = input.readLong();

            LOG.info("id: " + Integer.toString(id) + " name: " + name + " size: " + Long.toString(size));
        }

        client.close();
    }

    // newfile command
    public int newfile(String trackerAddr, String path) throws IOException {
        Socket client = new Socket(trackerAddr, Tracker.PORT);
        DataInputStream input = new DataInputStream(client.getInputStream());
        DataOutputStream output = new DataOutputStream(client.getOutputStream());

        output.writeByte(Tracker.UPLOAD);
        output.writeUTF(path);
        long size = Files.size(new File(path).toPath());
        output.writeLong(size);
        output.flush();

        LOG.info("NEWFILE request: ");

        int id = input.readInt();
        LOG.info("id: " + Integer.toString(id));

        FileContents fc = new FileContents(path, size);
        ownedFiles.put(id, fc);
        LOG.info("File uploaded");

        client.close();
        return id;
    }

    // get command
    public void get(String trackerAddress, String fileId) {
        wishList.add(new FileRequest(Integer.parseInt(fileId)));
    }


    // run implementation
    public void run(String trackerAddr) throws IOException {
        synchronized(this) {
            // start sharing
            clientsServer = new ClientsServer();
            clientsServer.start();
            // start downloading
            for (FileRequest fr : wishList) {
                threadPool.submit((Runnable) () -> {
                    while (!fullyDownloaded(fr)) {
                        processFileRequest(fr, trackerAddr);
                    }
                });
            }
            // start notifying tracker
            updater = new Updater(trackerAddr);

            running = true;
        }
    }

    public void stop() throws IOException, InterruptedException {
        synchronized (this) {
            if (!running) {
                return;
            }
            clientsServer.stop();
            updater.stop();
            threadPool.shutdown();
        }
    }

    private boolean fullyDownloaded(FileRequest fr) {
        for(byte[] part: ownedFiles.get(fr.getId()).getContents()) {
            if(part == null) {
                return false;
            }
        }
        return true;
    }

    private void processFileRequest(FileRequest fr, String trackerAddr) {
        try (
                Socket client = new Socket(trackerAddr, Tracker.PORT);
                DataInputStream input = new DataInputStream(client.getInputStream());
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
        )   {
            int fileId = fr.getId();

            output.writeByte(Tracker.SOURCES);
            output.writeInt(fileId);
            output.flush();

            int count = input.readInt();
            for(int i = 0; i < count; i++) {
                byte[] addr = new byte[4];
                short port;
                if(input.read(addr) != 4) {
                    LOG.warning("Wrong addr format in sources request");
                    return;
                }
                port = input.readShort();
                tryToGet(fileId, InetAddress.getByAddress(addr).toString(), port);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryToGet(int id, String hostAddr, short port) {
        Socket client;
        DataInputStream input;
        DataOutputStream output;
        try {
            client = new Socket(hostAddr, port);
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());

            output.writeByte(STAT);
            output.writeInt(id);
            output.flush();

            int count = input.readInt();
            synchronized(this) {
                for (int i = 0; i < count; i++) {
                    int partId = input.readInt();
                    if (ownedFiles.get(id).getContents()[partId] == null) {
                        ownedFiles.get(id).getContents()[partId] = new byte[0];
                        threadPool.submit((Runnable) () -> {
                            try {
                                ownedFiles.get(id).getContents()[partId] = getPart(id, partId, input, output);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getPart(int id, int partId, DataInputStream input, DataOutputStream output) throws IOException {
        output.writeByte(GET);
        output.writeInt(id);
        output.writeInt(partId);
        output.flush();

        byte[] result = new byte[PART_SIZE];
        if(input.read(result) == -1) {
            return null;
        }
        return result;
    }
}
