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
	private ServerSocket[] mServerSocket;
	private DatagramSocket[] mDatagramSocket;

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
		mDatagramSocket = new DatagramSocket[NPORTS];  // to keep track each of the datagram socket
		Thread[] dgramThreads = new Thread[NPORTS];  // keep track each of the thread corresponding to a port
		for (int i = 0; i < NPORTS; i++) {
			mDatagramSocket[i] = new DatagramSocket(new InetSocketAddress(serverIP, mBasePort + i));
			mDatagramSocket[i].setSoTimeout(NetBase.theNetBase().config()
					.getAsInt("net.timeout.granularity", 500));
			final DatagramSocket soc = mDatagramSocket[i];  // need to do this because of inner class issue in java
			// for datagram thread to wait for incoming connections
			Thread dgramThread = new Thread() {
				public void run() {
					byte buf[] = new byte[HEADER_LEN];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					// Thread termination in this code is primitive. When shutdown()
					// is called (by the application's main thread, so asynchronously to the threads
					// just mentioned) it closes the sockets. This causes an exception on any thread
					// trying to read from it, which is what provokes thread termination.
					try {
						while (!mAmShutdown) {
							try {
								int bytesLeft = XFERSIZE[soc.getLocalPort() - mBasePort];  // we get the amount of bytes we need to transfer
								soc.receive(packet);
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
								// This loop is for dividing the data into chucks of UDP payload size, 1000
								while (bytesLeft > 0) {
									int size = RESPONSE_OKAY_LEN + UDP_PAYLOAD_SIZE;
									if (bytesLeft < UDP_PAYLOAD_SIZE) {
										// if the bytes left is less than the payload size
										// set the size to the "header + bytesleft"
										size = RESPONSE_OKAY_LEN + bytesLeft;
									}
									byte[] returnPacket = new byte[size];
									System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0,
											returnPacket, 0, HEADER_STR.length());  // put the header into the packet 
									soc.send(new DatagramPacket(returnPacket, returnPacket.length,
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
						if (soc != null) {
							soc.close();
						}
					}
				}
			};
			dgramThread.start();
			dgramThreads[i] = dgramThread;
		}
		
		mServerSocket = new ServerSocket[NPORTS];
		Thread[] tcpThreads = new Thread[NPORTS];
		// TCP sockets
		for (int i = 0; i < NPORTS; i++) {
			mServerSocket[i] = new ServerSocket();
			mServerSocket[i].bind(new InetSocketAddress(serverIP, mBasePort + i));
			mServerSocket[i].setSoTimeout(NetBase.theNetBase().config()
					.getAsInt("net.timeout.granularity", 500));
			Log.i(TAG, "Server socket = " + mServerSocket[i].getLocalSocketAddress());
			final ServerSocket serverSocket = mServerSocket[i];
			Thread tcpThread = new Thread() {

				public void run() {
					byte[] header = new byte[4];
					int socketTimeout = NetBase.theNetBase().config()
							.getAsInt("net.timeout.socket", 5000);
					try {
						while (!isShutdown()) {
							Socket sock = null;
							try {
								int xferSize = XFERSIZE[serverSocket.getLocalPort() - mBasePort];
								// accept() blocks until a client connects. When it
								// does, a new socket is created that communicates only
								// with that client. That socket is returned.
								sock = serverSocket.accept();
								// We're going to read from sock, to get the message
								// to echo, but we can't risk a client mistake
								// blocking us forever. So, arrange for the socket
								// to give up if no data arrives for a while.
								sock.setSoTimeout(socketTimeout);
								InputStream is = sock.getInputStream();
								OutputStream os = sock.getOutputStream();
								// Read the header. Either it gets here in one chunk
								// or we ignore it. (That's not exactly the
								// spec, admittedly.)
								int len = is.read(header);
								if (len != HEADER_STR.length())
									throw new Exception("Bad header length: got "
											+ len + " but wanted "
											+ HEADER_STR.length());
								String headerStr = new String(header);
								if (!headerStr.equalsIgnoreCase(HEADER_STR))
									throw new Exception("Bad header: got '"
											+ headerStr + "' but wanted '"
											+ HEADER_STR + "'");
								os.write(RESPONSE_OKAY_STR.getBytes());

								// Keep on sending the packet until the transfer size is 0.
								// This is to prevent memory outage in the client side.
								while (xferSize > 0) {
									int size = UDP_PAYLOAD_SIZE;
									if (xferSize < UDP_PAYLOAD_SIZE) {
										size = xferSize;
									}
									byte[] returnPacket = new byte[size];
									os.write(returnPacket, 0, returnPacket.length);
								}

							} catch (SocketTimeoutException e) {
								// normal behavior, but we're done with the client
								// we were talking with
							} catch (Exception e) {
								Log.i(TAG, "TCP thread caught "
										+ e.getClass().getName() + " exception: "
										+ e.getMessage());
							} finally {
								if (sock != null)
									try {
										sock.close();
										sock = null;
									} catch (Exception e) {
									}
							}
						}
					} catch (Exception e) {
						Log.w(TAG, "TCP server thread exiting due to exception: "
								+ e.getMessage());
					} finally {
						if (mServerSocket != null)
							try {
								serverSocket.close();
							} catch (Exception e) {
								
							}
					}
				}
			};
			tcpThread.start();
			tcpThreads[i] = tcpThread;
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
