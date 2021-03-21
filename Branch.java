package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;

import static gitlet.Main.COMMITS;
import static gitlet.Main.CWD;
import static gitlet.Main.BLOBS;
import static gitlet.Main.BRANCHES;
import static gitlet.Main.headBranch;
import static gitlet.Main.checkoutBranch;
import static gitlet.Main.add;
import static gitlet.Main.rm;
import static gitlet.Utils.sha1;
import static gitlet.Utils.error;
import static gitlet.Utils.plainFilenamesIn;
import static gitlet.Utils.join;
import static gitlet.Utils.readObject;
import static gitlet.Utils.writeObject;
import static gitlet.Utils.writeContents;
import static gitlet.Utils.message;
import static gitlet.Utils.serialize;

/** Class that handles all functions related to branches in gitlet.
 *  @author Ishaan Mauli Mishra
 */
public class Branch implements Serializable {

    /**
     * Makes a Branch that stores a tree of commits.
     *
     * @param name name of this branch.
     * @param head hash of the head commit of this branch.
     */
    public Branch(String name, String head) throws IOException {
        this._name = name;
        this._head = head;
        _commits = new HashSet<>();
        if (head != null) {
            _commits.addAll(headBranch()._commits);
        }
        storeBranch();
    }

    /**
     * Makes a Branch that stores a tree of commits.
     *
     * @param name        name of this branch.
     * @param otherBranch name of the branch whose commits will be copied.
     * @param head        hash of the head commit of this branch.
     */
    public Branch(String name, Branch otherBranch, String head)
            throws IOException {
        this._name = name;
        this._head = head;
        _commits = new HashSet<String>();
        _commits.addAll(otherBranch._commits);
        String storeName = name.replace("/", "--");
        File branchFile = join(BRANCHES, storeName + ".txt");
        branchFile.createNewFile();
        writeObject(branchFile, this);
    }

    /**
     * Stores this branch in a file in the .gitlet/branches directory.
     */
    void storeBranch() throws IOException {
        File branchFile = join(BRANCHES, _name + ".txt");
        branchFile.createNewFile();
        writeObject(branchFile, this);
    }

    /**
     * Commits to this branch.
     *
     * @param message message of the commit to be made.
     */
    public void commit(String message) throws IOException {
        Commit commit;
        if (_head != null) {
            commit = new Commit(message, headCommit());
        } else {
            commit = new Commit(message, null);
        }
        String commitid = sha1(serialize(commit));
        _head = commitid;
        _commits.add(commitid);
        storeBranch();
    }

    /**
     * Makes a commit that is a merge.
     *
     * @param givenBranch branch which is merged into THIS.
     */
    public void mergeCommit(Branch givenBranch) throws IOException {
        String mergeMessage = "Merged " + givenBranch._name + " into "
                + _name + ".";
        Commit commit = new Commit(mergeMessage, headCommit(),
                givenBranch.headCommit());
        String commitid = sha1(serialize(commit));
        _head = commitid;
        _commits.add(commitid);
        _commits.add(sha1(serialize(givenBranch.headCommit())));
        storeBranch();
    }

