package gitlet;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 * does at a high level.
 *
 * @author Jiayi Mao
 */
public class Repository {
    /**
     * The .gitlet directory looks like:
     * .gitlet
     * |--objects
     * |   |--commits and blobs
     * |--HEAD
     * |--refs
     * |   |--heads
     * |       |--master
     * |       |--dev...
     * |--stage
     * |   |--add_stage
     * |   |--remove_stage
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");
    public static final File STAGE_DIR = join(GITLET_DIR, "stage");
    public static final File ADD_STAGE_FILE = join(STAGE_DIR, "add_stage");
    public static final File REMOVE_STAGE_FILE = join(STAGE_DIR, "remove_stage");

    public static Commit currentCommit;
    public static String currentBranch;
    public static Stage addStage;
    public static Stage removeStage;


    /**
     * ---------------------- gitlet core commands ----------------------
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            Utils.exitWithMessage("A Gitlet version-control system already exists in the current directory.");
        }
        // init necessary directories
        GITLET_DIR.mkdirs();
        OBJECTS_DIR.mkdirs();
        REFS_DIR.mkdirs();
        HEADS_DIR.mkdirs();
        STAGE_DIR.mkdirs();

        Commit commit = new Commit("initial commit");
        commit.save();  // save to ./gitlet/objects/xxx

        File file = Utils.join(HEADS_DIR, "master");
        Utils.writeContents(file, commit.getId());   // init branch---"master"

        Utils.writeContents(HEAD_FILE, "master");  // init HEAD with master branch
    }

    public static void add(String fileName) {
        File file = Utils.join(CWD, fileName);
        if (!file.exists()) {
            Utils.exitWithMessage("File does not exist.");
        }
        Blob blob = new Blob(file);

        currentCommit = getCurrentCommit();
        addStage = getAddStage();
        removeStage = getRemoveStage();


        if (!currentCommit.exitsBlob(blob) || removeStage.exitsBlob(blob)) {
            if (!addStage.exitsBlob(blob)) {

                // if removeStage exist the blob,we delete it and then return
                if (removeStage.exitsBlob(blob)) {
                    removeStage.delete(blob);
                    removeStage.saveTo(REMOVE_STAGE_FILE);
                    return;
                }

                // both addStage and removeStage don't exist the blob
                blob.save();
                if (addStage.exitsOldVersionOfBlob(blob)) {
                    addStage.delete(blob);  // delete the old one, then add the new one
                }
                addStage.add(blob);
                addStage.saveTo(ADD_STAGE_FILE);
            }
        }
    }

    public static void commit(String message) {
        if (message.equals("")) {
            Utils.exitWithMessage("Please enter a commit message.");
        }
        currentCommit = getCurrentCommit();
        addStage = getAddStage();
        removeStage = getRemoveStage();
        if (addStage.isEmpty() && removeStage.isEmpty()) {
            Utils.exitWithMessage("No changes added to the commit.");
        }
        TreeMap<String, String> originalBlobMap = currentCommit.getBlobMap();
        TreeMap<String, String> addBlobMap = addStage.getBlobMap();
        TreeMap<String, String> removeBlobMap = removeStage.getBlobMap();

        TreeMap<String, String> calMap = calFinalMap(originalBlobMap, addBlobMap, removeBlobMap);
        Commit commit = new Commit(currentCommit.getId(), null, calMap, message);
        commit.save();
        currentBranch = getCurrentBranch();
        Utils.writeContents(Utils.join(HEADS_DIR, currentBranch), commit.getId());
        clearStage();
    }

    public static void rm(String fileName) {
        currentCommit = getCurrentCommit();
        addStage = getAddStage();
        removeStage = getRemoveStage();
        if (addStage.getBlobNames().contains(fileName)) {
            addStage.delete(fileName);
            addStage.saveTo(ADD_STAGE_FILE);
        } else if (currentCommit.getBlobNames().contains(fileName)) {
            File file = Utils.join(CWD, fileName);
            if (file.exists()) {
                file.delete();
            }
            Blob removeBlob = currentCommit.getBlobByName(fileName);
            removeStage.add(removeBlob);
            removeStage.saveTo(REMOVE_STAGE_FILE);
        } else {
            Utils.exitWithMessage("No reason to remove the file.");
        }
    }

    public static void log() {
        Commit commit = getCurrentCommit();

        while (commit.hasParent()) {
            printCommitLog(commit);
            commit = getCommitById(commit.getParent());
        }
        printCommitLog(commit);  // print the initial commit
    }

    public static void globalLog() {
        List<String> objectIds = Utils.plainFilenamesIn(OBJECTS_DIR);
        if (objectIds == null) return;
        for (String id : objectIds) {
            try {
                Commit commit = getCommitById(id);
                printCommitLog(commit);
            } catch (Exception e) {
                // if the id belongs to a blob, we do nothing.
            }
        }
    }

    public static void find(String message) {
        List<String> objectIds = Utils.plainFilenamesIn(OBJECTS_DIR);
        List<String> commitIds = new ArrayList<>();
        for (String id : objectIds) {

            try {
                Commit commit = getCommitById(id);
                if (commit != null && commit.getMessage().equals(message)) {
                    commitIds.add(commit.getId());
                }
            } catch (Exception e) {
                // if the object's type is BLOB,we do nothing
            }
        }
        if (commitIds.isEmpty()) {
            Utils.exitWithMessage("Found no commit with that message.");
        }
        for (String id : commitIds) {
            System.out.println(id);
        }
    }

    public static void status() {
        printAllBranches();
        printAddStage();
        printRemoveStage();
        printModifiedAndUntrackedInfos();
    }

    // checkout -- [file name]
    public static void checkout(String fileName) {
        currentCommit = getCurrentCommit();
        List<String> commitFiles = currentCommit.getBlobNames();
        if (!commitFiles.contains(fileName)) {
            Utils.exitWithMessage("File does not exist in that commit.");
        }
        Blob blob = currentCommit.getBlobByName(fileName);
        Utils.writeContents(Utils.join(CWD, fileName), blob.getContent());
    }

    // checkout [commit id] -- [file name]
    public static void checkout(String commitId, String fileName) {
        Commit commit = getCommitById(commitId);
        if (commit == null) {
            Utils.exitWithMessage("No commit with that id exists.");
        }
        List<String> commitFiles = commit.getBlobNames();
        if (!commitFiles.contains(fileName)) {
            Utils.exitWithMessage("File does not exist in that commit.");
        }
        Blob blob = commit.getBlobByName(fileName);
        Utils.writeContents(Utils.join(CWD, fileName), blob.getContent());
    }

    // checkout [branch name]
    public static void checkoutBranch(String branchName) {
        currentBranch = getCurrentBranch();
        currentCommit = getCurrentCommit();

        List<String> branchList = Utils.plainFilenamesIn(HEADS_DIR);
        if (!branchList.contains(branchName)) {
            Utils.exitWithMessage("No such branch exists.");
        }
        if (branchName.equals(currentBranch)) {
            Utils.exitWithMessage("No need to checkout the current branch.");
        }

        Commit newCommit = getCommitByBranch(branchName);

        List<String> originalFiles = currentCommit.getBlobNames();
        List<String> checkFiles = newCommit.getBlobNames();

        List<String> filesOnlyTrackedByCurr = getFilesOnlyTrackedByCurr(originalFiles, checkFiles);
        List<String> filesOnlyTrackedByCheck = getFilesOnlyTrackedByCheck(originalFiles, checkFiles);
        List<String> filesBothTracked = getFilesBothTracked(originalFiles, checkFiles);

        deleteFiles(filesOnlyTrackedByCurr);
        overwriteFiles(filesBothTracked, newCommit);
        writeFiles(filesOnlyTrackedByCheck, newCommit);
        clearStage();

        Utils.writeContents(HEAD_FILE, branchName);
    }

    public static void branch(String branchName) {
        List<String> branchList = Utils.plainFilenamesIn(HEADS_DIR);
        if (branchList.contains(branchName)) {
            Utils.exitWithMessage("A branch with that name already exists.");
        }
        currentCommit = getCurrentCommit();
        Utils.writeContents(Utils.join(HEADS_DIR, branchName), currentCommit.getId());
    }

    public static void rmBranch(String branchName) {
        checkIfBranchExists(branchName);
        currentBranch = getCurrentBranch();
        if (branchName.equals(currentBranch)) {
            Utils.exitWithMessage("Cannot remove the current branch.");
        }
        File file = Utils.join(HEADS_DIR, branchName);
        file.delete();
    }

    public static void reset(String commitId) {
        List<String> commitIds = Utils.plainFilenamesIn(OBJECTS_DIR);
        if (!commitIds.contains(commitId)) {
            Utils.exitWithMessage("No commit with that id exists.");
        }

        currentCommit = getCurrentCommit();
        Commit commit = getCommitById(commitId);

        List<String> originalFiles = currentCommit.getBlobNames();
        List<String> checkFiles = commit.getBlobNames();

        List<String> filesOnlyTrackedByCurr = getFilesOnlyTrackedByCurr(originalFiles, checkFiles);
        List<String> filesOnlyTrackedByCheck = getFilesOnlyTrackedByCheck(originalFiles, checkFiles);
        List<String> filesBothTracked = getFilesBothTracked(originalFiles, checkFiles);

        deleteFiles(filesOnlyTrackedByCurr);
        overwriteFiles(filesBothTracked, commit);
        writeFiles(filesOnlyTrackedByCheck, commit);
        clearStage();

        currentBranch = getCurrentBranch();
        Utils.writeContents(Utils.join(HEADS_DIR, currentBranch), commit.getId());
    }

    public static void merge(String branchName) {
        addStage = getAddStage();
        removeStage = getRemoveStage();
        if (!addStage.isEmpty() || !removeStage.isEmpty()) {
            Utils.exitWithMessage("You have uncommitted changes.");
        }
        checkIfBranchExists(branchName);
        currentBranch = getCurrentBranch();
        if (currentBranch.equals(branchName)) {
            Utils.exitWithMessage("Cannot merge a branch with itself.");
        }
        currentCommit = getCurrentCommit();
        Commit mergeCommit = getCommitByBranch(branchName);
        Commit splitPoint = getSplitPoint(currentCommit, mergeCommit);

        if(splitPoint.getId().equals(currentCommit.getId())){
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(branchName);
        }

        if(splitPoint.getId().equals(mergeCommit.getId())){
            Utils.exitWithMessage("Given branch is an ancestor of the current branch.");
        }
        String message = "Merged " + branchName + " into " + currentBranch + ".";
        Commit newCommit = getMergedCommit(splitPoint,currentCommit,mergeCommit,message);
        newCommit.save();
        Utils.writeContents(Utils.join(HEADS_DIR, currentBranch), newCommit.getId());
        clearStage();
    }


    /**
     * ---------------------- functions below are designed to help core commands ----------------------
     */
    private static Commit getMergedCommit(Commit splitCommit,Commit currentCommit,Commit mergeCommit,String message){
        addStage = getAddStage();
        removeStage = getRemoveStage();
        List<String> overwriteFiles = getOverWriteFiles(splitCommit,currentCommit,mergeCommit);
        List<String> writeFiles = getWriteFiles(splitCommit,currentCommit,mergeCommit);
        List<String> deleteFiles = getDeleteFiles(splitCommit,currentCommit,mergeCommit);

        overwriteFiles(overwriteFiles,mergeCommit);
        writeFiles(writeFiles,mergeCommit);
        deleteFiles(deleteFiles);

        dealWithConflict(splitCommit,currentCommit,mergeCommit);


        TreeMap<String,String> currentBlobMaps = currentCommit.getBlobMap();
        if(!overwriteFiles.isEmpty()){
            for(String fileName:overwriteFiles){
                currentBlobMaps.put(fileName,mergeCommit.getBlobByName(fileName).getId());
            }
        }
        if(!writeFiles.isEmpty()){
            for(String fileName:writeFiles){
                currentBlobMaps.put(fileName,mergeCommit.getBlobByName(fileName).getId());
            }
        }
        if(!deleteFiles.isEmpty()){
            for(String fileName:deleteFiles){
                currentBlobMaps.remove(fileName);
            }
        }
        if(!removeStage.isEmpty()){
            for(String fileName:removeStage.getBlobNames()){
                currentBlobMaps.remove(fileName);
            }
        }
        if(!addStage.isEmpty()){
            for(String fileName:addStage.getBlobMap().keySet()){
                currentBlobMaps.put(fileName,addStage.getBlobMap().get(fileName));
            }
        }
        return new Commit(currentCommit.getId(),mergeCommit.getId(),currentBlobMaps,message);

    }

