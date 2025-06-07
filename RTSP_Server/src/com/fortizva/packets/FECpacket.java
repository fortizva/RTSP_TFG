package com.fortizva.packets;
public class FECpacket {

	/* FEC Packet Format
	 *  0                   1                   2                   3	(Bits)
	 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	 *  0               1               2               3				(Bytes)
	 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |E|L|P|X|  CC   |M| PT recovery |            SN base            |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |                          TS recovery                          |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |        length recovery        |							   |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 *
	 * FEC Packet Format Description
	 * As defined in RFC 5109 - RTP Payload Format for Generic Forward Error Correction
	 *  
	 * The E bit is the extension flag reserved to indicate any future
	 * extension to this specification.  It SHALL be set to 0, and SHOULD be
	 * ignored by the receiver.
	 * 
	 * The L bit indicates whether the long mask is used.  When the L bit is
	 * not set, the mask is 16 bits long.  When the L bit is set, the mask
	 * is then 48 bits long.
	 * 
	 * The P recovery field, the X recovery field, the CC recovery field,
	 * the M recovery field, and the PT recovery field are obtained via the
	 * protection operation applied to the corresponding P, X, CC, M, and PT
	 * values from the RTP header of the media packets associated with the
	 * FEC packet.
	 * 
	 * The SN base field MUST be set to the lowest sequence number, taking
	 * wrap around into account, of those media packets protected by FEC (at
	 * all levels).  This allows for the FEC operation to extend over any
	 * string of at most 16 packets when the L field is set to 0, or 48
	 * packets when the L field is set to 1, and so on.
	 * 
	 * The TS recovery field is computed via the protection operation
	 * applied to the timestamps of the media packets associated with this
	 * FEC packet.  This allows the timestamp to be completely recovered.
	 * 
	 * The length recovery field is used to determine the length of any
	 * recovered packets.  It is computed via the protection operation
	 * applied to the unsigned network-ordered 16-bit representation of the
	 * sums of the lengths (in bytes) of the media payload, CSRC list,
	 * extension and padding of each of the media packets associated with
	 * this FEC packet (in other words, the CSRC list, RTP extension, and
	 * padding of the media payload packets, if present, are "counted" as
	 * part of the payload).  This allows the FEC procedure to be applied
	 * even when the lengths of the protected media packets are not
	 * identical.  For example, assume that an FEC packet is being generated
	 * by xor'ing two media packets together.  The length of the payload of
	 * two media packets is 3 (0b011) and 5 (0b101) bytes, respectively.
	 * The length recovery field is then encoded as 0b011 xor 0b101 = 0b110.
	 *
	 *
	 * FEC Level Header for FEC Packets
	 *  0                   1                   2                   3 	(Bits)
	 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	 *  0               1               2               3				(Bytes)
	 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |       Protection Length       |             mask              |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |              mask cont. (present only when L = 1)             | (if L = 1, not used in this implementation)
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |                     FEC Payload (XOR)                         |
	 * |                              ...                              |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * 
	 * Every FEC packet MUST contain a FEC level header that contains the
	 * protection length and the mask.
	 * The protection length field is 16 bits long.  The mask field is 16 bits
	 * long (when the L bit is not set) or 48 bits long (when the L bit is set).
	 *
	 * The mask field in the FEC level header indicates which packets are
	 * associated with the FEC packet at the current level.  It is either 16
	 * or 48 bits depending on the value of the L bit (In this case is always
	 * 16 bits).
	 * 
	 * If bit i in the mask is set to 1, then the media packet with sequence
	 * number N + i is associated with this FEC packet, where N is the SN Base field in the
	 * FEC packet header.
	 * 
	 * The most significant bit of the mask corresponds to i=0, and the least significant
	 * to i=15 when the L bit is set to 0, or i=47 when the L bit is set to 1.
	 * 
	 * Only base FEC Level (0) is used in this implementation.
	 */


	/* As the FEC packet needs to be prepared before packetization, class extending
	 * of RTPpacket is not viable, as super() must be called first.
	 * This class is designed to create a FEC packet that goes inside an RTP packet
	 * (created afterwards by using all of this as payload).
	 */
	
	// RTP variables
	static final int FECPType = 0xBE;
	private int packetFECSize; // Size of the FEC packet (excluding RTP header as this is the RTP payload size)
	
	// FEC variables
	private byte[] flags = new byte[2]; // Flags field combining E, L, P, X, CC, M, and PT recovery fields	
	private int baseSequenceNumber; // Base sequence number of the first RTP packet protected by this FEC packet
	private int maskLength; // Number of RTP packets protected by this FEC packet
	private byte[] protectionMask = new byte[2]; // Default size of the protection mask is 2 bytes (16 bits) (No long mask used in this implementation)
	private int lengthRecovery; // Length recovery field (16 bits) to determine the length of the recovered packets
	private int protectionLength; // Protection length field (16 bits) to determine the length of the FEC payload
	private int timestampRecovery; // Timestamp recovery field (32 bits) to determine the timestamp of the recovered packets
	// FEC Payload
	private byte[] fecPacketBytes; // FEC payload containing the FEC header and XOR of the RTP packets
	private byte[] xorPayload; // XOR of the payloads of the RTP packets
	//private int xorPayloadSize; // Size of the XOR payload (should be equal to the size of the largest RTP packet payload)
	
