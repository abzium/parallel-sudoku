import java.util.Arrays;

/**
 * @implNote Every deduction strategy (the {@code private} methods besides {@link #setValue} and {@link #clearCandidateBox}) has the following attributes:
 * <ul><li>name ending in {@code Cols}, {@code Rows}, or {@code Box},
 *   indicating whether it scans across the {@link #BOX_WIDTH} columns the box occupies, the {@link #BOX_HEIGHT} rows, or just the box itself
 * <li>parameters {@code (minY, maxY, minX, maxX)}, with {@code min}s inclusive and {@code max}es exclusive,
 *   indicating the box of cells to be modified
 * <li>does <em>not</em> add additional {@link #candidates} or remove solved {@link #sudoku} cells,
 *   nor actively attempt to solve cells outside the specified box (eliminating others' candidates is okay though)
 * <li>conversely, assumes no {@link #candidates} will be added or {@link #sudoku} cells will be removed during its execution
 * <li>returns {@code true} if any {@link #candidates} have been removed or {@link #sudoku} cells have been filled within the specified box.</ul>
 * Note that {@link #pointingBox} never attempts to modify the given box, instead eliminating candidates in the <em>other</em> boxes in its row/column,
 * so instead of returning anything it sets the appropriate {@code row}/{@code colDirtied} states directly.
 * @implNote Also, the following conventions are adhered to within each method:
 * <ul><li>{@code i} and {@code j} refer to the column/row currently being modified.
 * <li>{@code i1} and {@code j1} (and so on) refer to the column/row being compared.
 * <li>{@code c} is the candidate being considered.</ul>
 */
public class Logical {
  /**
   * Exception thrown to indicate when a Sudoku cannot be solved.
   */
  public static class UnsolvableException extends Exception {
    public UnsolvableException() {}
    public UnsolvableException(String msg) {
      super(msg);
    }
  }
  @FunctionalInterface
  public static interface FallbackSolver {
    /**
     * Solves the grid in-place. Must be idempotent on already-solved grids.
     * @param sudoku The sudoku to solve.
     * @param candidates The remaining candidates.
     * @throws UnsolvableException If solving the grid failed.
     */
    void solve(int[][] sudoku, boolean[][][] candidates) throws UnsolvableException;

    /**
     * Finds a cell with the fewest candidates, and tries each of them with the {@link Logical} solver until one works.
     */
    FallbackSolver guessAndCheck = (sudoku, candidates) -> {
      // find a cell by fewest candidates
      int minCandidates = SIZE + 1,
        minI = -1, minJ = -1;
      for (int i = 0; i < SIZE; i++) {
        for (int j = 0; j < SIZE; j++) {
          if (sudoku[i][j] != UNKNOWN) continue;
          int numCandidates = 0;
          for (int c = 1; c <= SIZE; c++) if (candidates[i][j][c]) numCandidates++;
          if (numCandidates < minCandidates) {
            minCandidates = numCandidates;
            minI = i;
            minJ = j;
          }
        }
      }
      // if there was no such unfilled cell, the grid's solved.
      if (minCandidates > SIZE) return;
      // otherwise, try each of its candidates
      final int[][] tempSudoku = new int[SIZE][];
      final boolean[][][] tempCandidates = new boolean[SIZE][SIZE][];
      for (int c = 1; c <= SIZE; c++) {
        if (!candidates[minI][minJ][c]) continue;
        // on a copy of the grid
        for (int i = 0; i < SIZE; i++) {
          tempSudoku[i] = sudoku[i].clone();
          for (int j = 0; j < SIZE; j++) {
            tempCandidates[i][j] = candidates[i][j].clone();
          }
        }
        tempSudoku[minI][minJ] = c;
        try {
          new Logical(tempSudoku, tempCandidates).solve(FallbackSolver.guessAndCheck);
          // if the solution worked copy it back into the original grid
          for (int i = 0; i < SIZE; i++) {
            sudoku[i] = tempSudoku[i];
            candidates[i] = tempCandidates[i];
          }
          return;
        } catch (UnsolvableException ignored) {}
      }
      throw new UnsolvableException("no guess for R" + minI + "C" + minJ + " worked!");
    };
    /**
     * Doesn't attempt to solve the grid, instead failing if there are any unknown cells.
     */
    FallbackSolver die = (sudoku, candidates) -> {
      for (int i = 0; i < SIZE; i++)
        for (int j = 0; j < SIZE; j++)
          if (sudoku[i][j] == UNKNOWN) throw new UnsolvableException("no fallback!");
    };
  }

