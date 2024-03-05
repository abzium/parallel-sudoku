import java.lang.Math;

public class BruteForce {
    int[][] sudoku;
    int size;

    public BruteForce(int[][] sudoku) {
        this.sudoku = sudoku;
        this.size = sudoku.length;
    }

    public int[][] solve() {

        return sudoku;
    }

    private boolean isValid(int row, int col, int num) {
        // Check if a number does not violate the rules
        // Check the row for violations
        for (int i = 0; i < size; i++) {
            if (sudoku[row][i] == num)
                return false;
        }
        // Check column for violations
        for (int i = 0; i < size; i++) {
            if (sudoku[i][col] == num)
                return false;
        }
        // Check 3x3 square for violations
        // Get top right corner of square
        int squareSize = (int) Math.sqrt(size);
        int rowSquare = (row / squareSize) * squareSize;
        int colSquare = (col / squareSize) * squareSize;
        for (int i = rowSquare; i < rowSquare + squareSize; i++) {
            for (int j = colSquare; j < colSquare + squareSize; j++) {
                if (sudoku[i][j] == num)
                    return false;
            }
        }

        return true;
    }

}

// 1. grab a row from the board
// 2. make an array Fill of the numbers that are needed to complete the row
// 3. input the first element of Fill into the board and check if it is valid
// 4. if it is, move on to the next number in the Fill array and input it into
// the next spot on the row
// 5. if it isnt, continue and reorder the array
// 6. if we get a complete valid row, save that whole row
// 7. repeat for every row
// 8. combine bos
