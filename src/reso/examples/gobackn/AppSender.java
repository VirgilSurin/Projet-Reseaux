package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPAddress;
import reso.ip.IPHost;

import java.util.ArrayList;
import java.util.Random;

/**
 * Sender application of the Go-Back-N pipelining protocol.
 * Sends messages to the AppReceiver.
 */
public class AppSender
    extends AbstractApplication
{
    /**
     * The address of the receiver application, destination of the messages.
     */
    private final IPAddress dst;
    /**
     * The number of packets to deliver.
     */
    private final int numberOfPackets;
    
    /**
     * Sender application constructor.
     * @param host of the protocol.
     * @param dst destination of the messages.
     * @param numberOfPackets to deliver.
     */
    public AppSender(IPHost host, IPAddress dst, int numberOfPackets) {	
    	super(host, "sender");
    	this.dst= dst;
    	this.numberOfPackets = numberOfPackets;
    }

    /**
     * Starts the application.
     * @throws Exception from reso.example.gobackn.GoBackNProtocol.sendData()
     * and reso.example.gobackn.GoBackNProtocol.timeout methods(). Same ultimate source in reso.ip.IPLayer.send().
     * @see reso.examples.gobackn.GoBackNProtocol GoBackNProtocol
     */
    public void start()
    throws Exception {
        Random rand = new Random();
        TCPSegment[] packetList = new TCPSegment[numberOfPackets];
        for (int i = 0; i < numberOfPackets; i++) {
            packetList[i] = new TCPSegment(new int[] { rand.nextInt() }, i);
        }
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host, packetList);

        for(int i=0; i < numberOfPackets; i++){
            transport.sendData(packetList[i].data[0], dst);
        }
    }

    /**
     * Stops the application. #TODO might be useless. If so remove.
     */
    public void stop() {}
    
}

