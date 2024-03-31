import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;

public class SudokuSolver {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java SudokuSolver <filename> <algorithm>");
            return;
        }
        String filename = args[0];
        String algorithm = args[1];

        // Decide algorithm based on argument
        switch (algorithm) {
            case "bruteforce":
                System.out.println("Running brute force algorithm.");
                BruteForce bruteForce = new BruteForce(readFile(filename));
                bruteForce.solve();
                break;

            case "backtracking":
                System.out.println("Running backtracking algorithm.");
                Backtracking backtracking = new Backtracking(readFile(filename));
                backtracking.solve();
                break;

            case "logical":
                System.out.println("Running logical algorithm.");
                Logical logical = new Logical(readFile(filename));
                logical.solve();
                break;

                
            case "parallelLogical":
                System.out.println("Running parallelized logical algorithm.");
                ParallelLogical parallelLogical = new ParallelLogical(readFile(filename));
                parallelLogical.solve();
                break;

            case "coordinatedLogical":
                System.out.println("Running coordinated logical algorithm.");
                CoordinatedLogical coordinatedLogical = new CoordinatedLogical(readFile(filename));
                coordinatedLogical.solve();
                break;

            default:
                System.out.println("Please enter a valid algorithm (bruteforce, backtracking, logical).");
                break;
        }
    }

    private static int[][] readFile(String filename) {
        try (final FileReader fr = new FileReader(filename);
            final BufferedReader br = new BufferedReader(fr)) {
            return br.lines().map(line -> line.chars().map(c -> c == '.' ? 0 : c - '0').toArray()).toArray(int[][]::new);
        } catch (FileNotFoundException e) {
            System.err.println("File \""+filename+"\" not found!");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UncheckedIOException e) {
            e.getCause().printStackTrace();
        }
        System.exit(-1);
        return null;
    }
}
