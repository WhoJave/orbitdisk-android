package dev.orbitdisk.app;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;

/** Private, versioned data only. Never uses Java object deserialization. */
final class ScanSnapshot {
    private static final int MAGIC=0x4f524254, VERSION=1;
    private static final int MAX_NODES=500000, MAX_DEPTH=128;
    private static final long MAX_BYTES=256L*1024*1024;
    static final class Saved {
        final StorageScanner.Result result;
        final long time;
        Saved(StorageScanner.Result result,long time){this.result=result;this.time=time;}
    }
    private static final Comparator<StorageScanner.Node> ORDER=(a,b)->{
        int c=Long.compare(b.size,a.size);return c!=0?c:a.path.compareTo(b.path);
    };
    static void write(File file, StorageScanner.Result result, long time) throws IOException {
        if(result.root==null || !result.root.directory || time<=0)throw new IOException("Incomplete scan");
        File temp=new File(file.getPath()+".tmp");
        try {
            try(FileOutputStream raw=new FileOutputStream(temp);
                BufferedOutputStream buffer=new BufferedOutputStream(raw)) {
                CheckedOutputStream checked=new CheckedOutputStream(buffer,new CRC32());
                DataOutputStream out=new DataOutputStream(checked);
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(time);
                out.writeLong(result.files);out.writeLong(result.folders);out.writeLong(result.skipped);
                writeNode(out,result.root,0,new int[1]);out.flush();
                long checksum=checked.getChecksum().getValue();
                new DataOutputStream(buffer).writeLong(checksum);buffer.flush();
                if(raw.getChannel().size()>MAX_BYTES)throw new IOException("Snapshot too large");
                raw.getFD().sync();
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp.toPath()); }
    }
    private static void writeNode(DataOutputStream out,StorageScanner.Node n,int depth,int[] count)throws IOException {
        if(depth>MAX_DEPTH || ++count[0]>MAX_NODES || n.size<0 || (!n.directory&&!n.children.isEmpty()))throw new IOException("Invalid tree");
        out.writeUTF(n.name);out.writeUTF(n.path);out.writeBoolean(n.directory);out.writeLong(n.size);out.writeInt(n.children.size());
        if(out.size()>MAX_BYTES-8)throw new IOException("Snapshot too large");
        for(StorageScanner.Node child:n.children)writeNode(out,child,depth+1,count);
    }
    /** Missing, unsupported, truncated, oversized or corrupt caches are simply ignored. */
    static Saved read(File file) {
        if(!file.isFile() || file.length()>MAX_BYTES)return null;
        try(BufferedInputStream buffer=new BufferedInputStream(new FileInputStream(file))) {
            CheckedInputStream checked=new CheckedInputStream(buffer,new CRC32());
            DataInputStream in=new DataInputStream(checked);
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION)return null;
            long time=in.readLong();
            StorageScanner.Result r=new StorageScanner.Result();
            r.files=in.readLong();r.folders=in.readLong();r.skipped=in.readLong();
            if(time<=0 || r.files<0 || r.folders<1 || r.skipped<0)return null;
            Reader reader=new Reader();r.root=reader.node(in,0,null);
            if(!r.root.directory || r.files!=reader.files || r.folders!=reader.folders)return null;
            long checksum=checked.getChecksum().getValue();
            if(new DataInputStream(buffer).readLong()!=checksum || buffer.read()!=-1)return null;
            r.largest.addAll(reader.top);r.largest.sort(ORDER);
            return new Saved(r,time);
        } catch(IOException | RuntimeException e){return null;}
    }
    private static final class Reader {
        int count;long files,folders;
        final PriorityQueue<StorageScanner.Node> top=new PriorityQueue<>(100,ORDER.reversed());
        StorageScanner.Node node(DataInputStream in,int depth,StorageScanner.Node parent)throws IOException {
            if(depth>MAX_DEPTH || ++count>MAX_NODES)throw new IOException("Tree limit");
            String name=in.readUTF(),path=in.readUTF();boolean directory=in.readBoolean();
            long size=in.readLong();int children=in.readInt();
            if(name.isEmpty() || !new File(path).isAbsolute() || size<0 || children<0 || children>MAX_NODES-count || (!directory&&children!=0))throw new IOException("Invalid node");
            if(parent!=null && (!name.equals(new File(path).getName()) || !new File(path).getParent().equals(parent.path)))throw new IOException("Invalid path");
            StorageScanner.Node n=new StorageScanner.Node(name,path,directory);n.size=size;
            if(directory)folders++;else {files++;top.offer(n);if(top.size()>100)top.poll();}
            long total=0;
            for(int i=0;i<children;i++) {
                StorageScanner.Node child=node(in,depth+1,n);n.children.add(child);
                total=Long.MAX_VALUE-total<child.size?Long.MAX_VALUE:total+child.size;
            }
            if(directory&&total!=size)throw new IOException("Invalid total");
            return n;
        }
    }
}