	/** * Constructor to create a FEC packet from an array of RTP packets.
	 * @param rtpPackets Ordered array of RTP packets to be protected by this FEC packet. (First packet must have the lowest sequence number)
	 */
	public FECpacket(RTPpacket[] rtpPackets) {
		
		/* First flags have the same values for all FEC packets so we can set them here
		 * E bit (Extension flag) is set to 0 as per RFC 5109
		 * L bit (Long mask) is set to 0 as we assume the mask length is 16 bits
		 * P, X, CC, M, and PT recovery fields are not used in this implementation but should be calculated based on the RTP packets.
		 */
		
		flags[0] = (byte) 0b00000000; // Initialize flags to 0 as these flags are not used in this implementation
		/*
		 *  Second bytes of flags would be equals to the P, X, CC, M, and PT recovery fields.
		 *  The only field used in this implementation is the PType, but XORing it will always result in 0 as the PType is the same
		 *  for all protected RTP packets. So we can set it to 0.
		 */
		flags[1] = (byte) 0b00000000;
		
		// We asume that the first RTP packet has the lowest sequence number (aka base sequence number)
		baseSequenceNumber = rtpPackets[0].getsequencenumber();
		
		// Determine the number of RTP packets to be protected by this FEC packet
		maskLength = rtpPackets.length; 
		
		
		/* Protection Mask
		 * Build a maskLength bit array of 1s indicating which RTP packets are protected
		 * In this implementation, we assume that all packets are protected, consecutive and ordered
		 * If maskLength's last byte is not fully filled, it will be padded with zeroes at the end.
		 */
		int j = 0;
		for (int i = 0; i < maskLength; i++) {
			if (i % 8 == 0 && i != 0) {
				j++;
			}
			/* Left bitwise shift the value 1 "i%8" times (0-7 times) starting from the leftmost bit (7)
			 * to the rightmost bit (0).
			 * The bigger the i value, the more right the bit will be set to 1.
			 * The operator |= means "bitwise OR" which sets the bit to 1 if it is not already set, 
			 * so it does not overwrite the previous bits.
			 */
			protectionMask[j] |= (1 << (7 - (i % 8))); // Set the bit in the protection mask to 1
		}
		
		// Get largest RTP packet size and calculate length_recovery field as an XOR of all RTP packets' payload sizes
		/* 
		 * Protection length is set to the size of the biggest RTP packet payload, but in real case scenarios
		 * can be set to only a number of bytes.
		 * 
		 * Length recovery field is used to determine the length of any recovered packets, this is calculated by applying
		 * the protection operation to the payload sizes of the RTP packets.
		 * 
		 * TimeStamp Recovery is calculated by applying the protection operation to the timestamps of the RTP packets. Same
		 * as the length recovery.
		 */
		int current_size;
		protectionLength = 0; // Initialize protection length value to 0
		lengthRecovery = 0; // Initialize length recovery value to 0
		timestampRecovery = 0; // Initialize timestamp recovery value to 0
		for (RTPpacket rtpPacket : rtpPackets) {
			// Calculate length recovery value as an XOR of all RTP packets' payload sizes
			lengthRecovery ^= rtpPacket.getpayload_length();
			timestampRecovery ^= rtpPacket.gettimestamp();
			current_size = rtpPacket.getpayload_length();
			if (current_size > protectionLength) {
				protectionLength = current_size; 
			}
		}		
		
		// Initialize FEC Packet bytes array
		// Calculate total FEC size (without RTP header) to insert it as an RTP payload
		// Main FEC Header
		// If the L bit is set to 1, we would add 3 more bytes for the mask continuation (48 bits) (Not used in this implementation)
		packetFECSize = CommonPacketValues.FEC_HEADER_SIZE + CommonPacketValues.FEC_LEVEL_HEADER_SIZE; // Headers size
		packetFECSize += protectionLength;// Add the size of the XOR payload (size of the largest RTP packet payload)

		fecPacketBytes = new byte[packetFECSize];
		
		// Prepare FEC packet
		// Flags
		fecPacketBytes[0] = flags[0]; // E, L, P, X, CC, M bits
		fecPacketBytes[1] = flags[1]; // PT recovery field
		
		// Fill the Base Sequence Number
		this.baseSequenceNumber = rtpPackets[0].getsequencenumber();
		byte[] basesequencebytes = BinaryField.binarySplitter(this.baseSequenceNumber);
		for (int i = 0; i < Math.min(basesequencebytes.length, 2); i++) {
			// Fill Base Sequence Number in reverse order so the beginning is zeroes
			fecPacketBytes[2+1-i] = basesequencebytes[(basesequencebytes.length - 1) - i];
		}

		// Fill the TimeStamp recovery field
		byte[] timestampBytes = BinaryField.binarySplitter(timestampRecovery);
		for (int i = 0; i < Math.min(timestampBytes.length, 4); i++) {
			fecPacketBytes[4 + 3 - i] = timestampBytes[(timestampBytes.length - 1) - i];
		}

		// Fill the Length recovery field
		byte[] lengthRecoveryBytes = BinaryField.binarySplitter(lengthRecovery);
		for (int i = 0; i < Math.min(lengthRecoveryBytes.length, 2); i++) {
			fecPacketBytes[8 + 1 - i] = lengthRecoveryBytes[(lengthRecoveryBytes.length - 1) - i];
		}
		// Fill the Protection Length field
		byte[] protectionLengthBytes = BinaryField.binarySplitter(protectionLength);
		for (int i = 0; i < Math.min(protectionLengthBytes.length, 2); i++) {
			fecPacketBytes[10 + 1 - i] = protectionLengthBytes[(protectionLengthBytes.length - 1) - i];
		}
		
		// Fill the Protection Mask field
		fecPacketBytes[12] = protectionMask[0]; // First byte of the protection mask
		fecPacketBytes[13] = protectionMask[1]; // Second byte of the protection mask
		
		// If the L bit was set to 1, we would fill the next 3 bytes with the mask continuation (not used in this implementation)
		
		// XOR the payloads of the RTP packets
		/* As the packets may have different sizes, we need to handle that
		 * by filling the packet with zeros to match the largest size.
		 */		
		xorPayload = new byte[protectionLength]; // Initialize XOR payload with the size of the largest RTP packet
		byte currentByte;
		boolean firstPacket = true; // Flag to check if it's the first packet to initialize the XOR payload
		// Iterate through each RTP packet and perform XOR operation on payloads
		for (RTPpacket rtpPacket : rtpPackets) {
			if(firstPacket) {
				rtpPacket.getpayload(xorPayload); // Get the first packet payload to initialize the XOR payload
			}else {
				byte[] currentPayload = new byte[rtpPacket.getpayload_length()];
				rtpPacket.getpayload(currentPayload);
				for (int i = 0; i < xorPayload.length; i++) {
					currentByte = (i < currentPayload.length) ? currentPayload[i] : 0; // Fill with 0 if index exceeds payload length
					xorPayload[i] ^= currentByte; // XOR operation with subsequent packets
					
				}
			}
			firstPacket = false; // Set the flag to false after the first packet
		}
		
		// Fill the FEC payload with the XOR result
		// Total header length of the FEC packet (FEC header + FEC level header);
		int totalHeaderLength = CommonPacketValues.FEC_HEADER_SIZE+CommonPacketValues.FEC_LEVEL_HEADER_SIZE; 
		for (int i = 0; i < xorPayload.length; i++) {
			fecPacketBytes[totalHeaderLength + i] = xorPayload[i]; // Start filling after the protection mask
		}
	}
	
