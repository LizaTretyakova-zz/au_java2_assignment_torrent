import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Client {

    // constants
    public static final int PART_SIZE = 1024;
    public static final byte STAT = 1;
    public static final byte GET = 2;
    public static final String CONFIG_FILE = "/configClient";
    public static final int IP_LEN = 4;
    public static final int TIMEOUT = 3 * Tracker.TIMEOUT / 4;

    private static final Logger logger = Logger.getLogger("CLIENT");
    // thread pool
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final ClientState clientState = new ClientState();
//    private ClientsServer clientsServer = null;
    private Updater updater = null;
    private Boolean running = false;

    public Client(String dirPath) {

        if(!Files.exists(Paths.get(dirPath))) {
            logger.info("Client's starting from scratch");
            return;
        }

        try (DataInputStream src = new DataInputStream(new FileInputStream(dirPath + CONFIG_FILE))) {
            int wishListSize = src.readInt();
            for (int i = 0; i < wishListSize; i++) {
                int id = src.readInt();
                clientState.getWishList().add(new FileRequest(id));
            }

            int ownedFilesSize = src.readInt();
            for (int i = 0; i < ownedFilesSize; i++) {
                String path = src.readUTF();
                long size = src.readLong();
                int id = src.readInt();
                clientState.getOwnedFiles().put(id, new FileContents(path, size));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        //Client inner = new Client("./");

        switch(args[0]) {
            case "list":
                ClientConsoleUtils.list(args[1]);
                break;
            case "get":
                ClientConsoleUtils.get(args[1], args[2], state);
                break;
            case "newfile":
                ClientConsoleUtils.newfile(args[1], args[2], state);
                break;
            case "run":
                //inner.run(args[1]);
                run(args[1]);
                break;
        }
        inner.store();
    }

    public byte[][] getFileContents(int id) {
        FileContents tmp = clientState.getOwnedFiles().get(id);
        if (tmp == null) {
            return null;
        }
        return tmp.getContents();
    }

    // run implementation
    public static void run(String trackerAddr) throws IOException {
        synchronized (this) {
            // start sharing
            clientsServer = new ClientsServer();
            clientsServer.start();
            // start downloading
            for (FileRequest fr : clientState.getWishList()) {
                threadPool.submit((Runnable) () -> {
                    while (!fullyDownloaded(fr)) {
                        processFileRequest(fr, trackerAddr);
                    }
                });
            }
            // start notifying tracker
            updater = new Updater(this, trackerAddr);

            running = true;
        }
    }

    public void stop() throws IOException, InterruptedException {
        synchronized (this) {
            if (!running) {
                return;
            }
            updater.stop();
            threadPool.shutdown();
            clientsServer.stop();
        }
    }

    private void store() {
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(CONFIG_FILE))) {
            output.writeInt(clientState.getWishList().size());
            for (int i = 0; i < clientState.getWishList().size(); i++) {
                output.writeInt(clientState.getWishList().get(i).getId());
            }
            output.writeInt(clientState.getOwnedFiles().size());
            for (Map.Entry entry : clientState.getOwnedFiles().entrySet()) {
                String path = ((FileContents) entry.getValue()).getPath();
                output.writeUTF(path);
                output.writeLong(new File(path).length());
                output.writeInt((Integer) entry.getKey());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean fullyDownloaded(FileRequest fr) {
        if (clientState.getOwnedFiles() == null || clientState.getOwnedFiles().size() == 0) {
            return true;
        }
        FileContents fc = clientState.getOwnedFiles().get(fr.getId());
        if (fc == null) {
            return false;
        }
        for (byte[] part : fc.getContents()) {
            if (part == null) {
                return false;
            }
        }
        return true;
    }

    private void processFileRequest(FileRequest fr, String trackerAddr) {
        Utils.tryConnectAndDoJob(trackerAddr, (input, output) -> {
            try {
                int fileId = fr.getId();

                output.writeByte(Tracker.SOURCES);
                output.writeInt(fileId);
                output.flush();

                int count = input.readInt();
                for (int i = 0; i < count; i++) {
                    byte[] addr = new byte[IP_LEN];
                    int port;
                    if (input.read(addr) != IP_LEN) {
                        logger.warning("Wrong addr format in sources request");
                        return;
                    }
                    port = input.readInt();
                    logger.info("Downloading: try to get the file parts");
                    tryToGet(fileId, InetAddress.getByAddress(addr).toString(), port);
                    logger.info("Downloading: proceeding to the next in the SOURCES");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void tryToGet(int id, String hostAddr, int port) {
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
            logger.info("In tryToGet: count=" + Integer.toString(count));
            synchronized (this) {
                for (int i = 0; i < count; i++) {
                    int partId = input.readInt();
                    if (clientState.getOwnedFiles().get(id).getContents()[partId] == null) {
                        logger.info("In tryToGet: matched a part");
                        clientState.getOwnedFiles().get(id).getContents()[partId] = new byte[0];
                        threadPool.submit((Runnable) () -> {
                            try {
                                clientState.getOwnedFiles().get(id).getContents()[partId] = getPart(id, partId, input, output);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private byte[] getPart(int id, int partId, DataInputStream input, DataOutputStream output) throws IOException {
        output.writeByte(GET);
        output.writeInt(id);
        output.writeInt(partId);
        output.flush();

        logger.info("Getting the part");

        byte[] result = new byte[PART_SIZE];
        if (input.read(result) == -1) {
            return null;
        }
        return result;
    }

}
