package edu.uw.cs.cse461.net.tcpmessagehandler;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.Log;


/**
 * Sends/receives a message over an established TCP connection.
 * To be a message means the unit of write/read is demarcated in some way.
 * In this implementation, that's done by prefixing the data with a 4-byte
 * length field.
 * <p>
 * Design note: TCPMessageHandler cannot usefully subclass Socket, but rather must
 * wrap an existing Socket, because servers must use ServerSocket.accept(), which
 * returns a Socket that must then be turned into a TCPMessageHandler.
 *  
 * @author zahorjan
 *
 */
public class TCPMessageHandler implements TCPMessageHandlerInterface {
	private static final String TAG="TCPMessageHandler";
	
	private Socket 	sock;
	private int 	maxReadLength;
	
	//--------------------------------------------------------------------------------------
	// helper routines
	//--------------------------------------------------------------------------------------

	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method encodes into that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param i
	 * @return A byte[4] encoding the integer argument.
	 */
	protected static byte[] intToByte(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(i);
		byte buf[] = b.array();
		return buf;
	}
	
	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method decodes from that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param buf
	 * @return 
	 */
	protected static int byteToInt(byte buf[]) {
		// You need to implement this.  It's the inverse of intToByte().
		ByteBuffer b = ByteBuffer.allocate(buf.length);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.put(buf);
		return b.getInt(0);
	}

	/**
	 * Constructor, associating this TCPMessageHandler with a connected socket.
	 * @param sock
	 * @throws IOException
	 */
	public TCPMessageHandler(Socket sock) throws IOException {
		this.sock = sock;
		this.maxReadLength = 2097148;
	}
	
	/**
	 * Closes the underlying socket and renders this TCPMessageHandler useless.
	 */
	public void close() {
		try {
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Set the read timeout on the underlying socket.
	 * @param timeout Time out, in msec.
	 * @return The previous time out.
	 */
	@Override
	public int setTimeout(int timeout) throws SocketException {
		int retval = sock.getSoTimeout();
		sock.setSoTimeout(timeout);
		return retval;
	}
	
	/**
	 * Enable/disable TCPNoDelay on the underlying TCP socket.
	 * @param value The value to set
	 * @return The old value
	 */
	@Override
	public boolean setNoDelay(boolean value) throws SocketException {
		boolean retval = sock.getTcpNoDelay();
		sock.setTcpNoDelay(value);
		return retval;
	}
	
	/**
	 * Sets the maximum allowed size for which decoding of a message will be attempted.
	 * @return The previous setting of the maximum allowed message length.
	 */
	@Override
	public int setMaxReadLength(int maxLen) {
		int retval = getMaxReadLength();
		this.maxReadLength = maxLen;
		return retval;
	}

	/**
	 * Returns the current setting for the maximum read length
	 */
	@Override
	public int getMaxReadLength() {
		return this.maxReadLength;
	}
	
	//--------------------------------------------------------------------------------------
	// send routines
	//--------------------------------------------------------------------------------------
	
	@Override
	public void sendMessage(byte[] buf) throws IOException {
		OutputStream out = sock.getOutputStream();
		byte[] lengthField = TCPMessageHandler.intToByte(buf.length);
		ByteBuffer byteBuffer = ByteBuffer.allocate(lengthField.length + buf.length);
		byteBuffer.put(lengthField, 0, lengthField.length);  // put the size header
		byteBuffer.put(buf, 0, buf.length);  // append the payload
		out.write(byteBuffer.array());
	}
	
	/**
	 * Uses str.getBytes() for conversion.
	 */
	@Override
	public void sendMessage(String str) throws IOException {
		sendMessage(str.getBytes());
	}

	/**
	 * We convert the int to the one the wire format and send as bytes.
	 */
	@Override
	public void sendMessage(int value) throws IOException{
		byte[] sendVal = TCPMessageHandler.intToByte(value);
		sendMessage(sendVal);
	}
	
	/**
	 * Sends JSON string representation of the JSONArray.
	 */
	@Override
	public void sendMessage(JSONArray jsArray) throws IOException {
		sendMessage(jsArray.toString());
	}
	
	/**
	 * Sends JSON string representation of the JSONObject.
	 */
	@Override
	public void sendMessage(JSONObject jsObject) throws IOException {
		sendMessage(jsObject.toString());
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//   All of these invert any encoding done by the corresponding send method.
	//--------------------------------------------------------------------------------------
	
	@Override
	public byte[] readMessageAsBytes() throws IOException {
		InputStream in = sock.getInputStream();
		byte[] lengthArray = new byte[4];
		if (in.read(lengthArray) == -1) {
			throw new EOFException("Unexpected EOF here");
		}
		int length = TCPMessageHandler.byteToInt(lengthArray);
		byte[] retval = new byte[length];
		int leftToRead = length;
		while (leftToRead > 0) {
			int result = in.read(retval, length - leftToRead, leftToRead);
			leftToRead -= result;
			if (result == -1) // EOF detected
				throw new EOFException("Unexpected EOF here");
		}
		return retval;
	}
	
	@Override
	public String readMessageAsString() throws IOException {
		String retval = new String(readMessageAsBytes());
		return retval;
	}

	@Override
	public int readMessageAsInt() throws IOException {
		int retval = TCPMessageHandler.byteToInt(readMessageAsBytes());
		return retval;
	}
	
	@Override
	public JSONArray readMessageAsJSONArray() throws IOException, JSONException {
		JSONArray retval = new JSONArray(readMessageAsString());
		return retval;
	}
	
	@Override
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		String msg = readMessageAsString();
		JSONObject retval = new JSONObject(msg);
		return retval;
	}
}
