package edu.uw.cs.cse461.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;

/**
 * Transfers reasonably large amounts of data to client over raw TCP and UDP
 * sockets. In both cases, the server simply sends as fast as it can. The server
 * does not implement any correctness mechanisms, so, when using UDP, clients
 * may not receive all the data sent.
 * <p>
 * Four consecutive ports are used to send fixed amounts of data of various
 * sizes.
 * <p>
 * 
 * @author zahorjan
 * 
 */
public class DataXferRawService extends DataXferServiceBase implements
		NetLoadableServiceInterface {
	private static final String TAG = "DataXferRawService";

	public static final int NPORTS = 4;
	public static final int[] XFERSIZE = { 1000, 10000, 100000, 1000000 };

	private int mBasePort;
	private ServerSocket[] mServerSockets;
	private DatagramSocket[] mDatagramSockets;

	public DataXferRawService() throws Exception {
		super("dataxferraw");

		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.server.baseport", 0);
		if (mBasePort == 0)
			throw new RuntimeException(
					"dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");

		// Sanity check -- code below relies on this property
		if (HEADER_STR.length() != RESPONSE_OKAY_STR.length())
			throw new Exception(
					"Header and response strings must be same length: '"
							+ HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");

		// The echo raw service's IP address is the ip the entire app is running
		// under
		String serverIP = IPFinder.localIP();
		if (serverIP == null)
			throw new Exception(
					"IPFinder isn't providing the local IP address.  Can't run.");

		// There is (purposefully) no config file field to define the echo raw
		// service's ports.
		// Instead, ephemeral ports are used. (You can run the
		// dumpservericestate application
		// to see ports are actually allocated.)
		// allocate four ports
		mServerSockets = new ServerSocket[NPORTS];
		for (int i = 0; i < NPORTS; i++) {
			mServerSockets[i] = new ServerSocket();
			mServerSockets[i].bind(new InetSocketAddress(serverIP, mBasePort + i));
			mServerSockets[i].setSoTimeout(NetBase.theNetBase().config()
					.getAsInt("net.timeout.granularity", 500));
			Log.i(TAG, "Server socket = " + mServerSockets[i].getLocalSocketAddress());
		}
		
		mDatagramSockets = new DatagramSocket[NPORTS];
		for (int i = 0; i < NPORTS; i++) {
			mDatagramSockets[i] = new DatagramSocket(new InetSocketAddress(serverIP, mBasePort + i));
			mDatagramSockets[i].setSoTimeout(NetBase.theNetBase().config()
					.getAsInt("net.timeout.granularity", 500));
	
			Log.i(TAG,
					"Datagram socket = " + mDatagramSockets[i].getLocalSocketAddress());
		}
		Thread[] dgramThreads = new Thread[NPORTS];
		Thread[] tcpPacketThreads = new Thread[NPORTS];
		for (int i = 0; i < NPORTS; i++) {
			// for datagram thread to wait for incoming connections
			Thread dgramThread = new Thread() {
				public void run() {
					
				}
			};
			dgramThread.start();
			dgramThreads[i] = dgramThread;
			// for accepting tcp packets
			Thread tcpPacketThread = new Thread() {
				public void run() {
	
				}
			};
			tcpPacketThread.start();
			tcpPacketThreads[i] = tcpPacketThread;
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		Log.d(TAG, "Shutting down");
	}

	/**
	 * Returns string summarizing the status of this server. The string is
	 * printed by the dumpservicestate console application, and is also
	 * available by executing dumpservicestate through the web interface.
	 */
	@Override
	public String dumpState() {
		return "";

	}
}
