package ru.spbau.mit;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
    public static final String CONFIG_FILE = "./configTracker";

    private static final Logger LOGGER = Logger.getLogger("TRACKER");
    private final TrackerState state = new TrackerState(CONFIG_FILE);
    private ServerSocket serverSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();


    public TorrentTrackerMain() {
    }

    public static void main(String[] args) throws IOException {
        new TorrentTrackerMain().startTracker();
    }

    // wake up tracker
    public void startTracker() throws IOException {
        DataOutputStream backup = new DataOutputStream(new FileOutputStream(CONFIG_FILE));
        serverSocket = new ServerSocket(PORT);
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
                        TrackerUtils.executeList(output, state);
                        break;
                    case UPLOAD:
                        TrackerUtils.executeUpload(clientSocket, input, output, state);
                        break;
                    case SOURCES:
                        TrackerUtils.executeSources(input, output, state);
                        break;
                    case UPDATE:
                        TrackerUtils.executeUpdate(clientSocket, input, output, state);
                        break;
                    default:
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
