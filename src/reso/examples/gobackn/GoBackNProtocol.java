
package reso.examples.gobackn;

import reso.common.AbstractTimer;
import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.scheduler.AbstractScheduler;
import java.lang.Math;

/**
 * GoBackNProtocol, implementation of the namesake pipelining protocol.
 * Uses two constructors, one using a host parameter and a packet list parameters for the Sender application, and the
 * other using only a host parameter for the Receiver application.
 * Uses a built-in timer named MyTimer, adapted from the AppAlone class and using the same structure and principles.
 */
public class GoBackNProtocol implements IPInterfaceListener {

    /**
     * The protocol number identifying Go-Back-N.
     */
    public static final int IP_PROTO_GOBACKN= Datagram.allocateProtocolNumber("GOBACKN");

    /**
     * The host of the protocol.
     */
    private final IPHost host;

    /**
     * Current sequence number, determines packet being treated.
     */
    public int sequenceNumber = 0;
    /**
     * The sequence number of 1st packet in window, correspond to the start of the window.
     */
    public int sendBase = 0;
    /**
     * Size of the window. End of the window is sequenceNumber = sendBase + size.
     */
    public int size = 5;
    /**
     * ACK detection boolean.
     */
    private boolean ack = false;
    /**
     * The list of packets to be treated by the protocol.
     */
    private TCPSegment[] packetList;

    /**
     * Instance of the timer.
     */
    protected MyTimer timer;

    /**
     * Alpha.
     */
    private static double ALPHA = 0.125;
    /**
     * Beta.
     */
    private static double BETA = 0.25;

    /**
     * Previous SRTT.
     */
    private double SRTT;
    /**
     * Old DevRTT
     */
    private double DevRTT;
    /**
     * RTO
     */
    private double RTO;

    /**
     * Built-in timer adapted from the AppAlone class and using the same structure and principles.
     */
    private class MyTimer extends AbstractTimer {
        
        private IPAddress dst;
        private double startTime;
        private double stopTime;

    	public MyTimer(AbstractScheduler scheduler, double interval, IPAddress dst) {
            super(scheduler, interval, false);
            this.dst = dst;
    	}
    	protected void run() throws Exception {
            timeout(dst);
            System.out.println("app=[" + host.name + "]" +
                               " time=" + scheduler.getCurrentTime());
        }

        @Override
        public void start() {
            super.start();
            startTime = scheduler.getCurrentTime();
        }

        @Override
        public void stop() {
            super.stop();
            stopTime = scheduler.getCurrentTime();
        }

        public double getR(){
    	    return stopTime - startTime;
        }
    }

    /**
     * Receiver GoBackNProtocol constructor.
     * @param host of the protocol.
     */
    public GoBackNProtocol(IPHost host) {
        this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
    	RTO = 3;
    }

    /**
     * Sender GoBackNProtocol constructor.
     * @param host of the protocol.
     * @param packetList to be treated by the protocol.
     */
    public GoBackNProtocol(IPHost host, TCPSegment[] packetList) {
        this.host= host;
        this.packetList = packetList;
        RTO = 3;
    }

    /**
     * The receiving method of the protocol. Treats incoming packets.
     * @param src source of the message.
     * @param datagram content of the message.
     * @throws Exception caught from local sendAcknowledgment() method.
     */
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
                changeRTO();
            } else {
                timer = new MyTimer(host.getNetwork().getScheduler(), RTO, datagram.src);
                timer.start();
            }
            if (sequenceNumber < packetList.length) {
                sendData(packetList[sequenceNumber].data[0], datagram.src);
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

    /**
     * Timeout event handler.
     * @param dst destination of the cause of the timeout.
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     */
    public void timeout(IPAddress dst) throws Exception {
        if (!timer.isRunning()) {
            for (int i = sendBase; i < sequenceNumber; i++) {
                host.getIPLayer().send(IPAddress.ANY, dst, IP_PROTO_GOBACKN, packetList[i]);
            }
        }
    }

    /**
     * The sending method of the protocol. Proceeds to send packet to the destination.
     * @param data to send.
     * @param destination of the message.
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        if (sequenceNumber < sendBase + size) {
            int[] segmentData = new int[]{data};
            TCPSegment packet = new TCPSegment(segmentData, sequenceNumber);
            packetList[sequenceNumber] = packet;
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, packet);
            if (sendBase == sequenceNumber) {
                timer = new MyTimer(host.getNetwork().getScheduler(), RTO, destination);
                timer.start();
            }
            sequenceNumber += 1;
        } else {
            // no gud
        }

    }

    /**
     * ACK sending method of the protocol. Acknowledges messages.
     * @param datagram
     * @throws Exception
     */
    private void sendAcknowledgment(Datagram datagram) throws Exception{
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(sequenceNumber));
    }

    /**
     * Method to get current SRTT.
     * @return current SRTT.
     */
    private double getSRTT(){
        if (SRTT > 0) {
            SRTT =  ((1 - ALPHA) * this.SRTT + ALPHA * timer.getR());
        }
        else {
            SRTT = timer.getR();
        }
        return SRTT;
    }

    /**
     * Method to get current DevRTT.
     * @return current DevRTT.
     */
    private double getDevRTT(){
        if (DevRTT > 0) {
            DevRTT = ((1 - BETA) * DevRTT + BETA * Math.abs(getSRTT() - timer.getR()));
        }
        else {
            DevRTT = timer.getR()/2;
        }
        return DevRTT;
    }

    /**
     * Method to get current RTO.
     * @return current RTO.
     */
    private void changeRTO(){
        double devRTT = getDevRTT();
        RTO = getSRTT() + 4*devRTT;
    }


}
