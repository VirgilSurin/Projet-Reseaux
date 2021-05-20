
package reso.examples.gobackn;

import java.util.Random;

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
    private Datagram ack = null;

    /**
     * The list of packets to be treated by the protocol.
     */
    private TCPSegment[] packetList;

    /**
     * Instance of the timer.
     */
    protected MyTimer timer;

    /**
     * Random generator used to simulate the loss of packet
     */
    private static Random rand = new Random();

    /**
     * The probability that has a packet to get lost.
     */
    private final double lossProbability = 0.20;

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
    private double RTO = 3;

    /**
     * Used to detect when we need to stop timeout
     */
    private boolean stop = false;

    /**
     * Counter used to detect when we recieve 3 ack for the same packet
     */
    private int tripleAck = 0;

    /**
     * Used to store the sequenceNumber of the last ack
     */    
    private int repeatedAckNumber = -1;
    
    /**
     * Built-in timer adapted from the AppAlone class and using the same structure and principles.
     */
    private class MyTimer extends AbstractTimer {

        /**
         * Destination IP address of the protocol.
         */
        private IPAddress dst;
        /**
         * Start time of the timer.
         */
        private double startTime;
        /**
         * Stop time of the timer.
         */
        private double stopTime;

        /**
         * Constructor for the timer.
         * @param scheduler and instance of an abstract scheduler.
         * @param interval the interval of time for the timer to work.
         * @param dst the destination of the protocol.
         */
    	public MyTimer(AbstractScheduler scheduler, double interval, IPAddress dst) {
            super(scheduler, interval, false);
            this.dst = dst;
    	}

        /**
         * Called when the timer expires.
         * @throws Exception caught from timeout.
         * @see reso.examples.gobackn.GoBackNProtocol Exception origin.
         * @prints the application using the protocol and the current time.
         */
    	protected void run() throws Exception {
            timeout(dst);
            System.out.println("app=[" + host.name + "]" +
                               " time=" + scheduler.getCurrentTime());
        }

        /**
         * Starts the timer, stores the current value of time in startTime;
         */
        @Override
        public void start() {
            super.start();
            startTime = scheduler.getCurrentTime();
        }
        /**
         * Stops the timer, stores the current value of time in stopTime;
         */
        @Override
        public void stop() {
            super.stop();
            stopTime = scheduler.getCurrentTime();
        }

        /**
         * "R" used for formula transparency.
         * @return the RTT of the timer.
         */
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
    }

    /**
     * Sender GoBackNProtocol constructor.
     * @param host of the protocol.
     * @param packetList to be treated by the protocol.
     */
    public GoBackNProtocol(IPHost host, TCPSegment[] packetList) {
        this.host= host;
        this.packetList = packetList;
        host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
    }

    /**
     * The receiving method of the protocol. Treats incoming packets.
     * Datagrams are loaded in an instance of TCPSegment and sorted by nature (ACK message or not an ACK message).
     * ACK messages trigger the progression of the window with cumulative logic and a new packet is sent.
     * Acknowledges any other massage.
     * Updates RTO after stopping any timer.
     * @param src source of the message.
     * @param datagram content of the message.
     * @throws Exception caught from local sendAcknowledgment() method.
     * @see reso.examples.gobackn.GoBackNProtocol Exception origin.
     * @prints "Data" + (receive time in ms), host, datagram source IP, datagram destination IP,
     * ethernet interface name, and sequence number and nature (ACK or not) of the segment.
     */
    @Override
    public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {

    	TCPSegment segment= (TCPSegment) datagram.getPayload();
        System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
                           " host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
                           datagram.dst + ", iif=" + src + ", data=" + segment);
        if(segment.isAck()){
            // Is used to detect triple ack
            if (repeatedAckNumber >= 0 && segment.sequenceNumber == repeatedAckNumber) {
                tripleAck += 1;
            }
            repeatedAckNumber = segment.sequenceNumber;
            if (tripleAck == 3) {
                // Triple ack detected, we resend the whole window
                System.out.println("TRIPLE ACK ///////////////");
                tripleAck = 0;
                timeout(datagram.src);
            }


            //TODO Check if corrupt or not
            sendBase = segment.sequenceNumber + 1;
            if (sendBase == this.sequenceNumber) {
                System.out.println("-------------------------STOP");
                stop = true;
                timer.stop();
            } else {
                // changeRTO();
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
                // TODO find how do we deliver data to application
                sendAcknowledgment(datagram);
                sequenceNumber += 1;
            } else {
                if (ack != null) {
                    sendAcknowledgment(datagram);
                }
            }
        }
    }

    /**
     * Timeout event handler. Tries to send again.
     * @param dst destination of the cause of the timeout.
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     * @see reso.ip.IPLayer Exception origin.
     * @prints timeout message.
     */
    public void timeout(IPAddress dst) throws Exception {
        if (!stop) {
            timer = new MyTimer(host.getNetwork().getScheduler(), RTO, dst);
            timer.start();
            System.out.println("========== TIMEOUT ==========");
            int lowBound = sendBase-1;
            if (sendBase == 0) {
                lowBound = 0;
            }
            for (int i = lowBound; i < sequenceNumber; i++) {
                host.getIPLayer().send(IPAddress.ANY, dst, IP_PROTO_GOBACKN, packetList[i]);
            }
        }
    }

    /**
     * The sending method of the protocol. Proceeds to send packet to the destination.
     * Handles packet loss simulation via random rejection.
     * @param data to send.
     * @param destination of the message.
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     * @see reso.ip.IPLayer Exception origin.
     * @prints packet loss message if a packet is lost. Can be prevented by removing segment loss simulator (see code).
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        if (sequenceNumber < sendBase + size) {
            int[] segmentData = new int[]{data};
            TCPSegment packet = new TCPSegment(segmentData, sequenceNumber);
            packetList[sequenceNumber] = packet;

            //                              =>Segment loss simulator<=
            double x = rand.nextDouble();
            if (x > lossProbability) {
                // if x > lossProbability we can send the packet. Otherwhise the packet is lost.
                host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, packet);
            } else {
                System.out.println("PACKET LOST => sequenceNumber : " + packet.sequenceNumber);
            }
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
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     * @see reso.ip.IPLayer Exception origin.
     */
    private void sendAcknowledgment(Datagram datagram) throws Exception{
        ack = datagram;
        TCPSegment packet = new TCPSegment(sequenceNumber);
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, packet);
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
