package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * @author: jiayi mao
 * @create: 2024-01-26 15:24
 */
public class Stage implements Serializable {
    private TreeMap<String, String> blobMap;   // key: blob name, value: blob id

    public Stage() {
        blobMap = new TreeMap<>();
    }

    public void saveTo(File file) {
        Utils.writeObject(file, this);
    }

    public boolean exitsBlob(Blob blob) {
        return blobMap.values().contains(blob.getId());
    }

    public void add(Blob blob) {
        blobMap.put(blob.getFileName(), blob.getId());
    }

    public void delete(Blob blob) {
        blobMap.remove(blob.getFileName());
    }

    public void delete(String fileName) {
        blobMap.remove(fileName);
    }

    // has the same name, but different content
    public boolean exitsOldVersionOfBlob(Blob blob) {
        return blobMap.keySet().contains(blob.getFileName());
    }

    public TreeMap<String, String> getBlobMap() {
        return blobMap;
    }

    public boolean isEmpty() {
        return blobMap.isEmpty();
    }

    public void clear() {
        blobMap.clear();
    }

    public List<String> getBlobNames() {
        List<String> blobNames = new ArrayList<>();
        blobNames.addAll(blobMap.keySet());
        return blobNames;
    }
}
