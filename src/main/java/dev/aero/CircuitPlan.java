package dev.aero;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** A deterministic rotating-port puzzle with a guaranteed simple solution. */
public final class CircuitPlan {
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    private static final List<int[]> GENTLE_PATHS = enumeratePaths(3, 5);
    private static final List<int[]> COMPLEX_PATHS = enumeratePaths(4, 9);

    private final long seed;
    private final int size;
    private final int[] shapes;
    private final int[] initial;
    private final int[] solution;
    private final int[] path;

    public CircuitPlan(long seed, int size) {
        if (size != 3 && size != 4) {
            throw new IllegalArgumentException("Circuit width must be 3 or 4");
        }
        this.seed = seed;
        this.size = size;
        SplittableRandom random = new SplittableRandom(seed);
        List<int[]> candidates = size == 3 ? GENTLE_PATHS : COMPLEX_PATHS;
        path = candidates.get(random.nextInt(candidates.size())).clone();
        shapes = new int[tileCount()];
        initial = new int[tileCount()];
        solution = new int[tileCount()];

        for (int i = 0; i < tileCount(); i++) {
            shapes[i] = random.nextBoolean() ? NORTH | SOUTH : NORTH | EAST;
            solution[i] = random.nextInt(4);
        }
        for (int p = 0; p < path.length; p++) {
            int index = path[p];
            int incoming = p == 0 ? WEST : direction(index, path[p - 1], size);
            int outgoing = p == path.length - 1 ? EAST : direction(index, path[p + 1], size);
            int ports = incoming | outgoing;
            shapes[index] = ports == (NORTH | SOUTH) || ports == (EAST | WEST)
                    ? NORTH | SOUTH : NORTH | EAST;
            solution[index] = rotationFor(shapes[index], ports);
        }

        boolean scrambled = false;
        for (int attempt = 0; attempt < 128; attempt++) {
            for (int i = 0; i < tileCount(); i++) initial[i] = random.nextInt(4);
            if (!solved(initial) && misplacedPathTiles(initial) >= minimumMisplaced()) {
                scrambled = true;
                break;
            }
        }
        if (!scrambled) {
            System.arraycopy(solution, 0, initial, 0, solution.length);
            for (int index : path) initial[index] = (solution[index] + 1) & 3;
            while ((rotateMask(shapes[sourceIndex()], initial[sourceIndex()]) & WEST) != 0) {
                initial[sourceIndex()] = (initial[sourceIndex()] + 1) & 3;
            }
        }
    }

    public static CircuitPlan generate(long seed, int size) {
        return new CircuitPlan(seed, size);
    }

    public long seed() { return seed; }
    public int size() { return size; }
    public int n() { return size; }
    public int tileCount() { return size * size; }
    public int sourceIndex() { return 0; }
    public int sinkIndex() { return tileCount() - 1; }
    public int shapeMask(int index) { return shapes[Objects.checkIndex(index, tileCount())]; }
    public int initialRotation(int index) { return initial[Objects.checkIndex(index, tileCount())]; }
    public int solutionRotation(int index) { return solution[Objects.checkIndex(index, tileCount())]; }
    public int[] solutionPath() { return path.clone(); }

    /** Positive quarter turns rotate clockwise; rotations may be negative. */
    public static int rotateMask(int mask, int quarterTurns) {
        if ((mask & ~15) != 0) throw new IllegalArgumentException("Ports must fit four direction bits");
        int turns = Math.floorMod(quarterTurns, 4);
        return ((mask << turns) | (mask >>> (4 - turns))) & 15;
    }

    /** Only mutually connected ports carry water; the source must face its west inlet. */
    public boolean[] flow(int[] rotations) {
        Objects.requireNonNull(rotations, "rotations");
        if (rotations.length != tileCount()) throw new IllegalArgumentException("Wrong rotation count");
        boolean[] reachable = new boolean[tileCount()];
        int[] masks = new int[tileCount()];
        for (int i = 0; i < masks.length; i++) masks[i] = rotateMask(shapes[i], rotations[i]);
        if ((masks[sourceIndex()] & WEST) == 0) return reachable;

        int[] queue = new int[tileCount()];
        int head = 0, tail = 0;
        queue[tail++] = sourceIndex();
        reachable[sourceIndex()] = true;
        while (head < tail) {
            int index = queue[head++];
            int x = index % size, y = index / size;
            for (int d = 0; d < 4; d++) {
                int bit = 1 << d;
                if ((masks[index] & bit) == 0) continue;
                int nx = x + (d == 1 ? 1 : d == 3 ? -1 : 0);
                int ny = y + (d == 2 ? 1 : d == 0 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int next = ny * size + nx;
                if (reachable[next] || (masks[next] & (1 << ((d + 2) & 3))) == 0) continue;
                reachable[next] = true;
                queue[tail++] = next;
            }
        }
        return reachable;
    }

    public boolean solved(int[] rotations) {
        boolean[] reachable = flow(rotations);
        return reachable[sinkIndex()]
                && (rotateMask(shapes[sinkIndex()], rotations[sinkIndex()]) & EAST) != 0;
    }

    private int minimumMisplaced() { return Math.max(2, path.length / 3); }

    private int misplacedPathTiles(int[] rotations) {
        int misplaced = 0;
        for (int index : path) {
            if (rotateMask(shapes[index], rotations[index]) != rotateMask(shapes[index], solution[index])) {
                misplaced++;
            }
        }
        return misplaced;
    }

    private static int rotationFor(int shape, int ports) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (rotateMask(shape, rotation) == ports) return rotation;
        }
        throw new IllegalArgumentException("Route requires a straight or elbow tile");
    }

    private static int direction(int from, int to, int width) {
        if (to == from - width) return NORTH;
        if (to == from + width) return SOUTH;
        if (to == from + 1 && from / width == to / width) return EAST;
        if (to == from - 1 && from / width == to / width) return WEST;
        throw new IllegalArgumentException("Route tiles must be adjacent");
    }

    private static List<int[]> enumeratePaths(int width, int minimumLength) {
        List<int[]> paths = new ArrayList<>();
        int[] route = new int[width * width];
        boolean[] used = new boolean[route.length];
        route[0] = 0;
        used[0] = true;
        enumerate(width, minimumLength, route, used, 1, paths);
        if (paths.isEmpty()) throw new IllegalStateException("No valid circuit routes");
        return List.copyOf(paths);
    }

    private static void enumerate(int width, int minimumLength, int[] route, boolean[] used,
                                  int length, List<int[]> paths) {
        int current = route[length - 1];
        if (current == route.length - 1) {
            int bends = 0;
            for (int i = 2; i < length; i++) {
                if (direction(route[i - 2], route[i - 1], width)
                        != direction(route[i - 1], route[i], width)) bends++;
            }
            if (length >= minimumLength && bends >= 3) paths.add(Arrays.copyOf(route, length));
            return;
        }
        int x = current % width, y = current / width;
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 1 ? 1 : d == 3 ? -1 : 0);
            int ny = y + (d == 2 ? 1 : d == 0 ? -1 : 0);
            if (nx < 0 || ny < 0 || nx >= width || ny >= width) continue;
            int next = ny * width + nx;
            if (used[next]) continue;
            used[next] = true;
            route[length] = next;
            enumerate(width, minimumLength, route, used, length + 1, paths);
            used[next] = false;
        }
    }
}
