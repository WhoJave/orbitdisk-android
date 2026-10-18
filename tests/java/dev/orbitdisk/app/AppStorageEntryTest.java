package dev.orbitdisk.app;
import java.util.*;
public final class AppStorageEntryTest {
 public static void main(String[] args){
  AppStorageEntry a=new AppStorageEntry("dev.alpha","Alpha",false,100,200,80,null);
  AppStorageEntry b=new AppStorageEntry("dev.beta","Beta",true,200,300,10,null);
  AppStorageEntry denied=new AppStorageEntry("dev.denied","Denied",false,-1,-1,-1,"SecurityException");
  assert a.total()==300 : "cache must not be counted twice";
  assert !denied.available() && denied.total()==-1;
  assert !new AppStorageEntry("x","x",false,Long.MAX_VALUE,1,0,null).available();
  assert !new AppStorageEntry("x","x",false,-1,1,0,null).available();
  assert a.matches("ALPHA",false) && a.matches("DEV.ALP",false);
  assert !b.matches("",false) && b.matches("BETA",true);
  List<AppStorageEntry> list=new ArrayList<>(Arrays.asList(denied,a,b));list.sort(AppStorageEntry.ORDER);
  assert list.get(0)==b && list.get(1)==a && list.get(2)==denied;
  assert AppStorageEntry.ORDER.compare(a,new AppStorageEntry("dev.zz","Alpha",false,100,200,0,null))<0;
  assert !a.matches("missing",true);
  System.out.println("AppStorageEntryTest: all assertions passed");
 }
}
