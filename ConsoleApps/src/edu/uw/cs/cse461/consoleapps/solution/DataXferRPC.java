package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.DataXferRPCService;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferRPC extends NetLoadableConsoleApp implements
		DataXferRPCInterface {

	private static final String TAG = "DataXferTCPMessageHandler";

	public DataXferRPC() {
		super("dataxferrpc");
	}

	@Override
	public byte[] DataXfer(JSONObject header, String hostIP, int port,
			int timeout) throws JSONException, IOException {
		// Do a RPC call to the server
		JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc",
				"dataxfer", header);
		if (response == null)
			throw new IOException("RPC failed; response is null");
		JSONObject rcvdHeader = response.getJSONObject(DataXferRPCService.HEADER_KEY);
		if ( rcvdHeader == null || !rcvdHeader.has(DataXferRPCService.HEADER_TAG_KEY)||
				!rcvdHeader.getString(DataXferRPCService.HEADER_TAG_KEY).equalsIgnoreCase(DataXferRPCService.RESPONSE_OKAY_STR))
			throw new IOException("Bad response header: got '" + rcvdHeader.toString() +
					               "' but wanted a JSONOBject with key '" + DataXferRPCService.HEADER_TAG_KEY + "' and string value '" +
					               DataXferRPCService.RESPONSE_OKAY_STR + "'");
		
		if (!response.has(DataXferRPCService.DATA_KEY))
			throw new IOException("Data key not found");
		String resultBytes = (String) response.get(DataXferRPCService.DATA_KEY);
		return Base64.decode(resultBytes);
	}

	@Override
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP,
			int port, int timeout, int nTrials) {
		int xferLength = 0;
		JSONObject args = null;
		try {
			args = new JSONObject().put(DataXferRPCService.HEADER_KEY, header);
			xferLength = header.getInt(DataXferRPCService.DATA_LENGTH_KEY);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		for (int trial = 0; trial < nTrials; trial++) {
			try {
				TransferRate.start("tcp");
				DataXfer(args, hostIP, port, timeout);
				TransferRate.stop("tcp", xferLength);
			} catch (Exception e) {
				TransferRate.abort("tcp", xferLength);
				System.out.println("TCP trial failed: " + e.getMessage());
			}
		}
		return TransferRate.get("tcp");
	}

	@Override
	public void run() throws Exception {
		try {

			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(
					System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("net.server.ip");
			if (server == null) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if (server == null)
					return;
				if (server.equals("exit"))
					return;
			}
			System.out.println("Server location: " + server);
			System.out.print("Enter port number, or empty line to exit: ");
			String portStr = console.readLine();
			if (portStr == null || portStr.trim().isEmpty())
				return;
			int basePort = Integer.parseInt(portStr);

			int socketTimeout = config.getAsInt("net.timeout.socket", -1);
			if (socketTimeout < 0) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);

			}

			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);

			while (true) {
				System.out.print("Enter amount of data to transfer (-1 to exit): ");
				int xferLength = Integer.parseInt(console.readLine());
				if (xferLength == -1) {
					break;
				}
				TransferRate.clear();

				System.out.println("Transferring "+ xferLength + " bytes from " + server + ":" + portStr);

				// -----------------------------------------------------
				// TCP transfer
				// -----------------------------------------------------
				JSONObject header = new JSONObject().put(DataXferRPCService.HEADER_TAG_KEY, DataXferRPCService.HEADER_STR)
													.put(DataXferRPCService.DATA_LENGTH_KEY, xferLength);
				TransferRateInterval tcpStats = DataXferRate(header, server,
						basePort, socketTimeout, nTrials);

				System.out.println("\nTCP: xfer rate = "
						+ String.format("%9.0f", tcpStats.mean() * 1000.0)
						+ " bytes/sec.");
				System.out.println("TCP: failure rate = "
						+ String.format("%5.1f", tcpStats.failureRate()) + " ["
						+ tcpStats.nAborted() + "/" + tcpStats.nTrials() + "]");

			}

		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}

	}

}
