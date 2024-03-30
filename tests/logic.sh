cd $(dirname "$0")/..
for i in tests/logic*.in.txt; do
  if java -ea -cp bin SudokuSolver "$i" "$@" | tail -n +2 | diff --strip-trailing-cr "${i%.in.txt}.out.txt" -; then
    echo "$i passed!"
  else
    echo "$i failed!"
    break
  fi
done
