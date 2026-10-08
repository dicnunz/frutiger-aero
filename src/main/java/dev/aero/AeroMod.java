package dev.aero;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.hit.BlockHitResult;
import java.util.*;

public final class AeroMod implements ModInitializer {
 public static Identifier id(String path){return Identifier.of("aero",path);}
 private static Block block(String name){return Registry.register(Registries.BLOCK,id(name),new Block(AbstractBlock.Settings.create().strength(1.5f).mapColor(MapColor.WHITE)));}
 private static Block lit(String name,int light){return Registry.register(Registries.BLOCK,id(name),new Block(AbstractBlock.Settings.create().strength(2).luminance(s->light).mapColor(MapColor.PALE_GREEN)));}
 private static Item item(String name){return Registry.register(Registries.ITEM,id(name),new Item(new Item.Settings().maxCount(1)));}
 private static SoundEvent sound(String name){return Registry.register(Registries.SOUND_EVENT,id(name),SoundEvent.of(id(name)));}
 public static final Block TILE=block("porcelain"),AQUA=block("aqua_tile"),BLUE=block("blue_tile"),WHITE=block("white_panel"),GREEN=block("lawn"),LEAF=block("foliage"),SILVER=block("silver");
 public static final Block LAMP=lit("glow_panel",15),CONTROL=lit("control",7),NAVIGATOR=lit("navigator",8),WORKSHOP=lit("workshop",7),SPECIMEN=lit("specimen",6);
 public static final Block VALVE=Registry.register(Registries.BLOCK,id("valve"),new AeroValveBlock(AbstractBlock.Settings.create().strength(2).nonOpaque().luminance(s->s.get(AeroValveBlock.LIT)?9:0)));
 public static final Item FIELD_GUIDE=item("field_guide"),RETURN_STONE=item("return_stone");
 public static final SoundEvent ATRIUM=sound("atrium"),POOLS=sound("pools"),GARDEN=sound("garden"),CHIME=sound("chime");
 @Override public void onInitialize(){
  var decor=List.of(TILE,AQUA,BLUE,WHITE,GREEN,LEAF,SILVER,LAMP,CONTROL,NAVIGATOR,WORKSHOP);
  for(Block block:decor)Registry.register(Registries.ITEM,Registries.BLOCK.getId(block),new BlockItem(block,new Item.Settings()));
  ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries->{for(Block block:decor)entries.add(block);});
  AeroNetworking.register();
  ServerTickEvents.END_SERVER_TICK.register(AeroGame::tick);
  ServerLifecycleEvents.SERVER_STOPPED.register(server->AeroGame.stop());
  CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(CommandManager.literal("aero").executes(ctx->{AeroGame.enter(ctx.getSource().getPlayerOrThrow());return 1;}).then(CommandManager.literal("leave").executes(ctx->{AeroGame.leave(ctx.getSource().getPlayerOrThrow());return 1;}))));
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   BlockState block=world.getBlockState(hit.getBlockPos());
   boolean interactive=block.isOf(VALVE)||block.isOf(NAVIGATOR)||block.isOf(WORKSHOP)||block.isOf(SPECIMEN)||block.isOf(CONTROL);
   if(world.isClient)return interactive?ActionResult.SUCCESS:ActionResult.PASS;
   if(!(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
   if(interactive){ActionResult result=AeroGame.use(p,hit.getBlockPos());if(result!=ActionResult.PASS)return result;}
   if(AeroGame.here(p)){
    Item held=player.getStackInHand(hand).getItem();
    boolean within=AeroGame.plot(p.getServerWorld(),hit.getBlockPos());
    if(within&&!player.isSneaking()&&(block.hasBlockEntity()||block.getBlock() instanceof CraftingTableBlock))return ActionResult.PASS;
    var target=held instanceof BlockItem?(block.isReplaceable()?hit.getBlockPos():hit.getBlockPos().offset(hit.getSide())):hit.getBlockPos();
    if(!AeroGame.plot(p.getServerWorld(),target))return ActionResult.FAIL;
   }
   return ActionResult.PASS;
  });
  UseItemCallback.EVENT.register((player,world,hand)->{
   ItemStack stack=player.getStackInHand(hand);
   if(!world.isClient&&player instanceof ServerPlayerEntity p&&AeroGame.here(p)&&stack.getItem() instanceof BucketItem){
    var ray=player.raycast(5,0,true);
    if(ray instanceof BlockHitResult hit){var target=world.getBlockState(hit.getBlockPos()).isReplaceable()?hit.getBlockPos():hit.getBlockPos().offset(hit.getSide());if(!AeroGame.plot(p.getServerWorld(),target))return TypedActionResult.fail(stack);}
   }
   if(!stack.isOf(FIELD_GUIDE)&&!stack.isOf(RETURN_STONE))return TypedActionResult.pass(stack);
   if(!world.isClient&&player instanceof ServerPlayerEntity p)AeroGame.request(p,stack.isOf(FIELD_GUIDE)?"guide":"home");
   return TypedActionResult.success(stack,world.isClient);
  });
  PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!(player instanceof ServerPlayerEntity p)||AeroGame.canBreak(p,pos));
 }
}
