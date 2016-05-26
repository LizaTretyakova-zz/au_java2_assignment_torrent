package ru.spbau.mit;

// a description of a file we will download in a time: id & tracker address
public class FileRequest {
    private int id;

    public FileRequest(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
