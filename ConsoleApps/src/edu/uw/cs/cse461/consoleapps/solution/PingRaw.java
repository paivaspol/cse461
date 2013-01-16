package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingRawInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

/**
 * Raw sockets version of ping client.
 * @author zahorjan
 *
 */
public class PingRaw extends NetLoadableConsoleApp implements PingRawInterface {
	private static final String TAG="PingRaw";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRaw() {
		super("pingraw");
	}
	
	/* (non-Javadoc)
	 * @see edu.uw.cs.cse461.ConsoleApps.PingInterface#run()
	 */
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				String targetIP = config.getProperty("net.server.ip");
				if ( targetIP == null ) {
					System.out.println("No net.server.ip entry in config file.");
					System.out.print("Enter the server's ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				int targetUDPPort;
				System.out.print("Enter the server's UDP port, or empty line to skip: ");
				String targetUDPPortStr = console.readLine();
				if ( targetUDPPortStr == null || targetUDPPortStr.trim().isEmpty() ) return;
				targetUDPPort = Integer.parseInt(targetUDPPortStr);

				int targetTCPPort;
				System.out.print("Enter the server's TCP port, or empty line to skip: ");
				String targetTCPPortStr = console.readLine();
				if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
				else targetTCPPort = Integer.parseInt(targetTCPPortStr);

				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("udp port: " + targetUDPPort);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval udpResult = null;
				ElapsedTimeInterval tcpResult = null;

				if ( targetUDPPort != 0  ) {
					ElapsedTime.clear();
					// we rely on knowing the implementation of udpPing here -- we throw
					// away the return value because we'll print the ElaspedTime stats
					udpResult = udpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetUDPPort, socketTimeout, nTrials);
				}

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = tcpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( udpResult != null ) System.out.println("UDP: " + String.format("%.2f msec (%d failures)", udpResult.mean(), udpResult.nAborted()));
				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRaw.run() caught exception: " +e.getMessage());
		}
	}
	

	/**
	 * Pings the host/port named by the arguments the number of times named by the arguments.
	 * Returns the mean ping time of the trials.
	 */
	@Override
	public ElapsedTimeInterval udpPing(byte[] header, String hostIP, int udpPort, int socketTimeout, int nTrials) {
		ElapsedTime.start("PingRaw_UDPTotalDelay");
		ElapsedTime.stop("PingRaw_UDPTotalDelay");
		return ElapsedTime.get("PingRaw_UDPTotalDelay");
	}
	
	@Override
	public ElapsedTimeInterval tcpPing(byte[] header, String hostIP, int tcpPort, int socketTimeout, int nTrials) {
		ElapsedTime.start("PingRaw_TCPTotal");
		ElapsedTime.stop("PingRaw_TCPTotal");
		return ElapsedTime.get("PingRaw_TCPTotal");
	}
}
