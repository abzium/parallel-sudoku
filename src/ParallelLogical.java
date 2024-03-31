import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @implNote Every deduction strategy (the {@code private} methods besides {@link #setValue}) has the following attributes:
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
public class ParallelLogical {
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
     * Produces a list of states to fall back onto.
     * @param state The current state.
     * @return A list of BoardStates, one of which can be solved to find the solution to the given state, or {@code null} if the given state was already solved.
     */
    List<BoardState> recurse(BoardState state);

    /**
     * Finds a cell with the fewest candidates, and tries each of them.
     */
    FallbackSolver guessAndCheck = state -> {
      // find a cell by fewest candidates
      int minCandidates = SIZE + 1,
        minI = -1, minJ = -1;
      for (int i = 0; i < SIZE; i++) {
        for (int j = 0; j < SIZE; j++) {
          if (state.sudoku[i][j] != UNKNOWN) continue;
          int numCandidates = 0;
          for (int c = 1; c <= SIZE; c++) if (state.candidates[i][j][c]) numCandidates++;
          if (numCandidates < minCandidates) {
            minCandidates = numCandidates;
            minI = i;
            minJ = j;
          }
        }
      }
      // if there was no such unfilled cell, the grid's solved.
      if (minCandidates > SIZE) return null;
      final List<BoardState> states = new ArrayList<>(minCandidates);
      // otherwise, try each of its candidates
      for (int c = 1; c <= SIZE; c++) {
        if (!state.candidates[minI][minJ][c]) continue;
        // on a copy of the grid
        final int[][] tempSudoku = new int[SIZE][];
        final boolean[][][] tempCandidates = new boolean[SIZE][SIZE][];
        for (int i = 0; i < SIZE; i++) {
          tempSudoku[i] = state.sudoku[i].clone();
          for (int j = 0; j < SIZE; j++) {
            tempCandidates[i][j] = state.candidates[i][j].clone();
          }
        }
        final BoardState newState = new BoardState(tempSudoku, tempCandidates);
        newState.setValue(minI, minJ, c);
        states.add(newState);
      }
      return states;
    };
    /**
     * Doesn't attempt to solve the grid, instead letting the solver fail.
     */
    FallbackSolver die = state -> {
      for (int i = 0; i < SIZE; i++)
        for (int j = 0; j < SIZE; j++)
          if (state.sudoku[i][j] == UNKNOWN) return Collections.emptyList();
      return null;
    };
  }

  private static final class BoardState {
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
    
    private final AtomicIntegerArray rowReaders = new AtomicIntegerArray(NUM_BOXES_Y),
      colReaders = new AtomicIntegerArray(NUM_BOXES_X),
      boxWriters = new AtomicIntegerArray(NUM_BOXES_Y * NUM_BOXES_X);

    /**
     * Constructs the solver.
     * <p>Assumes input arrays are of the correct size, and are mutable.
     */
    private BoardState(int[][] sudoku, boolean[][][] candidates) {
      this.sudoku = sudoku;
      this.candidates = candidates;
    }

    /**
     * Prints the current state to {@link System#out}.
     * This includes the solution so far, and if it's not fully solved, the {@link #candidates} for each cell.
     */
    private void print() {
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
    
    void setValue(int i, int j, int c) {
      final int minX = j/BOX_WIDTH*BOX_WIDTH, maxX = minX + BOX_WIDTH,
        minY = i/BOX_HEIGHT*BOX_HEIGHT, maxY = minY + BOX_HEIGHT;
      setValue(i, j, c, minY, maxY, minX, maxX);
    }
    void setValue(int i, int j, int c, int minY, int maxY, int minX, int maxX) {
      for (int c1 = 1; c1 <= SIZE; c1++) if (c1 != c) candidates[i][j][c1] = false;
      for (int i1 = 0; i1 < SIZE; i1++) if (i1 != i) candidates[i1][j][c] = false;
      for (int j1 = 0; j1 < SIZE; j1++) if (j1 != j) candidates[i][j1][c] = false;
      for (int i2 = minY; i2 < maxY; i2++) {
        for (int j2 = minX; j2 < maxX; j2++) {
          // && vs || here doesn't matter, since cells in the row/column were already eliminated from.
          if (i2 != i && j2 != j) candidates[i2][j2][c] = false;
        }
      }
      sudoku[i][j] = c;
    }
  }

  // i THINK the algorithms here also work for different board sizes?
  // regardless, good to avoid magic numbers
  private static final int
    UNKNOWN = 0,  // value for undetermined cells in `sudoku`
    SIZE = 9,  // width and height of `sudoku`, and (inclusive) maximum a value can take
    BOX_WIDTH = 3, BOX_HEIGHT = 3,  // dimensions of each box
    NUM_BOXES_X = SIZE / BOX_WIDTH,  // number of boxes in each dimension
    NUM_BOXES_Y = SIZE / BOX_HEIGHT;

  private static final FallbackSolver FALLBACK = FallbackSolver.guessAndCheck;

  /**
   * The solver's current state.
   */
  private BoardState state;

  /**
   * Constructs the solver.
   * <p>If there are any givens, call {@link #init}; this initiializes the solver and checks for illegal givens.
   * @param sudoku The sudoku grid, with {@link #UNKNOWN <code>0</code>s for unknowns} and values from 1 to {@link #SIZE} for givens.
   *   Should not be modified externally, otherwise this solver could be in an inconsistent state.
   * @throws IllegalArgumentException If the {@code sudoku} grid isn't a square of size {@link #SIZE}.
   */
  public ParallelLogical(int[][] sudoku) {
    this(sudoku, new boolean[SIZE][SIZE][SIZE + 1]);
    if (sudoku.length != SIZE)
      throw new IllegalArgumentException("wrong number of sudoku rows");
    if (Arrays.stream(sudoku).anyMatch(row -> row.length != SIZE))
      throw new IllegalArgumentException("wrong number of sudoku columns");
    for (boolean[][] row : state.candidates) for (boolean[] cell : row) Arrays.fill(cell, true);
  }
  private ParallelLogical(int[][] sudoku, boolean[][][] candidates) {
    assert candidates.length == SIZE : "wrong number of candidates rows";
    assert Arrays.stream(candidates).allMatch(row -> row.length == SIZE) : "wrong number of candidates columns";
    assert Arrays.stream(candidates).flatMap(Arrays::stream).allMatch(cell -> cell.length == SIZE + 1) : "wrong number of candidates entries";
    state = new BoardState(sudoku, candidates);
  }

  /**
   * Solves the grid in-place as much as possible, using {@link #FALLBACK} when no more deductions can be made.
   * When there is no solution, this prints as much of the grid as could be determined along with the remaining candidates;
   * when there are multiple, this prints any one of the solutions.
   */
  public void solve() {
    try {
      init();
      final ForkJoinPool pool = ForkJoinPool.commonPool();
      final BoardState state;
      try {
        state = pool.invoke(new SolverTask(this.state));
      } catch (RuntimeException e) {
        for (Throwable thr = e; thr != null; thr = thr.getCause())
          if (thr instanceof UnsolvableException)
            throw (UnsolvableException)thr;
        e.printStackTrace();
        return;
      } finally {
        pool.shutdownNow();
      }
      if (state == null) throw new UnsolvableException("no guess worked!");
      this.state = state;
    } catch (UnsolvableException e) {
      System.out.println("The sudoku couldn't be solved!");
      if (e.getMessage() != null) System.out.println("Reason: " + e.getMessage());
    } finally {
      this.state.print();
    }
  }

  /**
   * Initializes the solver. Need only be called once.
   * <p>Specifically, this:
   * <ul><li>updates the {@link #candidates} on solved cells by eliminating those that don't match the given,
   * <li>clears each given from candidates in the rest of its row, column, and box,
   * <li>checks if the givens conflict, and
   * <li>dirties the entire grid.</ul>
   * Need only be called once, at initialization. Need only be called if there are any givens;
   * if there are, this must be called before {@link SubsolverTask#trySolve}, otherwise behavior is undefined.
   * @throws UnsolvableException If any givens directly conflict; i.e., if two of the same given are in the same row, column, or box.
   */
  public void init() throws UnsolvableException {
    for (int i = 0; i < SIZE; i++) {
      for (int j = 0; j < SIZE; j++) {
        final int c = state.sudoku[i][j];
        if (c != UNKNOWN) {
          if (!state.candidates[i][j][c])
            throw new UnsolvableException("Given at R"+i+"C"+j+" conflicts with another!");
          state.setValue(i, j, c);
        }
      }
    }
    for (boolean[] row : state.rowDirtied) Arrays.fill(row, true);
    for (boolean[] row : state.colDirtied) Arrays.fill(row, true);
  }

  /**
   * The thread that controls the solving of a single BoardState.
   * <p>Spawns {@link SubsolverTask}s to make as many deductions as possible, then recursively spawns new SolverTasks if that didn't solve the board.
   */
  private static class SolverTask extends CountedCompleter<BoardState> {
    private BoardState state;

    public SolverTask(BoardState state) {
      this.state = state;
    }
    private SolverTask(SolverTask completer, BoardState state) {
      super(completer);
      this.state = state;
    }

    @Override
    public void compute() {
      // this also propagates exceptions; if this returns successfully, state's solved as much as it can be
      invokeAll(Arrays.asList(new SubsolverTask(state), new SubsolverTask(state), new SubsolverTask(state)));
      final List<BoardState> states = FALLBACK.recurse(state);
      if (states == null) {
        CountedCompleter<?> root = getRoot();
        assert root instanceof SolverTask : "unexpected root class " + root.getClass().getName();
        ((SolverTask)root).state = state;
        root.quietlyComplete();
      } else {
        this.state = null;
        setPendingCount(states.size());
        for (BoardState state : states) {
          new SolverTask(this, state).fork();
        }
      }
      tryComplete();
    }

    @Override
    public BoardState getRawResult() {
      return state;
    }
    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter<?> caller) {
      return !(getCompleter() instanceof SolverTask);
    }
  }

  private static class SubsolverTask extends RecursiveTask<BoardState> {
    private final BoardState state;

    public SubsolverTask(BoardState state) {
      this.state = state;
    }

    @Override
    public BoardState compute() {
      try {
        trySolve();
      } catch (UnsolvableException e) {
        completeExceptionally(e);
      }
      return state;
    }

    /**
     * Solves the grid in-place as much as possible.
     * @throws UnsolvableException If some kind of contradiction was found.
     */
    public void trySolve() throws UnsolvableException {
      boolean avoidBoxContention = true,
        avoidLineContention = true;
      while (true) {
        boolean anyBoxContended = false, anyLineContended = false,
          anyDirtied = false;
        // rows and columns are separate loops to cycle through the rest of the boxes once before swinging back around for the column
        for (int x = 0; x < NUM_BOXES_X; x++) {
          for (int y = 0; y < NUM_BOXES_Y; y++) {
            if (state.rowDirtied[y][x]) {
              if (!state.boxWriters.compareAndSet(x + y*NUM_BOXES_X, 0, 1)) {
                anyBoxContended = true;
                if (avoidBoxContention) continue;
                else state.boxWriters.getAndIncrement(x + y*NUM_BOXES_X);
              }
              try {
                if (!state.rowReaders.compareAndSet(y, 0, 1)) {
                  anyLineContended = true;
                  if (avoidLineContention) continue;
                  else state.rowReaders.getAndIncrement(y);
                }
                try {
                  if (state.rowDirtied[y][x]) {
                    doSolveStep(y, x, true);
                    anyDirtied = true;
                  }
                } finally {
                  state.rowReaders.getAndDecrement(y);
                }
              } finally {
                state.boxWriters.getAndDecrement(x + y*NUM_BOXES_X);
              }
            }
          }
        }
        for (int y = 0; y < NUM_BOXES_Y; y++) {
          for (int x = 0; x < NUM_BOXES_X; x++) {
            if (!state.boxWriters.compareAndSet(x + y*NUM_BOXES_X, 0, 1)) {
              anyBoxContended = true;
              if (avoidBoxContention) continue;
              else state.boxWriters.getAndIncrement(x + y*NUM_BOXES_X);
            }
            try {
              if (!state.colReaders.compareAndSet(x, 0, 1)) {
                anyLineContended = true;
                if (avoidLineContention) continue;
                else state.colReaders.getAndIncrement(x);
              }
              try {
                if (state.colDirtied[y][x]) {
                  doSolveStep(y, x, false);
                  anyDirtied = true;
                }
              } finally {
                state.colReaders.getAndDecrement(x);
              }
            } finally {
              state.boxWriters.getAndDecrement(x + y*NUM_BOXES_X);
            }
          }
        }
        if (anyDirtied) {
          avoidLineContention = true;
          avoidBoxContention = true;
        } else {
          if (avoidLineContention && anyLineContended) avoidLineContention = false;
          else if (avoidBoxContention && anyBoxContended) {
            avoidBoxContention = false; avoidLineContention = true;
          } else break;
        }
      }
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
      (isRow ? state.rowDirtied : state.colDirtied)[boxI][boxJ] = false;
      boolean dirtied = false;
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
          state.colDirtied[i1][boxJ] = true;
        }
        Arrays.fill(state.rowDirtied[boxI], true);
      }
    }

    // Fills in cells that have only one candidate value.
    private boolean nakedSinglesBox(int minY, int maxY, int minX, int maxX) throws UnsolvableException {
      boolean dirtied = false;
      // for each cell,
      for (int i = minY; i < maxY; i++) {
        cells:
        for (int j = minX; j < maxX; j++) {
          if (state.sudoku[i][j] != UNKNOWN) continue;
          // if it has only one candidate value,
          int value = UNKNOWN;
          for (int c = 1; c <= SIZE; c++) {
            if (state.candidates[i][j][c]) {
              if (value != UNKNOWN) continue cells;
              value = c;
            }
          }
          // (if it has none that's a problem)
          if (value == UNKNOWN) {
            throw new UnsolvableException("no candidates in cell R" + i + "C" + j + "!");
          }
          // fill it in
          state.setValue(i, j, value, minY, maxY, minX, maxX);
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
          if (state.sudoku[i][j] != UNKNOWN) continue;
          // for each of its candidates,
          candidates:
          for (int c = 1; c <= SIZE; c++) {
            if (!state.candidates[i][j][c]) continue;
            // if no other cell in the row has it,
            for (int j1 = 0; j1 < SIZE; j1++) {
              if (j1 == j) continue;
              if (state.candidates[i][j1][c]) continue candidates;
            }
            // fill it in
            state.setValue(i, j, c, minY, maxY, minX, maxX);
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
          if (state.sudoku[i][j] != UNKNOWN) continue;
          // for each of its candidates,
          candidates:
          for (int c = 1; c <= SIZE; c++) {
            if (!state.candidates[i][j][c]) continue;
            // if no other cell in the column has it,
            for (int i1 = 0; i1 < SIZE; i1++) {
              if (i1 == i) continue;
              if (state.candidates[i1][j][c]) continue candidates;
            }
            // fill it in
            state.setValue(i, j, c, minY, maxY, minX, maxX);
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
          if (state.sudoku[i][j] != UNKNOWN) continue;
          // for each of its candidates,
          candidates:
          for (int c = 1; c <= SIZE; c++) {
            if (!state.candidates[i][j][c]) continue;
            // if no other cell in the box has it,
            for (int i1 = minY; i1 < maxY; i1++) {
              for (int j1 = minX; j1 < maxX; j1++) {
                if (i1 == i && j1 == j) continue;
                if (state.candidates[i1][j1][c]) continue candidates;
              }
            }
            // fill it in
            state.setValue(i, j, c, minY, maxY, minX, maxX);
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // if it only has two candidates,
          int candCount = 0;
          for (int c = 1; c <= SIZE; c++) {
            if (state.candidates[i][j][c]) {
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
              if (state.candidates[i][j1][c] != state.candidates[i][j][c]) continue row;
            }
            // clear the other cells of the row of the two candidates
            for (int j2 = 0; j2 < SIZE; j2++) {
              if (j2 == j || j2 == j1 || state.sudoku[i][j2] != UNKNOWN) continue;
              for (int c = 1; c <= SIZE; c++) {
                if (state.candidates[i][j2][c] && state.candidates[i][j][c]) {
                  state.candidates[i][j2][c] = false;
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // if it has only two candidates,
          int candCount = 0;
          for (int c = 1; c <= SIZE; c++) {
            if (state.candidates[i][j][c]) {
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
              if (state.candidates[i1][j][c] != state.candidates[i][j][c]) continue col;
            }
            // clear the other cells in the column of the two candidates
            for (int i2 = 0; i2 < SIZE; i2++) {
              if (i2 == i || i2 == i1 || state.sudoku[i2][j] != UNKNOWN) continue;
              for (int c = 1; c <= SIZE; c++) {
                if (state.candidates[i2][j][c] && state.candidates[i][j][c]) {
                  state.candidates[i2][j][c] = false;
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // if it has only two candidates,
          int candCount = 0;
          for (int c = 1; c <= SIZE; c++) {
            if (state.candidates[i][j][c]) {
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
                if (state.candidates[i1][j1][c] != state.candidates[i][j][c]) continue box;
              }
              // clear the other cells in the box of the two candidates
              for (int i2 = minY; i2 < maxY; i2++){
                for (int j2 = minX; j2 < maxX; j2++) {
                  if ((i2 == i && j2 == j) || (i2 == i1 && j2 == j1) || state.sudoku[i2][j2] != UNKNOWN) continue;
                  for (int c = 1; c <= SIZE; c++) {
                    if (state.candidates[i2][j2][c] && state.candidates[i][j][c]) {
                      state.candidates[i2][j2][c] = false;
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // for each pair of its candidates,
          for (int c1 = 1; c1 < SIZE; c1++) {
            if (!state.candidates[i][j][c1]) continue;
            candidatePairs:
            for (int c2 = c1+1; c2 <= SIZE; c2++) {
              if (!state.candidates[i][j][c2]) continue;
              // if there's exactly one other cell in the row sharing those candidates,
              int otherJ = -1;
              for (int j1 = 0; j1 < SIZE; j1++) {
                if (j1 == j) continue;
                if (state.candidates[i][j1][c1] || state.candidates[i][j1][c2]) {
                  if (otherJ != -1) continue candidatePairs;
                  otherJ = j1;
                }
              }
              if (otherJ != -1) {
                // eliminate other candidates from both cells
                for (int c = 1; c <= SIZE; c++) {
                  if (c == c1 || c == c2) continue;
                  if (state.candidates[i][j][c] || state.candidates[i][otherJ][c]) {
                    state.candidates[i][j][c] = false;
                    state.candidates[i][otherJ][c] = false;
                    state.colDirtied[i/BOX_HEIGHT][otherJ/BOX_WIDTH] = true;
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // for each pair of its candidates,
          for (int c1 = 1; c1 < SIZE; c1++) {
            if (!state.candidates[i][j][c1]) continue;
            candidatePairs: for (int c2 = c1 + 1; c2 <= SIZE; c2++) {
              if (!state.candidates[i][j][c2]) continue;
              // if there's exactly one other cell in the column sharing those candidates,
              int otherI = -1;
              for (int i1 = 0; i1 < SIZE; i1++) {
                if (i1 == i) continue;
                if (state.candidates[i1][j][c1] || state.candidates[i1][j][c2]) {
                  if (otherI != -1) continue candidatePairs;
                  otherI = i1;
                }
              }
              if (otherI != -1) {
                // eliminate other candidates from both cells
                for (int c = 1; c <= SIZE; c++) {
                  if (c == c1 || c == c2) continue;
                  if (state.candidates[i][j][c] || state.candidates[otherI][j][c]) {
                    state.candidates[i][j][c] = false;
                    state.candidates[otherI][j][c] = false;
                    state.rowDirtied[otherI/BOX_HEIGHT][j/BOX_WIDTH] = true;
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
          if (state.sudoku[i][j] != UNKNOWN) continue cells;
          // for each pair of its candidates,
          for (int c1 = 1; c1 < SIZE; c1++) {
            if (!state.candidates[i][j][c1]) continue;
            candidatePairs: for (int c2 = c1 + 1; c2 <= SIZE; c2++) {
              if (!state.candidates[i][j][c2]) continue;
              // if there's exactly one other cell in the box sharing those candidates,
              int otherI = -1, otherJ = -1;
              for (int i1 = minY; i1 < maxY; i1++) {
                for (int j1 = minX; j1 < maxX; j1++) {
                  if (i1 == i && j1 == j) continue;
                  if (state.candidates[i1][j1][c1] || state.candidates[i1][j1][c2]) {
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
                  if (state.candidates[i][j][c] || state.candidates[otherI][otherJ][c]) {
                    state.candidates[i][j][c] = false;
                    state.candidates[otherI][otherJ][c] = false;
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
            if (state.candidates[i][j1][c]) {
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
              if (state.candidates[i2][j2][c]) {
                dirtied = true;
                state.candidates[i2][j2][c] = false;
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
            if (state.candidates[i1][j][c]) {
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
              if (state.candidates[i2][j2][c]) {
                dirtied = true;
                state.candidates[i2][j2][c] = false;
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
              if (state.candidates[i1][j1][c]) foundC = true;
            }
            if (foundC != (i == i1)) continue candidates;
          }
          // eliminate it from elsewhere in the row
          for (int j = 0; j < SIZE; j++) {
            if (j >= minX && j < maxX) continue;
            if (state.candidates[i][j][c]) {
              state.candidates[i][j][c] = false;
              state.rowDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
              state.colDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
              state.rowDirtied[minY/BOX_HEIGHT][minX/BOX_WIDTH] = true;
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
              if (state.candidates[i1][j1][c]) foundC = true;
            }
            if (foundC != (j == j1)) continue candidates;
          }
          // eliminate it from elsewhere in the column
          for (int i = 0; i < SIZE; i++) {
            if (i >= minY && i < maxY) continue;
            if (state.candidates[i][j][c]) {
              state.candidates[i][j][c] = false;
              state.rowDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
              state.colDirtied[i/BOX_HEIGHT][j/BOX_WIDTH] = true;
              state.colDirtied[minY/BOX_HEIGHT][minX/BOX_WIDTH] = true;
            }
          }
        }
      }
    }
  }
}