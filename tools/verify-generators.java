import dev.aero.CircuitPlan;
import dev.aero.ExpeditionPlan;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/* Run from the project root:
 * javac -d /tmp/aero-generator-check src/main/java/dev/aero/CircuitPlan.java \
 *     src/main/java/dev/aero/ExpeditionPlan.java tools/verify-generators.java
 * java -cp /tmp/aero-generator-check VerifyGenerators
 */
final class VerifyGenerators {
    private static int circuitsChecked;

    public static void main(String[] args) {
        int seeds = args.length == 0 ? 4096 : Integer.parseInt(args[0]);
        check(seeds >= 1000, "Stress check requires at least 1000 seeds");
        Set<String> signatures = new HashSet<>(), graphs = new HashSet<>();
        Set<String> gentlePaths = new HashSet<>(), complexPaths = new HashSet<>();
        Set<Integer> themes = new HashSet<>(), specimens = new HashSet<>();
        for (int i = 0; i < seeds; i++) {
            long seed = i * 0x9e3779b97f4a7c15L ^ 0xd1b54a32d192ed03L;
            verifyCircuit(new CircuitPlan(seed, 3));
            verifyCircuit(new CircuitPlan(seed, 4));
            gentlePaths.add(Arrays.toString(new CircuitPlan(seed, 3).solutionPath()));
            complexPaths.add(Arrays.toString(new CircuitPlan(seed, 4).solutionPath()));
            ExpeditionPlan plan = new ExpeditionPlan(seed), repeated = new ExpeditionPlan(seed);
            check(plan.seed() == seed, "Expedition seed changed");
            check(plan.theme() >= 0 && plan.theme() < 3, "Theme range");
            check(plan.signature().equals(repeated.signature()), "Expedition identity is nondeterministic");
            themes.add(plan.theme()); signatures.add(plan.signature());
            int[] counts = new int[ExpeditionPlan.RoomKind.values().length];
            int[] doors = new int[9];
            int edgeEnds = 0, branchingRooms = 0, sideBranches = 0;
            Set<Integer> expeditionSpecimens = new HashSet<>();
            for (int room = 0; room < 9; room++) {
                counts[plan.roomKind(room).ordinal()]++;
                doors[room] = plan.connections(room);
                edgeEnds += Integer.bitCount(doors[room]);
                if (Integer.bitCount(doors[room]) >= 3) branchingRooms++;
                if (room > 0 && room < 8 && Integer.bitCount(doors[room]) == 1) sideBranches++;
                check(plan.roomKind(room) == repeated.roomKind(room), "Room kind differs");
                check(doors[room] == repeated.connections(room), "Doors differ");
                check((doors[room] & ~15) == 0, "Door mask range");
                int type = plan.specimenType(room);
                check(type >= 0 && type < 12, "Specimen range");
                check(type == repeated.specimenType(room), "Specimen differs");
                specimens.add(type);
                if (plan.roomKind(room) == ExpeditionPlan.RoomKind.SPECIMEN) {
                    check(expeditionSpecimens.add(type), "Repeated specimen inside one expedition");
                }
                for (int d = 0; d < 4; d++) {
                    if ((doors[room] & (1 << d)) == 0) continue;
                    int x = room % 3 + (d == 1 ? 1 : d == 3 ? -1 : 0);
                    int y = room / 3 + (d == 2 ? 1 : d == 0 ? -1 : 0);
                    check(x >= 0 && x < 3 && y >= 0 && y < 3, "Door leaves grid");
                    check((plan.connections(y * 3 + x) & (1 << ((d + 2) & 3))) != 0,
                            "Door is not reciprocal");
                }
                if (plan.roomKind(room) == ExpeditionPlan.RoomKind.FLOW) {
                    check(plan.puzzle(room) != null, "Flow room has no circuit");
                    check(plan.puzzle(room).size() == (plan.theme() == 2 ? 4 : 3), "Theme circuit width");
                    verifyCircuit(plan.puzzle(room));
                    sameCircuit(plan.puzzle(room), repeated.puzzle(room));
                } else check(plan.puzzle(room) == null, "Non-flow room has a circuit");
            }
            check(Arrays.equals(counts, new int[]{1, 3, 2, 2, 1}), "Wrong room-kind counts");
            check(plan.roomKind(0) == ExpeditionPlan.RoomKind.ENTRY, "Entry room changed");
            check(plan.roomKind(8) == ExpeditionPlan.RoomKind.FLOW
                    || plan.roomKind(8) == ExpeditionPlan.RoomKind.OVERLOOK, "Invalid exit kind");
            check(reachableRooms(doors) == 9, "Disconnected expedition");
            check(edgeEnds / 2 >= 9, "Expedition has no cycle");
            check(branchingRooms > 0 && sideBranches > 0, "Expedition has no side choice");
            graphs.add(Arrays.toString(doors));
            for (int requestedTheme = 0; requestedTheme < 3; requestedTheme++) {
                ExpeditionPlan themed = new ExpeditionPlan(seed, requestedTheme);
                ExpeditionPlan themedAgain = ExpeditionPlan.generate(seed, requestedTheme);
                check(themed.theme() == requestedTheme, "Explicit theme was ignored");
                check(themed.signature().equals(themedAgain.signature()), "Explicit theme is nondeterministic");
                if (requestedTheme == plan.theme()) {
                    check(themed.signature().equals(plan.signature()), "Seed-only theme differs from explicit theme");
                }
                for (int room = 0; room < 9; room++) if (themed.puzzle(room) != null) {
                    check(themed.puzzle(room).size() == (requestedTheme == 2 ? 4 : 3), "Explicit theme circuit width");
                    verifyCircuit(themed.puzzle(room));
                }
            }
        }
        check(signatures.size() >= seeds * .99, "Repeated expedition configurations");
        check(graphs.size() >= 100, "Insufficient graph variation");
        check(gentlePaths.size() == 8, "Gentle routes do not cover their available topologies");
        check(complexPaths.size() >= 100, "Insufficient complex path variation");
        check(themes.size() == 3 && specimens.size() == 12, "Missing themes or specimen types");
        for (int mask = 0; mask < 16; mask++) for (int turns = -8; turns <= 8; turns++) {
            int rotated = CircuitPlan.rotateMask(mask, turns);
            check(Integer.bitCount(rotated) == Integer.bitCount(mask), "Rotation loses ports");
            check(CircuitPlan.rotateMask(rotated, -turns) == mask, "Rotation inverse failed");
        }
        System.out.println("PASS: " + seeds + " expedition seeds, " + circuitsChecked
                + " circuits; " + signatures.size() + " distinct configurations, " + graphs.size()
                + " room graphs, " + gentlePaths.size() + " gentle routes, " + complexPaths.size()
                + " complex routes. Connectivity, cycles, side branches, objective counts, deterministic"
                + " identity, solved solutions, unsolved starts and defensive copies verified.");
    }

