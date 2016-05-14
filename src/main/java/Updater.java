import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by liza on 14.05.16.
 */ // remind tracker about our existence
class Updater {
    private Client client;
    private ScheduledExecutorService executorService = null;
    private Socket client;
    private DataInputStream input;
    private DataOutputStream output;

    Updater(Client client, String trackerAddr) {
        this.client = client;
        executorService = Executors.newSingleThreadScheduledExecutor();

        try {
            client = new Socket(trackerAddr, Tracker.PORT);
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());

            // TODO: java.util.Timer
            executorService.scheduleAtFixedRate(() -> {
                try {
                    output.writeByte(Tracker.UPDATE);
                    output.writeInt((int) client.getPort());
                    output.writeInt(client.ownedFiles.size());
                    for (Map.Entry entry : client.ownedFiles.entrySet()) {
                        output.writeInt((int) entry.getKey());
                    }
                    output.flush();
                    boolean succeed = input.readBoolean();

                    Client.logger.info("Update success: " + Boolean.toString(succeed));
                } catch (IOException e) {
                    // TODO:
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }, 0, Client.TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        executorService.shutdown();
    }
}
