package dev.aero;

import java.util.Arrays;
import java.util.Objects;
import java.util.SplittableRandom;

/** An immutable nine-room expedition with reciprocal doors, a loop and a side branch. */
public final class ExpeditionPlan {
    public enum RoomKind { ENTRY, FLOW, STEPPING, SPECIMEN, OVERLOOK }

    private final long seed;
    private final int theme;
    private final int[] connections = new int[9];
    private final RoomKind[] kinds = new RoomKind[9];
    private final CircuitPlan[] puzzles = new CircuitPlan[9];
    private final int[] specimens = new int[9];
    private final String signature;

    public ExpeditionPlan(long seed) {
        this(seed, new SplittableRandom(seed).nextInt(3));
    }

    public ExpeditionPlan(long seed, int theme) {
        if (theme < 0 || theme > 2) throw new IllegalArgumentException("Theme must be pools, canopy or gallery");
        this.seed = seed;
        this.theme = theme;
        SplittableRandom random = new SplittableRandom(seed);
        generateGraph(random);
        assignRooms(random);

        int[] specimenOrder = new int[12];
        for (int i = 0; i < specimenOrder.length; i++) specimenOrder[i] = i;
        shuffle(specimenOrder, random);
        System.arraycopy(specimenOrder, 0, specimens, 0, specimens.length);

        for (int i = 1; i < 9; i++) if (kinds[i] == RoomKind.FLOW) {
            puzzles[i] = new CircuitPlan(random.nextLong(), theme == 2 ? 4 : 3);
        }
        signature = makeSignature();
    }

    public static ExpeditionPlan generate(long seed) { return new ExpeditionPlan(seed); }
    public static ExpeditionPlan generate(long seed, int theme) { return new ExpeditionPlan(seed, theme); }
    public long seed() { return seed; }
    public int theme() { return theme; }
    public RoomKind roomKind(int room) { return kinds[Objects.checkIndex(room, 9)]; }
    public int connections(int room) { return connections[Objects.checkIndex(room, 9)]; }
    public CircuitPlan puzzle(int room) { return puzzles[Objects.checkIndex(room, 9)]; }
    public int specimenType(int room) { return specimens[Objects.checkIndex(room, 9)]; }
    public String signature() { return signature; }

    private void generateGraph(SplittableRandom random) {
        int[][] edges = new int[12][2];
        int edgeCount = 0;
        for (int room = 0; room < 9; room++) {
            if (room % 3 < 2) edges[edgeCount++] = new int[]{room, room + 1};
            if (room / 3 < 2) edges[edgeCount++] = new int[]{room, room + 3};
        }
        for (int i = edges.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = edges[i]; edges[i] = edges[j]; edges[j] = swap;
        }
        int[] parents = new int[9];
        for (int i = 0; i < parents.length; i++) parents[i] = i;
        boolean[] chosen = new boolean[edges.length];
        for (int i = 0; i < edges.length; i++) {
            int a = root(parents, edges[i][0]), b = root(parents, edges[i][1]);
            if (a == b) continue;
            parents[a] = b;
            chosen[i] = true;
            connect(edges[i][0], edges[i][1]);
        }
        // A Hamiltonian tree from the entry to the exit has no optional discovery branch.
        // Replace that rare shape before choosing chords, keeping an interior leaf.
        if (!hasSideBranch()) {
            Arrays.fill(connections, 0);
            int[] route = {0, 1, 2, 5, 8, 7, 6, 3, 4};
            for (int i = 1; i < route.length; i++) connect(route[i - 1], route[i]);
            for (int i = 0; i < edges.length; i++) {
                chosen[i] = (connections[edges[i][0]] & direction(edges[i][0], edges[i][1])) != 0;
            }
        }
        int requestedLoops = 1 + random.nextInt(2), addedLoops = 0;
        for (int i = 0; i < edges.length && addedLoops < requestedLoops; i++) {
            if (chosen[i]) continue;
            int a = edges[i][0], b = edges[i][1];
            connect(a, b);
            // Retain loops only when an interior discovery side branch survives.
            if (hasSideBranch()) addedLoops++;
            else disconnect(a, b);
        }
        if (addedLoops == 0) throw new IllegalStateException("An expedition needs a loop");
    }

    private void assignRooms(SplittableRandom random) {
        kinds[0] = RoomKind.ENTRY;
        kinds[8] = random.nextBoolean() ? RoomKind.FLOW : RoomKind.OVERLOOK;
        RoomKind[] remaining = kinds[8] == RoomKind.FLOW
                ? new RoomKind[]{RoomKind.FLOW, RoomKind.FLOW, RoomKind.STEPPING, RoomKind.STEPPING,
                        RoomKind.SPECIMEN, RoomKind.SPECIMEN, RoomKind.OVERLOOK}
                : new RoomKind[]{RoomKind.FLOW, RoomKind.FLOW, RoomKind.FLOW, RoomKind.STEPPING,
                        RoomKind.STEPPING, RoomKind.SPECIMEN, RoomKind.SPECIMEN};
        for (int i = remaining.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            RoomKind swap = remaining[i]; remaining[i] = remaining[j]; remaining[j] = swap;
        }
        System.arraycopy(remaining, 0, kinds, 1, remaining.length);
    }

    private void connect(int a, int b) {
        int direction = direction(a, b);
        connections[a] |= direction;
        connections[b] |= CircuitPlan.rotateMask(direction, 2);
    }

    private void disconnect(int a, int b) {
        int direction = direction(a, b);
        connections[a] &= ~direction;
        connections[b] &= ~CircuitPlan.rotateMask(direction, 2);
    }

    private boolean hasSideBranch() {
        for (int room = 1; room < 8; room++) if (Integer.bitCount(connections[room]) == 1) return true;
        return false;
    }

    private String makeSignature() {
        long hash = mix(0xcbf29ce484222325L, theme);
        for (int room = 0; room < 9; room++) {
            hash = mix(hash, kinds[room].ordinal());
            hash = mix(hash, connections[room]);
            hash = mix(hash, specimens[room]);
            CircuitPlan puzzle = puzzles[room];
            if (puzzle == null) continue;
            hash = mix(hash, puzzle.size());
            for (int tile = 0; tile < puzzle.tileCount(); tile++) {
                hash = mix(hash, puzzle.shapeMask(tile));
                hash = mix(hash, puzzle.initialRotation(tile));
                hash = mix(hash, puzzle.solutionRotation(tile));
            }
        }
        return Long.toUnsignedString(hash, 16);
    }

    private static long mix(long hash, int value) { return (hash ^ value) * 0x100000001b3L; }

    private static int direction(int from, int to) {
        if (to == from + 3) return CircuitPlan.SOUTH;
        if (to == from + 1 && from / 3 == to / 3) return CircuitPlan.EAST;
        if (to == from - 3) return CircuitPlan.NORTH;
        if (to == from - 1 && from / 3 == to / 3) return CircuitPlan.WEST;
        throw new IllegalArgumentException("Rooms must be grid neighbours");
    }

    private static int root(int[] parents, int node) {
        while (parents[node] != node) node = parents[node];
        return node;
    }

    private static void shuffle(int[] values, SplittableRandom random) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = values[i]; values[i] = values[j]; values[j] = swap;
        }
    }
}
