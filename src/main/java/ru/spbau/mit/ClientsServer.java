package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

// a server part of client
class ClientsServer {
    private static final Logger LOGGER = Logger.getLogger("ClientsServer");
    private ServerSocket server;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private Runnable processClient = new Runnable() {
        private Socket client;
        private DataInputStream input;
        private DataOutputStream output;
        private ClientState state;

        @Override
        public void run() {
            try {
                client = server.accept();
                input = new DataInputStream(client.getInputStream());
                output = new DataOutputStream(client.getOutputStream());
                state = new ClientState(TorrentClientMain.CURRENT_DIR);

                switch (input.readByte()) {
                    case TorrentClientMain.STAT:
                        threadPool.submit((Runnable) () -> {
                            try {
                                stat(input, output, state);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
                        break;
                    case TorrentClientMain.GET:
                        threadPool.submit((Runnable) () -> {
                            try {
                                get(input, output, state);
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
                        break;
                    default:
                        throw new UnsupportedOperationException("No such command code in the protocol");
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    };

    // start listening for incoming connections
    public void start() throws IOException {
        LOGGER.info("Starting clientsServer");

        server = new ServerSocket(0);
        LOGGER.warning("clientsServer socket port=" + Integer.toString(server.getLocalPort()));
        threadPool.submit(processClient);

        LOGGER.info("ClientsServer started");
    }

    // stop the client-client server
    public void stop() throws InterruptedException, IOException {
        LOGGER.info("Stopping clientsServer");

        threadPool.shutdown();

        LOGGER.info("ClientsServer stopped");
    }

    // stat request
    public void stat(DataInputStream input, DataOutputStream output, ClientState state) throws IOException {
        int id = input.readInt();
        List<Integer> available = state.getOwnedFiles().get(id).getAvailable();

        output.writeInt(available.size());
        for (Integer anAvailable : available) {
            output.writeInt(anAvailable);
        }
        output.flush();

        LOGGER.info("Stated");
    }

    // give the file to the other client
    public void get(DataInputStream input, DataOutputStream output, ClientState state) throws IOException {
        int id = input.readInt();
        int partId = input.readInt();

        FileContents fileContents = state.getOwnedFiles().get(id);
        if (fileContents == null) {
            output.flush();
            LOGGER.warning("Requested a missing file");
            return;
        }
//        byte[] content = fileContents.getContents()[partId];
//        if (content == null) {
//            output.flush();
//            LOGGER.warning("Requested a missing part of a file");
//            return;
//        }
//
//        output.write(content);
//        output.flush();

        fileContents.readPart(partId, output);

        LOGGER.info("File contents sent");
    }

    public int getPort() {
        if(server == null) {
            throw new RuntimeException("Accessing server before starting server");
        }
        return server.getLocalPort();
    }
}
