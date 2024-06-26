import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;

public class SudokuSolver {
    public static final int ROWS = 9;
    public static final int COLS = 9;
    public static long startTime = 0; // Added by John
    public static long endTime; // Added by John
    public static boolean success; // Added by John
    public static final int MAX_THREADS = 20; // Maximum number of threads to run for Parallel Backtracking algo; added
                                              // by John

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java SudokuSolver <filename> <algorithm>");
            return;
        }
        String filename = args[0];
        String algorithm = args[1];

        // Decide algorithm based on argument
        switch (algorithm) {
            case "simAnnealing":
                startTime = System.currentTimeMillis();
                System.out.println("Running simulated annealing algorithm.");
                SimAnnealing simAnnealing = new SimAnnealing(readFile(filename));
                simAnnealing.solve();
                endTime = System.currentTimeMillis();
                System.out.println(
                        "Run time solving board on single thread: " + (endTime - startTime) + " milliseconds.");
                break;

            case "parallelSimAnnealing":
                startTime = System.currentTimeMillis();
                System.out.println("Running parallel simulated annealing algorithm.");
                ParallelSimAnnealing parallelSimAnnealing = new ParallelSimAnnealing(readFile(filename));
                parallelSimAnnealing.solve();
                endTime = System.currentTimeMillis();
                System.out.println(
                        "Run time solving board on multiple threads: " + (endTime - startTime) + " milliseconds.");
                break;

            case "backtracking":
                System.out.println("Running backtracking algorithm.");
                Backtracking backtracking = new Backtracking(readFile(filename));
                startTime = System.currentTimeMillis();
                success = backtracking.solve();
                if (success)
                    printSudoku(backtracking.getBoard());
                else
                    System.out.println("Unsolvable board");
                endTime = System.currentTimeMillis();
                System.out.println(
                        "Run time solving board on single thread: " + (endTime - startTime) + " milliseconds.");
                break;

            case "parallelizedBacktracking":
                System.out.println("Running parallelized backtracking algorithm");
                ParallelBacktracking parallelizedBacktracking = new ParallelBacktracking(readFile(filename));
                startTime = System.currentTimeMillis();
                ForkJoinPool pool = new ForkJoinPool(MAX_THREADS); // Incorporating in here allows more control over
                                                                   // lifecycle of thread pool
                success = pool.invoke(parallelizedBacktracking);
                if (success)
                    printSudoku(ParallelBacktracking.getBoard());
                else
                    System.out.println("Unsolvable board");
                endTime = System.currentTimeMillis();
                System.out.println(
                        "Run time solving board on multiple threads: " + (endTime - startTime) + " milliseconds.");
                break;

            case "logical":
                System.out.println("Running logical algorithm.");
                Logical logical = new Logical(readFile(filename));
                startTime = System.currentTimeMillis();
                logical.solve();
                endTime = System.currentTimeMillis();
                System.out.println(
                        "Run time solving board on single thread: " + (endTime - startTime) + " milliseconds.");
                break;

            case "parallelLogical":
                System.out.println("Running parallelized logical algorithm.");
                ParallelLogical parallelLogical = new ParallelLogical(readFile(filename));
                startTime = System.currentTimeMillis();
                parallelLogical.solve();
                endTime = System.currentTimeMillis();
                System.out.println("Run time solving board on multiple independent threads: " + (endTime - startTime)
                        + " milliseconds.");
                break;

            case "coordinatedLogical":
                System.out.println("Running coordinated logical algorithm.");
                CoordinatedLogical coordinatedLogical = new CoordinatedLogical(readFile(filename));
                startTime = System.currentTimeMillis();
                coordinatedLogical.solve();
                endTime = System.currentTimeMillis();
                System.out.println("Run time solving board on multiple coordinated threads: " + (endTime - startTime)
                        + " milliseconds.");
                break;

            default:
                System.out.println("Please enter a valid algorithm (bruteforce, backtracking, logical).");
                break;
        }
    }

    private static int[][] readFile(String filename) {
        // read the sudoku file and insert it into an integer matrix
        int[][] sudokuGrid = new int[ROWS][COLS];
        File sudokuFile = new File(filename);
        Scanner fileScan = null;

        try {
            fileScan = new Scanner(sudokuFile);
        } catch (FileNotFoundException e) {
            System.out.println("Sudoku file not found!");
            System.exit(1);
        }

        for (int i = 0; i < ROWS; i++) {
            String line = fileScan.nextLine();
            for (int j = 0; j < COLS; j++) {
                int cell = Character.getNumericValue(line.charAt(j)); // returns -1 if the character is '.'
                if (cell == -1) {
                    sudokuGrid[i][j] = 0;
                } else {
                    sudokuGrid[i][j] = cell;
                }
            }
        }

        return sudokuGrid;
    }

    private static void printSudoku(int[][] sudoku) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (j % 3 == 0 && j != 0)
                    System.out.print(" |");
                System.out.print(" " + sudoku[i][j]);
            }
            System.out.print('\n');
            if (i % 3 == 2 && i != 8)
                System.out.println("-------|-------|-------");
        }
    }
}