	/**
	 * Returns the length of the protection mask.
	 * @return Length of the protection mask in bits.
	 */
	public int getMaskLength() {
		return maskLength;
	}
	
	/**
	 * Returns the FEC RTP Payload Type.
	 * @return FEC Payload Type.
	 */
	public int getFECPType() {
		return FECPType;
	}
	
	/**
	 * Returns the size of the FEC packet (excluding RTP header).
	 * @return Size of the FEC packet in bytes.
	 */
	public int getFECPacketSize() {
		return packetFECSize;
	}
	
	/**
	 * Returns the Base Sequence Number of the first RTP packet protected by this FEC packet.
	 * @return Base sequence number.
	 */
	public int getBaseSequenceNumber() {
		return baseSequenceNumber;
	}
	
	/**
	 * Copies the protection mask into the provided byte array and returns the size of the protection mask.
	 * @param mask
	 * @return Size of the protection mask in bytes.
	 */
	public int getProtectionMask(byte[] mask) {
		// Return the protection mask
		for (int i = 0; i < protectionMask.length; i++) {
			mask[i] = protectionMask[i];
		}
		return (int) protectionMask.length;
	}
	
	/**
	 * Copies the XOR payload into the provided byte array and returns the size of the XOR payload.
	 * @param payload
	 * @return Size of the XOR payload in bytes.
	 */
	public int getXORPayload(byte[] payload) {

		for (int i = 0; i < xorPayload.length; i++)
			payload[i] = xorPayload[i];

		return (xorPayload.length);
	}
	
	/**
	 * Copies the whole FEC packet in bytes into the provided byte array and returns the size of the FEC packet.
	 * @param packet
	 * @return Size of the FEC packet in bytes.
	 */
	public int getFECPacket(byte[] packet) {

		for (int i = 0; i < fecPacketBytes.length; i++)
			packet[i] = fecPacketBytes[i];

		return (fecPacketBytes.length);
	}
}
