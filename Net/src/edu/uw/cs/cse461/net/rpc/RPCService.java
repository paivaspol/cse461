package edu.uw.cs.cse461.net.rpc;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;


/**
 * Implements the side of RPC that receives remote invocation requests.
 * 
 * @author zahorjan
 *
 */
public class RPCService extends NetLoadableService implements Runnable, RPCServiceInterface {
	private static final String TAG="RPCService";
	private int rpcPort;
	private ServerSocket serverSocket;
	private HashMap<String, HashMap<String, RPCCallableMethod>> callableMethodStorage;
	private enum SocketState {
    	FRESH, PERSISTENT, WAITING, COMPLETED
	}	
	private List<TCPMessageHandler> socketList = new ArrayList<TCPMessageHandler>();
	private List<SocketState> socketStateList = new ArrayList<SocketState>();
	private ConfigManager config = NetBase.theNetBase().config();
	private String host = config.getProperty("net.host.name", "");
	private int id;
	
	/**
	 * Constructor.  Creates the Java ServerSocket and binds it to a port.
	 * If the config file specifies an rpc.server.port value, it should be bound to that port.
	 * Otherwise, you should specify port 0, meaning the operating system should choose a currently unused port.
	 * <p>
	 * Once the port is created, a thread needs to be spun up to listen for connections on it.
	 * 
	 * @throws Exception
	 */
	public RPCService() throws Exception {
		super("rpc");
		callableMethodStorage = new HashMap<String, HashMap<String, RPCCallableMethod>>();
		rpcPort = config.getAsInt("rpc.server.port", 0);
		String serverIP = IPFinder.localIP();
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(serverIP, rpcPort));
		serverSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
		id = 0;
		Thread thread = new Thread() {
			public void run() {
				run();
			}
		}; 
		thread.start();
	}
	
	/**
	 * Executed by an RPCService-created thread.  Sits in loop waiting for
	 * connections, then creates an RPCCalleeSocket to handle each one.
	 */
	@Override
	public void run() {
		try {
			while(!mAmShutdown) {
				while(true){
					try {
						// Add the TCPMessageHandler to the socketList. and also the respective	
						socketList.add(new TCPMessageHandler(serverSocket.accept()));
						socketStateList.add(SocketState.FRESH);
					} catch(SocketTimeoutException e) {
						// This is normal. Break to continue with receiving messages.
						break;	
					}	
				}
		
				// Process connections
				// Iterate through the socket list to remove closed connection socket.
				for (int i = 0; i < socketList.size(); i++) {
					try {
						TCPMessageHandler tcpSocket = socketList.get(i);
						while (true) {
							// Read message as JSONObject.
							JSONObject message = tcpSocket.readMessageAsJSONObject();
							String type = message.getString("type");
							int clientId = message.getInt("id");
							
							if (type.equals("control")) {
								// Format normal response message that has the id field, host fiels, callid field and also
								// type field.
								JSONObject responseMessage = new JSONObject();
								responseMessage.put("id", id);
								id++;
								responseMessage.put("host", host);
								responseMessage.put("callid", clientId);
								responseMessage.put("type", "OK");
								
								// Check if the caller wants persistent connection
								if (!message.isNull("option") &&
										!message.getJSONObject("option").isNull("connection") &&
										message.getJSONObject("option").getString("connection").equals("keep-alive")) {
									// Set the state to persistent
									socketStateList.set(i, SocketState.PERSISTENT);
									// Add extra key-value in the response message that indicates that we agree with
									// setting up persistent connection
									JSONObject connectionJsonObject = new JSONObject();
									connectionJsonObject.put("connection", "keep-alive");
									responseMessage.put("value", connectionJsonObject);
								}
								tcpSocket.sendMessage(responseMessage);
								
							} else if (type.equals("invoke")) {
								// Get the method that is being invoked
								String app = message.getString("app");
								String method = message.getString("method");
							
								// Format response message
								JSONObject responseMessage = new JSONObject();
								responseMessage.put("id", id);
								id++;
								responseMessage.put("host", host);
								responseMessage.put("callid", clientId);
								
								// Invoke the method if it exists
								HashMap<String, RPCCallableMethod> map = callableMethodStorage.get(app);
								JSONObject value = null ;
								if (map != null) {
									RPCCallableMethod rpcCallableMethod = map.get(method);
									if (rpcCallableMethod != null) {
										value = rpcCallableMethod.handleCall(message.getJSONObject("args"));
									}
									responseMessage.put("value", value);
									responseMessage.put("type", "OK");
									
								} else {
									// No such method, send error message
									responseMessage.put("message", "some error message");
									responseMessage.put("type", "ERROR");
									responseMessage.put("callargs", message);
								}
								
								tcpSocket.sendMessage(responseMessage);
								
								// Send response, change the state to complete iff the state is not persistent.
								if (socketStateList.get(i) != SocketState.PERSISTENT) {
									socketStateList.set(i, SocketState.COMPLETED);
								}
								
							} else {
								// TODO(leelee): Not sure if this really needed.
								throw new Exception("Illegal type message");
							}
								
							// TODO(leelee): can there be a case where caller actually want persistent connection
							// at the beginning but changed it to not want persistent connection after that
						}
					} catch (SocketTimeoutException e) {
						Log.e(TAG, "Timed out waiting for data on tcp connection");
					} catch (EOFException e) {
						// normal termination of loop
						Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
					} catch (Exception e) {
						Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
						socketStateList.set(i, SocketState.COMPLETED);
					} finally {
						if ( socketStateList.get(i) == SocketState.COMPLETED) { 
							try {
								socketList.get(i).close(); 
							} catch (Exception e) {
								// Do nothing.
							}
							socketList.remove(i);
							socketStateList.remove(i);
							i--;
						}
					}
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
		} finally {
			if ( serverSocket != null )  {
				try {
					serverSocket.close();
				} catch (Exception e) {
					// Do nothing.
				}
			}
		}
	}
	
	
	/**
	 * Services and applications with RPC callable methods register them with the RPC service using this routine.
	 * Those methods are then invoked as callbacks when an remote RPC request for them arrives.
	 * @param serviceName  The name of the service.
	 * @param methodName  The external, well-known name of the service's method to call
	 * @param method The descriptor allowing invocation of the Java method implementing the call
	 * @throws Exception
	 */
	@Override
	public synchronized void registerHandler(String serviceName, String methodName, RPCCallableMethod method) throws Exception {
		// Put the serviceName, methodName and RPCCallableMethod into the hashmap of hashmap iff the service
		// name is not a key inside the hashmap yet.
		HashMap<String, RPCCallableMethod> methodNameToRPCCallableMethodMap = callableMethodStorage.get(serviceName);
		if (methodNameToRPCCallableMethodMap == null) {
			methodNameToRPCCallableMethodMap = new HashMap<String, RPCCallableMethod>();
		}
		methodNameToRPCCallableMethodMap.put(methodName, method);
		callableMethodStorage.put(serviceName, methodNameToRPCCallableMethodMap);
		
	}
	
	/**
	 * Some of the testing code needs to retrieve the current registration for a particular service and method,
	 * so this interface is required.  You probably won't find a use for it in your code, though.
	 * 
	 * @param serviceName  The service name
	 * @param methodName The method name
	 * @return The existing registration for that method of that service, or null if no registration exists.
	 */
	public RPCCallableMethod getRegistrationFor( String serviceName, String methodName) {
		// Look up in the hashmap of hashmap that we have.
		HashMap<String, RPCCallableMethod> methodNameToRPCCallableMethodMap = callableMethodStorage.get(serviceName);
		return methodNameToRPCCallableMethodMap.get(methodName);
	}
	
	/**
	 * Returns the port to which the RPC ServerSocket is bound.
	 * @return The RPC service's port number on this node
	 */
	@Override
	public int localPort() {
		return rpcPort;
	}
	
	@Override
	public String dumpState() {
		// TODO(leelee): I guess I can only work on this when we have the provided solution jar??
		return "";
	}
}