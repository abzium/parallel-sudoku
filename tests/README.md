This directory contains several test cases. Sudoku puzzles are found in `*.in.txt`, and their corresponding solutions are in the `*.out.txt` accompanying them.

---

The files named `logic*.txt` form a series of *incremental* tests, particularly useful for the `logical` algorithm (and its parallel counterparts) but usable by any solver.
These are sorted by the logical strategies required to solve them, per [the QQWing generator](https://qqwing.com/generate.html) used to generate them.
Any file after the first may also require strategies from files before it.  
In order, the strategies required for each of these Sudokus are:
1. Naked singles
2. Hidden singles
3. Naked pair (singular)
4. Naked pairs (plural)
5. Hidden pair(s)
6. Pointing pairs and/or triples
7. Box/line intersections (according to the generator, but doesn't actually strictly require them; hidden pairs are sufficient)
8. Box/line intersections (for real this time)
9. Guessing

`logic.sh` runs each of these tests on the input algorithm up until one fails, assuming the project's already compiled. Because it's intended to verify correctness, not test performance, it also enables assertions. Takes the same arguments as `SudokuSolver`.

The two files named `invalid*.in.txt` are error test cases: they have no solutions. `0` has (its only two) givens in direct conflict, `1` does not and requires some deductions to be made.

---

The files named `puzzle*.in.txt` and `hardestSudoku*.in.txt` are inputs intended for the `backtracking` and `parallelBacktracking` algorithms but usable by any solver.

Additionally, `unsolvable.in.txt` is another error test case; `formatPuzzle.py` formats this as a 2D Java array, for development purposes.
