package dev.aero;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Block work is admitted on the server thread with a small time budget. */
public final class AeroBuild {
    public record Placement(BlockPos pos, BlockState state) { }
    private final ServerWorld world;
    private final List<Placement> work;
    private final Runnable finish;
    private int cursor;
    public AeroBuild(ServerWorld world, List<Placement> work, Runnable finish) { this.world = world; this.work = work; this.finish = finish; }
    public int percent() { return work.isEmpty() ? 100 : cursor * 100 / work.size(); }
    public boolean tick() {
        long start = System.nanoTime(); int count = 0;
        while (cursor < work.size() && count++ < 2400) {
            Placement next = work.get(cursor++);
            if (!world.getBlockState(next.pos).equals(next.state)) world.setBlockState(next.pos, next.state, 2);
            if ((count & 63) == 0 && System.nanoTime() - start > 5_000_000) break;
        }
        if (cursor == work.size()) { finish.run(); return true; }
        return false;
    }
}
