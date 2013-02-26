package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferRPC extends NetLoadableConsoleApp implements
		DataXferRPCInterface {

	protected DataXferRPC(String name) {
		super(name);
	}

	@Override
	public byte[] DataXfer(JSONObject header, String hostIP, int port,
			int timeout) throws JSONException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP,
			int port, int timeout, int nTrials) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub

	}

}
