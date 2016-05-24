package ru.spbau.mit;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class TorrentTrackerMain {

    // constants
    public static final byte LIST = 1;
    public static final byte UPLOAD = 2;
    public static final byte SOURCES = 3;
    public static final byte UPDATE = 4;
    public static final byte OTHER = 5;
    public static final Integer PORT = 8081;
    public static final int TIMEOUT = 60 * 1000;
    public static final String CONFIG_FILE = "configTracker";
    public static final String CURRENT_DIR = ".";

    private static final Logger LOGGER = Logger.getLogger("TRACKER");
    private TrackerState state;
    private ServerSocket serverSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();


    public TorrentTrackerMain(String path) {
        state = new TrackerState(path);
    }

    public static void main(String[] args) throws IOException {
        new TorrentTrackerMain(Paths.get(CURRENT_DIR, CONFIG_FILE).toString()).startTracker();
    }

    // wake up tracker
    public void startTracker() throws IOException {
        DataOutputStream backup = new DataOutputStream(new FileOutputStream(CONFIG_FILE));
        serverSocket = new ServerSocket(PORT);
        LOGGER.info(serverSocket.getLocalSocketAddress().toString());
        LOGGER.info(serverSocket.getInetAddress().getCanonicalHostName());
        LOGGER.info(serverSocket.getInetAddress().getHostAddress());
        LOGGER.info(serverSocket.getInetAddress().getHostName());
        LOGGER.info(serverSocket.getInetAddress().toString());
        threadPool.submit((Runnable) () -> {
            while (!Thread.interrupted()) {
                LOGGER.info("New loop");
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("ru.spbau.mit.TorrentClientMain accepted: " + clientSocket.toString());
                    threadPool.submit((Runnable) () -> {
                        try {
                            processClient(clientSocket);
                            LOGGER.info("CLIENT PROCESSED");
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
                state.store(backup);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }


    // tracker part

    // processing of client have come
    private void processClient(Socket clientSocket) throws IOException {
        Utils.tryAndDoJob(clientSocket, (input, output) -> {
            try {
                switch (input.readByte()) {
                    case LIST:
                        LOGGER.warning("enter TORRENT_TRACKER_MAIN" + "list port=" + clientSocket.getPort());
                        TrackerUtils.executeList(output, state);
                        LOGGER.warning("exit TORRENT_TRACKER_MAIN" + "list");
                        break;
                    case UPLOAD:
                        LOGGER.warning("enter TORRENT_TRACKER_MAIN" + "upload port=" + clientSocket.getPort());
                        TrackerUtils.executeUpload(clientSocket, input, output, state);
                        LOGGER.warning("exit TORRENT_TRACKER_MAIN" + "sources");
                        break;
                    case SOURCES:
                        LOGGER.warning("enter TORRENT_TRACKER_MAIN" + "sources port=" + clientSocket.getPort());
                        TrackerUtils.executeSources(input, output, state);
                        LOGGER.warning("exit TORRENT_TRACKER_MAIN" + "sources");
                        break;
                    case UPDATE:
                        LOGGER.warning("enter TORRENT_TRACKER_MAIN update port=" + clientSocket.getPort());
                        TrackerUtils.executeUpdate(clientSocket, input, output, state);
                        LOGGER.warning("exit TORRENT_TRACKER_MAIN update");
                        break;
                    default:
                        LOGGER.warning("enter TORRENT_TRACKER_MAIN default port=" + clientSocket.getPort());
                        int id = input.readInt();
                        for (FileDescr file : state.getFiles()) {
                            if (file.getId() == id) {
                                long size = file.getSize();
                                output.writeLong(size);
                                output.writeUTF(file.getName());
                                break;
                            }
                        }
                        output.flush();
                        LOGGER.warning("exit TORRENT_TRACKER_MAIN default");
                        break;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // shut down
    public void stopTracker() throws IOException {
        serverSocket.close();
        threadPool.shutdown();
        state.store(new DataOutputStream(new FileOutputStream(CONFIG_FILE)));
/*
 Мы хотим записать список всех файлов, которые когда-либо регистрировались, в файл. Так что создаём поток
  */
    }
}
