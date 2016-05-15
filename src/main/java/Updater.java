import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

// remind tracker about our existence
class Updater {
    private static final Logger logger = Logger.getLogger("Updater");
    private Timer timer = new Timer();
    private Socket client;
    private DataInputStream input;
    private DataOutputStream output;

    public Updater(String trackerAddr, ClientState state) {
        try {
            client = new Socket(trackerAddr, Tracker.PORT);
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        output.writeByte(Tracker.UPDATE);
                        output.writeInt(client.getPort());
                        output.writeInt(state.getOwnedFiles().size());
                        for (Map.Entry entry : state.getOwnedFiles().entrySet()) {
                            output.writeInt((int) entry.getKey());
                        }
                        output.flush();
                        boolean succeed = input.readBoolean();

                        logger.info("Update success: " + Boolean.toString(succeed));
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            }, 0, Client.TIMEOUT);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        timer.cancel();
    }
}