    private static void dealWithConflict(Commit splitCommit, Commit currentCommit, Commit mergeCommit) {
        List<String> files = getAllFiles(splitCommit,currentCommit,mergeCommit);
        TreeMap<String,String> splitMap = splitCommit.getBlobMap();
        TreeMap<String,String> currentMap = currentCommit.getBlobMap();
        TreeMap<String,String> mergeMap = mergeCommit.getBlobMap();
        boolean conflict = false;
        for(String fileName:files){
            int count = 0;
            if(splitMap.containsKey(fileName)){
                count+=1;
            }
            if(currentMap.containsKey(fileName)){
                count+=2;
            }
            if(mergeMap.containsKey(fileName)){
                count+=4;
            }
            if((count==3 && !splitMap.get(fileName).equals(currentMap.get(fileName))) ||
                    (count==5 && !splitMap.get(fileName).equals(mergeMap.get(fileName))) ||
                    (count==6 && !currentMap.get(fileName).equals(mergeMap.get(fileName))) ||
                    (count==7 && !splitMap.get(fileName).equals(currentMap.get(fileName)) &&
                            !splitMap.get(fileName).equals(mergeMap.get(fileName)) &&
                            !currentMap.get(fileName).equals(mergeMap.get(fileName)))
            ){
                conflict = true;
                String currentContent = "";
                if(currentMap.containsKey(fileName)){
                    Blob blob = currentCommit.getBlobByName(fileName);
                    currentContent = new String(blob.getContent(), StandardCharsets.UTF_8);
                    removeStage.add(blob);
                }
                String mergeContent = "";
                if(mergeMap.containsKey(fileName)){
                    Blob blob = mergeCommit.getBlobByName(fileName);
                    mergeContent = new String(blob.getContent(),StandardCharsets.UTF_8);
                }
                String content = "<<<<<<< HEAD\n" + currentContent + "=======\n" + mergeContent + ">>>>>>>\n";
                File file = Utils.join(CWD,fileName);
                Utils.writeContents(file,content);
                addStage.add(new Blob(file));
            }
        }
        if(conflict==true){
            System.out.println("Encountered a merge conflict.");
        }

    }

