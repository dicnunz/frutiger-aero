package dev.aero;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.util.WorldSavePath;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Files;

/** Durable world progress. Garden blocks and player inventories use Minecraft's normal saves. */
public final class AeroState extends PersistentState {
    private static final Gson JSON = new Gson();
    private static final Type<AeroState> TYPE = new Type<>(AeroState::new, AeroState::read, null);
    public Book book = new Book();
    public boolean loadFailed;
    private static final Map<MinecraftServer,AeroState> rejected = new WeakHashMap<>();

    public static AeroState get(ServerWorld world) {
        MinecraftServer server=world.getServer();
        if(rejected.containsKey(server))return rejected.get(server);
        var manager=server.getOverworld().getPersistentStateManager();
        AeroState loaded=manager.get(TYPE,"aero_retreat");
        if(loaded!=null)return loaded;
        if(Files.exists(server.getSavePath(WorldSavePath.ROOT).resolve("data/aero_retreat.dat"))){
            AeroState blocked=new AeroState();blocked.loadFailed=true;rejected.put(server,blocked);
            System.err.println("AERO_SAVE blocked: existing progress could not be loaded. Preserve aero_retreat.dat and restore a world backup.");
            return blocked;
        }
        return manager.getOrCreate(TYPE,"aero_retreat");
    }

    public static final class Book {
        public int schema = 2;
        public boolean hubReady, building, active, completed;
        public String buildKind = "";
        public long serial, seed;
        public int theme, gardenOldSize, gardenSize = 18;
        public Set<Integer> solved = new HashSet<>(), visited = new HashSet<>(), sampled = new HashSet<>();
        public Map<Integer, int[]> rotations = new HashMap<>();
        public Map<String, PlayerBook> players = new HashMap<>();
    }

    public static final class PlayerBook {
        public int credits = 8, journeys;
        public Set<Integer> discoveries = new HashSet<>();
        public boolean kit;
        public String returnDimension = "minecraft:overworld", returnMode = "survival";
        public double returnX = .5, returnY = 70, returnZ = .5;
        public float returnYaw, returnPitch;
    }

    public PlayerBook player(UUID id) {
        PlayerBook value = book.players.get(id.toString());
        if (value == null) { value = new PlayerBook(); book.players.put(id.toString(), value); markDirty(); }
        return value;
    }

    static AeroState read(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        AeroState state = new AeroState();
        Book data = JSON.fromJson(tag.getString("book"), Book.class);
        if (data == null || data.schema != 2 || data.gardenSize < 18 || data.gardenSize > 10000
                || data.players == null || data.rotations == null || data.solved == null
                || data.sampled == null || data.visited == null || data.theme < 0 || data.theme > 2)
            throw new IllegalArgumentException("Unsupported or malformed Frutiger Aero progress; preserve the world backup.");
        for (PlayerBook player : data.players.values()) {
            if (player == null || player.discoveries == null || player.credits < 0 || player.journeys < 0)
                throw new IllegalArgumentException("Malformed Frutiger Aero player progress.");
        }
        state.book = data;
        return state;
    }

    @Override public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putString("book", JSON.toJson(book));
        return tag;
    }
}
