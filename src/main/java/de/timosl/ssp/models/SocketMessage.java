package de.timosl.ssp.models;


/**
 * A SocketMessage wraps an actual event and adds additional
 * attributes for managing the flow of messages between the client and
 * the server. The {@link #messageID} and {@link #messageReplyID} can be used
 * to create requests and responses. The {@link #eventClass} attribute
 * declares the class type of the event that is carried by this
 * SocketMessage. Use this to filter out any unwanted messages and to make
 * sure to what class the {@link #eventContent} shall be unwraped to. To create
 * a response chain, the client receiving a SocketMessage must put the {@link #messageID}
 * into the {@link #messageReplyID} field and create a new ID for the response message.
 *
 */
public class SocketMessage {
	
	/**
	 * The ID of the {@link SocketMessage}. This does not need to be
	 * globally unique, but the server or the client may need this ID
	 * to respond to a particular message. 
	 */
	public String messageID;
	
	/**
	 * If set, this ID represents the message, this message is in response
	 * to.
	 * @see #messageID
	 */
	public String messageReplyID;
	
	/**
	 * The class of the event this {@link SocketMessage} delivers.
	 */
	public String eventClass;
	
	/**
	 * The event this {@link SocketMessage} delivers, serialized in JSON.
	 */
	public String eventContent;
	
	/**
	 * The time (in UNIX time) this message was created.
	 */
	public long timestamp;
	
	/**
	 * Constructs a new {@link SocketMessage} with no values set.
	 */
	public SocketMessage() {}
	
	/**
	 * Constructs a new {@link SocketMessage} by copying the values
	 * from the given SocketMessage.
	 */
	public SocketMessage(SocketMessage original) {
		this.eventClass = original.eventClass;
		this.eventContent = original.eventContent;
		this.messageID = original.messageID;
		this.messageReplyID = original.messageReplyID;
		this.timestamp = original.timestamp;
	}
}
