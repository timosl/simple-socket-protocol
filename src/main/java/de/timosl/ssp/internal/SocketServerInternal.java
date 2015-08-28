package de.timosl.ssp.internal;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;

import de.timosl.ssp.models.SocketMessage;

/**
 * Internal implementation of the {@link WebSocketServer} class.
 * Works in conjunction with the {@link SocketServer} class.
 *
 */
class SocketServerInternal extends WebSocketServer {
	
	/**
	 * The {@link SocketServer} associated with this {@link SocketServerInternal}.
	 */
	private SocketServer parent;
	
	/**
	 * A {@link Gson} instance for converting objects from and to JSON.
	 */
	private Gson gson = new Gson();
	
	/**
	 * Maps the connections to the last activity timestamp (in UNIX time). 
	 */
	private Map<WebSocket, Long> socketActivity = new HashMap<WebSocket,Long>();
	
	/**
	 * Creates a new {@link SocketServerInternal}.
	 * 
	 * @param parent The {@link SocketServer} associated with 
	 * this {@link SocketServerInternal}
	 * @param address The socket address this server will use
	 */
	public SocketServerInternal(SocketServer parent, InetSocketAddress address) {
		super(address);
		this.parent = parent;
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		this.parent.onOpen(conn);
		this.updateSocketActivity(conn);
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		this.parent.onClose(conn);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		// Update the activity timestamp for this connection
		this.updateSocketActivity(conn);
		
		// Try to deserialize the incoming message
		// and drop invalid messages silently
		try {
			SocketMessage msgObj = gson.fromJson(message, SocketMessage.class);
			this.parent.onMessage(conn, msgObj);
		} catch(Exception e) {
			System.err.println("[Server] Error while processing message: "+message+".\nThe error was: "+e.toString()+"("+e.getMessage()+")");
			e.printStackTrace();
		}		
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.err.println("[Server] Received error: "+ex);
		ex.printStackTrace();
	}
	
	@Override
	public void onWebsocketPong(WebSocket conn, Framedata f) {
		this.updateSocketActivity(conn);
	}
	
	/**
	 * Updates the last activity timestamp of the given
	 * connection to the current time.
	 * 
	 * @param conn The {@link WebSocket} connection to update
	 */
	private void updateSocketActivity(WebSocket conn) {
		synchronized (this.socketActivity) {
			this.socketActivity.put(conn, System.currentTimeMillis());
		}
	}
	
	/**
	 * Returns the last activity of the given connection.
	 * 
	 * @param conn The {@link WebSocket} connection
	 * @return The timestamp (in UNIX time) the last message was 
	 * received on this connection
	 */
	public long getLastActivity(WebSocket conn) {
		synchronized (this.socketActivity) {
			return this.socketActivity.get(conn);
		}
	}
}
