package ru.spbau.mit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ClientState {

    private static final Logger LOGGER = Logger.getLogger("ClientState");
    // list of files we need to download
    private final List<FileRequest> wishList = new ArrayList<>();
    // list of files we have
    private final HashMap<Integer, FileContents> ownedFiles = new HashMap<>();
    private String downloadsPath;
    private String path;

    public ClientState(String dirPath) {
        path = dirPath;
        downloadsPath = Paths.get(dirPath, "downloads").toString();
        try {
            Files.createDirectories(Paths.get(downloadsPath));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        String configPath = Paths.get(dirPath, TorrentClientMain.CONFIG_FILE).toString();
        if (!Files.exists(Paths.get(configPath))) {
            LOGGER.info("ru.spbau.mit.TorrentClientMain's starting from scrat ch");
            return;
        }

        try (DataInputStream src = new DataInputStream(new FileInputStream(configPath))) {
            int wishListSize = src.readInt();
            for (int i = 0; i < wishListSize; i++) {
                int id = src.readInt();
                wishList.add(new FileRequest(id));
            }

            int ownedFilesSize = src.readInt();
            for (int i = 0; i < ownedFilesSize; i++) {
                String path = src.readUTF();
                long size = src.readLong();
                int id = src.readInt();
                ownedFiles.put(id, new FileContents(path, size));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String getDownloadsPath() {
        return downloadsPath;
    }

    public synchronized List<FileRequest> getWishList() {
        return wishList;
    }

    public synchronized HashMap<Integer, FileContents> getOwnedFiles() {
        return ownedFiles;
    }

    public synchronized void store() {
        try (
                DataOutputStream output =
                        new DataOutputStream(new FileOutputStream(
                                /*TorrentClientMain.CURRENT_DIR*/
                                Paths.get(path, TorrentClientMain.CONFIG_FILE).toString()
                        ))
        ) {
            output.writeInt(wishList.size());
            for (FileRequest aWishList : wishList) {
                output.writeInt(aWishList.getId());
            }
            output.writeInt(ownedFiles.size());
            for (Map.Entry entry : ownedFiles.entrySet()) {
                String path = ((FileContents) entry.getValue()).getPath();
                output.writeUTF(path);
                output.writeLong(new File(path).length());
                output.writeInt((Integer) entry.getKey());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
