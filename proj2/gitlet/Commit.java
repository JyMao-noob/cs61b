package gitlet;


import com.sun.source.tree.Tree;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Repository.OBJECTS_DIR;

/**
 * Represents a gitlet commit object.
 *
 * @author JiayiMao
 */
public class Commit implements Serializable {
    private String id;
    private String parent;
    private String secondParent;
    private String timeStamp;
    private TreeMap<String, String> blobMap;   // key: blob name, value: blob id
    private String message;

    public Commit(String message) {
        this.parent = "";
        this.secondParent = "";
        this.timeStamp = dateToTimeStamp(new Date(0));
        this.blobMap = new TreeMap<>();
        this.message = message;
        this.id = generateId();
    }

    public Commit(String parent, String secondParent, TreeMap<String, String> blobMap, String message) {
        this.parent = parent;
        this.secondParent = secondParent == null ? "" : secondParent;
        this.timeStamp = dateToTimeStamp(new Date());
        this.blobMap = blobMap;
        this.message = message;
        this.id = generateId();
    }

    private String generateId() {
        return Utils.sha1(parent, secondParent, timeStamp, message, blobMap.toString());
    }

    private String dateToTimeStamp(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return dateFormat.format(date);
    }

    public String getId() {
        return id;
    }

    public String getParent() {
        return parent;
    }

    public String getSecondParent() {
        return secondParent;
    }

    public boolean hasSecondParent() {
        return !secondParent.equals("");
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public TreeMap<String, String> getBlobMap() {
        return blobMap;
    }

    public String getMessage() {
        return message;
    }

    public void save() {
        File file = Utils.join(OBJECTS_DIR, id);
        Utils.writeObject(file, this);
    }

    public boolean exitsBlob(Blob blob) {
        return blobMap.values().contains(blob.getId());
    }

    public List<String> getBlobNames() {
        List<String> blobNames = new ArrayList<>();
        blobNames.addAll(blobMap.keySet());
        return blobNames;
    }

    public Blob getBlobByName(String fileName) {
        String blobId = blobMap.get(fileName);
        return Utils.readObject(Utils.join(OBJECTS_DIR, blobId), Blob.class);
    }

    public boolean hasParent() {
        return !parent.equals("");
    }
}
