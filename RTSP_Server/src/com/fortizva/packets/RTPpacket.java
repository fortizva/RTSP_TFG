package com.fortizva.packets;

import java.util.Arrays;

/**
 * RTPpacket class represents a Real-time Transport Protocol (RTP) packet.
 * It encapsulates the RTP header and payload, providing methods to construct,
 * parse, and manipulate RTP packets.
 * <pre>
 *  0                   1                   2                   3		(Bits)
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  0               1               2               3			(Bytes)
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            contributing source (CSRC) identifiers             | // Not used
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *</pre>
 */
public class RTPpacket implements Comparable<RTPpacket> {

	// size of the RTP header:
	static int HEADER_SIZE = 12;

	// Fields that compose the RTP header
	public int Version;
	public int Padding;
	public int Extension;
	public int CC;
	public int Marker;
	public int PayloadType;
	public int SequenceNumber;
	public int TimeStamp;
	public int Ssrc;

	// Bitstream of the RTP header
	public byte[] header;

	// size of the RTP payload
	public int payload_size;
	// Bitstream of the RTP payload
	public byte[] payload;

	/**
	 * Constructs an RTP packet with the specified parameters.
	 * 
	 * @param PType      The payload type of the RTP packet
	 * @param Framenb    The sequence number of the RTP packet
	 * @param Time       The timestamp of the RTP packet
	 * @param data       The payload data as a byte array
	 * @param data_length The length of the payload data
	 */
	public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
		// fill by default header fields:
		Version = CommonValues.RTP_VERSION;
		Padding = CommonValues.RTP_PADDING;
		Extension = CommonValues.RTP_EXTENSION;
		CC = CommonValues.RTP_CC;
		Marker = CommonValues.RTP_MARKER;
		Ssrc = CommonValues.RTP_SSRC;

		// fill changing header fields:
		SequenceNumber = Framenb;
		TimeStamp = Time;
		PayloadType = PType;

		// build the header bistream:
		// --------------------------
		header = new byte[HEADER_SIZE];

		// Fill the header array of byte with RTP header fields
		// HEADER_SIZE = 12 -> 3bytes * 4

		header [0] = BinaryField.binaryBuilder(header[0],
             	new BinaryField(Version, 2),
				new BinaryField(Padding, 1),
				new BinaryField(Extension, 1),
				new BinaryField(CC, 4));
		header [1] = BinaryField.binaryBuilder(header[1],
		new BinaryField(Marker, 1),
		new BinaryField(PayloadType, 7));
		
		// Sequence number
		byte[] sequencebytes = BinaryField.binarySplitter(SequenceNumber);
		for(int i = 0; i < Math.min(sequencebytes.length, 2); i++) {
			header[2+1-i] = sequencebytes[(sequencebytes.length-1) - i];
		}
		
		// TimeStamp
		byte[] timestampbytes = BinaryField.binarySplitter(TimeStamp);
		for(int i = 0; i < Math.min(timestampbytes.length, 4); i++) {
			header[4+3-i] = timestampbytes[(timestampbytes.length-1) - i];
		}
		
		// SSRC
		byte[] ssrcbytes = BinaryField.binarySplitter(Ssrc);
		for(int i = 0; i < Math.min(ssrcbytes.length, 4); i++) {
			header[8+3-i] = ssrcbytes[(ssrcbytes.length-1) - i];
		}
		// fill the payload bitstream:
		payload_size = data_length;
		payload = new byte[data_length];

		// fill payload array of byte from data (given in parameter of the constructor)
		for(int i = 0; i < data_length; i++) {
			payload[i] = data[i];
		}

