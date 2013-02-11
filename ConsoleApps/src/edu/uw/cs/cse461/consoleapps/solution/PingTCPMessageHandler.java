package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

/**
 * Sends a length 0 message to EchoTCPMessageHandlerService and measure the elapsed time to receive a reply.
 * 
 * @author leelee
 *
 */

public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingTCPMessageHandlerInterface {
	private static final String TAG="PingTCPMessageHandler";
	
	public PingTCPMessageHandler() {
		super("pingtcpmessagehandler");
	}

	@Override
	public ElapsedTimeInterval ping(String header, String hostIP, int port, int timeout, int nTrials) throws Exception {
		Socket tcpSocket = null;
		TCPMessageHandler tcpMsgHandler = null;
		try {
			for (int i = 0; i < nTrials; i++) {
				ElapsedTime.start("PingTCPMessageHandler_Total");
				tcpSocket = new Socket(hostIP, port);
				tcpMsgHandler = new TCPMessageHandler(tcpSocket);
				tcpMsgHandler.setTimeout(timeout);
				tcpMsgHandler.setMaxReadLength(EchoServiceBase.RESPONSE_LEN);

				tcpMsgHandler.sendMessage(header);

				// read the header.  Either the entire header arrives in one chunk, or we
				// (mistakenly) reject it.
				String response = tcpMsgHandler.readMessageAsString();
				int len = response.length();
				if ( len != EchoServiceBase.RESPONSE_LEN )
					throw new Exception("Bad response header length: got " + len + " but expected " + header.length());
				String headerStr = response.substring(0, 4);  // TCP packet might return something else after the header too
				if ( !headerStr.equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR))
					throw new Exception("Bad response header: got '" + headerStr + "' but expected '" + EchoServiceBase.RESPONSE_OKAY_STR + "'");
				ElapsedTime.stop("PingTCPMessageHandler_Total");
			}
		} catch (Exception e) {
			System.out.println(e);
		} finally {
			tcpMsgHandler.close();
		}
		return ElapsedTime.get("PingTCPMessageHandler_Total");
	}

	@Override
	public void run() throws Exception {
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
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);

				ElapsedTimeInterval tcpResult = null;

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = ping(EchoServiceBase.HEADER_STR, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingTCPMessageHandler.run() caught exception: " + e.getMessage());
		}
	}
}