    /**
     * Merges GIVENBRANCH into this branch.
     *
     * @param givenBranch branch to be merged into THIS.
     */
    public void merge(Branch givenBranch) throws IOException {
        Commit splitPoint = getSplitPoint(givenBranch);
        Commit givenCommit = givenBranch.headCommit();
        if (sha1(serialize(splitPoint)).equals(_head)) {
            checkoutBranch(givenBranch._name);
            message("Current branch fast-forwarded.");
            System.exit(0);
        } else if (sha1(serialize(splitPoint)).equals(givenBranch._head)) {
            message("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        mergeTraverseGiven(givenCommit, splitPoint);
        mergeTraverseCur(givenCommit, splitPoint);
        mergeCommit(givenBranch);
    }

    /**
     * Implements the part of the merge logic involving traversing through
     * files in the commit of the given branch.
     * @param givenCommit commit of the given branch.
     * @param splitPoint split point of current branch and given branch.
     */
    private void mergeTraverseGiven(Commit givenCommit, Commit splitPoint)
            throws IOException {
        for (String fileName : givenCommit.getAllFiles().keySet()) {
            String givenFileContents
                    = givenCommit.getBlob(fileName).getFileContents();
            if (!splitPoint.getAllFiles().containsKey(fileName)) {
                if (!headCommit().getAllFiles().containsKey(fileName)) {
                    File file = join(CWD, fileName);
                    if (file.exists()) {
                        throw error("There is an untracked file in the way; "
                                + "delete it, or add and commit it first.");
                    }
                    file.createNewFile();
                    writeContents(file, givenFileContents);
                    givenCommit.checkout(fileName);
                    add(fileName);
                } else {
                    String curFileContents
                            = headCommit().getBlob(fileName).getFileContents();
                    if (!givenFileContents.equals(curFileContents)) {
                        mergeConflict(fileName, curFileContents,
                                givenFileContents);
                    }
                }
            } else if (!headCommit().getAllFiles().containsKey(fileName)) {
                String splitFileContents
                        = splitPoint.getBlob(fileName).getFileContents();
                if (!givenFileContents.equals(splitFileContents)) {
                    mergeConflict(fileName, "", givenFileContents);
                }
            } else {
                String splitFileContents
                        = splitPoint.getBlob(fileName).getFileContents();
                String curFileContents
                        = headCommit().getBlob(fileName).getFileContents();
                if (!givenFileContents.equals(splitFileContents)
                        && curFileContents.equals(splitFileContents)) {
                    File file = join(CWD, fileName);
                    writeContents(file, givenFileContents);
                    add(fileName);
                }
                if (!givenFileContents.equals(splitFileContents)
                        && !curFileContents.equals(splitFileContents)
                        && !givenFileContents.equals(curFileContents)) {
                    mergeConflict(fileName, curFileContents,
                            givenFileContents);
                }
            }
        }
    }

    /**
     * Implements the part of the merge logic involving traversing through
     * files in the commit of the current branch.
     * @param givenCommit commit of the given branch.
     * @param splitPoint split point of current branch and given branch.
     */
    private void mergeTraverseCur(Commit givenCommit, Commit splitPoint)
            throws IOException {
        for (String fileName : headCommit().getAllFiles().keySet()) {
            String curFileContents
                    = headCommit().getBlob(fileName).getFileContents();
            if (!givenCommit.getAllFiles().containsKey(fileName)
                    && splitPoint.getAllFiles().containsKey(fileName)) {
                String splitFileContents
                        = splitPoint.getBlob(fileName).getFileContents();
                if (splitFileContents.equals(curFileContents)) {
                    rm(fileName);
                } else {
                    mergeConflict(fileName, curFileContents, "");
                }
            }
        }
    }

    /**
     * Gets the split point of THIS and GIVENBRANCH.
     * @param givenBranch branch to be merged into THIS.
     * @return split point of THIS and GIVENBRANCH.
     */
    private Commit getSplitPoint(Branch givenBranch) throws IOException {
        Queue<String> fringe = new ArrayDeque<>();
        fringe.add(_head);
        Set<String> visited = new HashSet<>();
        while (!fringe.isEmpty()) {
            String curCommit = fringe.remove();
            if (!visited.contains(curCommit)) {
                Commit com = readObject(join(COMMITS, curCommit + ".txt"),
                        Commit.class);
                if (givenBranch._commits.contains(curCommit)) {
                    return com;
                } else {
                    fringe.add(com.getParentHash());
                    if (com.getMergeParentHash() != null) {
                        fringe.add(com.getMergeParentHash());
                    }
                }
                visited.add(curCommit);
            }
        }
        return null;
    }

    /**
     * Implements the code for a merge conflict. Writes the appropriate
     * contents in the conflicted file according to the specification.
     * @param fileName Name of the file with the conflict.
     * @param curFileContents  Contents of the conflict file in the current
     *                         branch.
     * @param givenFileContents  Contents of the conflict file in the given
     *                           branch.
     */
    private void mergeConflict(String fileName, String curFileContents,
                               String givenFileContents) throws IOException {
        String toWrite = "<<<<<<< HEAD\n" + curFileContents + "=======\n"
                + givenFileContents + ">>>>>>>\n";
        writeContents(join(CWD, fileName), toWrite);
        add(fileName);
        message("Encountered a merge conflict.");
    }

    /**
     * Pushes from THIS branch to the branch in the given remote.
     * @param remoteBranch Branch of the remote to be pushed to.
     * @param remoteGitletDir .gitlet directory of the remote repository.
     */
    public void push(Branch remoteBranch, File remoteGitletDir)
            throws IOException {
        Queue<String> fringe = new ArrayDeque<>();
        fringe.add(_head);
        String curCommitID = fringe.remove();
        while (!curCommitID.equals(remoteBranch._head)) {
            File curCommitFile = join(COMMITS, curCommitID + ".txt");
            Files.copy(curCommitFile.toPath(), join(join(remoteGitletDir,
                    "commits"), curCommitID + ".txt").toPath());
            Commit curCommit = readObject(curCommitFile, Commit.class);
            for (String blobName : curCommit.getAllFiles().values()) {
                File remoteBlob = join(join(remoteGitletDir, "blobs"),
                        blobName + ".txt");
                if (!remoteBlob.exists()) {
                    Files.copy(join(BLOBS, blobName + ".txt").toPath(),
                            remoteBlob.toPath());
                }
            }
            _commits.add(curCommitID);
            fringe.add(curCommit.getParentHash());
            if (curCommit.getMergeParentHash() != null) {
                fringe.add(curCommit.getMergeParentHash());
            }
            curCommitID = fringe.remove();
        }
        remoteBranch._head = _head;
        writeObject(join(join(remoteGitletDir, "branches"),
                remoteBranch._name + ".txt"), remoteBranch);
    }

    /**
     * Fetches files (commits and blobs) from remote repository to current
     * repository.
     * @param remoteGitletDir .gitlet directory of the remote repository.
     */
    public void fetchFiles(File remoteGitletDir) throws IOException {
        Queue<String> fringe = new ArrayDeque<>();
        fringe.add(_head);
        String curCommitID = fringe.remove();
        while (!plainFilenamesIn(COMMITS).contains(curCommitID + ".txt")) {
            File curCommitFile = join(join(remoteGitletDir, "commits"),
                    curCommitID + ".txt");
            Commit curCommit = readObject(curCommitFile, Commit.class);
            Files.copy(curCommitFile.toPath(),
                    join(COMMITS, curCommitID + ".txt").toPath());
            for (String blobID : headCommit().getAllFiles().values()) {
                File blobFile = join(join(remoteGitletDir, "blobs"),
                        blobID + ".txt");
                File curBlobFile = join(BLOBS, blobID + ".txt");
                if (!curBlobFile.exists()) {
                    Files.copy(blobFile.toPath(), curBlobFile.toPath());
                }
            }
            fringe.add(curCommit.getParentHash());
            if (curCommit.getMergeParentHash() != null) {
                fringe.add(curCommit.getMergeParentHash());
            }
            curCommitID = fringe.remove();
        }
    }

    /**
     * Sets all commits in THIS (the head commit and all its ancestors).
     */
    public void setCommits() {
        _commits.clear();
        Queue<String> fringe = new ArrayDeque<>();
        fringe.add(_head);
        while (!fringe.isEmpty()) {
            String curCommitID = fringe.remove();
            _commits.add(curCommitID);
            Commit curCommit = readObject(join(COMMITS, curCommitID + ".txt"),
                    Commit.class);
            if (curCommit.getParentHash() != null) {
                fringe.add(curCommit.getParentHash());
            }
            if (curCommit.getMergeParentHash() != null) {
                fringe.add(curCommit.getMergeParentHash());
            }
        }
    }

    /**
     * Returns the head commit of this branch.
     * @return the head commit.
     */
    public Commit headCommit() {
        File commitFile = join(COMMITS, _head + ".txt");
        return readObject(commitFile, Commit.class);
    }

    /**
     * Returns a list of all branches in this repository.
     * @return list of all branches as Branch objects.
     */
    public static List<Branch> allBranches() {
        List<String> allBranchFiles = plainFilenamesIn(BRANCHES);
        List<Branch> allBranches = new ArrayList<Branch>();
        for (String branchName : allBranchFiles) {
            allBranches.add(readObject(join(BRANCHES, branchName),
                    Branch.class));
        }
        return allBranches;
    }

    /**
     * Returns a list of names of all branches in this repository.
     * @return list of names of all branches.
     */
    public static List<String> branchNames() {
        List<String> branchNames = new ArrayList<String>();
        for (Branch branch : allBranches()) {
            branchNames.add(branch._name);
        }
        return branchNames;
    }

    /**
     * Get the name of this branch.
     * @return name of this branch.
     */
    public String getName() {
        return _name;
    }

    /**
     * Get the hash value of the head commit of this branch.
     * @return hash value of head commit.
     */
    public String getHead() {
        return _head;
    }

    /**
     * Returns a set of this branch's commits.
     * @return set of commits.
     */
    public Set<String> getCommits() {
        return _commits;
    }

    /**
     * Set the head commit to given hash value.
     * @param head hash value of commit to set as head of this branch.
     */
    public void setHead(String head) {
        _head = head;
    }

    /** Name of this branch. */
    private String _name;

    /** Hash value of the head commit of this branch. */
    private String _head;

    /** Set of all commits in this branch. */
    private Set<String> _commits;
}
