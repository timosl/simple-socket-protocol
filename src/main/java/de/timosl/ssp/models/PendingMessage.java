package de.timosl.ssp.models;

import de.timosl.ssp.ResultHandler;

/**
 * Combines a {@link SocketMessage} with the {@link ResultHandler}
 * to be called when the response for it arrives.
 *
 */
public class PendingMessage {
	
	/**
	 * The {@link SocketMessage}. 
	 */
	public SocketMessage message;
	
	/**
	 * The {@link ResultHandler} associated with the
	 * {@link #message}. 
	 */
	public ResultHandler handler;
	
	/**
	 * Creates a new, empty {@link PendingMessage}.
	 */
	public PendingMessage() {}
	
	/**
	 * Creates a new {@link PendingMessage} with the
	 * given parameters.
	 * 
	 * @param message The {@link SocketMessage}
	 * @param handler The {@link ResultHandler} associated with the message
	 */
	public PendingMessage(SocketMessage message, ResultHandler handler) {
		this.message = message;
		this.handler = handler;
	}
}

