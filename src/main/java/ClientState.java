import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientState {
    // list of files we need to download
    private final List<FileRequest> wishList = new ArrayList<>();
    // list of files we have
    private final HashMap<Integer, FileContents> ownedFiles = new HashMap<>();


    public ClientState(String dirPath) {
        try (DataInputStream src = new DataInputStream(new FileInputStream(dirPath))) {
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

    public List<FileRequest> getWishList() {
        return wishList;
    }

    public HashMap<Integer, FileContents> getOwnedFiles() {
        return ownedFiles;
    }

    public void store(/*ClientState clientState*/) {
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(Client.CURRENT_DIR
                + Client.CONFIG_FILE))) {
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