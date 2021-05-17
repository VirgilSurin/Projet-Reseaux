package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPAddress;
import reso.ip.IPHost;

import java.util.ArrayList;
import java.util.Random;

public class AppSender
    extends AbstractApplication
{ 
	
    private final IPAddress dst;
    private final int numberOfPackets;

    public AppSender(IPHost host, IPAddress dst, int numberOfPackets) {	
    	super(host, "sender");
    	this.dst= dst;
    	this.numberOfPackets = numberOfPackets;
    }

    public void start()
    throws Exception {
        Random rand = new Random();
        TCPSegment[] packetList = new TCPSegment[numberOfPackets];
        for (int i = 0; i < numberOfPackets; i++) {
            packetList[i] = new TCPSegment(new int[] { rand.nextInt() }, i);
        }
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host, packetList);


        for(int i=0; i < numberOfPackets; i++){
            if (!transport.timer.isRunning()) {
                transport.timeout(dst);
            } else {
                transport.sendData(packetList[i].data[0], dst);
            }
        }
    }
    
    public void stop() {}
    
}

