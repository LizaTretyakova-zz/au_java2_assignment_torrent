package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// file description with contents splitted into byte arrays
public class FileContents {
    public static final int PART_SIZE = 1024;
    private String path;
    private boolean[] parts;
    private RandomAccessFile ra;

    public FileContents(String path, long size) throws IOException {
        this.path = path;
        parts = new boolean[(int) ((size + PART_SIZE - 1) / PART_SIZE)];
        Files.createDirectories(Paths.get(path).getParent());
        ra = new RandomAccessFile(path, "rw");
    }

    public String getPath() {
        return path;
    }

    public long getContentsSize() {
        return parts.length;
    }

    public synchronized List<Integer> getAvailable() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i]) {
                result.add(i);
            }
        }
        return result;
    }

    public synchronized void writePart(int index, DataInputStream src) throws IOException {
        if (parts[index]) {
            return;
        }

        byte[] buffer = new byte[PART_SIZE];

        if (src.read(buffer) > 0) {
            ra.seek(index * PART_SIZE);
            ra.write(buffer);
            parts[index] = true;
        }
    }

    public synchronized void readPart(int index, DataOutputStream output) throws IOException {
        if (!parts[index]) {
            return;
        }

        byte[] buffer = new byte[PART_SIZE];

        ra.seek(index * PART_SIZE);
        ra.read(buffer);
        output.write(buffer);
        output.flush();
    }

    public synchronized boolean isPartDownloaded(int index) {
        return parts[index];
    }
}
