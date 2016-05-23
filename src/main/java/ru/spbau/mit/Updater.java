package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

// remind tracker about our existence
class Updater {
    private static final Logger LOGGER = Logger.getLogger("Updater");
    private Timer timer = new Timer();
//    private Socket client;
//    private DataInputStream input;
//    private DataOutputStream output;

    Updater(String trackerAddr, ClientState state) {
//        try {
//
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // cannot use Utils.tryConnectWithResourcesAndDoJob
                    // because the socket is also used inside the function
                    try (
                            Socket client = new Socket(trackerAddr, TorrentTrackerMain.PORT);
                            DataInputStream input = new DataInputStream(client.getInputStream());
                            DataOutputStream output = new DataOutputStream(client.getOutputStream())
                    ) {
//                    Utils.tryConnectWithResourcesAndDoJob(trackerAddr, (input, output) -> {
//                        try {
                        output.writeByte(TorrentTrackerMain.UPDATE);
                        output.writeInt(client.getPort());
                        output.writeInt(state.getOwnedFiles().size());
                        for (Map.Entry entry : state.getOwnedFiles().entrySet()) {
                            output.writeInt((int) entry.getKey());
                        }
                        output.flush();
                        boolean succeed = input.readBoolean();

                        LOGGER.info("Update success: " + Boolean.toString(succeed));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
//                    });
//                    try {
//
//                        output.writeByte(TorrentTrackerMain.UPDATE);
//                        output.writeInt(client.getPort());
//                        output.writeInt(state.getOwnedFiles().size());
//                        for (Map.Entry entry : state.getOwnedFiles().entrySet()) {
//                            output.writeInt((int) entry.getKey());
//                        }
//                        output.flush();
//                        boolean succeed = input.readBoolean();
//
//                        LOGGER.info("Update success: " + Boolean.toString(succeed));
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                        throw new RuntimeException(e);
//                    }
                }
            }, 0, TorrentClientMain.TIMEOUT);

//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
    }

    public void stop() {
        timer.cancel();
    }
}
