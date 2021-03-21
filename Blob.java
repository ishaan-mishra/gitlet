package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import static gitlet.Utils.serialize;
import static gitlet.Utils.sha1;
import static gitlet.Utils.error;
import static gitlet.Utils.join;
import static gitlet.Utils.writeObject;
import static gitlet.Utils.readContentsAsString;
import static gitlet.Main.CWD;
import static gitlet.Main.BLOBS;

/** Class that handles all functions related to blobs in gitlet.
 *  @author Ishaan Mauli Mishra
 */
public class Blob implements Serializable {

    /** Represents a file and its contents.
     *
     * @param fileName name of the file in the CWD whose blob object is being
     *                 made.
     */
    public Blob(String fileName) throws IOException {
        this(join(CWD, fileName));
    }

    /** Represents a file and its commits.
     *
     * @param file file whose blob object is being made.
     */
    public Blob(File file) throws IOException {
        if (!file.exists()) {
            throw error("File does not exist.");
        }
        _name = file.getName();
        _fileContents = readContentsAsString(file);
        storeBlob();
    }

    /** Stores the blob in a file in .gitlet/blobs directory. */
    private void storeBlob() throws IOException {
        File blobFile = join(BLOBS, sha1(serialize(this)) + ".txt");
        blobFile.createNewFile();
        writeObject(blobFile, this);
    }

    /**
     * Returns the name of the file of this blob.
     * @return name of file of this blob.
     */
    public String getName() {
        return _name;
    }

    /** Gets the contents of the file of this blob.
     *
     * @return the file contents of this blob.
     */
    public String getFileContents() {
        return _fileContents;
    }

    /** Name of the file of this blob. */
    private String _name;

    /** Contents of the file of this blob. */
    private String _fileContents;

}