    private static List<String> getAllFiles(Commit splitCommit, Commit currentCommit, Commit mergeCommit) {
        List<String> list1 = splitCommit.getBlobNames();
        List<String> list2 = currentCommit.getBlobNames();
        List<String> list3 = mergeCommit.getBlobNames();
        Set<String> set = new HashSet<>();
        set.addAll(list1);
        set.addAll(list2);
        set.addAll(list3);
        return new ArrayList<>(set);
    }

    private static List<String> getDeleteFiles(Commit splitCommit, Commit currentCommit, Commit mergeCommit) {
        List<String> res = new ArrayList<>();
        TreeMap<String,String> splitBlobMaps = splitCommit.getBlobMap();
        TreeMap<String,String> currentBlobMaps = currentCommit.getBlobMap();
        TreeMap<String,String> mergeBlobMaps = mergeCommit.getBlobMap();
        for(String fileName:splitBlobMaps.keySet()){
            if(currentBlobMaps.containsKey(fileName) && !mergeBlobMaps.containsKey(fileName)){
                res.add(fileName);
            }
        }
        return res;
    }

    private static List<String> getWriteFiles(Commit splitCommit, Commit currentCommit, Commit mergeCommit) {
        List<String> res = new ArrayList<>();
        TreeMap<String,String> splitBlobMaps = splitCommit.getBlobMap();
        TreeMap<String,String> currentBlobMaps = currentCommit.getBlobMap();
        TreeMap<String,String> mergeBlobMaps = mergeCommit.getBlobMap();
        for(String fileName:mergeBlobMaps.keySet()){
            if(!splitBlobMaps.containsKey(fileName) && !currentBlobMaps.containsKey(fileName)){
                res.add(fileName);
            }
        }
        return res;
    }

