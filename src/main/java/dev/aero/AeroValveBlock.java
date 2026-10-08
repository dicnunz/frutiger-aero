package dev.aero;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** A visibly directional two-port conduit, rotated by ordinary right-click. */
public final class AeroValveBlock extends Block {
    public static final MapCodec<AeroValveBlock> CODEC = createCodec(AeroValveBlock::new);
    public static final IntProperty ROTATION = IntProperty.of("rotation", 0, 3);
    public static final BooleanProperty ELBOW = BooleanProperty.of("elbow"), LIT = BooleanProperty.of("lit");
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 5, 16);
    public AeroValveBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(ROTATION, 0).with(ELBOW, false).with(LIT, false));
    }
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(ROTATION, ELBOW, LIT); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
}
