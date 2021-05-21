package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPHost;
import reso.ip.IPLayer;

import java.io.IOException;

/**
 * Receiver application of the Go-Back-N pipelining protocol.
 * Receives messages from AppSender.
 */
public class AppReceiver
    extends AbstractApplication
{
    /**
     * The IP layer necessary to the workings of the protocol, which belongs to the transport layer.
     */
    private final IPLayer ip;

    /**
     * Probability that has a packet to be lost.
     */
    private double lossProbability;

    /**
     * Receiver application constructor.
     * @param host of the protocol.
     * @param Probability that has a packet to be lost.    
     */
    public AppReceiver(IPHost host, double lossProbability) {
        super(host, "receiver");
        this.ip= host.getIPLayer();
        this.lossProbability = lossProbability;
    }

    /**
     * Starts the application.
     * @see reso.examples.gobackn.GoBackNProtocol GoBackNProtocol
     */
    public void start() throws IOException {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host, lossProbability);
    }

    /**
     * Stops the application. #TODO might be useless. If so remove.
     */
    public void stop() {}
	
}
