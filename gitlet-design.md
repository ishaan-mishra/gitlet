# Gitlet Design Document

**Name**: Ishaan Mauli Mishra

## Classes and Data Structures

### Main
1. `static Branch HEADbranch`: Current branch.
2. `String CurRepo`: Current Repository name.

### Blob
Represents a file/blob.
1. `String name`: Stores the name of the file/blob.*
2. `String contents`: Stores the entire text in the file.

### Commit
Represents a commit.
1. `String logMessage`: Log message for this commit.
2. `HashMap<String, String> files`: Stores the SHA1 of files/blobs in this commit.
3. `String parent`: SHA1 of its parent commit.
4. `String mergeParent`: SHA1 of its second parent (null if it is not a merged commit).
5. `Date timeStamp`: Time this commit was made.

### Branch
Represents a branch
1. `String name`: Name of branch.
2. `Commit _head`: Commit this branch points to.

### Repository
Represents the different (remote) repositories.


## Algorithms

### Commit class
1. `void log()`: Prints the log of this commit. Calls the parent of the commit recursively.
2. `void globalLog()`: Uses `Utils.plainFileNamesIn` to get all the commit object files in `.gitlet/tracked` and prints
out each commit.
3. `String toString()`: Returns a String representation of the commit. Uses 
`SimpleDateFormat("E dd MMM yyyy HH:mm:ss z")` to print the date.

### Branch class
1. `List<String> staged()`: Returns all the file names of the files currently staged.


## Persistence
1. Each staged file is added to `".gitlet/index/staged"`.
2. Files staged for removal are in `.gitlet/index/stagedrm`.
2. Each commit is stored as a file in `".gitlet/commits"`. This subdirectory has
a file representing each commit
3. `".gitlet/branches"` stores information of each `Branch` object as files.
5. `.gitlet/blobs` stores blobs.
