import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

public class ParallelBacktracking extends RecursiveTask<Boolean> {
    private int[][] board;
    private int depth; //Depth of recursion to stop calling new threads
    public static final int SIZE = 9;
    public static final int MAX_DEPTH = 2; // Maximum depth level
    public static final int MAX_THREADS = 20; // Maximum number of threads

    public ParallelBacktracking(int[][] board, int depth) {
        this.board = board;
        this.depth = depth;
    }

    @Override
    protected Boolean compute() {
        // Find the first empty cell
        int row = -1, col = -1;
        boolean emptyFound = false;
        for (int i = 0; i < board.length && !emptyFound; i++) {
            for (int j = 0; j < board[i].length; j++) {
                if (board[i][j] == 0) {
                    row = i;
                    col = j;
                    emptyFound = true;
                    break;
                }
            }
        }

        // If no empty cell is found, the board is solved
        if (!emptyFound) {
            return true;
        }

        // Try all possible numbers for this cell
        for (int num = 1; num <= 9; num++) {
            if (isValidMove(board, row, col, num)) {
                board[row][col] = num;
                
                ParallelBacktracking task = new ParallelBacktracking(board, depth + 1);
                
                // Limit parallel execution depth or decide based on a condition
                if (depth < MAX_DEPTH && ForkJoinPool.getCommonPoolParallelism() < MAX_THREADS) {
                    task.fork(); // Run this task in parallel
                    boolean result = task.join(); // Wait for the result
                    if (result) {
                        return true;
                    }
                } else {
                    if (task.compute()) { // Solve sequentially
                        return true;
                    }
                }

                board[row][col] = 0; // Backtrack
            }
        }

        return false;
    }

    // Checks if a move is valid
    public static boolean isValidMove(int[][] board, int row, int col, int number) {
        // Check row and column
        for (int i = 0; i < SIZE; i++) {
            if (board[row][i] == number || board[i][col] == number) {
                return false;
            }
        }

        // Check 3x3 subgrid
        int subRowStart = row - row % 3;
        int subColStart = col - col % 3;

        for (int subRow = 0; subRow < 3; subRow++) {
            for (int subCol = 0; subCol < 3; subCol++) {
                if (board[subRowStart + subRow][subColStart + subCol] == number) {
                    return false;
                }
            }
        }

        return true; // No violation found
    }
    public static void main(String[] args) {
        int[][] board = {
            { 5, 3, 0, 0, 7, 0, 0, 0, 0 },
            { 6, 0, 0, 1, 9, 5, 0, 0, 0 },
            { 0, 9, 8, 0, 0, 0, 0, 6, 0 },
            { 8, 0, 0, 0, 6, 0, 0, 0, 3 },
            { 4, 0, 0, 8, 0, 3, 0, 0, 1 },
            { 7, 0, 0, 0, 2, 0, 0, 0, 6 },
            { 0, 6, 0, 0, 0, 0, 2, 8, 0 },
            { 0, 0, 0, 4, 1, 9, 0, 0, 5 },
            { 0, 0, 0, 0, 8, 0, 0, 7, 9 }            
        };
        // Start time
        long startTime = System.currentTimeMillis();

        ForkJoinPool pool = new ForkJoinPool(MAX_THREADS);
        ParallelBacktracking solver = new ParallelBacktracking(board, 0); // Start at depth level 0
        boolean solved = pool.invoke(solver);
        
        if (solved) {
            System.out.println("Sudoku solved.");
        } else {
            System.out.println("Sudoku cannot be solved.");
        }

        // End time
        long endTime = System.currentTimeMillis();
        System.out.println("Run time solving board on single thread: " + (endTime - startTime) + " milliseconds.");
    }
}
