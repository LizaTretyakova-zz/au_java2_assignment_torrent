import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// a server part of client
class ClientsServer {
    private ServerSocket server;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private Runnable processClient = new Runnable() {
        private Socket client;
        private DataInputStream input;
        private DataOutputStream output;

        @Override
        public void run() {
            try {
                client = server.accept();
                input = new DataInputStream(client.getInputStream());
                output = new DataOutputStream(client.getOutputStream());

                byte type = input.readByte();
                if (type == Client.STAT) {
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
                } else if (type == Client.GET) {
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
        Client.logger.info("Starting clientsServer");

        server = new ServerSocket(0);
        threadPool.submit(processClient);

        Client.logger.info("ClientsServer started");
    }

    // stop the client-client server
    public void stop() throws InterruptedException, IOException {
        Client.logger.info("Stopping clientsServer");

        threadPool.shutdown();

        Client.logger.info("ClientsServer stopped");
    }

    // stat request
    public void stat(DataInputStream input, DataOutputStream output) throws IOException {
        int id = input.readInt();
        ArrayList<Integer> available = new ArrayList<>();
        synchronized (this) {
            FileContents fc = ownedFiles.get(id);
            byte[][] contents = fc.getContents();
            for (int i = 0; i < fc.getContentsSize(); i++) {
                if (contents[i] != null) {
                    available.add(i);
                }
            }
        }

        output.writeInt(available.size());
        for (Integer anAvailable : available) {
            output.writeInt(anAvailable);
        }
        output.flush();

        Client.logger.info("Stated");
    }

    // give the file to the other client
    public void get(DataInputStream input, DataOutputStream output) throws IOException {
        int id = input.readInt();
        int partId = input.readInt();

        FileContents fileContents = ownedFiles.get(id);
        if (fileContents == null) {
            output.flush();
            Client.logger.warning("Requested a missing file");
            return;
        }
        byte[] content = fileContents.getContents()[partId];
        if (content == null) {
            output.flush();
            Client.logger.warning("Requested a missing part of a file");
            return;
        }

        output.write(content);
        output.flush();
        Client.logger.info("File contents sent");
    }
}
