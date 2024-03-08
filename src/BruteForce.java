import java.lang.Math;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class BruteForce {
    int[][] sudoku;
    int size;

    public BruteForce(int[][] sudoku) {
        this.sudoku = sudoku;
        this.size = sudoku.length;
    }

    public int[][] solve() {
        boolean solutionFound = false;
        while (!solutionFound) {
            double coolingRate = 0.99;
            int stuckCount = 0;
            int[][] fixedSudoku = new int[sudoku.length][];
            for (int i = 0; i < sudoku.length; i++) {
                fixedSudoku[i] = sudoku[i].clone();
            }
            // Print Sudoku
            fixSudokuValues(fixedSudoku);
            ArrayList<ArrayList<ArrayList<Integer>>> listBlocks = create3x3Blocks();
            int[][] tmpSudoku = randomlyFill3x3Blocks(sudoku, listBlocks);
            double sigma = calculateInitialSigma(tmpSudoku, fixedSudoku, listBlocks);
            int score = calculateNumberOfErrors(convert2dArray(tmpSudoku));
            int iterations = chooseNumberOfIterations(fixedSudoku);
            if (score <= 0) {
                solutionFound = true;
            }

            while (!solutionFound) {
                int previousScore = score;
                for (int i = 0; i < iterations; i++) {
                    ArrayList<ArrayList<ArrayList<Integer>>> newState = chooseNewState(tmpSudoku, fixedSudoku,
                            listBlocks, sigma);
                    tmpSudoku = newState.get(0);
                    int scoreDiff = newState.get(1).get(0).get(0);
                    score += scoreDiff;
                    System.out.println(score);
                    if (score <= 0) {
                        solutionFound = true;
                        break;
                    }
                }

                sigma *= coolingRate;
                if (score <= 0) {
                    solutionFound = true;
                    break;
                }
                if (score >= previousScore) {
                    stuckCount += 1;
                } else {
                    stuckCount = 0;
                }
                if (stuckCount > 80) {
                    sigma += 2;
                }
                if (calculateNumberOfErrors(convert2dArray(tmpSudoku)) == 0) {
                    // print
                    break;
                }
            }
        }

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

    private ArrayList<Integer> getMissingNumbers(ArrayList<Integer> row) {
        // Returns the missing numbers in a row
        ArrayList<Integer> missing_numbers = new ArrayList<>();
        for (int i = 1; i < size + 1; i++) {
            // 1 through 9
            boolean hasNum = false;
            for (int j = 0; j < size; j++) {
                if (row.get(j) == i) {
                    hasNum = true;
                    break;
                }
            }
            if (!hasNum) {
                missing_numbers.add(i);
            }
        }

        return missing_numbers;
    }

    private ArrayList<ArrayList<ArrayList<Integer>>> create3x3Blocks() {
        // finalListOfBlocks = []
        ArrayList<ArrayList<ArrayList<Integer>>> finalListOfBlocks = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            ArrayList<ArrayList<Integer>> tmpList = new ArrayList<>();
            ArrayList<Integer> block1 = new ArrayList<>();
            ArrayList<Integer> block2 = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                block1.add(j + 3 * (i % 3));
                block2.add(j + 3 * (i / 3));
            }
            for (int row : block1) {
                for (int col : block2) {
                    ArrayList<Integer> coordinate = new ArrayList<>();
                    coordinate.add(row);
                    coordinate.add(col);
                    tmpList.add(coordinate);
                }
            }
            finalListOfBlocks.add(tmpList);
        }
        return finalListOfBlocks;
    }

    private int[][] randomlyFill3x3Blocks(int[][] sudoku, ArrayList<ArrayList<ArrayList<Integer>>> listOfBlocks) {
        for (ArrayList<ArrayList<Integer>> block : listOfBlocks) {
            for (ArrayList<Integer> box : block) {
                if (sudoku[box.get(0)][box.get(1)] == 0) {
                    ArrayList<Integer> currentBlock = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        currentBlock.add(sudoku[block.get(i).get(0)][block.get(i).get(1)]);
                    }
                    ArrayList<Integer> missingNumbers = getMissingNumbers(currentBlock);
                    Random rand = new Random();
                    int randomFill = missingNumbers.get(rand.nextInt(missingNumbers.size()));
                    sudoku[box.get(0)][box.get(1)] = randomFill;
                }
            }
        }
        return sudoku;
    }

    private ArrayList<ArrayList<ArrayList<Integer>>> proposedState(ArrayList<ArrayList<Integer>> sudoku,
            int[][] fixedSudoku,
            ArrayList<ArrayList<ArrayList<Integer>>> listBlocks) {
        Random rand = new Random();
        ArrayList<ArrayList<Integer>> randomBlock = listBlocks.get(rand.nextInt(listBlocks.size()));

        if (sumOfOneBlock(fixedSudoku, randomBlock) > 6) {
            // return sudoku, 1, 1. TODO: fix code
        }
        ArrayList<ArrayList<Integer>> boxesToFlip = twoRandomBoxesWithinBlock(fixedSudoku, randomBlock);
        ArrayList<ArrayList<Integer>> proposedSudoku = FlipBoxes(sudoku, boxesToFlip);
        ArrayList<ArrayList<ArrayList<Integer>>> proposedStateArray = new ArrayList<>();
        proposedStateArray.add(proposedSudoku);
        proposedStateArray.add(boxesToFlip);
        return proposedStateArray;
    }

    private int sumOfOneBlock(int[][] sudoku, ArrayList<ArrayList<Integer>> oneBlock) {
        // returns the sum of numbers in a 3x3 block
        int finalSum = 0;
        for (ArrayList<Integer> box : oneBlock) {
            finalSum += sudoku[box.get(0)][box.get(1)];
        }
        return finalSum;
    }

    private ArrayList<ArrayList<Integer>> twoRandomBoxesWithinBlock(int[][] fixedSudoku,
            ArrayList<ArrayList<Integer>> block) {
        while (true) {
            ArrayList<Integer> firstBox = new ArrayList<>();
            Random rand = new Random();
            int randomIndex = rand.nextInt(block.size());
            firstBox = block.get(randomIndex);
            ArrayList<Integer> secondBox = new ArrayList<>();
            ArrayList<ArrayList<Integer>> secondBoxChoices = new ArrayList<>();
            for (int i = 0; i < block.size(); i++) {
                if (i != randomIndex) {
                    secondBoxChoices.add(block.get(i));
                }
            }
            secondBox = secondBoxChoices.get(rand.nextInt());
            if (fixedSudoku[firstBox.get(0)][firstBox.get(1)] != 1
                    && fixedSudoku[secondBox.get(0)][secondBox.get(1)] != 1) {
                ArrayList<ArrayList<Integer>> twoBoxes = new ArrayList<>();
                twoBoxes.add(firstBox);
                return twoBoxes;
            }
        }
    }

    private int[][] fixSudokuValues(int[][] fixedSudoku) {
        for (int i = 0; i < fixedSudoku.length; i++) {
            for (int j = 0; j < fixedSudoku.length; j++) {
                if (fixedSudoku[i][j] != 0) {
                    fixedSudoku[i][j] = 1;
                }
            }
        }
        return fixedSudoku;
    }

    private ArrayList<ArrayList<Integer>> FlipBoxes(ArrayList<ArrayList<Integer>> sudoku,
            ArrayList<ArrayList<Integer>> boxesToFlip) {
        ArrayList<ArrayList<Integer>> proposedSudoku = new ArrayList<>();

        // Copy Sudoku ArrayList
        for (int i = 0; i < this.sudoku.length; i++) {
            ArrayList<Integer> copiedRow = new ArrayList<>();
            for (int j = 0; j < this.sudoku.length; j++) {
                copiedRow.add(sudoku.get(i).get(j));
            }
            proposedSudoku.add(copiedRow);
        }

        int temp = proposedSudoku.get(boxesToFlip.get(0).get(0)).get(boxesToFlip.get(0).get(1));

        proposedSudoku.get(boxesToFlip.get(0).get(0)).set(boxesToFlip.get(0).get(1),
                proposedSudoku.get(boxesToFlip.get(1).get(0)).get(boxesToFlip.get(1).get(1)));

        proposedSudoku.get(boxesToFlip.get(1).get(0)).set(boxesToFlip.get(1).get(1), temp);

        return proposedSudoku;
    }

    private int calculateNumberOfErrors(ArrayList<ArrayList<Integer>> sudoku) {
        int numberOfErrors = 0;
        for (int i = 0; i < 9; i++) {
            numberOfErrors += calculateNumberOfErrorsRowColumn(i, i, sudoku);
        }
        return numberOfErrors;
    }

    private int calculateNumberOfErrorsRowColumn(int row, int col, ArrayList<ArrayList<Integer>> sudoku) {
        // number of duplicates in the row + number of duplicates in the column
        int numberOfErrors = 0;
        // List of unique numbers
        ArrayList<Integer> uniqueNumbers = new ArrayList<>();
        for (int i = 0; i < this.sudoku.length; i++) {
            // If sudoku[row][i] is not in uniqueNumbers, add it to uniqueNumbers
            // Otherwise, increment numberOfErrors
            if (uniqueNumbers.contains(sudoku.get(row).get(i))) {
                numberOfErrors += 1;
            } else {
                uniqueNumbers.add(sudoku.get(row).get(i));
            }
        }
        // Same process for sudoku[i][col]. Reset uniqueNumbers
        uniqueNumbers.clear();
        for (int i = 0; i < this.sudoku.length; i++) {
            // If sudoku[row][i] is not in uniqueNumbers, add it to uniqueNumbers
            // Otherwise, increment numberOfErrors
            if (uniqueNumbers.contains(sudoku.get(i).get(col))) {
                numberOfErrors += 1;
            } else {
                uniqueNumbers.add(sudoku.get(i).get(col));
            }
        }
        return numberOfErrors;
    }

    private double popStdDev(ArrayList<Integer> list) {
        double sum = 0.0;
        for (int i : list) {
            sum += i;
        }

        int length = list.size();
        double mean = sum / length;

        double standardDeviation = 0.0;
        for (int i : list) {
            standardDeviation += Math.pow(i - mean, 2);
        }
        return Math.sqrt(standardDeviation / length);
    }

    private double calculateInitialSigma(int[][] sudoku, int[][] fixedSudoku,
            ArrayList<ArrayList<ArrayList<Integer>>> listBlocks) {

        ArrayList<Integer> listOfDifferences = new ArrayList<>();
        ArrayList<ArrayList<Integer>> tmpSudoku = convert2dArray(sudoku);

        for (int i = 1; i < 10; i++) {
            tmpSudoku = proposedState(tmpSudoku, fixedSudoku, listBlocks).get(0);
            listOfDifferences.add(calculateNumberOfErrors(tmpSudoku));
        }

        return popStdDev(listOfDifferences);
    }

    private int chooseNumberOfIterations(int[][] fixedSudoku) {
        int numberOfIterations = 0;
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (fixedSudoku[i][j] != 0) {
                    numberOfIterations += 1;
                }
            }
        }
        return numberOfIterations;
    }

    private ArrayList<ArrayList<Integer>> convert2dArray(int[][] array) {
        ArrayList<ArrayList<Integer>> convertedArray = new ArrayList<>();
        for (int i = 0; i < sudoku.length; i++) {
            ArrayList<Integer> sudokuRow = new ArrayList<>();
            for (int j = 0; j < sudoku.length; j++) {
                sudokuRow.add(sudoku[i][j]);
            }
            convertedArray.add(sudokuRow);
        }
        return convertedArray;
    }

    private ArrayList<ArrayList<ArrayList<Integer>>> chooseNewState(int[][] currentSudoku, int[][] fixedSudoku,
            ArrayList<ArrayList<ArrayList<Integer>>> listOfBlocks, double sigma) {
        ArrayList<ArrayList<ArrayList<Integer>>> proposal = proposedState(convert2dArray(currentSudoku), fixedSudoku,
                listOfBlocks);
        ArrayList<ArrayList<Integer>> newSudoku = proposal.get(0);
        ArrayList<ArrayList<Integer>> boxesToCheck = proposal.get(1);
        int currentCost = calculateNumberOfErrorsRowColumn(boxesToCheck.get(0).get(0), boxesToCheck.get(0).get(1),
                convert2dArray(currentSudoku))
                + calculateNumberOfErrorsRowColumn(boxesToCheck.get(1).get(0), boxesToCheck.get(1).get(1),
                        convert2dArray(currentSudoku));
        int newCost = calculateNumberOfErrorsRowColumn(boxesToCheck.get(0).get(0), boxesToCheck.get(0).get(1),
                newSudoku)
                + calculateNumberOfErrorsRowColumn(boxesToCheck.get(1).get(0), boxesToCheck.get(1).get(1), newSudoku);
        int costDifference = newCost - currentCost;
        double rho = Math.exp(-costDifference / sigma);
        ArrayList<ArrayList<ArrayList<Integer>>> newState = new ArrayList<>();
        if (1 < rho) {
            newState.add(newSudoku);
            // wrapping time
            ArrayList<Integer> cd = new ArrayList<>();
            cd.add(costDifference);
            ArrayList<ArrayList<Integer>> wrap = new ArrayList<>();
            wrap.add(cd);
            newState.add(wrap);
            return newState;
        }
        newState.add(convert2dArray(currentSudoku));
        ArrayList<Integer> cd = new ArrayList<>();
        cd.add(0);
        ArrayList<ArrayList<Integer>> wrap = new ArrayList<>();
        wrap.add(cd);
        newState.add(wrap);
        return newState;
    }
}