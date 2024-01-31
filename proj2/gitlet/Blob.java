package gitlet;

import java.io.File;
import java.io.Serializable;

import static gitlet.Repository.OBJECTS_DIR;

/**
 * @author: jiayi mao
 * @create: 2024-01-25 10:19
 */
public class Blob implements Serializable {
    private String id;
    private byte[] content;
    private File file;

    public Blob(File file) {
        this.file = file;
        this.content = Utils.readContents(file);
        this.id = Utils.sha1(getFileName(), getContent());
    }

    public String getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public File getFile() {
        return file;
    }


    public String getFileName() {
        return file.getName();
    }

    public void save() {
        File file = Utils.join(OBJECTS_DIR, id);
        Utils.writeObject(file, this);
    }
}