    private static List<String> getOverWriteFiles(Commit splitCommit, Commit currentCommit, Commit mergeCommit) {
        List<String> res = new ArrayList<>();
        TreeMap<String,String> splitBlobMaps = splitCommit.getBlobMap();
        TreeMap<String,String> currentBlobMaps = currentCommit.getBlobMap();
        TreeMap<String,String> mergeBlobMaps = mergeCommit.getBlobMap();
        for(String fileName:splitBlobMaps.keySet()){
            if(currentBlobMaps.containsKey(fileName) && mergeBlobMaps.containsKey(fileName)){
                String id = splitBlobMaps.get(fileName);
                if(id.equals(currentBlobMaps.get(fileName)) && !id.equals(mergeBlobMaps.get(fileName))){
                    res.add(fileName);
                }
            }
        }
        return res;
    }

    private static Commit getSplitPoint(Commit c1, Commit c2) {
        HashMap<String, Integer> depthMap1 = getDepthMap(c1, 0);
        HashMap<String, Integer> depthMap2 = getDepthMap(c2, 0);
        String commitId = "";
        int minValue = Integer.MAX_VALUE;
        for(String id:depthMap1.keySet()){
            if(depthMap2.containsKey(id) && depthMap1.get(id)<minValue){
                minValue = depthMap1.get(id);
                commitId = id;
            }
        }
        return getCommitById(commitId);
    }

