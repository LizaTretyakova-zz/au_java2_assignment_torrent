import java.util.ArrayList;
import java.util.HashMap;

public class ClientState {
    // list of files we need to download
    private final ArrayList<FileRequest> wishList = new ArrayList<>();
    // list of files we have
    private final HashMap<Integer, FileContents> ownedFiles = new HashMap<>();


    public ClientState() {}

    public ArrayList<FileRequest> getWishList() {
        return wishList;
    }

    public HashMap<Integer, FileContents> getOwnedFiles() {
        return ownedFiles;
    }
}