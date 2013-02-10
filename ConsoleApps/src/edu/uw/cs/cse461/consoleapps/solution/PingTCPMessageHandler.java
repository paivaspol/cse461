package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;
import java.net.Socket;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;
import edu.uw.cs.cse461.service.EchoServiceBase;

public class PingTCPMessageHandler implements PingTCPMessageHandlerInterface {
	
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
				
				// send header
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
}
