package de.timosl.ssp.models;

/**
 * A SocketAck is sent as a response to a {@link SocketMessage},
 * if no custom response was set manually. The SocketAck can
 * contain an additional message, describing how the initial message
 * was handled by the receiving end. A SSP client must not send
 * a SocketAck in response to receiving a SocketAck.
 *
 */
public class SocketAck {
	
	/**
	 * Describes how the initial request was handled by the receiving client.
	 * This is an optional attribute. 
	 */
	public String message;
	
	/**
	 * Creates a new, empty {@link SocketAck}.
	 */
	public SocketAck() {}
	
	/**
	 * Creates a new {@link SocketAck} with the given
	 * String as an additional message. 
	 * 
	 * @param message The additional message to send with this
	 * {@link SocketAck}
	 */
	public SocketAck(String message) {
		this.message = message;
	}
}
