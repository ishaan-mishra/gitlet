package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;

import static gitlet.Main.COMMITS;
import static gitlet.Main.STAGED;
import static gitlet.Main.clearDir;
import static gitlet.Main.CWD;
import static gitlet.Main.STAGEDRM;
import static gitlet.Main.headCommit;
import static gitlet.Main.BLOBS;
import static gitlet.Utils.serialize;
import static gitlet.Utils.sha1;
import static gitlet.Utils.error;
import static gitlet.Utils.plainFilenamesIn;
import static gitlet.Utils.join;
import static gitlet.Utils.readObject;
import static gitlet.Utils.writeObject;
import static gitlet.Utils.writeContents;
import static gitlet.Utils.restrictedDelete;

/** Class that handles all functions related to commits in gitlet.
 *  @author Ishaan Mauli Mishra
 */
public class Commit implements Serializable {

    /**
     * Makes a commit.
     * @param message message with which this commit was made.
     * @param parent hash value of this commit's parent.
     */
    public Commit(String message, Commit parent) throws IOException {
        this(message, parent, null);
    }

    /**
     * Makes a commit.
     * @param message message with which this commit was made.
     * @param parent hash value of this commit's parent.
     * @param mergeParent hash value of this commit's second parent.
     */
    public Commit(String message, Commit parent, Commit mergeParent)
            throws IOException {
        this._message = message;
        _allFiles = new HashMap<>();
        if (parent == null && message.equals("initial commit")) {
            this._parent = null;
            _timestamp = new Date(0);
        } else {
            this._parent = sha1(serialize(parent));
            _timestamp = new Date();
            copyParentFiles();
        }
        if (mergeParent != null) {
            this._mergeParent = sha1(serialize(mergeParent));
        }
        commitFiles();
        storeCommit();
    }

    /**
     * Prints all commits ever in this repository.
     */
    public static void globalLog() {
        for (Commit commit : allCommits()) {
            System.out.println(commit.toString());
        }
    }

    /**
     * Finds and prints the hash value of all commits with MESSAGE.
     * @param message given message.
     */
    public static void find(String message) {
        boolean found = false;
        for (Commit commit : allCommits()) {
            if (commit._message.equals(message)) {
                found = true;
                System.out.println(sha1(serialize(commit)));
            }
        }
        if (!found) {
            throw error("Found no commit with that message.");
        }
    }

    /**
     * Gets a list of all Commits ever made in this repository in lexicographic
     * order.
     * @return list of all commits.
     */
    public static List<Commit> allCommits() {
        List<String> allCommitFiles = plainFilenamesIn(COMMITS);
        List<Commit> allCommits = new ArrayList<Commit>();
        for (String commitName : allCommitFiles) {
            allCommits.add(readObject(join(COMMITS, commitName),
                    Commit.class));
        }
        return allCommits;
    }

    /**
     * Copies all files from my parent commit.
     */
    private void copyParentFiles() {
        Commit parent = getParent();
        HashMap<String, String> parentfiles = parent._allFiles;
        for (String file : parentfiles.keySet()) {
            _allFiles.put(file, parentfiles.get(file));
        }
    }

    /**
     * Tracks thhe files staged for commit and removes the files staged for
     * removal.
     */
    private void commitFiles() {
        List<String> stagedFiles = plainFilenamesIn(STAGED);
        List<String> stagedrmFiles = plainFilenamesIn(STAGEDRM);
        if (stagedFiles.size() == 0 && stagedrmFiles.size() == 0
                && _parent != null) {
            throw error("No changes added to the commit.");
        }
        for (String name : stagedFiles) {
            Blob blob = readObject(join(STAGED, name), Blob.class);
            _allFiles.put(blob.getName(), sha1(serialize(blob)));
            join(STAGED, name).delete();
        }
        for (String name : stagedrmFiles) {
            _allFiles.remove(name);
            join(STAGEDRM, name).delete();
        }
    }

