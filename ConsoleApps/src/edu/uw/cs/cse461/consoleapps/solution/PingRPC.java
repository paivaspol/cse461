package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingRPCInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingRPC extends NetLoadableConsoleApp implements PingRPCInterface {
	
	private static final String TAG="PingRPC";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRPC() {
		super("pingrpc");
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
					JSONObject header = new JSONObject().put(EchoRPCService.HEADER_TAG_KEY, EchoRPCService.HEADER_STR);
					tcpResult = ping(header, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingTCPMessageHandler.run() caught exception: " + e.getMessage());
		}
	}


	@Override
	public ElapsedTimeInterval ping(JSONObject header, String hostIP, int port,
			int timeout, int nTrials) throws Exception {
		Socket tcpSocket = null;
		TCPMessageHandler tcpMsgHandler = null;
		try {
			ElapsedTime.start("PingRPC_Total");
			tcpSocket = new Socket(hostIP, port);
			tcpMsgHandler = new TCPMessageHandler(tcpSocket);
			tcpMsgHandler.setTimeout(timeout);
			tcpMsgHandler.setNoDelay(true);
			// send message
			JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header);
			JSONObject response = RPCCall.invoke(hostIP, port, "echorpc", "echo", args, timeout );
			if ( response == null ) throw new IOException("RPC failed; response is null");
			
			// examine response
			JSONObject rcvdHeader = response.optJSONObject(EchoRPCService.HEADER_KEY);
			if ( rcvdHeader == null || !rcvdHeader.has(EchoRPCService.HEADER_TAG_KEY)||
					!rcvdHeader.getString(EchoRPCService.HEADER_TAG_KEY).equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR))
				throw new IOException("Bad response header: got '" + rcvdHeader.toString() +
						               "' but wanted a JSONOBject with key '" + EchoRPCService.HEADER_TAG_KEY + "' and string value '" +
						               	EchoServiceBase.RESPONSE_OKAY_STR + "'");
			ElapsedTime.stop("PingRPC_Total");
		} catch (IOException e) {
			System.out.println(e);
		} finally {
			tcpMsgHandler.close();
		}
		return ElapsedTime.get("PingRPC_Total");
	}

}
