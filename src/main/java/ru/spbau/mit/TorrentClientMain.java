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
    public static final String CONFIG_FILE = "configClient";
    public static final String CURRENT_DIR = ".";
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
        state = path == null ? new ClientState(CURRENT_DIR) : new ClientState(path);
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
        LOGGER.warning("enter TORRENT_CLIENT_MAIN" + "run");
        synchronized (this) {
            LOGGER.warning("TORRENT_CLIENT_MAIN: start sharing");
            // start sharing

            clientsServer = new ClientsServer();
            clientsServer.start();

            LOGGER.warning("TORRENT_CLIENT_MAIN: start downloading");
            // start downloading
            for (FileRequest fr : state.getWishList()) {
                threadPool.submit((Runnable) () -> {
                    LOGGER.warning("enter TORRENT_CLIENT_MAIN lambda" + "threadPool_task");

                    while (!fullyDownloaded(fr)) {
                        LOGGER.config("TORRENT_CLIENT_MAIN: still not fully downloaded");

                        processFileRequest(fr, trackerAddr);
                    }

                    LOGGER.warning("exit TORRENT_CLIENT_MAIN lambda" + "threadPool_task");
                });
            }

            LOGGER.warning("TORRENT_CLIENT_MAIN: start notifying tracker");
            // start notifying tracker
            updater = new Updater(trackerAddr, state);

            running = true;
        }
        LOGGER.warning("exit TORRENT_CLIENT_MAIN" + "run");
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
        LOGGER.warning("enter TORRENT_CLIENT_MAIN" + "processFileRequest");

        Utils.tryConnectWithResourcesAndDoJob(trackerAddr, (input, output) -> {
            try {
                LOGGER.warning("TORRENT_CLIENT_MAIN: try to get file");

                int fileId = fr.getId();
                LOGGER.warning("TORRENT_CLIENT_MAIN: fileId=" + Integer.toString(fileId));

                output.writeByte(TorrentTrackerMain.SOURCES);
                output.writeInt(fileId);
                output.flush();
                LOGGER.warning("TORRENT_CLIENT_MAIN: SOURCES code and fileId outputted to tracker");

                int count = input.readInt();
                for (int i = 0; i < count; i++) {
                    byte[] addr = new byte[IP_LEN];
                    int port;
                    if (input.read(addr) != IP_LEN) {
                        LOGGER.warning("Wrong addr format in sources request");
                        return;
                    }
                    LOGGER.warning("TORRENT_CLIENT_MAIN got addr: " + InetAddress.getByAddress(addr).getHostAddress());
                    port = input.readUnsignedShort();
                    LOGGER.warning("TORRENT_CLIENT_MAIN: port=" + Integer.toString(port));
                    LOGGER.info("Downloading: try to get the file parts");
                    tryToGet(fileId, InetAddress.getByAddress(addr).getHostAddress(), port);
                    LOGGER.info("Downloading: proceeding to the next in the SOURCES");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.warning("exit TORRENT_CLIENT_MAIN" + "processFileRequest");
    }

    private void tryToGet(int id, String hostAddr, int port) {
        LOGGER.warning("enter TORRENT_CLIENT_MAIN" + "tryToGet");

        Socket client;
        DataInputStream input;
        DataOutputStream output;
        try {
            LOGGER.warning("TRY_TO_GET: trying to open socket & Co");
            client = new Socket(hostAddr, port);
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());

            LOGGER.warning("TRY_TO_GET: trying to write STAT code and id to tracker");
            output.writeByte(STAT);
            output.writeInt(id);
            output.flush();

            LOGGER.warning("TRY_TO_GET: after outputting trying to read");
            int count = input.readInt();
            LOGGER.info("In tryToGet: count=" + Integer.toString(count));

            synchronized (this) {
                LOGGER.warning("enter TRY_TO_GET" + "for loop looking for the missing parts");
                for (int i = 0; i < count; i++) {
                    int partId = input.readInt();
                    if (!state.getOwnedFiles().get(id).isPartDownloaded(partId)) {
                        LOGGER.info("TRY_TO_GET: matched a part!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        getPart(id, partId, input, output, state.getOwnedFiles().get(id));
                    }
                }
                LOGGER.warning("exit TRY_TO_GET" + "for loop looking for the missing parts");
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.warning("TRY_TO_GET exception");
            throw new RuntimeException(e);
        }

        LOGGER.warning("exit TORRENT_CLIENT_MAIN" + "tryToGet");
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
