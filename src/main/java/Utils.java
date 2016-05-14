import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Utils {

    // public static class ConnectToServerStarterPack B)
//    public static class SocketSet {
//        private Socket client;
//        private DataInputStream input;
//        private DataOutputStream output;
//
//        public SocketSet(Socket c, DataInputStream i, DataOutputStream o) {
//            client = c;
//            input = i;
//            output = o;
//        }
//
//        public Socket getClient() {
//            return client;
//        }
//
//        public DataInputStream getInput() {
//            return input;
//        }
//
//        public DataOutputStream getOutput() {
//            return output;
//        }
//    }

//    public static void tryConnectAndDoJob(String trackerAddr, Function<SocketSet, Void> job) {
//        try (
//                Socket client = new Socket(trackerAddr, Tracker.PORT);
//                DataInputStream input = new DataInputStream(client.getInputStream());
//                DataOutputStream output = new DataOutputStream(client.getOutputStream());
//        ) {
//            job.apply(new SocketSet(client, input, output));
//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

    public static void tryConnectAndDoJob(String trackerAddr, BiConsumer<DataInputStream, DataOutputStream> job) {
        try (
                Socket client = new Socket(trackerAddr, Tracker.PORT);
                DataInputStream input = new DataInputStream(client.getInputStream());
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
        ) {
//            job.apply(new SocketSet(client, input, output));
            job.accept(input, output);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