  // i THINK the algorithms here also work for different board sizes?
  // regardless, good to avoid magic numbers
  private static final int
    UNKNOWN = 0,  // value for undetermined cells in `sudoku`
    SIZE = 9,  // width and height of `sudoku`, and (inclusive) maximum a value can take
    BOX_WIDTH = 3, BOX_HEIGHT = 3,  // dimensions of each box
    NUM_BOXES_X = SIZE / BOX_WIDTH,  // number of boxes in each dimension
    NUM_BOXES_Y = SIZE / BOX_HEIGHT;

  /**
   * The solved grid so far.
   * @see #UNKNOWN
   */
  private final int[][] sudoku;
  /**
   * A {@code boolean[]} of length {@link #SIZE SIZE+1} for each cell in the grid, where the {@code c}th element indicates whether {@code c} is a potential value.
   * Index {@link #UNKNOWN <code>0</code>} is unused.
   * For cells with a nonzero {@link #sudoku} value {@code c}, the {@code c}th element must be {@code true} and other elements {@code false} (except possibly the {@code 0}th).
   */
  private final boolean[][][] candidates;
  /**
   * A {@code boolean} for each box in the grid, indicating whether its rows/columns have been modified since the last time {@link #doSolveStep} was called on it.
   */
  private final boolean[][] rowDirtied = new boolean[NUM_BOXES_Y][NUM_BOXES_X],
    colDirtied = new boolean[NUM_BOXES_Y][NUM_BOXES_X];

  /**
   * Initializes the solver.
   * <p>This does <em>not</em> check if the givens break any rules; if they do, behavior is undefined.
   * @param sudoku The sudoku grid, with {@link #UNKNOWN <code>0</code>s for unknowns} and values from 1 to {@link #SIZE} for givens.
   *   Should not be modified externally, otherwise this solver could be in an inconsistent state.
   * @throws IllegalArgumentException If the {@code sudoku} grid isn't a square of size {@link #SIZE}.
   */
  public Logical(int[][] sudoku) {
    this(sudoku, new boolean[SIZE][SIZE][SIZE + 1]);
    if (sudoku.length != SIZE)
      throw new IllegalArgumentException("wrong number of sudoku rows");
    if (Arrays.stream(sudoku).anyMatch(row -> row.length != SIZE))
      throw new IllegalArgumentException("wrong number of sudoku columns");
    for (boolean[][] row : candidates) for (boolean[] cell : row) Arrays.fill(cell, true);
  }
  private Logical(int[][] sudoku, boolean[][][] candidates) {
    assert candidates.length == SIZE : "wrong number of candidates rows";
    assert Arrays.stream(candidates).allMatch(row -> row.length == SIZE) : "wrong number of candidates columns";
    assert Arrays.stream(candidates).flatMap(Arrays::stream).allMatch(cell -> cell.length == SIZE + 1) : "wrong number of candidates entries";
    this.sudoku = sudoku;
    this.candidates = candidates;
  }

  /**
   * Prints the current state to {@link System#out}.
   * This includes the solution so far, and if it's not fully solved, the {@link #candidates} for each cell.
   */
  public void printState() {
    boolean anyUnknown = false;
    for (int[] row : sudoku) {
      for (int c : row) {
        if (c == UNKNOWN) {
          anyUnknown = true;
          System.out.print('.');
        } else System.out.print(c);
      }
      System.out.println();
    }
    if (!anyUnknown) return;
    System.out.println();
    for (int i = 0; i < SIZE; i++) {
      for (int j = 0; j < SIZE; j++) {
        System.out.print('[');
        for (int c = 1; c <= SIZE; c++) {
          if (c == sudoku[i][j]) System.out.print("\u001b[1m");
          else if (sudoku[i][j] != UNKNOWN) System.out.print("\u001b[2m");
          System.out.print(candidates[i][j][c] ? c : ".");
          System.out.print("\u001b[0m");
        }
        System.out.print(']');
        if ((j+1) % BOX_WIDTH == 0) System.out.print("  ");
      }
      System.out.println();
      if ((i+1) % BOX_HEIGHT == 0) System.out.println();
    }
  }

