package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.Log;

/**
 * Class implementing the caller side of RPC -- the RPCCall.invoke() method.
 * The invoke() method itself is static, for the convenience of the callers,
 * but this class is a normal, loadable, service.
 * <p>
 * <p>
 * This class is responsible for implementing persistent connections. 
 * (What you might think of as the actual remote call code is in RCPCallerSocket.java.)
 * Implementing persistence requires keeping a cache that must be cleaned periodically.
 * We do that using a cleaner thread.
 * 
 * @author zahorjan
 *
 */
public class RPCCall extends NetLoadableService {
	
	private static final String TAG="RPCCall";
	
	private static final String ID_KEY = "id";
	private static final String HOST_KEY = "host";
	private static final String ACTION_KEY = "action";
	private static final String TYPE_KEY = "type";
	private static final String OPTIONS_KEY = "options";
	private static final String CONNECTION_KEY = "connection";
	private static final String MESSAGE_KEY_SHORT = "msg";
	private static final String MESSAGE_KEY_LONG = "message";
	private static final String APP_KEY = "app";
	private static final String METHOD_KEY = "method";
	private static final String ARG_KEY = "args";
	private static final String VALUE_KEY = "value";
	
	private static final int CLEANUP_TIME = 300;	// default idle time for cleaning up
	
	private static HashMap<String, Socket> cache = new HashMap<String, Socket>();
	private static HashMap<String, Timer> cleaner = new HashMap<String, Timer>();
	
	private static int idCounter = 1;

	//-------------------------------------------------------------------------------------------
	//-------------------------------------------------------------------------------------------
	// The static versions of invoke() are just a convenience for caller's -- it
	// makes sure the RPCCall service is actually running, and then invokes the
	// the code that actually implements invoke.
	
