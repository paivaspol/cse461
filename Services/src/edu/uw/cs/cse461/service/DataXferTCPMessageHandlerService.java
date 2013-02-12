package edu.uw.cs.cse461.service;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase {
	
	private static final String TAG="DataXferTCPMessageHandlerService";
	private ServerSocket[] mServerSocket;

	public DataXferTCPMessageHandlerService() throws Exception {
		super("dataxfertcpmessagehandler");
		String serverIP = IPFinder.localIP();
		int tcpPort = 0;
		mServerSocket = new ServerSocket[DataXferRawService.NPORTS];
		for (int i = 0; i < DataXferRawService.NPORTS; i++) {
			mServerSocket[i].bind(new InetSocketAddress(serverIP, tcpPort));
			mServerSocket[i].setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
			Log.i(TAG,  "Server socket = " + mServerSocket[i].getLocalSocketAddress());
	
			final ServerSocket serverSocket = mServerSocket[i];
			
			Thread tcpThread = new Thread() {
				public void run() {
					try {
						while ( !mAmShutdown ) {
							Socket sock = null;
							try {
								sock = serverSocket.accept();  // if this fails, we want out of the while loop...
								// should really spawn a thread here, but the code is already complicated enough that we don't bother
								TCPMessageHandler tcpMessageHandlerSocket = null;
								try {
									// this loop exits when readMessageAsString() throws an IOException indicating EOF, or 
									// because it has timed out on the read
									while ( true ) {
										tcpMessageHandlerSocket = new TCPMessageHandler(sock);
										tcpMessageHandlerSocket.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
										tcpMessageHandlerSocket.setNoDelay(true);
										
										String header = tcpMessageHandlerSocket.readMessageAsString();
										if ( ! header.equalsIgnoreCase(EchoServiceBase.HEADER_STR))
											throw new Exception("Bad header: '" + header + "'");
										// Get the transfer size
										JSONObject transferSizeObj = tcpMessageHandlerSocket.readMessageAsJSONObject();
										int transferSize = transferSizeObj.getInt("transferSize");
										
										// now respond
										tcpMessageHandlerSocket.sendMessage(EchoServiceBase.RESPONSE_OKAY_STR);
										tcpMessageHandlerSocket.sendMessage(new byte[transferSize]);
									}
								} catch (SocketTimeoutException e) {
									Log.e(TAG, "Timed out waiting for data on tcp connection");
								} catch (EOFException e) {
									// normal termination of loop
									Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
								} catch (Exception e) {
									Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
								} finally {
									if ( tcpMessageHandlerSocket != null ) try { tcpMessageHandlerSocket.close(); } catch (Exception e) {}
								}
							} catch (SocketTimeoutException e) {
								// this is normal.  Just loop back and see if we're terminating.
							}
						}
					} catch (Exception e) {
						Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
					} finally {
						if ( serverSocket != null )  try { serverSocket.close(); } catch (Exception e) {}
					}
				}
			};
			tcpThread.start();
		}
	}

	@Override
	public String dumpState() {
		StringBuilder sb = new StringBuilder(super.dumpState());
		sb.append("\nListening on: ");
		if ( mServerSocket != null ) sb.append(mServerSocket.toString());
		sb.append("\n");
		return sb.toString();
	}
}
