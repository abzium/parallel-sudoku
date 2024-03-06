/*
 * John Dufresne
 * Solving a 9x9 sudoku board using backtracking (not parallelized)
 */

public class Backtracking {
    private int[][] board; // Sudoku Board
    public static final int SIZE = 9;

    public Backtracking(int[][] board) {
        this.board = board;
    }

    public boolean solve() {
        // Loop through each row and column
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                // If board position value is 0, we need to try a number
                if (board[row][col] == 0) {
                    // Try all possible numbers, (1-9)
                    for (int number = 1; number <= SIZE; number++) {
                        if (isValidMove(board, row, col, number)) {
                            // Place the number
                            board[row][col] = number;
                            /*
                                Recursivly solve the rest of the board
                                If solve() returns true, board has been successfully solved
                                Otherwise, we need to continue backtracking
                            */
                            if (solve()) {
                                return true;
                            } else {
                                board[row][col] = 0;
                            }
                        }
                    }
                    return false; // No valid number found, backtrack
                }
            }
        }
        return true; // Solved
    }

    // Checks to see if number meets the requirements of a successful move in Sudoku
    private boolean isValidMove(int[][] board, int row, int col, int number){
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
        return true; // Valid Move
    }

    // Returns the completed board
    public int[][] getBoard(){
        return board;
    }
}
