package dev.aero;

import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.registry.Registries;

/** Development-only capture/verification driver. Walking uses native key bindings and collision. */
public final class AeroDirector {
 private List<JsonObject> steps=List.of();private int index;private long started;private boolean running,fired;
 public boolean running(){return running;}
 public void stop(MinecraftClient mc){running=false;release(mc);}
 private void release(MinecraftClient mc){mc.options.forwardKey.setPressed(false);mc.options.backKey.setPressed(false);mc.options.leftKey.setPressed(false);mc.options.rightKey.setPressed(false);mc.options.jumpKey.setPressed(false);mc.options.sprintKey.setPressed(false);}
 public void command(MinecraftClient mc,JsonObject data){
  String action=data.get("action").getAsString();
  switch(action){
   case "key"->net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(data.get("key").getAsInt()));
   case "play"->{stop(mc);mc.options.hudHidden=false;mc.setScreen(null);}
   case "shot"->ScreenshotRecorder.saveScreenshot(mc.runDirectory,data.get("name").getAsString(),mc.getFramebuffer(),t->System.out.println("AERO_SHOT "+t.getString()));
   case "close"->mc.scheduleStop();
   case "request"->AeroClient.action(data.get("value").getAsString());
   case "select"->mc.player.getInventory().selectedSlot=MathHelper.clamp(data.get("slot").getAsInt(),0,8);
   case "resolution"->{mc.getWindow().setWindowedSize(data.get("width").getAsInt(),data.get("height").getAsInt());if(data.has("distance"))mc.options.getViewDistance().setValue(data.get("distance").getAsInt());}
   case "inspect"->inspect(mc,data);
   case "reloadShader"-> {try{Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);}catch(Exception ex){System.err.println("AERO_SHADER "+ex);}}
   case "move"->{stop(mc);var a=data.getAsJsonArray("pos");double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),z=a.get(2).getAsDouble();mc.getServer().execute(()->{var p=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());p.teleport(p.getServerWorld(),x,y,z,data.get("yaw").getAsFloat(),data.get("pitch").getAsFloat());});mc.setScreen(null);}
   case "interact"->{var a=data.getAsJsonArray("pos");click(mc,new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
   case "sequence"->{stop(mc);ArrayList<JsonObject> commands=new ArrayList<>();for(var e:data.getAsJsonArray("steps"))commands.add(e.getAsJsonObject());steps=commands;index=0;fired=false;started=System.nanoTime();running=!steps.isEmpty();mc.options.hudHidden=data.has("hideHud")&&data.get("hideHud").getAsBoolean();mc.setScreen(null);}
   case "hide"->{mc.options.hudHidden=true;mc.setScreen(null);}
 }
 }
 private void inspect(MinecraftClient mc,JsonObject request){mc.getServer().execute(()->{try{
  var server=mc.getServer();var player=server.getPlayerManager().getPlayer(mc.player.getUuid());var save=AeroGame.state(server);var world=server.getWorld(AeroGame.RETREAT);var data=new JsonObject();var gson=new GsonBuilder().setPrettyPrinting().create();
  data.add("book",gson.toJsonTree(save.book));data.addProperty("loadFailed",save.loadFailed);data.addProperty("mode",player.interactionManager.getGameMode().getName());
  var inventory=new JsonArray();for(int i=0;i<36;i++){var stack=player.getInventory().getStack(i);var item=new JsonObject();item.addProperty("slot",i);item.addProperty("id",Registries.ITEM.getId(stack.getItem()).toString());item.addProperty("count",stack.getCount());inventory.add(item);}data.add("inventory",inventory);
  if(save.book.active){var plan=new ExpeditionPlan(save.book.seed,save.book.theme);var rooms=new JsonArray();for(int id=0;id<9;id++){var room=new JsonObject();room.addProperty("id",id);room.addProperty("kind",plan.roomKind(id).name());room.addProperty("links",plan.connections(id));room.addProperty("species",plan.specimenType(id));var origin=AeroSectorBuilder.room(id);room.add("origin",gson.toJsonTree(List.of(origin.getX(),origin.getY(),origin.getZ())));
    if(plan.puzzle(id)!=null){var c=plan.puzzle(id);room.addProperty("size",c.size());var nodes=new JsonArray();for(int i=0;i<c.tileCount();i++){var node=new JsonObject();var pos=AeroSectorBuilder.valve(id,i,c.size());node.addProperty("index",i);node.addProperty("rotation",save.book.rotations.get(id)[i]);node.addProperty("solution",c.solutionRotation(i));node.addProperty("shape",c.shapeMask(i));node.add("pos",gson.toJsonTree(List.of(pos.getX(),pos.getY(),pos.getZ())));nodes.add(node);}room.add("nodes",nodes);}rooms.add(room);}data.add("rooms",rooms);}
  if(request.has("positions")){var probes=new JsonArray();for(var e:request.getAsJsonArray("positions")){var a=e.getAsJsonArray();var pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());var result=new JsonObject();result.add("pos",a.deepCopy());result.addProperty("block",world.getBlockState(pos).toString());probes.add(result);}data.add("probes",probes);}
  Files.writeString(mc.runDirectory.toPath().resolve("aero-inspection.json"),gson.toJson(data));
 }catch(Exception ex){System.err.println("AERO_INSPECT "+ex);}});}
 private void click(MinecraftClient mc,BlockPos pos){if(mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))>24)return;var hit=new BlockHitResult(Vec3d.ofCenter(pos).add(0,.45,0),Direction.UP,pos,false);mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,hit);mc.player.swingHand(Hand.MAIN_HAND);}
 public void tick(MinecraftClient mc){
  if(!running)return;JsonObject step=steps.get(index);double elapsed=(System.nanoTime()-started)/1e9;
  double duration=step.has("seconds")?step.get("seconds").getAsDouble():.25;
  release(mc);
  if(step.has("yaw")){float target=step.get("yaw").getAsFloat();float difference=MathHelper.wrapDegrees(target-mc.player.getYaw());mc.player.setYaw(mc.player.getYaw()+MathHelper.clamp(difference,-1.6f,1.6f));}
  if(step.has("pitch")){float target=step.get("pitch").getAsFloat();mc.player.setPitch(mc.player.getPitch()+MathHelper.clamp(target-mc.player.getPitch(),-1.2f,1.2f));}
  mc.options.forwardKey.setPressed(step.has("forward")&&step.get("forward").getAsBoolean());mc.options.jumpKey.setPressed(step.has("jump")&&step.get("jump").getAsBoolean());
  if(step.has("click")&&elapsed>.2&&!fired){fired=true;var a=step.getAsJsonArray("click");click(mc,new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
  if(step.has("mine")){var a=step.getAsJsonArray("mine");var pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());if(mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))<24){if(!fired){mc.interactionManager.attackBlock(pos,Direction.UP);fired=true;}mc.interactionManager.updateBlockBreakingProgress(pos,Direction.UP);mc.player.swingHand(Hand.MAIN_HAND);}}
  if(elapsed>=duration){release(mc);index++;fired=false;started=System.nanoTime();if(index>=steps.size()){running=false;System.out.println("AERO_SEQUENCE finished");}}
 }
}
