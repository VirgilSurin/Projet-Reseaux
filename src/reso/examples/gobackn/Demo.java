package reso.examples.gobackn;

import java.util.Scanner;

import reso.common.*;
import reso.ethernet.*;
import reso.examples.static_routing.AppSniffer;
import reso.ip.*;
import reso.scheduler.AbstractScheduler;
import reso.scheduler.Scheduler;
import reso.utilities.NetworkBuilder;

public class Demo
{
    public static void main(String [] args) {
		AbstractScheduler scheduler= new Scheduler();
		Network network= new Network(scheduler);

                System.out.println("----====>>>><<<<====----");
                System.out.println("|                      |");
                System.out.println("|  GoBackN  Simulator  |");
                System.out.println("|                      |");
                System.out.println("----====>>>><<<<====----");
                System.out.println("\nby Simon Michel & Virgil Surin\n\n");
                
                Scanner scanner = new Scanner(System.in);
                System.out.println("Choose the number of packet you want to send : ");
                int pktNumber = scanner.nextInt();
                System.out.println("Choose the loss probability (0,00 - 1,00) : ");
                double lossProbability = scanner.nextDouble();
                System.out.println("Choose the link bitrate : ");
                int bitrate = scanner.nextInt();
                System.out.println("Choose the link distance (km) : ");
                int distance = scanner.nextInt();
                
    	try {
    		final EthernetAddress MAC_ADDR1= EthernetAddress.getByAddress(0x00, 0x26, 0xbb, 0x4e, 0xfc, 0x28);
    		final EthernetAddress MAC_ADDR2= EthernetAddress.getByAddress(0x00, 0x26, 0xbb, 0x4e, 0xfc, 0x29);
    		final IPAddress IP_ADDR1= IPAddress.getByAddress(192, 168, 0, 1);
    		final IPAddress IP_ADDR2= IPAddress.getByAddress(192, 168, 0, 2);

    		IPHost host1= NetworkBuilder.createHost(network, "H1", IP_ADDR1, MAC_ADDR1);
    		host1.getIPLayer().addRoute(IP_ADDR2, "eth0");
    		host1.addApplication(new AppSender(host1, IP_ADDR2, pktNumber, lossProbability));

    		IPHost host2= NetworkBuilder.createHost(network,"H2", IP_ADDR2, MAC_ADDR2);
    		host2.getIPLayer().addRoute(IP_ADDR1, "eth0");
    		host2.addApplication(new AppReceiver(host2, lossProbability));

    		EthernetInterface h1_eth0= (EthernetInterface) host1.getInterfaceByName("eth0");
    		EthernetInterface h2_eth0= (EthernetInterface) host2.getInterfaceByName("eth0");
    		
    		// Connect both interfaces with a 5000km long link
    		new Link<EthernetFrame>(h1_eth0, h2_eth0, distance, bitrate);

            ((IPEthernetAdapter) host2.getIPLayer().getInterfaceByName("eth0")).addARPEntry(IP_ADDR1, MAC_ADDR1);
            ((IPEthernetAdapter) host1.getIPLayer().getInterfaceByName("eth0")).addARPEntry(IP_ADDR2, MAC_ADDR2);

    		host1.start();
    		host2.start();
    		
    		scheduler.run();
    	} catch (Exception e) {
    		System.err.println(e.getMessage());
    		e.printStackTrace(System.err);
    	}
    }

}
