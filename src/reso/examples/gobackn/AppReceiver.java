package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPHost;
import reso.ip.IPLayer;

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
	 * Receiver application constructor.
	 * @param host of the protocol.
	 */
	public AppReceiver(IPHost host) {
		super(host, "receiver");
		ip= host.getIPLayer();
    }

	/**
	 * Starts the application.
	 */
	public void start() {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host);
    }

	/**
	 * Stops the application. #TODO
	 */
	public void stop() {}
	
}
