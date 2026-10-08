package dev.aero;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import java.util.*;
import static dev.aero.AeroMod.*;

/** A single compact architectural route. All coordinates are relative to its floor. */
public final class AeroWorld {
 private final List<AeroBuild.Placement> work=new ArrayList<>();private final BlockPos at;
 private AeroWorld(BlockPos origin){at=origin;}
 private void put(int x,int y,int z,Block b){work.add(new AeroBuild.Placement(at.add(x,y,z),b.getDefaultState()));}
 private void fill(int x0,int y0,int z0,int x1,int y1,int z1,Block b){
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y0;y<=y1;y++)put(x,y,z,b);
 }
 private void disc(int cx,int cy,int cz,int r,Block b){for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=r*r)put(cx+x,cy,cz+z,b);}
 private void pool(int x0,int z0,int x1,int z1){
  fill(x0-1,-3,z0-1,x1+1,0,z1+1,TILE);
  fill(x0,-2,z0,x1,-1,z1,AQUA);
  fill(x0,-1,z0,x1,0,z1,Blocks.WATER);
  for(int z=z0;z<=z1;z+=6)put(x0-1,-1,z,LAMP);
 }
 private void column(int x,int z,int h){fill(x,1,z,x+1,h,z+1,WHITE);fill(x,0,z,x+1,0,z+1,BLUE);fill(x-1,h+1,z-1,x+2,h+1,z+2,WHITE);}
 private void tree(int x,int z,int size){
  fill(x,1,z,x,4,z,SILVER);
  for(int y=3;y<=7;y++){int r=y==7?1:(y==3?2:size);for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)if(Math.abs(dx)+Math.abs(dz)<r*2)put(x+dx,y,z+dz,LEAF);}
 }
 private void control(int x,int z){fill(x-1,1,z-1,x+1,1,z+1,WHITE);put(x,2,z,CONTROL);put(x,3,z,Blocks.CYAN_STAINED_GLASS);}
 private void sphere(int cx,int cy,int cz,int r,Block b){
  for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++){
   double d=Math.sqrt(x*x+y*y+z*z);if(d<=r&&d>r-1.15)put(cx+x,cy+y,cz+z,b);
  }
 }
 public static List<AeroBuild.Placement> plan(BlockPos origin){var b=new AeroWorld(origin);b.build();return b.work;}
 private void build(){
  // A horizon of water beneath a raised, walkable retreat.
  fill(-72,-8,-45,72,-8,176,AQUA);
  fill(-72,-7,-45,72,-7,176,Blocks.WATER);
  fill(-27,-4,-15,27,-1,153,WHITE);fill(-27,0,-15,27,0,153,TILE);
  fill(-27,1,-15,27,13,-15,WHITE);
  fill(-27,1,-15,-27,12,113,WHITE);fill(27,1,-15,27,12,113,WHITE);
  // Atrium: lawn beds, a glass canopy and a faceted suspended water globe.
  fill(-27,1,-14,-27,3,34,BLUE);fill(27,1,-14,27,3,34,BLUE);
  fill(-27,4,-14,-27,11,34,Blocks.GLASS);fill(27,4,-14,27,11,34,Blocks.GLASS);
  fill(-27,13,-15,27,13,34,Blocks.GLASS);
  for(int z=-14;z<=34;z+=8)fill(-27,13,z,27,13,z,WHITE);
  for(int x=-27;x<=27;x+=9)fill(x,13,-15,x,13,34,WHITE);
  for(int s:new int[]{-1,1}){fill(14*s>0?13:-23,1,-3,14*s>0?23:-13,1,28,GREEN);tree(s*18,5,3);tree(s*18,23,3);}
  disc(0,0,13,10,BLUE);disc(0,0,13,9,TILE);disc(0,-1,13,8,AQUA);disc(0,0,13,8,Blocks.WATER);
  disc(0,1,13,2,WHITE);put(0,2,13,LAMP);sphere(0,7,13,4,Blocks.GLASS);disc(0,4,13,2,AQUA);put(0,5,13,LAMP);fill(0,2,13,0,3,13,SILVER);
  for(int x:new int[]{-10,10})for(int z:new int[]{-4,30})column(x,z,11);
  control(10,29);
  // High pool hall: side aisles, shallow stairs and a long axial bridge.
  fill(-27,1,35,27,15,35,WHITE);fill(-6,1,35,6,9,35,Blocks.AIR);
  fill(-27,16,35,27,16,88,WHITE);
  fill(-11,16,36,11,16,87,Blocks.GLASS);
  for(int z=36;z<=87;z+=10)fill(-27,16,z,27,16,z,WHITE);
  fill(-27,1,88,27,15,88,WHITE);fill(-4,1,88,4,8,88,Blocks.AIR);
  pool(-12,42,12,81);fill(-2,0,42,2,0,81,TILE);
  for(int z=42;z<=82;z+=10){column(-18,z,14);column(18,z,14);}
  for(int z=39;z<=85;z+=9){
   fill(-27,3,z,-27,12,z+6,Blocks.GLASS);
   fill(27,3,z,27,12,z+6,Blocks.GLASS);
   fill(-25,14,z,-21,14,z+4,LAMP);fill(21,14,z,25,14,z+4,LAMP);
  }
  // A transparent aquarium wall distinguishes the far end of the pool hall.
  fill(22,1,54,25,8,74,Blocks.GLASS);fill(23,1,55,24,7,73,Blocks.WATER);
  fill(23,0,55,24,0,73,AQUA);for(int z=56;z<=72;z+=4)fill(23,1,z,23,3,z,LEAF);
  control(-19,78);
  // Compression before release: a low gallery with luminous ceiling ribs.
  fill(-27,1,89,27,8,113,WHITE);fill(-7,1,89,7,6,112,Blocks.AIR);
  fill(-7,0,89,7,0,113,AQUA);
  for(int z=90;z<=110;z+=5){fill(-7,5,z,7,5,z,BLUE);fill(-6,6,z,6,6,z,LAMP);}
  fill(-27,9,89,27,9,113,GREEN);
  control(5,108);fill(-3,1,113,3,5,113,Blocks.AIR);
  // Sky garden: a broad reflecting pool, planted islands, a pixelated glass dome.
  fill(-34,-4,114,34,-1,153,WHITE);fill(-34,0,114,34,0,153,TILE);
  pool(-29,118,29,148);fill(-3,0,114,3,0,153,TILE);
  for(int s:new int[]{-1,1}){disc(s*18,0,132,7,WHITE);disc(s*18,1,132,6,GREEN);tree(s*18,132,4);}
  sphere(0,1,139,14,Blocks.GLASS);
  // Open the bottom of the greenhouse and give it a real walking route.
  fill(-3,1,124,3,4,153,Blocks.AIR);disc(0,0,139,11,TILE);disc(0,0,139,8,GREEN);fill(-2,0,125,2,0,152,TILE);
  for(int x:new int[]{-8,8})column(x,140,6);
  disc(0,1,144,3,WHITE);disc(0,2,144,3,WHITE);disc(0,2,144,2,Blocks.WATER);put(0,3,144,LAMP);
  for(int x=-33;x<=33;x++){put(x,1,153,WHITE);if(x%4==0)put(x,2,153,LAMP);}
  for(int s:new int[]{-1,1})fill(s*34,1,114,s*34,1,153,WHITE);
  System.out.println("AERO_BUILD complete: atrium / pools / gallery / sky garden");
 }
}
