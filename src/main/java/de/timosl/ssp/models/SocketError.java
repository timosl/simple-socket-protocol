package de.timosl.ssp.models;

/**
 * A SocketError is sent, when the sending client wants to
 * signal an error in the protocol or in the received request.
 * If a client receives a SocketError, or a subclass of it,
 * the onError() method will be called instead of the usual
 * onResponseReceived().
 *
 */
public class SocketError {
	
	/**
	 * The name or title of the error. 
	 */
	public String error;
	
	/**
	 * An additional message, further describing the error. 
	 */
	public String message;
	
	/**
	 * Creates a new, empty {@link SocketError}.
	 */
	public SocketError() {}
	
	/**
	 * Creates a new {@link SocketError} with the given
	 * parameters.
	 * 
	 * @param error The name or title of the error
	 * @param message An additional message, describing the error further
	 */
	public SocketError(String error, String message) {
		this.error = error;
		this.message = message;
	}	
}
