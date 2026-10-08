package dev.aero;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import static dev.aero.AeroMod.*;

/** Server-owned journeys, discovery and construction. Clients never award progress. */
public final class AeroGame {
    public static final RegistryKey<World> RETREAT=RegistryKey.of(RegistryKeys.WORLD,id("retreat"));
    private static final Gson JSON=new Gson();
    private static AeroBuild build;
    private static final Set<UUID> waiting=new HashSet<>();
    private static int ticks;
    public static final String[] THEMES={"Poolways","Canopy","Gallery"};
    public static final String[] SPECIES={"Blue orchid","Flowering azalea","Moss","Spore blossom","Fern","Amethyst","Sea pickle","Mangrove","Glow berries","Dripleaf","Cherry","Lily pad"};
    public static final Item[] CUTTINGS={Items.BLUE_ORCHID,Items.FLOWERING_AZALEA,Items.MOSS_BLOCK,Items.SPORE_BLOSSOM,Items.FERN,Items.AMETHYST_BLOCK,Items.SEA_PICKLE,Items.MANGROVE_PROPAGULE,Items.GLOW_BERRIES,Items.BIG_DRIPLEAF,Items.CHERRY_SAPLING,Items.LILY_PAD};
    public static void stop(){build=null;waiting.clear();ticks=0;}
    public static AeroState state(MinecraftServer server){return AeroState.get(server.getOverworld());}
    public static boolean here(ServerPlayerEntity player){return player.getServerWorld().getRegistryKey().equals(RETREAT);}
    public static boolean inSector(BlockPos pos){var p=pos.subtract(AeroSectorBuilder.SECTOR);return p.getX()>=-3&&p.getX()<=83&&p.getZ()>=-3&&p.getZ()<=83;}
    public static int roomAt(BlockPos pos){var p=pos.subtract(AeroSectorBuilder.SECTOR);int x=p.getX()/28,z=p.getZ()/28;return p.getX()>=0&&p.getZ()>=0&&x<3&&z<3?z*3+x:-1;}
    public static boolean plot(ServerWorld world,BlockPos pos){if(!world.getRegistryKey().equals(RETREAT))return false;var save=state(world.getServer());if(save.loadFailed)return false;int n=save.book.building&&save.book.buildKind.equals("garden")?save.book.gardenOldSize:save.book.gardenSize;return pos.getX()>=48&&pos.getX()<48+n&&pos.getZ()>=126&&pos.getZ()<126+n&&pos.getY()>180&&pos.getY()<=203;}
    public static boolean canBreak(ServerPlayerEntity player,BlockPos pos){return !here(player)||plot(player.getServerWorld(),pos);}
    public static void enter(ServerPlayerEntity player){
        MinecraftServer server=player.getServer();ServerWorld world=server.getWorld(RETREAT);
        if(world==null){player.sendMessage(Text.literal("Retreat dimension unavailable. Enable the mod's data pack."),false);return;}
        AeroState save=state(server);if(save.loadFailed){player.sendMessage(Text.literal("Aero progress could not be loaded. Preserve the save and restore a world backup; no retreat blocks were changed."),false);return;}var data=save.player(player.getUuid());
        if(!here(player)){data.returnDimension=player.getServerWorld().getRegistryKey().getValue().toString();data.returnX=player.getX();data.returnY=player.getY();data.returnZ=player.getZ();data.returnYaw=player.getYaw();data.returnPitch=player.getPitch();data.returnMode=player.interactionManager.getGameMode().getName();save.markDirty();}
        if(!save.book.hubReady){waiting.add(player.getUuid());if(build==null)beginHub(server);sync(player,"",0);return;}
        home(player);kit(player);sync(player,"",0);
    }
    private static void kit(ServerPlayerEntity player){var save=state(player.getServer());var p=save.player(player.getUuid());if(!p.kit){p.kit=true;save.markDirty();give(player,FIELD_GUIDE,1);give(player,RETURN_STONE,1);give(player,Items.IRON_PICKAXE,1);give(player,TILE.asItem(),32);give(player,Blocks.GLASS.asItem(),16);player.unlockRecipes(List.of(id("porcelain"),id("aqua_tile"),id("blue_tile"),id("white_panel"),id("lawn"),id("foliage"),id("silver"),id("glow_panel"),id("control")));player.sendMessage(Text.literal("M: field notes   G: return home   Blue compass: next journey"),false);}}
    private static void beginHub(MinecraftServer server){
        var save=state(server);save.book.building=true;save.book.buildKind="hub";save.markDirty();
        build=new AeroBuild(server.getWorld(RETREAT),AeroSectorBuilder.hub(),()->{
            save.book.hubReady=true;save.book.building=false;save.book.buildKind="";save.markDirty();
            for(UUID id:new HashSet<>(waiting)){var p=server.getPlayerManager().getPlayer(id);if(p!=null){home(p);kit(p);sync(p,"",0);}}
            waiting.clear();System.out.println("AERO_HUB ready");
        });
    }
    public static void home(ServerPlayerEntity player){
        if(!state(player.getServer()).book.hubReady)return;
        player.teleport(player.getServer().getWorld(RETREAT),4.5,181,-9.5,-35,0);player.changeGameMode(GameMode.SURVIVAL);player.setVelocity(Vec3d.ZERO);player.setHealth(player.getMaxHealth());
    }
    public static void leave(ServerPlayerEntity player){if(!here(player))return;var save=state(player.getServer());if(save.loadFailed){var world=player.getServer().getOverworld();var pos=world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,world.getSpawnPos()).up();player.teleport(world,pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);player.changeGameMode(player.getServer().getDefaultGameMode());AeroNetworking.send(player,"{\"here\":false}");player.sendMessage(Text.literal("Returned to world spawn. Aero progress is preserved but unavailable until you restore a backup."),false);return;}var p=save.player(player.getUuid());ServerWorld world=player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(p.returnDimension)));if(world==null)world=player.getServer().getOverworld();player.teleport(world,p.returnX,p.returnY,p.returnZ,p.returnYaw,p.returnPitch);player.changeGameMode(GameMode.byName(p.returnMode,GameMode.SURVIVAL));sync(player,"",0);}
    public static void tick(MinecraftServer server){
        ticks++;var save=state(server);ServerWorld world=server.getWorld(RETREAT);if(world==null||save.loadFailed)return;
        if(build==null&&save.book.building){if(save.book.buildKind.equals("hub"))beginHub(server);else if(save.book.buildKind.equals("sector"))generate(server);else if(save.book.buildKind.equals("garden"))expand(server);else{save.book.building=false;save.markDirty();}}
        if(build!=null&&build.tick())build=null;
        for(var player:server.getPlayerManager().getPlayerList()){
            if(here(player)){
                boolean sector=inSector(player.getBlockPos());GameMode mode=sector?GameMode.ADVENTURE:GameMode.SURVIVAL;
                if(!player.isSpectator()&&player.interactionManager.getGameMode()!=mode)player.changeGameMode(mode);
                player.getHungerManager().setFoodLevel(20);player.setHealth(player.getMaxHealth());
                if(player.getY()<177){home(player);player.sendMessage(Text.literal("Returned safely. Your journey is still there."),true);}
                if(sector&&save.book.active){int room=roomAt(player.getBlockPos());if(room>=0&&save.book.visited.add(room))save.markDirty();}
            }
            if(ticks%20==0&&(here(player)||waiting.contains(player.getUuid())))sync(player,"",build==null?0:build.percent());
        }
    }
    public static ActionResult use(ServerPlayerEntity player,BlockPos pos){
        if(!here(player)||player.isSpectator())return ActionResult.PASS;
        var world=player.getServerWorld();var save=state(player.getServer());if(save.loadFailed)return ActionResult.FAIL;BlockState block=world.getBlockState(pos);
        if(block.isOf(NAVIGATOR)){
            if(pos.equals(AeroSectorBuilder.exit())&&save.book.active){if(save.book.solved.size()<3){player.sendMessage(Text.literal("Restore the three flows. G can return you home at any time."),true);return ActionResult.SUCCESS;}complete(player);home(player);}
            sync(player,"navigator",0);return ActionResult.SUCCESS;
        }
        if(block.isOf(WORKSHOP)){sync(player,"workshop",0);return ActionResult.SUCCESS;}
        if(block.isOf(CONTROL)&&Set.of(AeroSectorBuilder.HOME.add(10,2,29),AeroSectorBuilder.HOME.add(-19,2,78),AeroSectorBuilder.HOME.add(5,2,108)).contains(pos)){world.setBlockState(pos.up(),LAMP.getDefaultState(),2);world.playSound(null,pos,CHIME,SoundCategory.BLOCKS,.45f,1);return ActionResult.SUCCESS;}
        if(!save.book.active)return ActionResult.PASS;
        var plan=new ExpeditionPlan(save.book.seed,save.book.theme);int room=roomAt(pos);
        if(block.isOf(VALVE)&&room>=0&&plan.roomKind(room)==ExpeditionPlan.RoomKind.FLOW){
            if(save.book.solved.contains(room))return ActionResult.SUCCESS;
            var circuit=plan.puzzle(room);int[] rotations=save.book.rotations.get(room);int selected=-1;
            for(int i=0;i<circuit.tileCount();i++)if(pos.equals(AeroSectorBuilder.valve(room,i,circuit.size()))){selected=i;break;}
            if(selected<0||rotations==null)return ActionResult.PASS;
            rotations[selected]=(rotations[selected]+1)&3;
            boolean[] flow=circuit.flow(rotations);
            for(int i=0;i<rotations.length;i++)world.setBlockState(AeroSectorBuilder.valve(room,i,circuit.size()),AeroSectorBuilder.valveState(circuit,i,rotations[i],flow[i]),2);
            world.playSound(null,pos,net.minecraft.sound.SoundEvents.BLOCK_COPPER_BULB_HIT,SoundCategory.BLOCKS,.45f,1+.08f*rotations[selected]);
            if(circuit.solved(rotations)){
                save.book.solved.add(room);save.player(player.getUuid()).credits+=4;
                BlockPos at=AeroSectorBuilder.room(room);for(int x=19;x<=21;x++)for(int z=6;z<=18;z++)world.setBlockState(at.add(x,0,z),Blocks.WATER.getDefaultState(),2);
                world.setBlockState(at.add(20,1,19),LAMP.getDefaultState(),2);int end=10+circuit.size()-1;world.setBlockState(at.add(end+2,1,end),LAMP.getDefaultState(),2);
                world.playSound(null,pos,CHIME,SoundCategory.BLOCKS,.7f,1+.12f*save.book.solved.size());
                player.sendMessage(Text.literal("Flow restored  "+save.book.solved.size()+" / 3    +4 materials"),true);
                System.out.println("AERO_FLOW room="+room+" restored="+save.book.solved.size());
            }
            save.markDirty();sync(player,"",0);return ActionResult.SUCCESS;
        }
        if(block.isOf(SPECIMEN)&&room>=0&&pos.equals(AeroSectorBuilder.sample(room,plan.theme()))&&save.book.sampled.add(room)){
            int species=plan.specimenType(room);var p=save.player(player.getUuid());boolean first=p.discoveries.add(species);p.credits+=first?3:1;
            give(player,CUTTINGS[species],4);world.setBlockState(pos,Blocks.FLOWERING_AZALEA.getDefaultState(),2);world.playSound(null,pos,CHIME,SoundCategory.BLOCKS,.55f,1.7f);
            player.sendMessage(Text.literal((first?"Found ":"Gathered ")+SPECIES[species]+". Plant it in your garden."),true);save.markDirty();sync(player,"",0);return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    private static void complete(ServerPlayerEntity player){var save=state(player.getServer());if(save.book.completed)return;save.book.completed=true;var p=save.player(player.getUuid());p.credits+=6;p.journeys++;save.markDirty();player.sendMessage(Text.literal("Journey complete. Your garden has new possibilities."),true);System.out.println("AERO_JOURNEY complete="+save.book.serial+" credits="+p.credits);}
    public static void request(ServerPlayerEntity player,String action){
        if(action.equals("enter")){enter(player);return;}
        if(!here(player)||player.isSpectator())return;
        var save=state(player.getServer());if(save.loadFailed)return;var p=save.player(player.getUuid());
        if(action.equals("home")){home(player);sync(player,"",0);return;}
        if(action.equals("map")||action.equals("guide")){sync(player,action,0);return;}
        if(action.equals("abandon")){boolean occupied=player.getServer().getPlayerManager().getPlayerList().stream().anyMatch(p2->here(p2)&&inSector(p2.getBlockPos()));if(!occupied&&nearNavigator(player)&&!save.book.building){save.book.active=false;save.markDirty();sync(player,"navigator",0);}return;}
        if(action.equals("leave")){leave(player);return;}
        if(action.equals("resume")){if(save.book.active&&!save.book.building){depart(player);sync(player,"",0);}return;}
        if(action.startsWith("route:")){
            int theme;try{theme=Integer.parseInt(action.substring(6));}catch(NumberFormatException ex){return;}
            if(theme<0||theme>2||save.book.building||!nearNavigator(player))return;
            if(save.book.active&&!save.book.completed){player.sendMessage(Text.literal("Finish or continue the current journey first."),true);sync(player,"navigator",0);return;}
            for(var other:player.getServer().getPlayerManager().getPlayerList())if(here(other)&&inSector(other.getBlockPos())){player.sendMessage(Text.literal("Everyone must return home before the next journey."),true);return;}
            save.book.serial++;save.book.seed=player.getServerWorld().getSeed()^save.book.serial*0x9e3779b97f4a7c15L;
            save.book.theme=theme;save.book.active=true;save.book.completed=false;save.book.solved.clear();save.book.visited.clear();save.book.sampled.clear();save.book.rotations.clear();
            save.book.building=true;save.book.buildKind="sector";save.markDirty();waiting.add(player.getUuid());generate(player.getServer());sync(player,"",0);return;
        }
        if(player.squaredDistanceTo(Vec3d.ofCenter(AeroSectorBuilder.SHOP))>49)return;
        if(action.equals("expand")){
            if(save.book.gardenSize>=9998||p.credits<12||save.book.building)return;
            p.credits-=12;save.book.gardenOldSize=save.book.gardenSize;save.book.gardenSize+=10;save.book.building=true;save.book.buildKind="garden";save.markDirty();
            expand(player.getServer());sync(player,"",0);return;
        }
        Item item=null;int count=32,cost=2;
        switch(action){case "material:white"->item=TILE.asItem();case "material:aqua"->item=AQUA.asItem();case "material:blue"->item=BLUE.asItem();case "material:glass"->item=Items.GLASS;case "material:green"->item=LEAF.asItem();case "material:light"->{item=LAMP.asItem();count=8;}default->{}}
        if(item!=null&&p.credits>=cost){if(!roomFor(player,item,count)){player.sendMessage(Text.literal("Make room in your inventory first."),true);return;}p.credits-=cost;give(player,item,count);save.markDirty();sync(player,"workshop",0);}
    }
    private static boolean nearNavigator(ServerPlayerEntity player){
        var w=player.getServerWorld();BlockPos center=player.getBlockPos();for(BlockPos pos:BlockPos.iterate(center.add(-5,-3,-5),center.add(5,3,5)))if(w.getBlockState(pos).isOf(NAVIGATOR))return true;return false;
    }
    private static void generate(MinecraftServer server){
        var save=state(server);var plan=new ExpeditionPlan(save.book.seed,save.book.theme);
        build=new AeroBuild(server.getWorld(RETREAT),AeroSectorBuilder.expedition(plan,save.book),()->{
            save.book.building=false;save.book.buildKind="";save.markDirty();
            for(UUID id:new HashSet<>(waiting)){var player=server.getPlayerManager().getPlayer(id);if(player!=null){depart(player);sync(player,"",0);}}
            waiting.clear();System.out.println("AERO_SECTOR ready serial="+save.book.serial+" theme="+save.book.theme+" signature="+plan.signature());
        });
    }
    private static void expand(MinecraftServer server){var save=state(server);build=new AeroBuild(server.getWorld(RETREAT),AeroSectorBuilder.garden(save.book.gardenOldSize,save.book.gardenSize),()->{save.book.building=false;save.book.buildKind="";save.markDirty();for(var p:server.getPlayerManager().getPlayerList())if(here(p))sync(p,"",0);});}
    private static void depart(ServerPlayerEntity player){var p=AeroSectorBuilder.room(0);player.teleport(player.getServer().getWorld(RETREAT),p.getX()+11.5,p.getY()+1,p.getZ()+8.5,0,0);player.changeGameMode(GameMode.ADVENTURE);player.setVelocity(Vec3d.ZERO);}
    private static void give(ServerPlayerEntity player,Item item,int count){ItemStack stack=new ItemStack(item,count);if(!player.getInventory().insertStack(stack)&&!stack.isEmpty())player.dropItem(stack,false);}
    private static boolean roomFor(ServerPlayerEntity player,Item item,int count){ItemStack target=new ItemStack(item);for(int i=0;i<36;i++){ItemStack stack=player.getInventory().getStack(i);if(stack.isEmpty())count-=target.getMaxCount();else if(ItemStack.areItemsAndComponentsEqual(stack,target))count-=Math.max(0,stack.getMaxCount()-stack.getCount());if(count<=0)return true;}return false;}
    public static void sync(ServerPlayerEntity player,String panel,int progress){
        var save=state(player.getServer());var b=save.book;var p=save.player(player.getUuid());var data=new LinkedHashMap<String,Object>();
        data.put("here",here(player));data.put("building",b.building);data.put("percent",progress);data.put("active",b.active);data.put("completed",b.completed);data.put("serial",b.serial);data.put("theme",b.theme);data.put("solved",b.solved);data.put("visited",b.visited);data.put("sampled",b.sampled);data.put("discoveries",p.discoveries);data.put("credits",p.credits);data.put("journeys",p.journeys);data.put("gardenSize",b.gardenSize);data.put("sector",here(player)&&inSector(player.getBlockPos()));data.put("room",here(player)?roomAt(player.getBlockPos()):-1);data.put("panel",panel);
        if(b.active){var plan=new ExpeditionPlan(b.seed,b.theme);var rooms=new ArrayList<Map<String,Object>>();for(int i=0;i<9;i++){var r=new LinkedHashMap<String,Object>();r.put("id",i);r.put("kind",plan.roomKind(i).name());r.put("links",plan.connections(i));r.put("species",plan.specimenType(i));rooms.add(r);}data.put("rooms",rooms);}
        AeroNetworking.send(player,JSON.toJson(data));
    }
}
