import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.logging.Logger;

public final class ClientConsoleUtils {
    private static final Logger LOGGER = Logger.getLogger("ClientConsoleUtils");

    private ClientConsoleUtils() {
    }

    // list command
    public static void list(String trackerAddr) throws IOException {
        Utils.tryConnectWithResourcesAndDoJob(trackerAddr, (input, output) -> {
            try {
                output.writeByte(Tracker.LIST);
                output.flush();

                LOGGER.info("LIST requested");

                int count = input.readInt();
                LOGGER.info("count: " + Integer.toString(count));

                for (int i = 0; i < count; i++) {
                    int id = input.readInt();
                    String name = input.readUTF();
                    long size = input.readLong();

                    LOGGER.info("id: "
                            + Integer.toString(id)
                            + " name: "
                            + name
                            + " size: "
                            + Long.toString(size)
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // newfile command
    public static int newfile(String trackerAddr, String path, ClientState state) throws IOException {
        Socket client = new Socket(trackerAddr, Tracker.PORT);
        DataInputStream input = new DataInputStream(client.getInputStream());
        DataOutputStream output = new DataOutputStream(client.getOutputStream());

        output.writeByte(Tracker.UPLOAD);
        output.writeUTF(path);
        long size = Files.size(new File(path).toPath());
        output.writeLong(size);
        output.flush();

        LOGGER.info("NEWFILE requested: name=" + path + " size=" + Long.toString(size));

        int id = input.readInt();
        LOGGER.info("id: " + Integer.toString(id));

        FileContents fc = new FileContents(path, size);
        state.getOwnedFiles().put(id, fc);
        LOGGER.info("File uploaded");

        client.close();
        return id;
    }

    // get command
    public static void get(String trackerAddress, String fileId, ClientState state) {
        int id = Integer.parseInt(fileId);
        state.getWishList().add(new FileRequest(id));

        Utils.tryConnectWithResourcesAndDoJob(trackerAddress, (input, output) -> {
            try {
                output.writeByte(Tracker.OTHER);
                output.writeInt(id);
                output.flush();

                long size = input.readLong();
                String path = input.readUTF();
                state.getOwnedFiles().put(Integer.parseInt(fileId), new FileContents(path, size));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
