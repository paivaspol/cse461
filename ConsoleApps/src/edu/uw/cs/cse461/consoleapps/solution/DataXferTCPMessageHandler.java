package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandler extends NetLoadableConsoleApp implements DataXferTCPMessageHandlerInterface {

	private static final String TAG = "DataXferTCPMessageHandler";
	private static final int PAYLOAD_SIZE = 1000;
	private static final int SIZE_FIELD_LEN = 4;
	
	protected DataXferTCPMessageHandler(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public byte[] DataXfer(String header, String hostIP, int port, int timeout,
			int xferLength) throws JSONException, IOException {
		Socket socket = null;
		try {
			socket = new Socket(hostIP, port);
			socket.setSoTimeout(timeout);
			TCPMessageHandler messageHandler = new TCPMessageHandler(socket);

			// sends the header
			messageHandler.sendMessage(header);

			JSONObject encoding = new JSONObject();
			encoding.put("transferSize", xferLength);

			// sends the packet using TCPMessageHandler
			messageHandler.sendMessage(encoding);

			ByteBuffer resultBuf = ByteBuffer.allocate(xferLength);
//			int headerAndPayloadSize = PAYLOAD_SIZE + EchoServiceBase.RESPONSE_LEN;
			
			byte[] result = messageHandler.readMessageAsBytes();
			resultBuf.put(result);
			int length = result.length;
			xferLength -= length;
			while (xferLength > 0) {
				result = messageHandler.readMessageAsBytes();
				resultBuf.put(result);
				xferLength -= length;
			}
			return resultBuf.array();
		} finally {
			if (socket != null) {
				socket.close();
			}	
		}
	}

	@Override
	public TransferRateInterval DataXferRate(String header, String hostIP,
			int port, int timeout, int xferLength, int nTrials) {
		// TODO Auto-generated method stub
		return null;
	}

}
