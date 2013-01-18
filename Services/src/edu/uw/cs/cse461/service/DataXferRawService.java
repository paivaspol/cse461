package edu.uw.cs.cse461.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
	public static final int UDP_PAYLOAD_SIZE = 1000;
	private int mBasePort;
//	private List<ServerSocket> mServerSocketsList;
//	private List<DatagramSocket> mDatagramSocketsList;
	private ServerSocket mServerSocket;
	private DatagramSocket mDatagramSocket;

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
//		mServerSockets = new ServerSocket[NPORTS];
//		for (int i = 0; i < NPORTS; i++) {
//			
//		}
		
//		mDatagramSocket = new DatagramSocket[NPORTS];
		for (int i = 0; i < NPORTS; i++) {
	
		}
		Thread[] dgramThreads = new Thread[NPORTS];
		Thread[] tcpPacketThreads = new Thread[NPORTS];
		for (int i = 0; i < NPORTS; i++) {
//			mServerSocket = new ServerSocket();
//			mServerSocket.bind(new InetSocketAddress(serverIP, mBasePort + i));
//			mServerSocket.setSoTimeout(NetBase.theNetBase().config()
//					.getAsInt("net.timeout.granularity", 500));
//			Log.i(TAG, "Server socket = " + mServerSocket.getLocalSocketAddress());
			mDatagramSocket = new DatagramSocket(new InetSocketAddress(serverIP, mBasePort + i));
			mDatagramSocket.setSoTimeout(NetBase.theNetBase().config()
					.getAsInt("net.timeout.granularity", 500));
			// for datagram thread to wait for incoming connections
			Thread dgramThread = new Thread() {
				public void run() {
					byte buf[] = new byte[HEADER_LEN];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					// Thread termination in this code is primitive. When shutdown()
					// is called (by the
					// application's main thread, so asynchronously to the threads
					// just mentioned) it
					// closes the sockets. This causes an exception on any thread
					// trying to read from
					// it, which is what provokes thread termination.
					try {
						while (!mAmShutdown) {
							try {
								int bytesLeft = XFERSIZE[mServerSocket.getLocalPort() - mBasePort];
								mDatagramSocket.receive(packet);
								if (packet.getLength() < HEADER_STR.length())
									throw new Exception("Bad header: length = "
											+ packet.getLength());
								String headerStr = new String(buf, 0,
										HEADER_STR.length());
								if (!headerStr.equalsIgnoreCase(HEADER_STR))
									throw new Exception("Bad header: got '"
											+ headerStr + "', wanted '"
											+ HEADER_STR + "'");
								// after this point we are sure that the request is correct
								// create a new buffer to send data back to the client
								while (bytesLeft > 0) {
									int size = RESPONSE_OKAY_LEN + UDP_PAYLOAD_SIZE;
									if (bytesLeft < UDP_PAYLOAD_SIZE) {
										size = RESPONSE_OKAY_LEN + bytesLeft;
									}
									byte[] returnPacket = new byte[size];
									System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0,
											returnPacket, 0, HEADER_STR.length());  // put the header into the packet 
									mDatagramSocket.send(new DatagramPacket(returnPacket, returnPacket.length,
											packet.getAddress(), packet.getPort()));
									bytesLeft -= UDP_PAYLOAD_SIZE;
								}
							} catch (SocketTimeoutException e) {
								// socket timeout is normal
							} catch (Exception e) {
								Log.w(TAG, "Dgram reading thread caught "
										+ e.getClass().getName() + " exception: "
										+ e.getMessage());
							}
						}
					} finally {
						if (mDatagramSocket != null) {
							mDatagramSocket.close();
							mDatagramSocket = null;
						}
					}
				}
			};
			dgramThread.start();
			dgramThreads[i] = dgramThread;
			// for accepting tcp packets
//			for (int i = 0; i < NPORTS; i++) {
//				mDatagramSocket = new DatagramSocket(new InetSocketAddress(serverIP, mBasePort + i));
//				mDatagramSocket.setSoTimeout(NetBase.theNetBase().config()
//						.getAsInt("net.timeout.granularity", 500));
//		
//				Log.i(TAG,
//						"Datagram socket = " + mDatagramSocket.getLocalSocketAddress());
//		
//				Thread tcpPacketThread = new Thread() {
//					public void run() {
//		
//					}
//				};
//			}
//			tcpPacketThread.start();
//			tcpPacketThreads[i] = tcpPacketThread;
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