  /**
   * Solves the grid in-place as much as possible, using {@link FallbackSolver#guessAndCheck} when no more deductions can be made.
   * Only fails when there is no solution (printing as much of the grid as could be determined along with the remaining candidates);
   * prints one of the solutions when there are multiple.
   */
  public void solve() {
    try {
      solve(FallbackSolver.guessAndCheck);
    } catch (UnsolvableException e) {
      System.out.println("The sudoku couldn't be solved!");
      if (e.getMessage() != null) System.out.println("Reason: " + e.getMessage());
    }
    printState();
  }
  /**
   * Solves the grid in-place as much as possible.
   * @param fallback The solver to fall back on when no more deductions can be made.
   * @throws UnsolvableException If either a deduction was made that eliminates all possibilities, or the {@code fallback} failed to solve the Sudoku for any reason.
   */
  public void solve(FallbackSolver fallback) throws UnsolvableException {
    for (boolean[] row : rowDirtied) Arrays.fill(row, true);
    for (boolean[] row : colDirtied) Arrays.fill(row, true);
    fixCandidatesBoard();
    while (true) {
      boolean anyDirtied = false;
      // rows and columns are separate loops to cycle through the rest of the boxes once before swinging back around for the column
      for (int x = 0; x < NUM_BOXES_X; x++) {
        for (int y = 0; y < NUM_BOXES_Y; y++) {
          if (rowDirtied[y][x]) {
            doSolveStep(y, x, true);
            anyDirtied = true;
          }
        }
      }
      for (int y = 0; y < NUM_BOXES_Y; y++) {
        for (int x = 0; x < NUM_BOXES_X; x++) {
          if (colDirtied[y][x]) {
            doSolveStep(y, x, false);
            anyDirtied = true;
          }
        }
      }
      if (!anyDirtied) break;
    }
    fallback.solve(sudoku, candidates);
  }

  /**
   * Attempts to make each type of logical deduction, focusing on a single box and its row/column.
   * @param boxI The index of the box vertically, from 0 (inclusive) to {@link #NUM_BOXES_Y} (exclusive).
   * @param boxJ The index of the box horizontally, from 0 (inclusive) to {@link #NUM_BOXES_X} (exclusive).
   * @param isRow {@code true} to scan along the row, {@code false} for the column.
   * @throws UnsolvableException If any cells in the given box ran out of candidates.
   */
  void doSolveStep(int boxI, int boxJ, boolean isRow) throws UnsolvableException {
    final int minX = boxJ * BOX_WIDTH, maxX = minX + BOX_WIDTH,
      minY = boxI * BOX_HEIGHT, maxY = minY + BOX_HEIGHT;
    (isRow ? rowDirtied : colDirtied)[boxI][boxJ] = false;
    boolean dirtied = false;
    updateCandidatesBox(minY, maxY, minX, maxX);
    dirtied |= nakedSinglesBox(minY, maxY, minX, maxX);
    dirtied |= isRow ? hiddenSinglesRows(minY, maxY, minX, maxX) : hiddenSinglesCols(minY, maxY, minX, maxX);
    dirtied |= hiddenSinglesBox(minY, maxY, minX, maxX);
    dirtied |= isRow ? nakedPairsRows(minY, maxY, minX, maxX) : nakedPairsCols(minY, maxY, minX, maxX);
    dirtied |= nakedPairsBox(minY, maxY, minX, maxX);
    dirtied |= isRow ? hiddenPairsRows(minY, maxY, minX, maxX) : hiddenPairsCols(minY, maxY, minX, maxX);
    dirtied |= hiddenPairsBox(minY, maxY, minX, maxX);
    dirtied |= isRow ? boxLineRows(minY, maxY, minX, maxX) : boxLineCols(minY, maxY, minX, maxX);
    pointingBox(minY, maxY, minX, maxX);
    if (dirtied) {
      // also set both dirtied states for this box, because we'll need to do a second pass
      // in case we eliminated enough to create a single and we can now fill something in.
      // or we eliminated enough for a pair and can eliminate. etc.
      for (int i1 = 0; i1 < NUM_BOXES_Y; i1++) {
        colDirtied[i1][boxJ] = true;
      }
      for (int j1 = 0; j1 < NUM_BOXES_X; j1++) {
        rowDirtied[boxI][j1] = true;
      }
    }
  }