	/**
	 * Invokes method() on serviceName located on remote host ip:port.
	 * @param ip Remote host's ip address
	 * @param port RPC service port on remote host
	 * @param serviceName Name of service to be invoked
	 * @param method Name of method of the service to invoke
	 * @param userRequest Arguments to call
	 * @param socketTimeout Maximum time to wait for a response, in msec.
	 * @return Returns whatever the remote method returns.
	 * @throws JSONException
	 * @throws IOException
	 */
	public static JSONObject invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest,   // arguments to send to remote method,
			int socketTimeout         // timeout for this call, in msec.
			) throws JSONException, IOException {
		RPCCall rpcCallObj =  (RPCCall)NetBase.theNetBase().getService( "rpccall" );
		if ( rpcCallObj == null ) throw new IOException("RPCCall.invoke() called but the RPCCall service isn't loaded");
		return rpcCallObj._invoke(ip, port, serviceName, method, userRequest, socketTimeout, true);
	}
	
	/**
	 * A convenience implementation of invoke() that doesn't require caller to set a timeout.
	 * The timeout is set to the net.timeout.socket entry from the config file, or 2 seconds if that
	 * doesn't exist.
	 */
	public static JSONObject invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest    // arguments to send to remote method,
			) throws JSONException, IOException {
		int socketTimeout  = NetBase.theNetBase().config().getAsInt("net.timeout.socket", 2000);
		return invoke(ip, port, serviceName, method, userRequest, socketTimeout);
	}

	//-------------------------------------------------------------------------------------------
	//-------------------------------------------------------------------------------------------
	
	/**
	 * The infrastructure requires a public constructor taking no arguments.  Plus, we need a constructor.
	 */
	public RPCCall() {
		super("rpccall");
	}

	/**
	 * This private method performs the actual invocation, including the management of persistent connections.
	 * Note that because we may issue the call twice, we  may (a) cause it to be executed twice at the server(!),
	 * and (b) may end up blocking the caller for around twice the timeout specified in the call. (!)
	 * 
	 * @param ip
	 * @param port
	 * @param serviceName
	 * @param method
	 * @param userRequest
	 * @param socketTimeout Max time to wait for this call
	 * @param tryAgain Set to true if you want to repeat call if a socket error occurs; e.g., persistent socket is no good when you use it
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 */
	private JSONObject _invoke(
			String ip,				  // ip or dns name of remote host
			int port,                 // port that RPC is listening on on the remote host
			String serviceName,       // name of the remote service
			String method,            // name of that service's method to invoke
			JSONObject userRequest,   // arguments to send to remote method
			int socketTimeout,        // max time to wait for reply
			boolean tryAgain          // true if an invocation failure on a persistent connection should cause a re-try of the call, false to give up
			) throws JSONException, IOException {
		// For persistent connection, we will do a mapping from IP,port(String typed) --> Socket
		Socket socket = null;
		JSONObject retval = null;
		final String key = ip + "," + port;
		socket = cache.get(key);
		if (socket == null) {
			socket = new Socket(ip, port);
			socket.setSoTimeout(socketTimeout);
		} else {
			Timer t = cleaner.get(key);
			t.cancel();
		}
		// create the timer and put it in the map
		Timer timer = scheduleCleaner(key);
		cleaner.put(key, timer);
		
		String hostName = socket.getLocalAddress().toString();
		TCPMessageHandler messageHandler = new TCPMessageHandler(socket);
		// First, send the connect message
		connectToHost(hostName, messageHandler);
		// TODO: double check if we need to check for the received callerid and the current id or not
		// Got the success response, we are ready to invoke methods
		JSONObject recvObject = invokeRemoteMethod(ip, serviceName, method, userRequest, hostName,
				messageHandler);
		if (recvObject.has(VALUE_KEY)) {
			retval = recvObject.getJSONObject(VALUE_KEY);
		}
		return retval;
	}

	private Timer scheduleCleaner(final String key) {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Socket sock = cache.get(key);
				try {
					sock.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}, CLEANUP_TIME * 1000);
		return timer;
	}
	
	private JSONObject connectToHost(String ip, TCPMessageHandler messageHandler)
			throws JSONException, IOException {
		JSONObject optionsMessage = new JSONObject().put(CONNECTION_KEY, "keep-alive");  // set it to be persistent
		JSONObject connectMessage = new JSONObject().put(ID_KEY, idCounter)
													.put(HOST_KEY, ip)
													.put(ACTION_KEY, "connect")
													.put(TYPE_KEY, "control")
													.put(OPTIONS_KEY, optionsMessage);
		return sendMessage(ip, messageHandler, connectMessage);
	}
	
	private JSONObject invokeRemoteMethod(String ip, String serviceName,
			String method, JSONObject userRequest, String hostName,
			TCPMessageHandler messageHandler) throws JSONException, IOException {
		JSONObject invokeMessage = new JSONObject().put(ID_KEY, idCounter)
												   .put(APP_KEY, serviceName)
												   .put(HOST_KEY, hostName)
												   .put(METHOD_KEY, method)
												   .put(TYPE_KEY, "invoke")
												   .put(ARG_KEY, userRequest);
		return sendMessage(ip, messageHandler, invokeMessage);
	}

	private JSONObject sendMessage(String ip, TCPMessageHandler messageHandler,
			JSONObject invokeMessage) throws IOException, JSONException {
		System.out.println("RPCCall: sending message: " + invokeMessage);
		JSONObject recvObject = null;
		try {
			messageHandler.sendMessage(invokeMessage);
			idCounter++;
			recvObject = messageHandler.readMessageAsJSONObject();
			if (!didSucceed(recvObject)) {
				// we cannot establish the connection
				String msg = "";
				if (recvObject.has(MESSAGE_KEY_LONG)) {
					msg = recvObject.getString(MESSAGE_KEY_LONG);
				} else {
					msg = recvObject.getString(MESSAGE_KEY_SHORT);
				}
				throw new IOException(msg);
			}
		} catch (SocketTimeoutException e) {
			Socket removed = cache.remove(ip);
			removed.close();
		}
		return recvObject;
	}

	// Returns whether the message received was a success or not
	private boolean didSucceed(JSONObject recvObject) throws JSONException {
		return recvObject.has(TYPE_KEY) && recvObject.getString(TYPE_KEY).equalsIgnoreCase("ok");
	}

	

	@Override
	public void shutdown() {
		for (String host : cache.keySet()) {
			Socket sock = cache.get(host);
			try {
				if (sock != null) {
					sock.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String dumpState() {
		return "Current persistent connections are ..." + cache.values();
	}
}
