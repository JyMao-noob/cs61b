package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Jiayi Mao
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // if args is empty, exit with message.
        if(args.length==0){
            Utils.exitWithMessage("Please enter a command.");
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                validateArgs(args,1);
                Repository.init();
                break;
            case "add":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.add(args[1]);
                break;
            case "commit":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.commit(args[1]);
                break;
            case "rm":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.rm(args[1]);
                break;
            case "log":
                Utils.checkIfRepositoryInit();
                validateArgs(args,1);
                Repository.log();
                break;
            case "global-log":
                Utils.checkIfRepositoryInit();
                validateArgs(args,1);
                Repository.globalLog();
                break;
            case "find":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.find(args[1]);
                break;
            case "status":
                Utils.checkIfRepositoryInit();
                validateArgs(args,1);
                Repository.status();
                break;
            case "checkout":
                Utils.checkIfRepositoryInit();
                switch (args.length){
                    case 3:
                        // checkout -- [file name]
                        if(!args[1].equals("--")){
                            Utils.exitWithMessage("Incorrect operands.");
                        }
                        Repository.checkout(args[2]);
                        break;
                    case 4:
                        // checkout [commit id] -- [file name]
                        if(!args[2].equals("--")){
                            Utils.exitWithMessage("Incorrect operands.");
                        }
                        Repository.checkout(args[1],args[3]);
                        break;
                    case 2:
                        // checkout [branch name]
                        Repository.checkoutBranch(args[1]);
                        break;
                    default:
                        Utils.exitWithMessage("Incorrect operands.");
                }
                break;
            case "branch":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.reset(args[1]);
                break;
            case "merge":
                Utils.checkIfRepositoryInit();
                validateArgs(args,2);
                Repository.merge(args[1]);
                break;
            default:
                Utils.exitWithMessage("No command with that name exists.");
        }
    }

    private static void validateArgs(String[] args,int count){
        if(args.length!=count){
            Utils.exitWithMessage("Incorrect operands.");
        }
    }
}
