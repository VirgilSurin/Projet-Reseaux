
package reso.examples.gobackn;

import java.util.ArrayList;
import java.util.LinkedList;

import reso.common.AbstractTimer;
import reso.common.HardwareInterface;
import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;
import reso.scheduler.AbstractScheduler;

public class GoBackNProtocol implements IPInterfaceListener {

    public static final int IP_PROTO_GOBACKN= Datagram.allocateProtocolNumber("GOBACKN");
    
    private final IPHost host;

    public int sequenceNumber = 0;
    public int sendBase = 0;
    public int size = 5;
    private boolean ack = false;
    private TCPSegment[] packetList;

    protected AbstractTimer timer;
    

    private class MyTimer extends AbstractTimer {
    	public MyTimer(AbstractScheduler scheduler, double interval) {
    		super(scheduler, interval, false);
    	}
    	protected void run() throws Exception {
            System.out.println("app=[" + host.name + "]" +
                               " time=" + scheduler.getCurrentTime());
        }
    }

public GoBackNProtocol(IPHost host) {
        this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
        timer = new MyTimer(host.getNetwork().getScheduler(), 1); // TODO : page 86 calcul TRO pour interval
    }
	
    public GoBackNProtocol(IPHost host, TCPSegment[] packetList) {
        this.host= host;
        this.packetList = packetList;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
        timer = new MyTimer(host.getNetwork().getScheduler(), 1); // TODO : page 86 calcul TRO pour interval
    }
	
    @Override
    public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
            
    	TCPSegment segment= (TCPSegment) datagram.getPayload();
        System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
                           " host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
                           datagram.dst + ", iif=" + src + ", data=" + segment);
        if(segment.isAck()){
            //TODO Check if corrupt or not
            sendBase = segment.sequenceNumber + 1;
            if (sendBase == sequenceNumber) {
                timer.stop();
            } else {
                timer.start();
            }
        }
        else{
            //TODO Check if corrupt or not
            if (segment.sequenceNumber == sequenceNumber) {
                int[] data = segment.data;
                // TODO find how do we deliver data to application
                sendAcknowledgment(datagram);
                ack = true;
                sequenceNumber += 1;
            } else {
                if (!ack) {
                    sendAcknowledgment(datagram);
                }
            }
        }
    }

    public void timeout(IPAddress dst) throws Exception {
        if (!timer.isRunning()) {
            for (int i = sendBase; i < sequenceNumber; i++) {
                host.getIPLayer().send(IPAddress.ANY, dst, IP_PROTO_GOBACKN, packetList[i]);
            }
        }
    }

    public void sendData(int data, IPAddress destination) throws Exception{
        if (sequenceNumber < sendBase + size) {
            int[] segmentData = new int[]{data};
            TCPSegment packet = new TCPSegment(segmentData, sequenceNumber);
            packetList[sequenceNumber] = packet;
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, packet);
            if (sendBase == sequenceNumber) {
            timer.start();
            }
            sequenceNumber += 1;
        } else {
            // no gud
        }

        
    }

    private void sendAcknowledgment(Datagram datagram) throws Exception{
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(sequenceNumber));
    }


}
