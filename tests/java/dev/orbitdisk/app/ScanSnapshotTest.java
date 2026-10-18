package dev.orbitdisk.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanSnapshotTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void same(StorageScanner.Node a,StorageScanner.Node b){
        check(a.name.equals(b.name)&&a.path.equals(b.path)&&a.size==b.size&&a.directory==b.directory&&a.children.size()==b.children.size(),"node roundtrip");
        for(int i=0;i<a.children.size();i++)same(a.children.get(i),b.children.get(i));
    }
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("orbit-snapshot-");
        try {
            Path root=Files.createDirectory(temp.resolve("tree"));
            Files.createDirectories(root.resolve("子目录/empty"));
            for(int i=0;i<150;i++)Files.write(root.resolve("子目录/f"+i),new byte[i]);
            StorageScanner.Result r=new StorageScanner().scan(root.toFile(),new AtomicBoolean(),null);
            File cache=temp.resolve("scan.bin").toFile();long time=1700000000123L;
            check(ScanSnapshot.read(cache)==null,"missing safe");
            ScanSnapshot.write(cache,r,time);byte[] valid=Files.readAllBytes(cache.toPath());
            ScanSnapshot.Saved s=ScanSnapshot.read(cache);
            check(s!=null&&s.time==time,"timestamp");same(r.root,s.result.root);
            check(s.result.files==r.files&&s.result.folders==r.folders&&s.result.skipped==r.skipped,"counts");
            check(s.result.largest.size()==100,"top100");
            for(int i=0;i<100;i++)check(s.result.largest.get(i).path.equals(r.largest.get(i).path),"top rank");
            System.out.println("PASS snapshot roundtrip, unicode, empty directory, timestamp, top100");
            for(int length:new int[]{0,1,7,40,valid.length-1}){
                Files.write(cache.toPath(),Arrays.copyOf(valid,length));check(ScanSnapshot.read(cache)==null,"truncated "+length);
            }
            for(int index:new int[]{0,7,15,30,valid.length/2,valid.length-1}){
                byte[] corrupt=valid.clone();corrupt[index]^=1;Files.write(cache.toPath(),corrupt);check(ScanSnapshot.read(cache)==null,"corrupt "+index);
            }
            byte[] extra=Arrays.copyOf(valid,valid.length+1);Files.write(cache.toPath(),extra);check(ScanSnapshot.read(cache)==null,"trailing bytes");
            System.out.println("PASS missing, version/header, truncation, checksum, trailing corruption");
            Files.write(cache.toPath(),valid);
            StorageScanner.Node original=r.root;
            StorageScanner.Node deep=new StorageScanner.Node("root","/root",true);r.root=deep;
            for(int i=0;i<130;i++){StorageScanner.Node next=new StorageScanner.Node("d",deep.path+"/d",true);deep.children.add(next);deep=next;}
            try{ScanSnapshot.write(cache,r,time);throw new AssertionError("depth must fail");}catch(IOException expected){}
            check(Arrays.equals(valid,Files.readAllBytes(cache.toPath())),"failed write preserves complete previous cache");
            check(!new File(cache.getPath()+".tmp").exists(),"temp cleaned");r.root=original;
            ScanSnapshot.write(cache,r,time+1);check(ScanSnapshot.read(cache).time==time+1,"atomic replacement");
            try(RandomAccessFile oversized=new RandomAccessFile(cache,"rw")){oversized.setLength(256L*1024*1024+1);}
            check(ScanSnapshot.read(cache)==null,"oversize rejected");
            System.out.println("PASS depth bound, failed write preservation, atomic replacement, oversize");
            System.out.println("OK: 3 snapshot test groups passed");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(temp)){
                for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);
            }
        }
    }
}