    private static void verifyCircuit(CircuitPlan plan) {
        circuitsChecked++;
        int n = plan.size(), count = plan.tileCount();
        check(n == 3 || n == 4, "Circuit width");
        check(plan.n() == n && count == n * n, "Circuit dimensions");
        int[] initial = new int[count], solution = new int[count];
        for (int i = 0; i < count; i++) {
            int shape = plan.shapeMask(i);
            check(shape == 5 || shape == 3, "Tile requires a tee or cross");
            initial[i] = plan.initialRotation(i); solution[i] = plan.solutionRotation(i);
            check(initial[i] >= 0 && initial[i] < 4 && solution[i] >= 0 && solution[i] < 4,
                    "Rotation range");
        }
        check(!plan.solved(initial), "Initial circuit is solved");
        check(plan.solved(solution), "Generated solution does not solve circuit");
        int[] path = plan.solutionPath();
        check(path[0] == plan.sourceIndex() && path[path.length - 1] == plan.sinkIndex(), "Route endpoints");
        boolean[] routeTiles = new boolean[count];
        int bends = 0, lastDirection = 0, misplaced = 0;
        for (int p = 0; p < path.length; p++) {
            int tile = path[p];
            check(tile >= 0 && tile < count && !routeTiles[tile], "Route repeats or leaves grid");
            routeTiles[tile] = true;
            if (CircuitPlan.rotateMask(plan.shapeMask(tile), initial[tile])
                    != CircuitPlan.rotateMask(plan.shapeMask(tile), solution[tile])) misplaced++;
            if (p == 0) continue;
            int dx = tile % n - path[p - 1] % n, dy = tile / n - path[p - 1] / n;
            check(Math.abs(dx) + Math.abs(dy) == 1, "Route has nonadjacent tiles");
            int direction = dx != 0 ? dx : dy * n;
            if (p > 1 && lastDirection != direction) bends++;
            lastDirection = direction;
        }
        check(bends >= 3, "Circuit lacks bends");
        check(misplaced >= Math.max(2, path.length / 3), "Scramble differs too little from solution");
        check(Arrays.equals(routeTiles, plan.flow(solution)), "Solution flow leaves intended unique route");
        int source = plan.sourceIndex(), sink = plan.sinkIndex();
        check((CircuitPlan.rotateMask(plan.shapeMask(source), solution[source]) & 8) != 0, "Source inlet");
        check((CircuitPlan.rotateMask(plan.shapeMask(sink), solution[sink]) & 2) != 0, "Sink outlet");
        int originalFirst = path[0]; path[0] = -1;
        check(plan.solutionPath()[0] == originalFirst, "Route exposes mutable state");
        sameCircuit(plan, new CircuitPlan(plan.seed(), n));
        while ((CircuitPlan.rotateMask(plan.shapeMask(source), solution[source]) & 8) != 0) {
            solution[source] = (solution[source] + 1) & 3;
        }
        check(!plan.solved(solution), "Closed source still solves puzzle");
        for (boolean wet : plan.flow(solution)) check(!wet, "Closed source emits water");
    }

    private static void sameCircuit(CircuitPlan a, CircuitPlan b) {
        check(a.seed() == b.seed() && a.size() == b.size(), "Circuit identity differs");
        check(Arrays.equals(a.solutionPath(), b.solutionPath()), "Circuit route is nondeterministic");
        for (int tile = 0; tile < a.tileCount(); tile++) {
            check(a.shapeMask(tile) == b.shapeMask(tile)
                    && a.initialRotation(tile) == b.initialRotation(tile)
                    && a.solutionRotation(tile) == b.solutionRotation(tile), "Circuit tile is nondeterministic");
        }
    }

    private static int reachableRooms(int[] connections) {
        boolean[] seen = new boolean[9];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0); seen[0] = true; int count = 0;
        while (!queue.isEmpty()) {
            int room = queue.remove(); count++;
            for (int d = 0; d < 4; d++) {
                if ((connections[room] & (1 << d)) == 0) continue;
                int next = room + (d == 0 ? -3 : d == 1 ? 1 : d == 2 ? 3 : -1);
                if (seen[next]) continue;
                seen[next] = true; queue.add(next);
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
