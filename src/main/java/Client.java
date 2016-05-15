import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Client {

    // constants
    public static final byte STAT = 1;
    public static final byte GET = 2;
    public static final String CONFIG_FILE = "/configClient";
    public static final String CURRENT_DIR = "./";
    public static final int IP_LEN = 4;
    public static final int TIMEOUT = 3 * Tracker.TIMEOUT / 4;

    private static final Logger logger = Logger.getLogger("CLIENT");
    // thread pool
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ClientsServer clientsServer = null;
    private Updater updater = null;
    private Boolean running = false;

    public Client() {}

    public static void main(String[] args) throws IOException {
        Client inner = new Client();
        ClientState state = new ClientState(CURRENT_DIR + CONFIG_FILE);

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
                inner.run(args[1], state);
                break;
        }
        state.store();
    }

//    public byte[][] getFileContents(int id, ClientState state) {
//        FileContents tmp = state.getOwnedFiles().get(id);
//        if (tmp == null) {
//            return null;
//        }
//        return tmp.getContents();
//    }

    // run implementation
    public void run(String trackerAddr, ClientState state) throws IOException {
        synchronized (this) {
            // start sharing
            clientsServer = new ClientsServer();
            clientsServer.start();
            // start downloading
            for (FileRequest fr : state.getWishList()) {
                threadPool.submit((Runnable) () -> {
                    while (!fullyDownloaded(fr, state)) {
                        processFileRequest(fr, trackerAddr, state);
                    }
                });
            }
            // start notifying tracker
            updater = new Updater(trackerAddr, state);

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

    private boolean fullyDownloaded(FileRequest fr, ClientState state) {
        if (state.getOwnedFiles() == null || state.getOwnedFiles().size() == 0) {
            return true;
        }
        FileContents fc = state.getOwnedFiles().get(fr.getId());
        if (fc == null) {
            return false;
        }
//        for (byte[] part : fc.getContents()) {
//            if (part == null) {
//                return false;
//            }
//        }
        for (int i = 0; i < fc.getContentsSize(); i++) {
            if(!fc.isPartDownloaded(i)) {
                return false;
            }
        }
        return true;
    }

    private void processFileRequest(FileRequest fr, String trackerAddr, ClientState state) {
        Utils.tryConnectWithResourcesAndDoJob(trackerAddr, (input, output) -> {
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
                    tryToGet(fileId, InetAddress.getByAddress(addr).toString(), port, state);
                    logger.info("Downloading: proceeding to the next in the SOURCES");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void tryToGet(int id, String hostAddr, int port, ClientState clientState) {
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
                    if (!clientState.getOwnedFiles().get(id).isPartDownloaded(partId)) {
                        logger.info("In tryToGet: matched a part");
                        getPart(id, partId, input, output, clientState.getOwnedFiles().get(id));
//                        clientState.getOwnedFiles().get(id).getContents()[partId] = new byte[0];
//                        threadPool.submit((Runnable) () -> {
//                            try {
//                                clientState.getOwnedFiles().get(id).getContents()[partId] = getPart(id, partId, input, output);
//                                client.close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                                throw new RuntimeException(e);
//                            }
//                        });
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private byte[] getPart(
            int id, int partId, DataInputStream input, DataOutputStream output, FileContents fc
    ) throws IOException {
        output.writeByte(GET);
        output.writeInt(id);
        output.writeInt(partId);
        output.flush();

        logger.info("Getting the part");

        fc.writePart(partId, input);
//
//        byte[] result = new byte[PART_SIZE];
//        if (input.read(result) == -1) {
//            return null;
//        }
//        return result;
    }

}
