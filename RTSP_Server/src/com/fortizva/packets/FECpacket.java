package com.fortizva.packets;
public class FECpacket {

	/* FEC Packet Format
	 *  0                   1                   2                   3	(Bits)
	 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	 *  0               1               2               3				(Bytes)
	 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |          RTP Header (12 bytes) [After packetization]          |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * | Base Sequence Number (16 bits)|     Mask Length (16 bits)     |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |                  Protection Mask (variable)                   |
	 * |                              ...                              |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	 * |                     FEC Payload (XOR)                         |
	 * |                              ...                              |
	 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
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
	private int baseSequenceNumber; // Base sequence number of the first RTP packet protected by this FEC packet
	private int maskLength; // Number of RTP packets protected by this FEC packet
	private byte[] protectionMask; // Bitmask indicating which RTP packets are protected
	
	// FEC Payload
	private byte[] fecPacketBytes; // FEC payload containing the FEC header and XOR of the RTP packets
	private byte[] xorPayload; // XOR of the payloads of the RTP packets
	private int xorPayloadSize; // Size of the XOR payload (should be equal to the size of the largest RTP packet payload)
	
	/** * Constructor to create a FEC packet from an array of RTP packets.
	 * @param rtpPackets Array of RTP packets to be protected by this FEC packet.
	 */
	public FECpacket(RTPpacket[] rtpPackets) {
		
		maskLength = rtpPackets.length;
		
		// Protection Mask
		// Build a maskLength bit array of 1s indicating which RTP packets are protected
		// In this implementation, we assume that every RTP packet is protected
		// If maskLength's last byte is not fully filled, it will be padded with zeroes.
		int j = 0;
		 // Create a byte array to hold the protection mask bits
		protectionMask = new byte[(int) Math.ceil((double) (maskLength + 7) / 8)]; // Calculate the number of bytes needed for the mask
		for (int i = 0; i < maskLength; i++) {
			if (i % 8 == 0 && i != 0) {
				j++;
			}
			protectionMask[j] |= (1 << (7 - (i % 8))); // Set the bit in the protection mask to 1
		}
		
		// Get largest RTP packet size
		xorPayloadSize = 0;
		int current_size;
		for (RTPpacket rtpPacket : rtpPackets) {
			current_size = rtpPacket.getlength(); // getlength() returns the size of the RTP packet including header and payload
			if (current_size > xorPayloadSize) {
				xorPayloadSize = current_size; 
			}
		}
		
		// Initialize FEC Packet bytes array
		// Calculate total FEC size (without RTP header) to insert it as an RTP payload
		packetFECSize = xorPayloadSize; // Size of XOR payload
		packetFECSize += 2; // +2 for Base Sequence Number
		packetFECSize += 2; // +2 for Mask Length
		packetFECSize += protectionMask.length; // Add size of protection mask
		
		System.out.println("FEC SIZE "+packetFECSize);
		fecPacketBytes = new byte[packetFECSize];
		
		// Prepare FEC packet with default values
		// Base Sequence Number
		this.baseSequenceNumber = rtpPackets[0].getsequencenumber();
		byte[] basesequencebytes = BinaryField.binarySplitter(this.baseSequenceNumber);
		for (int i = 0; i < Math.min(basesequencebytes.length, 2); i++) {
			// Fill Base Sequence Number in reverse order so the beginning is zeroes
			fecPacketBytes[1-i] = basesequencebytes[(basesequencebytes.length - 1) - i];
		}
		
		// Mask Length (16 bits)
		byte[] maskLengthBytes = BinaryField.binarySplitter(maskLength);
		for (int i = 0; i < Math.min(maskLengthBytes.length, 2); i++) {
			// Fill Mask Length in reverse order so the beginning is zeroes
			fecPacketBytes[2 + 1 - i] = maskLengthBytes[(maskLengthBytes.length - 1) - i];
		}
		
		// Insert the protection mask into the FEC packet bytes
		// If protectionMask last byte is not fully filled, it will be padded with zeroes
		for (int i = 0; i < protectionMask.length; i++) {
			fecPacketBytes[4 + (protectionMask.length - 1) - i] = protectionMask[(protectionMask.length - 1) - i];
		}
				
		// XOR the payloads of the RTP packets
		/* As the packets may have different sizes, we need to handle that
		 * by filling the packet with zeros to match the largest size.
		 */		
		xorPayload = new byte[xorPayloadSize]; // Initialize XOR payload with the size of the largest RTP packet
		boolean firstPacket = true;
		byte currentByte;
		
		// Iterate through each RTP packet and perform XOR operation on payloads
		for (RTPpacket rtpPacket : rtpPackets) {
			byte[] currentPacket = new byte[rtpPacket.getlength()];
			rtpPacket.getpacket(currentPacket);
			
			for (int i = 0; i < xorPayload.length; i++) {
				currentByte = (i < currentPacket.length) ? currentPacket[i] : 0; // Fill with 0 if index exceeds payload length
				if (firstPacket) {
					xorPayload[i] = currentByte; // Initialize with first packet's payload
				} else {
					xorPayload[i] ^= currentByte; // XOR operation with subsequent packets
				}
			}
			firstPacket = false; // After the first packet, we will always XOR with the next ones
		}
		
		// Fill the FEC payload with the XOR result
		for (int i = 0; i < xorPayload.length; i++) {
			fecPacketBytes[4 + protectionMask.length + i] = xorPayload[i]; // Start filling after the protection mask
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
