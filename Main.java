package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Collections;

import static gitlet.Utils.sha1;
import static gitlet.Utils.error;
import static gitlet.Utils.plainFilenamesIn;
import static gitlet.Utils.join;
import static gitlet.Utils.readObject;
import static gitlet.Utils.writeObject;
import static gitlet.Utils.writeContents;
import static gitlet.Utils.serialize;
import static gitlet.Utils.readContentsAsString;
import static gitlet.Utils.UID_LENGTH;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Ishaan Mauli Mishra
 */
public class Main {

    /** Current working directory. */
    static final File CWD = new File(System.getProperty("user.dir"));

    /** .gitlet repository. */
    static final File GITLET = join(CWD, ".gitlet");

    /** Index directory (for staged files). */
    static final File INDEX = join(GITLET, "index");

    /** Directory for files staged for addition. */
    static final File STAGED = join(INDEX, "staged");

    /** Directory for files staged for removal. */
    static final File STAGEDRM = join(INDEX, "stagedrm");

    /** Directory for Commit object files. */
    static final File COMMITS = join(GITLET, "commits");

    /** Directory for Blob object files. */
    static final File BLOBS = join(GITLET, "blobs");

    /** Directory for files storing information of all branches in this
     * repository. */
    static final File BRANCHES = join(GITLET, "branches");

    /** Stores the name of the HEAD branch. */
    static final File HEAD = join(GITLET, "HEAD.txt");

