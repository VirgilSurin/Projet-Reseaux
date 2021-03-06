package reso.examples.gobackn;

import java.util.Random;

import reso.common.AbstractTimer;
import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.scheduler.AbstractScheduler;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.Math;

/**
 * GoBackNProtocol, implementation of the namesake pipelining protocol.
 * Uses two constructors, one using a host parameter and a packet list parameters for the Sender application, and the
 * other using only a host parameter for the Receiver application.
 * Uses a built-in timer named MyTimer, adapted from the AppAlone class and using the same structure and principles.
 */
public class GoBackNProtocol implements IPInterfaceListener {

    // IP VARIABLES ===========================================================================
    /**
     * The protocol number identifying Go-Back-N.
     */
    public static final int IP_PROTO_GOBACKN= Datagram.allocateProtocolNumber("GOBACKN");

    /**
     * The host of the protocol.
     */
    private final IPHost host;

    // GO-BACK-N SPECIFIC VARIABLES ===========================================================

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
    public double size = 1;

    // PACKET RELATED VARIABLES ===============================================================
    
    /**
     * Last ack stored.
     */
    private Datagram ack = null;

    /**
     * The list of packets to be treated by the protocol.
     */
    private TCPSegment[] packetList;

    // TIMER ==================================================================================

    /**
     * Instance of the timer.
     */
    protected MyTimer timer;

    // PACKET LOSS SIMULATION RANDOMISATION VARIABLES =========================================

    /**
     * Random generator used to simulate the loss of packet.
     */
    private static Random rand = new Random();

    /**
     * The probability that has a packet to get lost.
     */
    private double lossProbability;

    // RTP CALCULATION RELATED VARIABLES ======================================================

    /**
     * Alpha.
     */
    private static double ALPHA = 0.125;
    
    /**
     * Beta.
     */
    private static double BETA = 0.25;

    /**
     * SRTT. Updated in calculation.
     */
    private double SRTT;
    /**
     * DevRTT. Updated in calculation.
     */
    private double DevRTT;
    /**
     * RTO. Used as interval of time in timer.
     */
    private double RTO = 3;

    // TIMEOUT RELATED VARIABLES ==============================================================

    /**
     * Counter used for the detection of triple ACKs for a packet.
     */
    private int tripleAck = 0;

    /**
     * Used to store the sequenceNumber of the last ACK.
     */    
    private int repeatedAckNumber = -1;

    // CONGESTION CONTROL VARIABLES ===========================================================
    /**
     * Storage variable for old window size. Used in congestion control.
     */
    private double oldSize;
    /**
     * Storage variable for new window size. Used in congestion control.
     */
    private double newSize;
    /**
     * Maximum Message Size constant for congestion control.
     * In this implementation we only ever send 1 packet per message.
     */
    private final int MSS = 1;
    /**
     * Threshold to delimit slow start, set to a high value to prevent self-chocking.
     */
    private double sstresh = 20;

    // FILE HANDLING VARIABLES =====================================================================

    /**
     * Used to write window's size variation in a csv file.
     */
    private String exportData; 
    
    // END OF VARIABLES =====================================================================
    
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
         * Starts the timer, stores the current value of time in startTime.
         */
        @Override
        public void start() {
            if (sequenceNumber < packetList.length) {
                super.start();
                startTime = scheduler.getCurrentTime();
            }
        }
        /**
         * Stops the timer, stores the current value of time in stopTime.
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
     * @throws IOException
     */
    public GoBackNProtocol(IPHost host, double lossProbability) throws IOException {
        this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
        this.lossProbability = lossProbability;
    }

