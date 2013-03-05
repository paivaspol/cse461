package edu.uw.cs.cse461.service;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.Log;

public class DataXferRPCService extends NetLoadableService {

	public static final String HEADER_STR = "xfer";
	public static final byte[] HEADER_BYTES = HEADER_STR.getBytes();
	public static final int HEADER_LEN = HEADER_BYTES.length;

	public static final String RESPONSE_OKAY_STR = "okay";
	public static final byte[] RESPONSE_OKAY_BYTES = RESPONSE_OKAY_STR.getBytes();
	public static final int RESPONSE_OKAY_LEN = RESPONSE_OKAY_BYTES.length;

	// Keys for JSON objects
	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String DATA_LENGTH_KEY = "xferLength";
	public static final String DATA_KEY = "data";

	private RPCCallableMethod dataxfer;
	private int maxLength;

	protected DataXferRPCService(String name) throws Exception {
		super("dataxferrpc");
        Log.e("MYTAG", "test");

		// Set up the method descriptor variable to refer to this->_dataxfer()
		dataxfer = new RPCCallableMethod(this, "_dataxfer");
		// Register the method with the RPC service as externally invocable method "dataxfer"
		((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(name, "dataxfer", dataxfer);
		maxLength = NetBase.theNetBase().config().getAsInt("dataxferrpc.maxlength", 14000000);
	}

	/**
	 * This method is callable by RPC (because of the actions taken by the constructor).
	 * <p>
	 * All RPC-callable methods take a JSONObject as their single parameter, and return
	 * a JSONObject.  (The return value can be null.)  This particular method simply
	 * echos its arguments back to the caller.
	 * @param args
	 * @return
	 * @throws JSONException
	 */
	public JSONObject _dataxfer(JSONObject args) throws Exception {
        System.out.println("test2");
        Log.e("MYTAG", "test2");
		JSONObject header = args.getJSONObject(DataXferRPCService.HEADER_KEY);
		if ( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) )
			throw new Exception("Missing or incorrect header value: '" + header + "'");

		JSONObject object = new JSONObject();
		header.put(HEADER_TAG_KEY, RESPONSE_OKAY_STR);

		object.put(DataXferRPCService.HEADER_KEY, header);
		object.put(DataXferRPCService.DATA_KEY, Base64.encodeBytes(new byte[header.getInt(DataXferRPCService.DATA_LENGTH_KEY)]));
		return object;
	}

	@Override
	public String dumpState() {
		return "dataxferrpc is up";
	}

}