    private static HashMap<String, Integer> getDepthMap(Commit commit, int length) {
        HashMap<String, Integer> map = new HashMap<>();
        map.put(commit.getId(), length);
        if (!commit.hasParent()) {
            return map;
        }
        length++;
        if (commit.hasParent()) {
            Commit c1 = getCommitById(commit.getParent());
            map.putAll(getDepthMap(c1, length));
        }
        if (commit.hasSecondParent()) {
            Commit c2 = getCommitById(commit.getSecondParent());
            map.putAll(getDepthMap(c2, length));
        }
        return map;
    }

    private static void checkIfBranchExists(String branchName) {
        List<String> branchList = Utils.plainFilenamesIn(HEADS_DIR);
        if (!branchList.contains(branchName)) {
            Utils.exitWithMessage("A branch with that name does not exist.");
        }
    }

    private static void writeFiles(List<String> filesOnlyTrackedByCheck, Commit newCommit) {
        for (String fileName : filesOnlyTrackedByCheck) {
            File file = Utils.join(CWD, fileName);
            if (file.exists()) {
                Utils.exitWithMessage("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
        overwriteFiles(filesOnlyTrackedByCheck, newCommit);
    }

    private static void overwriteFiles(List<String> filesBothTracked, Commit commit) {
        for (String fileName : filesBothTracked) {
            Blob blob = commit.getBlobByName(fileName);
            Utils.writeContents(Utils.join(CWD, fileName), blob.getContent());
        }
    }

    private static void deleteFiles(List<String> filesOnlyTrackedByCurr) {
        for (String fileName : filesOnlyTrackedByCurr) {
            File file = Utils.join(CWD, fileName);
            Utils.restrictedDelete(file);
        }
    }

    private static List<String> getFilesOnlyTrackedByCheck(List<String> originalFiles, List<String> checkFiles) {
        List<String> res = new ArrayList<>();
        for (String file : checkFiles) {
            if (!originalFiles.contains(file)) {
                res.add(file);
            }
        }
        return res;
    }

    private static List<String> getFilesBothTracked(List<String> originalFiles, List<String> checkFiles) {
        List<String> res = new ArrayList<>();
        for (String file : originalFiles) {
            if (checkFiles.contains(file)) {
                res.add(file);
            }
        }
        return res;
    }

    private static List<String> getFilesOnlyTrackedByCurr(List<String> originalFiles, List<String> checkFiles) {
        List<String> res = new ArrayList<>();
        for (String file : originalFiles) {
            if (!checkFiles.contains(file)) {
                res.add(file);
            }
        }
        return res;
    }

    private static Commit getCommitByBranch(String branchName) {
        String commitId = Utils.readContentsAsString(Utils.join(HEADS_DIR, branchName));
        return Utils.readObject(Utils.join(OBJECTS_DIR, commitId), Commit.class);
    }

    private static void printModifiedAndUntrackedInfos() {
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    private static void printRemoveStage() {
        System.out.println("=== Removed Files ===");
        removeStage = getRemoveStage();
        for (String fileName : removeStage.getBlobNames()) {
            System.out.println(fileName);
        }
        System.out.println();
    }

    private static void printAddStage() {
        System.out.println("=== Staged Files ===");
        addStage = getAddStage();
        for (String fileName : addStage.getBlobNames()) {
            System.out.println(fileName);
        }
        System.out.println();
    }

    private static void printAllBranches() {
        currentBranch = getCurrentBranch();
        System.out.println("=== Branches ===");
        System.out.println("*" + currentBranch);
        List<String> branches = Utils.plainFilenamesIn(HEADS_DIR);
        for (String branch : branches) {
            if (!branch.equals(currentBranch)) {
                System.out.println(branch);
            }
        }
        System.out.println();
    }

    private static void printCommitLog(Commit commit) {
        // the log of commit looks like:
        // ===
        // commit 3e8bf1d794ca2e9ef8a4007275acf3751c7170ff
        // Merge: 4975af1 2c1ead1
        // Date: Sat Nov 11 12:30:00 2017 -0800
        // Merged development into master.

        System.out.println("===");
        System.out.println("commit " + commit.getId());
        if (commit.hasSecondParent()) {
            System.out.println("Merge: " + commit.getParent().substring(0, 7) + " " + commit.getSecondParent().substring(0, 7));
        }
        System.out.println("Date: " + commit.getTimeStamp());
        System.out.println(commit.getMessage());
        System.out.println();
    }

    private static TreeMap<String, String> calFinalMap(TreeMap<String, String> original,
                                                       TreeMap<String, String> add, TreeMap<String, String> remove) {
        if (!add.isEmpty()) {
            for (String fileName : add.keySet()) {
                original.put(fileName, add.get(fileName));
            }
        }
        if (!remove.isEmpty()) {
            for (String fileName : remove.keySet()) {
                original.remove(fileName);
            }
        }
        return original;
    }

    private static void clearStage() {
        addStage = getAddStage();
        removeStage = getRemoveStage();
        addStage.clear();
        addStage.saveTo(ADD_STAGE_FILE);
        removeStage.clear();
        removeStage.saveTo(REMOVE_STAGE_FILE);
    }

    private static Commit getCurrentCommit() {
        String currentBranch = getCurrentBranch();
        String commitId = Utils.readContentsAsString(Utils.join(HEADS_DIR, currentBranch));
        return getCommitById(commitId);
    }

    private static String getCurrentBranch() {
        return Utils.readContentsAsString(HEAD_FILE);
    }

    private static Commit getCommitById(String id) {
        if (id.length() == 40) {
            File file = Utils.join(OBJECTS_DIR, id);
            if (!file.exists()) {
                return null;
            }
            return Utils.readObject(Utils.join(OBJECTS_DIR, id), Commit.class);
        }
        // if the given id is short
        List<String> ids = Utils.plainFilenamesIn(OBJECTS_DIR);
        for (String s : ids) {
            if (id.equals(s.substring(0, id.length()))) {
                return Utils.readObject(Utils.join(OBJECTS_DIR, s), Commit.class);
            }
        }
        return null;
    }

    private static Stage getAddStage() {
        if (!ADD_STAGE_FILE.exists()) {
            return new Stage();
        }
        return Utils.readObject(ADD_STAGE_FILE, Stage.class);
    }

    private static Stage getRemoveStage() {
        if (!REMOVE_STAGE_FILE.exists()) {
            return new Stage();
        }
        return Utils.readObject(REMOVE_STAGE_FILE, Stage.class);
    }

}
