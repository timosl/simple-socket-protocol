package de.timosl.ssp;

import de.timosl.ssp.internal.SocketClient;

/**
 * A listener that will be called when the state of the
 * {@link SocketClient} changes.
 *
 */
public interface StateListener {
	
	/**
	 * Called when the connection is ready and can be used to transmit
	 * messages.
	 */
	public void onConnectionReady();
	
	/**
	 * Called when the connection is closed and can no longer be used
	 * to transmit messages.
	 */
	public void onConnectionClosed();
	
	/**
	 * Called when an exception occurs while processing or sending a message.
	 * The {@link #onConnectionClosed()} method will be called if this exception
	 * causes the connection to close.
	 * 
	 * @param exception The exception that occurred
	 */
	public void onConnectionError(Exception exception);
}
