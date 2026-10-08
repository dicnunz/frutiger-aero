package dev.aero;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.*;
import net.minecraft.client.util.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

public final class AeroClient implements ClientModInitializer {
 public static JsonObject view=new JsonObject();
 private boolean creating,started;private int ticks,ambienceTick,room=-1;
 private SoundInstance ambience;private String lastCommand="";
 private KeyBinding notes,recall;
 private final AeroDirector director=new AeroDirector();
 private final AeroFramebufferRecorder framebufferRecorder=new AeroFramebufferRecorder();
 private static AeroClient captureClient;
 public static boolean bool(String key){return view.has(key)&&view.get(key).getAsBoolean();}
 public static int number(String key){return view.has(key)?view.get(key).getAsInt():0;}
 public static void action(String value){if(ClientPlayNetworking.canSend(AeroNetworking.Request.ID)){ClientPlayNetworking.send(new AeroNetworking.Request(value));if(!value.startsWith("material:"))MinecraftClient.getInstance().setScreen(null);}}
 public static void renderFrame(MinecraftClient mc){if(Boolean.getBoolean("aero.demo")&&captureClient!=null&&captureClient.director.running())captureClient.director.frame(mc);}
 public static void captureAfterFrame(MinecraftClient mc){if(Boolean.getBoolean("aero.demo")&&captureClient!=null)captureClient.framebufferRecorder.afterFrame(mc);}
 public static void captureStart(MinecraftClient mc,String name)throws java.io.IOException{if(Boolean.getBoolean("aero.demo")&&captureClient!=null)captureClient.framebufferRecorder.start(mc,name);}
 public static void captureStop(){if(Boolean.getBoolean("aero.demo")&&captureClient!=null)captureClient.framebufferRecorder.stop();}
 public static void rememberLaunchWindow(MinecraftClient mc,int width,int height){
  if(!Boolean.getBoolean("aero.launch")||width<320||height<180)return;
  try{JsonObject size=new JsonObject();size.addProperty("width",width);size.addProperty("height",height);Files.writeString(mc.runDirectory.toPath().resolve("aero-window.json"),size.toString());}
  catch(Exception ex){System.err.println("AERO_WINDOW "+ex);}
 }
 private void restoreLaunchWindow(MinecraftClient mc){
  int width=mc.options.overrideWidth,height=mc.options.overrideHeight;
  try{Path saved=mc.runDirectory.toPath().resolve("aero-window.json");if(Files.exists(saved)){JsonObject size=JsonParser.parseString(Files.readString(saved)).getAsJsonObject();int w=size.get("width").getAsInt(),h=size.get("height").getAsInt();if(w>=320&&h>=180){width=w;height=h;}}}
  catch(Exception ex){System.err.println("AERO_WINDOW "+ex);}
  if(width<320||height<180){boolean retina=mc.getWindow().getFramebufferWidth()>mc.getWindow().getWidth()*1.5;width=retina?640:1280;height=retina?360:720;}
  mc.getWindow().setWindowedSize(width,height);rememberLaunchWindow(mc,width,height);
 }
 @Override public void onInitializeClient(){
  captureClient=this;
  notes=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aero.notes",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.aero"));
  recall=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aero.return",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.aero"));
  ClientPlayNetworking.registerGlobalReceiver(AeroNetworking.Snapshot.ID,(payload,context)->context.client().execute(()->{
   view=JsonParser.parseString(payload.json()).getAsJsonObject();String panel=view.has("panel")?view.get("panel").getAsString():"";
   if(!panel.isEmpty())context.client().setScreen(new AeroScreen(panel));
  }));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{view=new JsonObject();if(ambience!=null)mc.getSoundManager().stop(ambience);ambience=null;room=-1;started=false;director.stop(mc);captureStop();if(!mc.getWindow().isFullscreen())rememberLaunchWindow(mc,mc.getWindow().getWidth(),mc.getWindow().getHeight());});
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  HudRenderCallback.EVENT.register((draw,delta)->{
   var mc=MinecraftClient.getInstance();if(mc.player==null||mc.options.hudHidden)return;
   if(bool("building")){draw.drawTextWithShadow(mc.textRenderer,"Preparing the next space  "+number("percent")+"%",10,10,0xffeaf7f5);return;}
   if(!bool("here"))return;
   String status=bool("sector")?AeroGame.THEMES[number("theme")]+"  /  Flows "+view.getAsJsonArray("solved").size()+" / 3":"Your garden  /  "+number("credits")+" materials";
   draw.drawTextWithShadow(mc.textRenderer,status,10,10,0xffeaf7f5);
   if(mc.crosshairTarget instanceof BlockHitResult hit){var b=mc.world.getBlockState(hit.getBlockPos());String hint=b.isOf(AeroMod.VALVE)?"Rotate the pipe":b.isOf(AeroMod.SPECIMEN)?"Gather a cutting":b.isOf(AeroMod.NAVIGATOR)?"Choose a journey":b.isOf(AeroMod.WORKSHOP)?"Build your garden":"";
    if(!hint.isEmpty())draw.drawCenteredTextWithShadow(mc.textRenderer,hint,mc.getWindow().getScaledWidth()/2,mc.getWindow().getScaledHeight()/2+18,0xfff0faf6);
   }
  });
 }
 private void tick(MinecraftClient mc){try{
  if(Boolean.getBoolean("aero.launch")&&creating&&!started&&mc.currentScreen!=null&&mc.currentScreen.getClass().getSimpleName().equals("BackupPromptScreen")){
   // Minecraft flags custom dimensions as experimental. Preserve a backup before resuming.
   for(var child:mc.currentScreen.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("Create Backup and Load")){button.onPress();break;}
  }
  if(Boolean.getBoolean("aero.launch")&&!creating&&mc.currentScreen!=null&&mc.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))mc.setScreen(new TitleScreen());
  if(Boolean.getBoolean("aero.launch")&&!creating&&mc.currentScreen instanceof TitleScreen&&mc.getOverlay()==null){
   creating=true;mc.options.pauseOnLostFocus=false;mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
   mc.options.getViewDistance().setValue(8);mc.options.getSimulationDistance().setValue(5);mc.options.getFov().setValue(68);mc.options.getFovEffectScale().setValue(0.0);mc.options.getMaxFps().setValue(60);mc.options.getEnableVsync().setValue(false);mc.options.getGuiScale().setValue(2);mc.options.getBobView().setValue(true);
   mc.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(0.0);mc.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(.75);restoreLaunchWindow(mc);
   Path marker=mc.runDirectory.toPath().resolve("aero-world.txt");String directory=Files.exists(marker)?Files.readString(marker).strip():"Frutiger Aero";
   if(!Boolean.getBoolean("aero.fresh")&&Files.exists(mc.runDirectory.toPath().resolve("saves").resolve(directory).resolve("level.dat")))mc.createIntegratedServerLoader().start(directory,()->mc.setScreen(new TitleScreen()));
   else{
    directory=Boolean.getBoolean("aero.fresh")?"Aero QA "+System.currentTimeMillis():"Frutiger Aero";Files.writeString(marker,directory);
    var rules=new GameRules();
    mc.createIntegratedServerLoader().createAndStart(directory,new LevelInfo("Frutiger Aero",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(8102005,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),new TitleScreen());
   }
  }
  if(creating&&!started&&mc.player!=null&&mc.getNetworkHandler()!=null){started=true;if(!mc.world.getRegistryKey().equals(AeroGame.RETREAT))mc.getNetworkHandler().sendChatCommand("aero");}
  if(mc.player==null||mc.world==null)return;
  ticks++;if(notes.wasPressed()&&bool("here"))action(bool("sector")?"map":"guide");if(recall.wasPressed()&&bool("here"))action("home");
  if(bool("here")){
   int currentRoom=bool("sector")?(number("theme")==1?2:1):(mc.player.getZ()<35?0:mc.player.getZ()<114?1:2);
   if(currentRoom!=room||ambience==null||++ambienceTick>1180){if(ambience!=null)mc.getSoundManager().stop(ambience);room=currentRoom;ambienceTick=0;SoundEvent sound=room==0?AeroMod.ATRIUM:room==1?AeroMod.POOLS:AeroMod.GARDEN;ambience=PositionedSoundInstance.master(sound,1.0f,.55f);mc.getSoundManager().play(ambience);}
  }else if(ambience!=null){mc.getSoundManager().stop(ambience);ambience=null;room=-1;}
  // Instrumentation is opt-in. Ordinary installs do not read or write control files.
  if(Boolean.getBoolean("aero.demo")){
   Path request=mc.runDirectory.toPath().resolve("control.json");
   if(Files.exists(request)){JsonObject data=JsonParser.parseString(Files.readString(request)).getAsJsonObject();String id=data.get("id").getAsString();if(!id.equals(lastCommand)){lastCommand=id;Files.deleteIfExists(request);director.command(mc,data);}}
   director.tick(mc);if(ticks%20==0)report(mc);
  }
 }catch(Exception ex){System.err.println("AERO_CLIENT "+ex);}}
 private void report(MinecraftClient mc){try{
  JsonObject data=view.deepCopy();data.addProperty("fps",mc.getCurrentFps());data.addProperty("tick",ticks);data.addProperty("command",lastCommand);data.addProperty("screen",mc.currentScreen==null?"game":mc.currentScreen.getClass().getSimpleName());data.addProperty("dimension",mc.world.getRegistryKey().getValue().toString());data.addProperty("x",mc.player.getX());data.addProperty("y",mc.player.getY());data.addProperty("z",mc.player.getZ());data.addProperty("yaw",mc.player.getYaw());data.addProperty("pitch",mc.player.getPitch());data.addProperty("directing",director.running());data.add("capture",framebufferRecorder.status());
  Files.writeString(mc.runDirectory.toPath().resolve("aero-state.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));
 }catch(Exception ex){System.err.println("AERO_REPORT "+ex);}}
}
