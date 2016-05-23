package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class TorrentClientMain {

    // constants
    public static final byte STAT = 1;
    public static final byte GET = 2;
    public static final String CONFIG_FILE = "/configClient";
    public static final String CURRENT_DIR = "./";
    public static final int IP_LEN = 4;
    public static final int TIMEOUT = 3 * TorrentTrackerMain.TIMEOUT / 4;

    private static final Logger LOGGER = Logger.getLogger("CLIENT");
    // thread pool
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ClientsServer clientsServer = null;
    private Updater updater = null;
    private Boolean running = false;
    private ClientState state = null;

    public TorrentClientMain(String path) {
        state = path == null ? new ClientState(CURRENT_DIR/) : new ClientState(path);
    }

    public static void main(String[] args) throws IOException {
        TorrentClientMain inner = new TorrentClientMain(CURRENT_DIR);
        //ClientState state = new ClientState(CURRENT_DIR + CONFIG_FILE);

        switch (args[0]) {
            case "list":
                ClientConsoleUtils.list(args[1]);
                break;
            case "get":
                ClientConsoleUtils.get(args[1], args[2], inner.getState());
                break;
            case "newfile":
                ClientConsoleUtils.newfile(args[1], args[2], inner.getState());
                break;
            case "run":
                inner.run(args[1]);
                break;
            default:
                throw new UnsupportedOperationException("No such console command");
        }
        inner.state.store();
    }

    public ClientState getState() {
        return state;
    }

    public FileContents getFileById(int id) {
        return state.getOwnedFiles().get(id);
    }

    // run implementation
    public void run(String trackerAddr) throws IOException {
        synchronized (this) {
            // start sharing
            clientsServer = new ClientsServer();
            clientsServer.start();
            // start downloading
            for (FileRequest fr : state.getWishList()) {
                threadPool.submit((Runnable) () -> {
                    while (!fullyDownloaded(fr)) {
                        processFileRequest(fr, trackerAddr);
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
            state.store();
        }
    }

    private boolean fullyDownloaded(FileRequest fr) {
        if (state.getOwnedFiles() == null || state.getOwnedFiles().size() == 0) {
            return true;
        }
        FileContents fc = state.getOwnedFiles().get(fr.getId());
        if (fc == null) {
            return false;
        }
        for (int i = 0; i < fc.getContentsSize(); i++) {
            if (!fc.isPartDownloaded(i)) {
                return false;
            }
        }
        return true;
    }

    private void processFileRequest(FileRequest fr, String trackerAddr) {
        Utils.tryConnectWithResourcesAndDoJob(trackerAddr, (input, output) -> {
            try {
                int fileId = fr.getId();

                output.writeByte(TorrentTrackerMain.SOURCES);
                output.writeInt(fileId);
                output.flush();

                int count = input.readInt();
                for (int i = 0; i < count; i++) {
                    byte[] addr = new byte[IP_LEN];
                    int port;
                    if (input.read(addr) != IP_LEN) {
                        LOGGER.warning("Wrong addr format in sources request");
                        return;
                    }
                    port = input.readInt();
                    LOGGER.info("Downloading: try to get the file parts");
                    tryToGet(fileId, InetAddress.getByAddress(addr).toString(), port);
                    LOGGER.info("Downloading: proceeding to the next in the SOURCES");
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
            LOGGER.info("In tryToGet: count=" + Integer.toString(count));
            synchronized (this) {
                for (int i = 0; i < count; i++) {
                    int partId = input.readInt();
                    if (!state.getOwnedFiles().get(id).isPartDownloaded(partId)) {
                        LOGGER.info("In tryToGet: matched a part");
                        getPart(id, partId, input, output, state.getOwnedFiles().get(id));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void getPart(
            int id, int partId, DataInputStream input, DataOutputStream output, FileContents fc
    ) throws IOException {
        output.writeByte(GET);
        output.writeInt(id);
        output.writeInt(partId);
        output.flush();

        LOGGER.info("Getting the part");

        fc.writePart(partId, input);
    }

}