    /**
     * Stores this commit in a file in the .gitlet/commits directory.
     */
    private void storeCommit() throws IOException {
        File commitFile = join(COMMITS, sha1(serialize(this)) + ".txt");
        commitFile.createNewFile();
        writeObject(commitFile, this);
    }

    /**
     * Checks out a file from THIS commit.
     * @param fileName file to be checked out.
     */
    public void checkout(String fileName) {
        if (!_allFiles.containsKey(fileName)) {
            throw error("File does not exist in that commit.");
        }
        restrictedDelete(fileName);
        Blob replace = getBlob(fileName);
        File checkedOut = join(CWD, fileName);
        writeContents(checkedOut, replace.getFileContents());
    }

    /**
     * Checks out all files from this commit.
     */
    public void checkout() {
        for (String file : _allFiles.keySet()) {
            if (join(CWD, file).exists()
                    && !headCommit()._allFiles.containsKey(file)) {
                throw error("There is an untracked file in the way; delete "
                        + "it, or add and commit it first.");
            }
            checkout(file);
        }
        for (String file : headCommit()._allFiles.keySet()) {
            if (!_allFiles.containsKey(file)) {
                join(CWD, file).delete();
            }
        }
        clearDir(STAGED);
        clearDir(STAGEDRM);
    }

    /**
     * Gets the blob from this commit with this file name.
     * @param fileName file whose blob is to be returned.
     * @return Blob of this file in this commit.
     */
    public Blob getBlob(String fileName) {
        String blobName = _allFiles.get(fileName);
        File blobFile = join(BLOBS, blobName + ".txt");
        return readObject(blobFile, Blob.class);
    }

    /**
     * Prints this commit and all its ancestors.
     */
    public void log() {
        System.out.println(toString());
        if (_parent != null) {
            Commit parent = getParent();
            parent.log();
        }
    }

    /**
     * Returns a string representation of this commit's timestamp.
     * @return String representation of this commit's timestamp.
     */
    public String getTimeStamp() {
        SimpleDateFormat fmt
                = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        return fmt.format(_timestamp);
    }

    /**
     * Returns my parent Commit.
     * @return my parent Commit.
     */
    public Commit getParent() {
        if (_parent == null) {
            return null;
        }
        File parentFile = join(COMMITS, _parent + ".txt");
        return readObject(parentFile, Commit.class);
    }

    @Override
    public String toString() {
        String commit = "commit " + sha1(serialize(this));
        String merge = "";
        if (_mergeParent != null) {
            merge = "\nMerge: " + _parent.substring(0, 7) +  " "
                    + _mergeParent.substring(0, 7);
        }
        String date = "Date: " + getTimeStamp();
        String message = this._message;
        return "===\n" + commit + merge + "\n" + date + "\n" + message + "\n";
    }

    /**
     * Gets the hash value of this commit's first parent.
     * @return hash value of my first parent.
     */
    public String getParentHash() {
        return _parent;
    }

    /**
     * Gets the hash value of this commit's second parent. Returns null if this
     * commit has no second parent.
     * @return hash value of my second parent. Null if this is not a merge
     * commit.
     */
    public String getMergeParentHash() {
        return _mergeParent;
    }

    /**
     * Get a map of all files in this commit.
     * @return a map of all files in this commit. It maps names of files to
     * the hash value of their corresponding blob objects.
     */
    public HashMap<String, String> getAllFiles() {
        return _allFiles;
    }

    /** Message of this commit. */
    private String _message;

    /** Timestamp of this commit. */
    private Date _timestamp;

    /** Hash value of this commit's parent. */
    private String _parent;

    /** Hash value of this commit's second parent (if it is a merge commit). */
    private String _mergeParent;

    /** Map of all files in this commit. Maps the name of the files to the hash
     * value of the associated blob.
     */
    private HashMap<String, String> _allFiles;

}