    /** Directory for files storing information for all added remotes in this
     *  repository. */
    static final File REPOSITORIES = join(GITLET, "repositories");

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        try {
            processCommands(args);
        } catch (GitletException | IOException e) {
            System.err.print(e.getMessage());
            System.exit(0);
        }
    }

    /** Process the commands passed into the program.
     *
     * @param commands ARGS from gitlet.Main.main.
     */
    public static void processCommands(String[] commands) throws IOException {
        if (commands.length == 0) {
            throw error("Please enter a command.");
        }
        if (commands[0].equals("init")) {
            checkLength(commands, 1);
            init();
            return;
        }
        if (!GITLET.exists()) {
            throw error("Not in an initialized Gitlet directory.");
        }
        processOtherCommands(commands);
    }

    /** Processes the commands other than init. Assumes Gitlet directory is
     * initialized.
     *
     * @param commands ARGS from gitlet.Main.main.
     */
    public static void processOtherCommands(String[] commands)
            throws IOException {
        switch (commands[0]) {
        case "add":
            checkLength(commands, 2);
            add(commands[1]);
            break;
        case "commit":
            checkLength(commands, 2);
            commit(commands[1]);
            break;
        case "checkout":
            processCheckout(commands);
            break;
        case "rm":
            checkLength(commands, 2);
            rm(commands[1]);
            break;
        case "log":
            checkLength(commands, 1);
            log();
            break;
        case "global-log":
            checkLength(commands, 1);
            globalLog();
            break;
        case "find":
            checkLength(commands, 2);
            find(commands[1]);
            break;
        case "status":
            checkLength(commands, 1);
            status();
            break;
        case "branch":
            checkLength(commands, 2);
            branch(commands[1]);
            break;
        case "rm-branch":
            checkLength(commands, 2);
            rmBranch(commands[1]);
            break;
        case "reset":
            checkLength(commands, 2);
            reset(commands[1]);
            break;
        case "merge":
            checkLength(commands, 2);
            merge(commands[1]);
            break;
        default:
            processRemoteCommands(commands);
        }
    }

    /** Processes remote commands.
     *
     * @param commands ARGS from gitlet.Main.main.
     */
    public static void processRemoteCommands(String[] commands)
            throws IOException {
        switch (commands[0]) {
        case "add-remote":
            checkLength(commands, 3);
            addRemote(commands[1], commands[2]);
            break;
        case "rm-remote":
            checkLength(commands, 2);
            rmRemote(commands[1]);
            break;
        case "push":
            checkLength(commands, 3);
            push(commands[1], commands[2]);
            break;
        case "fetch":
            checkLength(commands, 3);
            fetch(commands[1], commands[2]);
            break;
        case "pull":
            checkLength(commands, 3);
            pull(commands[1], commands[2]);
            break;
        default:
            throw error("No command with that name exists");
        }
    }

    /** Processes commands where COMMANDS contains
     * checkout <OPERANDS> ....
     * Processes the commands for checkout.
     *
     * @param commands ARGS from gitlet.Main.main.
     */
    public static void processCheckout(String[] commands) {
        if (commands.length >= 2) {
            if (commands.length == 2) {
                checkoutBranch(commands[1]);
                return;
            } else if (commands.length == 3 && commands[1].equals("--")) {
                checkoutFile(commands[2]);
                return;
            } else if (commands.length == 4 && commands[2].equals("--")) {
                checkoutCommit(commands[1], commands[3]);
                return;
            }
        }
        throw error("Incorrect operands.");
    }

    /** Processes the init command. */
    public static void init() throws IOException {
        if (GITLET.exists()) {
            throw error("A Gitlet version-control system already exists in the"
                    + " current directory.");
        }
        GITLET.mkdir();
        INDEX.mkdir();
        STAGED.mkdir();
        STAGEDRM.mkdir();
        COMMITS.mkdir();
        BLOBS.mkdir();
        BRANCHES.mkdir();
        HEAD.createNewFile();
        REPOSITORIES.mkdir();
        Branch master = new Branch("master", null);
        headBranch = master;
        writeContents(HEAD, headBranch.getName());
        commit("initial commit");
    }

    /** Processes the add command.
     *
     * @param fileName name of the file to be staged.
     * */
    public static void add(String fileName) throws IOException {
        join(STAGEDRM, fileName).delete();
        File file = join(CWD, fileName);
        Blob blob = new Blob(file);
        if (!headCommit().getAllFiles().containsValue(sha1(serialize(blob)))) {
            Files.copy(join(BLOBS, sha1(serialize(blob)) + ".txt").toPath(),
                    join(STAGED, fileName).toPath());
        }
    }

    /** Processes the commit command.
     * @param message message of the commit.
     */
    public static void commit(String message) throws IOException {
        if (message.length() == 0) {
            throw error("Please enter a commit message.");
        }
        String headBranchName = readContentsAsString(HEAD);
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        headBranch.commit(message);
    }

    /** Process the rm command.
     *
     * @param fileName name of the file to be removed.
     */
    public static void rm(String fileName) throws IOException {
        boolean found;
        found = join(STAGED, fileName).delete();
        if (headCommit().getAllFiles().containsKey(fileName)) {
            found = true;
            join(STAGEDRM, fileName).createNewFile();
            join(CWD, fileName).delete();
        }
        if (!found) {
            throw error("No reason to remove the file.");
        }
    }

    /** Checks out a file from the most recent commit.
     *
     * @param fileName name of the file to be checked out.
     */
    public static void checkoutFile(String fileName) {
        headCommit().checkout(fileName);
    }

    /** Checks out a file from the given commit.
     *
     * @param commitid hash of commit from which the file is to be checked out.
     * @param fileName name of file to be checked out.
     */
    public static void checkoutCommit(String commitid, String fileName) {
        if (commitid.length() < UID_LENGTH) {
            commitid = findCommitShortId(commitid);
        }
        File commitFile = join(COMMITS, commitid + ".txt");
        if (!commitFile.exists()) {
            throw error("No commit with that id exists.");
        }
        Commit commit = readObject(join(COMMITS, commitid + ".txt"),
                Commit.class);
        commit.checkout(fileName);
    }

    /** Checks out the given branch.
     *
     * @param branchName name of branch to be checked out.
     */
    public static void checkoutBranch(String branchName) {
        branchName = branchName.replace("/", "--");
        File branchFile = join(BRANCHES, branchName + ".txt");
        if (!branchFile.exists()) {
            throw error("No such branch exists.");
        }
        String headBranchName = readContentsAsString(HEAD);
        if (branchName.equals(headBranchName)) {
            throw error("No need to checkout the current branch.");
        }
        Branch branch = readObject(branchFile, Branch.class);
        checkoutBranch(branch);
    }

    /** Checks out the given branch.
     *
     * @param branch name of the branch to be checked out.
     */
    public static void checkoutBranch(Branch branch) {
        branch.headCommit().checkout();
        writeContents(HEAD, branch.getName());
    }

    /** Prints the log at the current time (of the current HEAD). */
    public static void log() {
        String headBranchName = readContentsAsString(HEAD);
        headBranchName = headBranchName.replace("/", "--");
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        Commit headCommit = headBranch.headCommit();
        headCommit.log();
    }

    /** Prints the global-log - the log of all commits ever made in this
     * repository.
     */
    public static void globalLog() {
        Commit.globalLog();
    }

    /** Finds all commits with the given message and prints their hash values.
     *
     * @param message message to find commits.
     */
    public static void find(String message) {
        Commit.find(message);
    }

    /** Prints the status of this repository. */
    public static void status() {
        String branchName = readContentsAsString(HEAD);
        headBranch = readObject(join(BRANCHES, branchName + ".txt"),
                Branch.class);
        System.out.println("=== Branches ===");
        List<String> branchNames = Branch.branchNames();
        for (String curBranch: branchNames) {
            if (branchName.equals(curBranch)) {
                System.out.print("*");
            }
            System.out.println(curBranch);
        }
        System.out.println("\n=== Staged Files ===");
        List<String> stagedFiles = plainFilenamesIn(STAGED);
        for (String fileName : stagedFiles) {
            System.out.println(fileName);
        }
        System.out.println("\n=== Removed Files ===");
        List<String> stagedrmFiles = plainFilenamesIn(STAGEDRM);
        for (String fileName : stagedrmFiles) {
            System.out.println(fileName);
        }
        System.out.println("\n=== Modifications Not Staged For Commit ===");
        TreeMap<String, Boolean> modifiedFiles = getModifiedFiles();
        for (String fileName : modifiedFiles.keySet()) {
            System.out.println(fileName + " (" + (modifiedFiles.get(fileName)
                    ? "modified" : "deleted") + ")");
        }
        System.out.println("\n=== Untracked Files ===");
        List<String> untrackedFiles = getUntrackedFiles();
        Collections.sort(untrackedFiles);
        for (String fileName : untrackedFiles) {
            System.out.println(fileName);
        }
    }

    /** Returns name of all files that have been modified byt not staged.
     *
     * @return set of all modified files. Each Key, Value element holds the
     * name of a modified file where the Boolean value signifies whether the
     * file has been modified or deleted. 0 means deleted and 1 means modified.
     */
    public static TreeMap<String, Boolean> getModifiedFiles() {
        TreeMap<String, Boolean> modifiedFiles = new TreeMap<>();
        Commit headCommit = headBranch.headCommit();
        for (String fileName : headCommit.getAllFiles().keySet()) {
            if (!join(CWD, fileName).exists()) {
                if (!join(STAGEDRM, fileName).exists()) {
                    modifiedFiles.put(fileName, false);
                }
            } else {
                String contentsTracked
                        = headCommit.getBlob(fileName).getFileContents();
                String contentscwd = readContentsAsString(join(CWD, fileName));
                if (!join(STAGED, fileName).exists()
                        && !contentsTracked.equals(contentscwd)) {
                    modifiedFiles.put(fileName, true);
                }
            }
        }
        for (String fileName : plainFilenamesIn(STAGED)) {
            File filecwd = join(CWD, fileName);
            String stagedContents = readObject(join(STAGED, fileName),
                    Blob.class).getFileContents();
            if (!filecwd.exists()) {
                modifiedFiles.put(fileName, false);
            } else if (!stagedContents.equals(readContentsAsString(filecwd))) {
                modifiedFiles.put(fileName, true);
            }
        }
        return modifiedFiles;
    }

    /** Returns name of untracked files in the current working directory.
     * These are files which are neither staged nor tracked.
     *
     * @return list of all untracked files.
     */
    public static List<String> getUntrackedFiles() {
        List<String> untrackedFiles = new ArrayList<>();
        Set<String> trackedFiles
                = headBranch.headCommit().getAllFiles().keySet();
        for (String fileName : plainFilenamesIn(CWD)) {
            if (!trackedFiles.contains(fileName)
                    && !join(STAGED, fileName).exists()) {
                untrackedFiles.add(fileName);
            }
        }
        return untrackedFiles;
    }

    /**
     * Makes a new branch with the name BRANCHNAME.
     * @param branchName name of new branch to be created.
     */
    public static void branch(String branchName) throws IOException {
        if (join(BRANCHES, branchName + ".txt").exists()) {
            throw error("A branch with that name already exists.");
        }
        String headBranchName = readContentsAsString(HEAD);
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        new Branch(branchName, headBranch.getHead());
    }

    /**
     * Deletes the branch with the given name BRANCHNAME.
     * @param branchName name of branch to be deleted.
     */
    public static void rmBranch(String branchName) {
        String headBranchName = readContentsAsString(HEAD);
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        if (headBranch.getName().equals(branchName)) {
            throw error("Cannot remove the current branch.");
        }
        if (!join(BRANCHES, branchName + ".txt").delete()) {
            throw error("A branch with that name does not exist.");
        }
    }

    /**
     * Resets the state of the repository to the given commit.
     * @param commitid hash value of the commit to reset to.
     */
    public static void reset(String commitid) throws IOException {
        if (commitid.length() < UID_LENGTH) {
            commitid = findCommitShortId(commitid);
        }
        File commitFile = join(COMMITS, commitid + ".txt");
        if (!commitFile.exists()) {
            throw error("No commit with that id exists.");
        }
        Commit commit = readObject(commitFile, Commit.class);
        commit.checkout();
        String headBranchName = readContentsAsString(HEAD);
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        headBranch.setHead(commitid);
        headBranch.setCommits();
        headBranch.storeBranch();
    }

    /**
     * Merges the given branch into the current branch.
     * @param branchName name of branch to be merged into the current.
     */
    public static void merge(String branchName) throws IOException {
        if (plainFilenamesIn(STAGED).size() != 0
                || plainFilenamesIn(STAGEDRM).size() != 0) {
            throw error("You have uncommitted changes.");
        }
        if (!join(BRANCHES, branchName.replace("/", "--") + ".txt").exists()) {
            throw error("A branch with that name does not exist.");
        }
        String headBranchName = readContentsAsString(HEAD);
        if (branchName.equals(headBranchName)) {
            throw error("Cannot merge a branch with itself.");
        }
        headBranch = readObject(join(BRANCHES,
                headBranchName.replace("/", "--") + ".txt"), Branch.class);
        Branch givenBranch = readObject(join(BRANCHES,
                branchName.replace("/", "--") + ".txt"), Branch.class);
        headBranch.merge(givenBranch);
    }

    /** If the commit hash passed in is short, this method finds and returns
     * the full hash of the intended commit.
     *
     * @param shortID the short commit hash/id.
     * @return the full hash/id of the intended commit.
     */
    public static String findCommitShortId(String shortID) {
        for (String commitFile : plainFilenamesIn(COMMITS)) {
            if (commitFile.startsWith(shortID)) {
                return commitFile.substring(0, UID_LENGTH);
            }
        }
        return "";
    }

    /**
     * Adds a remote repository reference.
     * @param name name under which the remote is stored.
     * @param dir path to the .gitlet directory of the given remote.
     */
    public static void addRemote(String name, String dir) {
        File remoteFile = join(REPOSITORIES, name + ".txt");
        if (remoteFile.exists()) {
            throw error("A remote with that name already exists.");
        }
        File remotePath = new File(dir);
        writeObject(remoteFile, remotePath);
    }

    /**
     * Removes the remote repository associated with the name NAME.
     * @param name name of the remote to be deleted.
     */
    public static void rmRemote(String name) {
        if (!join(REPOSITORIES, name + ".txt").delete()) {
            throw error("A remote with that name does not exist.");
        }
    }

    /**
     * Pushes the current branch to the given branch of the given remote.
     * @param remoteName name of the remote to be pushed to.
     * @param remoteBranchName name of the branch in the remote to be pushed to.
     */
    public static void push(String remoteName, String remoteBranchName)
            throws IOException {
        File remoteGitletDir = readObject(join(REPOSITORIES,
                remoteName + ".txt"), File.class);
        if (!remoteGitletDir.exists()) {
            throw error("Remote directory not found.");
        }
        Branch remoteBranch = readObject(join(join(remoteGitletDir,
                "branches"), remoteBranchName + ".txt"), Branch.class);
        headBranch = readObject(join(BRANCHES,
                readContentsAsString(HEAD) + ".txt"), Branch.class);
        if (!headBranch.getCommits().contains(remoteBranch.getHead())) {
            throw error("Please pull down remote changes before pushing.");
        }
        headBranch.push(remoteBranch, remoteGitletDir);
    }

    /**
     * Fetches the commits from the given branch of the given remote.
     * @param remoteName name of the remote to be fetched from.
     * @param remoteBranchName name of the branch of the remote to be fetched
     *                        from.
     */
    public static void fetch(String remoteName, String remoteBranchName)
            throws IOException {
        File remoteGitletDir = readObject(join(REPOSITORIES,
                remoteName + ".txt"), File.class);
        if (!remoteGitletDir.exists()) {
            throw error("Remote directory not found.");
        }
        File remoteBranchFile = join(join(remoteGitletDir, "branches"),
                remoteBranchName.replace("/", "--") + ".txt");
        if (!remoteBranchFile.exists()) {
            throw error("That remote does not have that branch.");
        }
        Branch remoteBranch = readObject(remoteBranchFile, Branch.class);
        remoteBranch.fetchFiles(remoteGitletDir);
        String newBranchName = remoteName + "/" + remoteBranchName;
        File newBranchFile = join(BRANCHES, newBranchName + ".txt");
        if (newBranchFile.exists()) {
            Branch newBranch = readObject(newBranchFile, Branch.class);
            newBranch.setHead(remoteBranch.getHead());
            newBranch.storeBranch();
        } else {
            new Branch(newBranchName, remoteBranch, remoteBranch.getHead());
        }
    }

    /**
     * Pulls from the given branch of the given remote.
     * @param remoteName remote to be fetched from.
     * @param remoteBranchName branch of the remote to be fetched from.
     */
    public static void pull(String remoteName, String remoteBranchName)
            throws IOException {
        fetch(remoteName, remoteBranchName);
        merge(remoteName + "/" + remoteBranchName);
    }

    /**
     * Returns the HEAD commit.
     * @return the HEAD commit.
     */
    public static Commit headCommit() {
        String headBranchName = readContentsAsString(HEAD);
        headBranchName = headBranchName.replace("/", "--");
        headBranch = readObject(join(BRANCHES, headBranchName + ".txt"),
                Branch.class);
        return headBranch.headCommit();
    }

    /**
     * Clears the given directory.
     * @param dir directory to be cleared.
     */
    public static void clearDir(File dir) {
        for (String file : plainFilenamesIn(dir)) {
            join(dir, file).delete();
        }
    }

    /**
     * Checks that the length of the passed in commands is correct. Throws a
     * GitletException if it isn't.
     * @param arr commands whose length is to be checked.
     * @param len expected length of ARR.
     */
    public static void checkLength(String[] arr, int len) {
        if (len != arr.length) {
            throw error("Incorrect operands");
        }
    }

    /**
     * Gets the head branch of this repository.
     * @return head branch.
     */
    public static Branch headBranch() {
        return headBranch;
    }

    /** HEAD branch for this repository. */
    private static Branch headBranch;
}