		//printheader();
	}

	/**
	 * Constructs an RTP packet from a byte array representing the packet.
	 * 
	 * @param packet       The byte array containing the RTP packet data
	 * @param packet_size  The size of the RTP packet in bytes
	 */
	public RTPpacket(byte[] packet, int packet_size) {
		// Fill default fields:
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// Check if total packet size is lower than the header size
		if (packet_size >= HEADER_SIZE) {
			// Get the header bitstream:
			header = new byte[HEADER_SIZE];
			for (int i = 0; i < HEADER_SIZE; i++)
				header[i] = packet[i];

			// Get the payload bitstream:
			payload_size = packet_size - HEADER_SIZE;
			payload = new byte[payload_size];
			for (int i = HEADER_SIZE; i < packet_size; i++)
				payload[i - HEADER_SIZE] = packet[i];

			// Interpret the changing fields of the header:
			// unsigned_int() is used to ensure that byte values are treated as unsigned integers
			PayloadType = header[1] & 127;
			SequenceNumber = unsigned_int(header[3]) + 256 * unsigned_int(header[2]);
			TimeStamp = unsigned_int(header[7]) + 256 * unsigned_int(header[6]) + 65536 * unsigned_int(header[5])
					+ 16777216 * unsigned_int(header[4]);
		}
	}

	/**
	 * Returns a copy of the RTP packet header as a byte array.
	 * 
	 * @return A copy of the RTP header byte array
	 */
	public byte[] getPayload() {
		// Return copy of payload byte array
		return Arrays.copyOf(payload, payload_size);
	}

	/**
	 * Returns the size of the RTP packet payload.
	 * 
	 * @return The size of the payload in bytes
	 */
	public int getPayloadLength() {
		return (payload_size);
	}

	/**
	 * Returns the total length of the RTP packet, which is the sum of the header size
	 * and the payload size.
	 * 
	 * @return The total length of the RTP packet
	 */
	public int getSize() {
		return (payload_size + HEADER_SIZE);
	}

	/**
	 * Returns the complete RTP packet as a byte array, including both header and payload.
	 * 
	 * @return A byte array containing the complete RTP packet
	 */
	public byte[] getPacket() {
		// Return a copy of the complete RTP packet directly
		byte[] packet = new byte[getSize()];
		System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
		System.arraycopy(payload, 0, packet, HEADER_SIZE, payload_size); // Copy payload after header
		return packet;
	}
	
	/**
	 * Returns the timestamp of the RTP packet
	 * 
	 * @return the timestamp as an integer
	 */
	public int getTimeStamp() {
		return (TimeStamp);
	}

	/** 
	 * Returns the sequence number of the RTP packet
	 * 
	 * @return the sequence number as an integer
	 */
	public int getSequenceNumber() {
		return (SequenceNumber);
	}

	/**
	 * Returns the payload type of the RTP packet
	 * 
	 * @return the payload type as an integer
	 */
	public int getPayloadType() {
		return (PayloadType);
	}

	/**
	 * Prints relevant RTP header information to the console
	 */
	public void printHeader() {
		System.out.print("[RTP-Header] ");
		System.out.println("Version: " + Version + ", Padding: " + Padding + ", Extension: " + Extension + ", CC: " + CC
				+ ", Marker: " + Marker + ", PayloadType: " + PayloadType + ", SequenceNumber: " + SequenceNumber
				+ ", TimeStamp: " + TimeStamp);
	}

	/**
	 *  Return the unsigned value of 8-bit integer number
	 *  This function ensures that the byte value is an unsigned integer
	 *  @param nb the signed integer to convert
	 *  @return the unsigned value of the integer
	 */
	static int unsigned_int(int nb) {
		if (nb >= 0)
			return (nb);
		else
			return (256 + nb);
	}

	/**
	 * Compares this RTP packet with another RTP packet based on their sequence numbers.
	 * 
	 * @param p The RTP packet to compare with
	 * @return A negative integer, zero, or a positive integer as this packet's sequence number
	 *         is less than, equal to, or greater than the specified packet's sequence number
	 */
	@Override
	public int compareTo(RTPpacket p) {
		// Compare RTP packets based on their sequence number
		if (this.SequenceNumber < p.SequenceNumber) {
			return -1;
		} else if (this.SequenceNumber > p.SequenceNumber) {
			return 1;
		} else {
			return 0; // They are equal
		}
	}

}
