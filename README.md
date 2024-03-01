# parallel-sudoku

## Compiling
```
javac -d bin src/*.java
```

## Running
```
java -cp bin SudokuSolver <filename> <algorithm>
```
Where `<filename>` is the name of the input sudoku file and `<algorithm>` is the desired algorithm (`bruteforce`, `backtracking`, or `logical`)
