import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class Utils {

    private static final Logger logger = Logger.getLogger("Utils");

    public static void tryConnectWithResourcesAndDoJob(
            String trackerAddr, BiConsumer<DataInputStream, DataOutputStream> job
    ) {
        try (
                Socket client = new Socket(trackerAddr, Tracker.PORT);
                DataInputStream input = new DataInputStream(client.getInputStream());
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
        ) {
            job.accept(input, output);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void tryAndDoJob(Socket socket, BiConsumer<DataInputStream, DataOutputStream> job) {
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            job.accept(input, output);
        } catch (IOException e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e);
        }

    }

}