    /**
     * Sender GoBackNProtocol constructor.
     * @param host of the protocol.
     * @param packetList to be treated by the protocol.
     */
    public GoBackNProtocol(IPHost host, TCPSegment[] packetList, double lossProbabilit) {
        this.host= host;
        this.packetList = packetList;
        host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
        this.lossProbability = lossProbability;
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
    	// If is an ACK segment, sender side of Go-Back-N.
        if(segment.isAck()){
            // Is used to detect triple ack.
            System.out.println("- RECIEVED ACK n??" + segment.sequenceNumber);
            if (repeatedAckNumber >= 0){
                if (sequenceNumber == repeatedAckNumber){
                    tripleAck += 1;
                }
                else {
                    tripleAck = 0;
                }
            }
            repeatedAckNumber = segment.sequenceNumber;
            if (tripleAck == 3) {
                // Triple ack detected, we resend the whole window.
                timeout(datagram.src);
                tripleAck = 0;
                // Multiplicative decrease.
                size = size/2;
            }
            // Additive increase.
            oldSize = size;
            if (size <= sstresh){
                size += MSS;
            }
            else {
                size = size + MSS/size;
            }
            newSize = size;
            exportData += host.getNetwork().getScheduler().getCurrentTime() + "," + size + "\n"; 
            
            double offset = newSize - oldSize;

            // Check if corrupt or not
            sendBase = segment.sequenceNumber + 1;
            if (sendBase == sequenceNumber) {
                System.out.println("-------------------------STOP");
                timer.stop();
                FileWriter fw = new FileWriter("WindowsSize.csv");
                fw.write(exportData);
                fw.close();
            } else {
                changeRTO();
                timer = new MyTimer(host.getNetwork().getScheduler(), RTO, datagram.src);
                timer.start();

            }
            for (int i = 0; i <= offset; i++) {
                sendData(packetList[sequenceNumber].data[0], datagram.src);
            }
        }
        // Else is not an ACK segment, receiver side of Go-Back-N.
        else{
            System.out.println("- RECIEVED PKT n" + segment.sequenceNumber);
            // Check if corrupt or not
            if (segment.sequenceNumber == sequenceNumber) {
                // find how do we deliver data to application
                sendAcknowledgment(datagram);
                sequenceNumber += 1; 
            } else {
                if (ack != null) {
                    sendAcknowledgment(ack);
                }
            }
        }
    }

    /**
     * Timeout event handler. Tries to send the whole window again.
     * @param dst destination of the cause of the timeout.
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     * @see reso.ip.IPLayer Exception origin.
     * @prints timeout message.
     */
    public void timeout(IPAddress dst) throws Exception {
        changeRTO();
        timer = new MyTimer(host.getNetwork().getScheduler(), RTO, dst);
        timer.start();
        if (tripleAck == 3) {
            System.out.println("------ Triple ACK");
        } else {
            System.out.println("------ Timeout");
        }
        for (int i = sendBase; i < sequenceNumber; i++) {
            System.out.println("-------- RESEND pkt n" + i);
            host.getIPLayer().send(IPAddress.ANY, dst, IP_PROTO_GOBACKN, packetList[i]);
        }
        // Congestion control.
        sstresh = size / 2;
        size = 1;
        exportData += host.getNetwork().getScheduler().getCurrentTime() + "," + size + "\n"; 

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
        if (sequenceNumber < sendBase + size && sequenceNumber < packetList.length) {
            int[] segmentData = new int[]{data};
            TCPSegment packet = new TCPSegment(segmentData, sequenceNumber);
            packetList[sequenceNumber] = packet;

            //                              =>Packet loss simulator<=
            double x = rand.nextDouble();
            if (x > lossProbability) {
                // if x > lossProbability we can send the packet. Otherwhise the packet is lost.
                System.out.println("-- SENDING pkt n??" + sequenceNumber);
                host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, packet);
            } else {
                System.out.println("== PACKET LOST => sequenceNumber : " + packet.sequenceNumber);
            }
            if (sendBase == sequenceNumber) {
                if (timer != null) {
                    changeRTO();
                }
                timer = new MyTimer(host.getNetwork().getScheduler(), RTO, destination);
                timer.start();
            }
            sequenceNumber += 1;
        }
    }

    /**
     * ACK sending method of the protocol. Acknowledges messages.
     * @param datagram
     * @throws Exception caught from reso.ip.IPLayer.send() method.
     * @see reso.ip.IPLayer Exception origin.
     */
    private void sendAcknowledgment(Datagram datagram) throws Exception{
        TCPSegment packet = new TCPSegment(((TCPSegment) datagram.getPayload()).sequenceNumber);
        //                              =>ACK loss simulator<=
        double x = rand.nextDouble();
        if (x > lossProbability) {
            // if x > lossProbability we can send the packet. Otherwhise the packet is lost.
            System.out.println("---- ACK pkt n??" + packet.sequenceNumber);
            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, packet);
        }
        ack = datagram;
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
     */
    private void changeRTO(){
        double devRTT = getDevRTT();
        RTO = getSRTT() + 4*devRTT;
    }


}