  // Updates the candidates on solved cells by eliminating those that don't match the cell's value.
  void fixCandidatesBoard() {
    for (int i = 0; i < SIZE; i++) {
      for (int j = 0; j < SIZE; j++) {
        if (sudoku[i][j] != UNKNOWN) {
          Arrays.fill(candidates[i][j], false);
          candidates[i][j][sudoku[i][j]] = true;
        }
      }
    }
  }

  // Updates the candidates in this box by eliminating those that match solved cells in their row, column, and box.
  // TODO: Should this return whether something changed in case the elimination of candidates is enough to make more deductions in other columns/rows? Or do the strategies for this box cover that already?
  private void updateCandidatesBox(int minY, int maxY, int minX, int maxX) {
    for (int i = minY; i < maxY; i++) {
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] == UNKNOWN) {
          for (int c : sudoku[i])
            candidates[i][j][c] = false;
          for (int i1 = 0; i1 < SIZE; i1++)
            candidates[i][j][sudoku[i1][j]] = false;
          for (int i1 = minY; i1 < maxY; i1++)
            for (int j1 = minX; j1 < maxX; j1++)
              candidates[i][j][sudoku[i1][j1]] = false;
        }
      }
    }
  }

  // Sets a known value while clearing the other candidates from that cell.
  private void setValue(int i, int j, int c) {
    for (int c1 = 1; c1 <= SIZE; c1++) if (c1 != c) candidates[i][j][c1] = false;
    sudoku[i][j] = c;
  }

  // Clears the given candidate value from a box.
  // Necessary because the singles methods continue iterating within the same box, and may hence attempt to assign c elsewhere
  // because it doesn't otherwise get removed until the next updateCandidatesBox. This only occurs if the puzzle's unsolvable,
  // but that can happen under normal circumstances, especially if we need to guessAndCheck.
  // Not otherwise necessary; in particular, shouldn't be a barrier to parallelization if boxes are locked properly.
  private void clearCandidateBox(int c, int minY, int maxY, int minX, int maxX) {
    for (int i = minY; i < maxY; i++) {
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != c) candidates[i][j][c] = false;
      }
    }
  }

  // Fills in cells that have only one candidate value.
  private boolean nakedSinglesBox(int minY, int maxY, int minX, int maxX) throws UnsolvableException {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue;
        // if it has only one candidate value,
        int value = UNKNOWN;
        for (int c = 1; c <= SIZE; c++) {
          if (candidates[i][j][c]) {
            if (value != UNKNOWN) continue cells;
            value = c;
          }
        }
        // (if it has none that's a problem)
        if (value == UNKNOWN) {
          throw new UnsolvableException("no candidates in cell R" + i + "C" + j + "!");
        }
        // fill it in
        setValue(i, j, value);
        clearCandidateBox(value, minY, maxY, minX, maxX);
        dirtied = true;
      }
    }
    return dirtied;
  }

  // Fills in cells that have a candidate value that no other cell in their region has.
  private boolean hiddenSinglesRows(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue;
        // for each of its candidates,
        candidates:
        for (int c = 1; c <= SIZE; c++) {
          if (!candidates[i][j][c]) continue;
          // if no other cell in the row has it,
          for (int j1 = 0; j1 < SIZE; j1++) {
            if (j1 == j) continue;
            if (candidates[i][j1][c]) continue candidates;
          }
          // fill it in
          setValue(i, j, c);
          clearCandidateBox(c, minY, maxY, minX, maxX);
          dirtied = true;
          break candidates;
        }
      }
    }
    return dirtied;
  }
  private boolean hiddenSinglesCols(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue;
        // for each of its candidates,
        candidates:
        for (int c = 1; c <= SIZE; c++) {
          if (!candidates[i][j][c]) continue;
          // if no other cell in the column has it,
          for (int i1 = 0; i1 < SIZE; i1++) {
            if (i1 == i) continue;
            if (candidates[i1][j][c]) continue candidates;
          }
          // fill it in
          setValue(i, j, c);
          clearCandidateBox(c, minY, maxY, minX, maxX);
          dirtied = true;
          break candidates;
        }
      }
    }
    return dirtied;
  }
  private boolean hiddenSinglesBox(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue;
        // for each of its candidates,
        candidates:
        for (int c = 1; c <= SIZE; c++) {
          if (!candidates[i][j][c]) continue;
          // if no other cell in the box has it,
          for (int i1 = minY; i1 < maxY; i1++) {
            for (int j1 = minX; j1 < maxX; j1++) {
              if (i1 == i && j1 == j) continue;
              if (candidates[i1][j1][c]) continue candidates;
            }
          }
          // fill it in
          setValue(i, j, c);
          clearCandidateBox(c, minY, maxY, minX, maxX);
          dirtied = true;
          break candidates;
        }
      }
    }
    return dirtied;
  }

  // Looks for cells within the same region which share their only two candidates, and eliminates those from other cells in that region.
  // Note that this also finds cells which share a single candidate, in which case this reduces to naked singles again which is fine.
  private boolean nakedPairsRows(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // if it only has two candidates,
        int candCount = 0;
        for (int c = 1; c <= SIZE; c++) {
          if (candidates[i][j][c]) {
            candCount++;
            if (candCount > 2) continue cells;
          }
        }
        // for each other cell in its row,
        row:
        for (int j1 = 0; j1 < SIZE; j1++) {
          if (j1 == j) continue row;
          // if they have the same candidates,
          for (int c = 1; c <= SIZE; c++) {
            if (candidates[i][j1][c] != candidates[i][j][c]) continue row;
          }
          // clear the other cells of the row of the two candidates
          for (int j2 = 0; j2 < SIZE; j2++) {
            if (j2 == j || j2 == j1 || sudoku[i][j2] != UNKNOWN) continue;
            for (int c = 1; c <= SIZE; c++) {
              if (candidates[i][j2][c] && candidates[i][j][c]) {
                candidates[i][j2][c] = false;
                dirtied = true;
              }
            }
          }
          continue cells;
        }
      }
    }
    return dirtied;
  }
  private boolean nakedPairsCols(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // if it has only two candidates,
        int candCount = 0;
        for (int c = 1; c <= SIZE; c++) {
          if (candidates[i][j][c]) {
            candCount++;
            if (candCount > 2) continue cells;
          }
        }
        // for each other cell in the column,
        col:
        for (int i1 = 0; i1 < SIZE; i1++) {
          if (i1 == i) continue col;
          // if they have the same candidates,
          for (int c = 1; c <= SIZE; c++) {
            if (candidates[i1][j][c] != candidates[i][j][c]) continue col;
          }
          // clear the other cells in the column of the two candidates
          for (int i2 = 0; i2 < SIZE; i2++) {
            if (i2 == i || i2 == i1 || sudoku[i2][j] != UNKNOWN) continue;
            for (int c = 1; c <= SIZE; c++) {
              if (candidates[i2][j][c] && candidates[i][j][c]) {
                candidates[i2][j][c] = false;
                dirtied = true;
              }
            }
          }
          continue cells;
        }
      }
    }
    return dirtied;
  }
  private boolean nakedPairsBox(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // if it has only two candidates,
        int candCount = 0;
        for (int c = 1; c <= SIZE; c++) {
          if (candidates[i][j][c]) {
            candCount++;
            if (candCount > 2) continue cells;
          }
        }
        // for each other cell in the box,
        for (int i1 = minY; i1 < maxY; i1++) {
          box:
          for (int j1 = minX; j1 < maxX; j1++) {
            if (i1 == i && j1 == j) continue box;
            // if they have the same candidates,
            for (int c = 1; c <= SIZE; c++) {
              if (candidates[i1][j1][c] != candidates[i][j][c]) continue box;
            }
            // clear the other cells in the box of the two candidates
            for (int i2 = minY; i2 < maxY; i2++){
              for (int j2 = minX; j2 < maxX; j2++) {
                if ((i2 == i && j2 == j) || (i2 == i1 && j2 == j1) || sudoku[i2][j2] != UNKNOWN) continue;
                for (int c = 1; c <= SIZE; c++) {
                  if (candidates[i2][j2][c] && candidates[i][j][c]) {
                    candidates[i2][j2][c] = false;
                    dirtied = true;
                  }
                }
              }
            }
            continue cells;
          }
        }
      }
    }
    return dirtied;
  }

  // Looks for cells which are the only two in their region that have two candidates, and eliminates other candidates from them.
  private boolean hiddenPairsRows(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // for each pair of its candidates,
        for (int c1 = 1; c1 < SIZE; c1++) {
          if (!candidates[i][j][c1]) continue;
          candidatePairs:
          for (int c2 = c1+1; c2 <= SIZE; c2++) {
            if (!candidates[i][j][c2]) continue;
            // if there's exactly one other cell in the row sharing those candidates,
            int otherJ = -1;
            for (int j1 = 0; j1 < SIZE; j1++) {
              if (j1 == j) continue;
              if (candidates[i][j1][c1] || candidates[i][j1][c2]) {
                if (otherJ != -1) continue candidatePairs;
                otherJ = j1;
              }
            }
            if (otherJ != -1) {
              // eliminate other candidates from both cells
              for (int c = 1; c <= SIZE; c++) {
                if (c == c1 || c == c2) continue;
                if (candidates[i][j][c] || candidates[i][otherJ][c]) {
                  candidates[i][j][c] = false;
                  candidates[i][otherJ][c] = false;
                  colDirtied[i/BOX_HEIGHT][otherJ/BOX_WIDTH] = true;
                  dirtied = true;
                }
              }
              continue cells;
            }
          }
        }
      }
    }
    return dirtied;
  }
  private boolean hiddenPairsCols(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // for each pair of its candidates,
        for (int c1 = 1; c1 < SIZE; c1++) {
          if (!candidates[i][j][c1]) continue;
          candidatePairs: for (int c2 = c1 + 1; c2 <= SIZE; c2++) {
            if (!candidates[i][j][c2]) continue;
            // if there's exactly one other cell in the column sharing those candidates,
            int otherI = -1;
            for (int i1 = 0; i1 < SIZE; i1++) {
              if (i1 == i) continue;
              if (candidates[i1][j][c1] || candidates[i1][j][c2]) {
                if (otherI != -1) continue candidatePairs;
                otherI = i1;
              }
            }
            if (otherI != -1) {
              // eliminate other candidates from both cells
              for (int c = 1; c <= SIZE; c++) {
                if (c == c1 || c == c2) continue;
                if (candidates[i][j][c] || candidates[otherI][j][c]) {
                  candidates[i][j][c] = false;
                  candidates[otherI][j][c] = false;
                  rowDirtied[otherI/BOX_HEIGHT][j/BOX_WIDTH] = true;
                  dirtied = true;
                }
              }
              continue cells;
            }
          }
        }
      }
    }
    return dirtied;
  }
  private boolean hiddenPairsBox(int minY, int maxY, int minX, int maxX) {
    boolean dirtied = false;
    // for each cell,
    for (int i = minY; i < maxY; i++) {
      cells:
      for (int j = minX; j < maxX; j++) {
        if (sudoku[i][j] != UNKNOWN) continue cells;
        // for each pair of its candidates,
        for (int c1 = 1; c1 < SIZE; c1++) {
          if (!candidates[i][j][c1]) continue;
          candidatePairs: for (int c2 = c1 + 1; c2 <= SIZE; c2++) {
            if (!candidates[i][j][c2]) continue;
            // if there's exactly one other cell in the box sharing those candidates,
            int otherI = -1, otherJ = -1;
            for (int i1 = minY; i1 < maxY; i1++) {
              for (int j1 = minX; j1 < maxX; j1++) {
                if (i1 == i && j1 == j) continue;
                if (candidates[i1][j1][c1] || candidates[i1][j1][c2]) {
                  if (otherI != -1) continue candidatePairs;
                  otherI = i1;
                  otherJ = j1;
                }
              }
            }
            if (otherI != -1) {
              // eliminate other candidates from those cells
              for (int c = 1; c <= SIZE; c++) {
                if (c == c1 || c == c2) continue;
                if (candidates[i][j][c] || candidates[otherI][otherJ][c]) {
                  candidates[i][j][c] = false;
                  candidates[otherI][otherJ][c] = false;
                  dirtied = true;
                }
              }
              continue cells;
            }
          }
        }
      }
    }
    return dirtied;
  }

  // Looks for candidates whose only cells in a row/column are in this box, and eliminates them from other cells in this box.
  private boolean boxLineRows(int minY, int maxY, int minX, int maxX) throws UnsolvableException {
    boolean dirtied = false;
    // for each row,
    for (int i = minY; i < maxY; i++) {
      // for each candidate:
      candidates:
      for (int c = 1; c <= SIZE; c++) {
        // if, within this row, it's only found in this box,
        boolean foundC = false;
        for (int j1 = 0; j1 < SIZE; j1++) {
          if (candidates[i][j1][c]) {
            if (minX <= j1 && j1 < maxX) foundC = true;
            else continue candidates;
          }
        }
        // (if it's not found at all that's a problem)
        if (!foundC) throw new UnsolvableException("candidate " + c + " not found in row " + i + "!");
        // eliminate it from other rows in this box
        for (int i2 = minY; i2 < maxY; i2++) {
          if (i2 == i) continue;
          for (int j2 = minX; j2 < maxX; j2++) {
            if (candidates[i2][j2][c]) {
              dirtied = true;
              candidates[i2][j2][c] = false;
            }
          }
        }
      }
    }
    return dirtied;
  }
  private boolean boxLineCols(int minY, int maxY, int minX, int maxX) throws UnsolvableException {
    boolean dirtied = false;
    // for each column,
    for (int j = minX; j < maxX; j++) {
      // for each candidate:
      candidates:
      for (int c = 1; c <= SIZE; c++) {
        // if, within this column, it's only found in this box,
        boolean foundC = false;
        for (int i1 = 0; i1 < SIZE; i1++) {
          if (candidates[i1][j][c]) {
            if (minY <= i1 && i1 < maxY) foundC = true;
            else continue candidates;
          }
        }
        // (if it's not found at all that's a problem)
        if (!foundC) throw new UnsolvableException("candidate " + c + " not found in column " + j + "!");
        // eliminate it from other columns in this box
        for (int j2 = minX; j2 < maxX; j2++) {
          if (j2 == j) continue;
          for (int i2 = minY; i2 < maxY; i2++) {
            if (candidates[i2][j2][c]) {
              dirtied = true;
              candidates[i2][j2][c] = false;
            }
          }
        }
      }
    }
    return dirtied;
  }

  // Looks for candidates whose only cells in this box are in the same row/column, and eliminates them from other cells in that row/column.
  private void pointingBox(int minY, int maxY, int minX, int maxX) {
    // for each row,
    for (int i = minY; i < maxY; i++) {
      // for each candidate,
      candidates:
      for (int c = 1; c <= SIZE; c++) {
        // if, within this box, it's only found in that row,
        for (int i1 = minY; i1 < maxY; i1++) {
          boolean foundC = false;
          for (int j1 = minX; j1 < maxX; j1++) {
            if (candidates[i1][j1][c]) foundC = true;
          }
          if (foundC != (i == i1)) continue candidates;
        }
        // eliminate it from elsewhere in the row
        for (int j = 0; j < SIZE; j++) {
          if (j >= minX && j < maxX) continue;
          if (candidates[i][j][c]) {
            candidates[i][j][c] = false;
            rowDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
            colDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
            rowDirtied[minY/BOX_HEIGHT][minX/BOX_WIDTH] = true;
          }
        }
      }
    }
    // for each column,
    for (int j = minX; j < maxX; j++) {
      // for each candidate,
      candidates:
      for (int c = 1; c <= SIZE; c++) {
        // if, within this box, it's only found in that column,
        for (int j1 = minX; j1 < maxX; j1++) {
          boolean foundC = false;
          for (int i1 = minY; i1 < maxY; i1++) {
            if (candidates[i1][j1][c]) foundC = true;
          }
          if (foundC != (j == j1)) continue candidates;
        }
        // eliminate it from elsewhere in the column
        for (int i = 0; i < SIZE; i++) {
          if (i >= minY && i < maxY) continue;
          if (candidates[i][j][c]) {
            candidates[i][j][c] = false;
            rowDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
            colDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
            colDirtied[minY/BOX_HEIGHT][minX/BOX_WIDTH] = true;
          }
        }
      }
    }
  }
}
