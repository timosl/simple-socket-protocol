package de.timosl.ssp.internal;

import java.net.URI;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;

import de.timosl.ssp.models.SocketMessage;

/**
 * Internal implementation of the {@link WebSocketClient} class.
 * Works in conjunction with the {@link SocketClient} class.
 *
 */
class SocketClientInternal extends WebSocketClient {
	
	/**
	 * Timestamp (in UNIX time) of the last message (or PONG message) received
	 * from the server.
	 */
	private long lastActivity;
	
	/**
	 * The {@link SocketClient} associated with this {@link SocketClientInternal}.
	 */
	private SocketClient parent;
	
	/**
	 * A {@link Gson} instance for converting objects from and to JSON.
	 */
	private Gson gson = new Gson();
	
	/**
	 * Returns the last activity on the current connection
	 * 
	 * @return The timestamp (in UNIX time) of the last message 
	 * (or PONG message) received from the server
	 */
	public long getLastActivity() {
		return this.lastActivity;
	}
	
	/**
	 * Creates a new {@link SocketClientInternal}.
	 * 
	 * @param parent The {@link SocketClient} associated with 
	 * this {@link SocketClientInternal}
	 * @param serverURI The {@link URI} of the server
	 */
	public SocketClientInternal(SocketClient parent, URI serverURI) {
		super(serverURI);
		this.parent = parent;
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		this.lastActivity = System.currentTimeMillis();
		this.parent.connectionReady();		
	}

	@Override
	public void onMessage(String message) {
		// Update the last activity timestamp
		this.lastActivity = System.currentTimeMillis();
		
		// Try to deserialize the incoming message
		// and drop invalid messages silently
		try {
			SocketMessage msgObj = gson.fromJson(message, SocketMessage.class);
			this.parent.receivedMessage(msgObj);
		} catch(Exception e) {
			System.err.println("[Client] Error while processing message: "+message+".\nThe error was: "+e.toString()+"("+e.getMessage()+")");
			e.printStackTrace();
		}
	}
	
	@Override
	public void onWebsocketPong(WebSocket conn, Framedata f) {
		this.lastActivity = System.currentTimeMillis();
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		this.parent.receivedClose();
	}

	@Override
	public void onError(Exception ex) {
		this.parent.receivedError(ex);
		ex.printStackTrace();
	}
}
