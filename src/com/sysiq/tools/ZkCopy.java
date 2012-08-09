package com.sysiq.tools;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

//import com.intershop.beehive.core.capi.log.Logger;

public class ZkCopy implements Watcher
{

    String znode;    
    String source;
    String destination;
    ZooKeeper zkOut;
    
    BlockingQueue<String> queue = new ArrayBlockingQueue<String>(100000);
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        // TODO Auto-generated method stub
        String source = args[0];
        String destination = args[1];
        String znode = args[2];
        
        try {
            new ZkCopy(source, destination, znode).executeThreads();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public ZkCopy(String source, String destination, String znode) throws IOException, KeeperException, InterruptedException {
        this.source = source;
        this.destination = destination;
        this.znode = znode;
        zkOut = new ZooKeeper(destination, 3000, this);
        checkCreatePath(zkOut, znode);
    }  
    
    public void checkCreatePath(ZooKeeper zk, String path) throws KeeperException, InterruptedException {
//        System.out.println("CCP:"+path + " ");
        String[] l = path.split("/");
        StringBuffer b = new StringBuffer();
        for(int i=1; i<l.length; i++) {            
            b.append('/');
            b.append(l[i]);
            System.out.println("CCP: " + b.toString());
            Stat stat = zk.exists(b.toString(), false);
            if (stat == null) {
                zk.create(b.toString(), null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }
    }
    
    public void executeThreads() throws KeeperException, InterruptedException {
        checkCreatePath(zkOut, znode);
        //*
        final int threadsNumber = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadsNumber, new ZkThreadFactory(source));
        
        AtomicInteger totalCounter = new AtomicInteger(0);
        AtomicInteger processedCounter = new AtomicInteger(0);
        pool.execute(new ZNodeWalker(zkOut, znode, pool, totalCounter, processedCounter));
        
//        pool.shutdown();
        try
        {
            while(true) {
                if (pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.out.println("complete");
                    break;
                } 
                System.out.println("total=" + totalCounter + " processed=" + processedCounter);
                if (totalCounter.get() == processedCounter.get()) {
                    // all work finished
                    pool.shutdown();
                }
            }
        }
        catch(InterruptedException e)
        {
            System.out.println("Await Termination of pool was unsuccessful: " + e.getMessage());
        }
        //*/
    }

    @Override
    public void process(WatchedEvent event)
    {
        // TODO Auto-generated method stub
        
    }

}

class ZNodeWalker implements Runnable {
    
    private final String znode;
    private final ExecutorService pool;
    private final AtomicInteger totalCounter;
    private final AtomicInteger processedCounter;
    private final ZooKeeper zkOut;
    
    ZNodeWalker(ZooKeeper zkOut, String znode, ExecutorService pool, AtomicInteger totalCounter, AtomicInteger processedCounter) {
        this.znode = znode;
        this.pool = pool;
        this.totalCounter = totalCounter;
        this.processedCounter = processedCounter;
        this.zkOut = zkOut;
        totalCounter.incrementAndGet();
    }

    @Override
    public void run()
    {
        try {
            ZkThread thread = (ZkThread)Thread.currentThread();
            ZooKeeper zk = thread.getZooKeeper();
            Stat stat = null;
            
            stat = zk.exists(znode, false);
            
//            System.out.println("Synchronizing " + znode + " ...");
            
            if (stat != null) {
                
                
                  
                sync(zk, znode);
                
                List<String> children = null;
                
                    children = zk.getChildren(znode, false);
                
                
//                System.out.println("Children count of " + znode + " : " + children.size() + children.toString());
                for(String child:children) {
    //                System.out.println(child);
                    if ("zookeeper".equals(child)) {
                        // reserved
                        continue;
                    }
                    if ("/".equals(znode)) {
                        push(znode + child);
                    } else {
                        push(znode + "/" + child);
                    }
                }
            } else {
                System.out.print("Node " + znode + " doesn't exist");
            }        
        }
        catch (KeeperException e) {
            e.printStackTrace();
        }
        catch(InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally {
            processedCounter.incrementAndGet();
//            System.out.println("F " + znode + " " + totalCounter + " " + processedCounter);
        }
    }
    
    private void push(String node) {
//        System.out.println("Pushing " + node);
        pool.execute(new ZNodeWalker(zkOut, node, pool, totalCounter, processedCounter));
    }
    
    private void sync(ZooKeeper zk, String node) throws KeeperException, InterruptedException {
        
        Stat stat = zkOut.exists(node, false);
//        List<ACL> acl = zk.getACL(node, stat);
        List<ACL> acl = Ids.OPEN_ACL_UNSAFE;
        byte[] data = zk.getData(node, false, stat);
        if (stat == null) {
            zkOut.create(node, data, acl, CreateMode.PERSISTENT);
        } else {
//            zkOut.setACL(node, acl, stat.getVersion());
            zkOut.setData(node, data, -1);
        }
    }
}

class ZkThreadFactory implements ThreadFactory {
    
    private final String hostPort;
    
    public ZkThreadFactory(String hostPort) {
        this.hostPort = hostPort;
    }
    public Thread newThread(Runnable r) {
      return new ZkThread(r, hostPort);
    }
    
}

class ZkThread extends Thread implements Watcher {
    
    private ZooKeeper zk = null;
    
    public ZkThread(Runnable r, String hostPort) {
        super(r);
        try
        {
            zk = new ZooKeeper(hostPort, 3000, this);
        }
        catch(IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
//            zk = null;
        }
    }
    
    @Override
    public void process(WatchedEvent event)
    {
        // TODO Auto-generated method stub
        
    }
    
    public ZooKeeper getZooKeeper() {
        return zk;
    }
}