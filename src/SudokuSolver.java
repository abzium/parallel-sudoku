public class SudokuSolver {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java SudokuSolver <filename> <algorithm>");
            return;
        }
        String filename = args[0];
        String algorithm = args[1];

        // Decide algorithm based on argument
        switch (algorithm) {
            case "bruteforce":
                System.out.println("Running brute force algorithm.");
                BruteForce bruteForce = new BruteForce(readFile(filename));
                bruteForce.solve();
                break;

            case "backtracking":
                System.out.println("Running backtracking algorithm.");
                Backtracking backtracking = new Backtracking(readFile(filename));
                backtracking.solve();
                break;

            case "logical":
                System.out.println("Running logical algorithm.");
                Logical logical = new Logical(readFile(filename));
                logical.solve();
                break;

            default:
                System.out.println("Please enter a valid algorithm (bruteforce, backtracking, logical).");
                break;
        }
    }

    private static int[][] readFile(String filename) {
        // TODO: implement
        throw new UnsupportedOperationException("readFile not yet implemented!");
    }
}
