package edu.uw.cs.cse461.service;

import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;

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
	
	protected DataXferRPCService(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String dumpState() {
		// TODO Auto-generated method stub
		return null;
	}

}
