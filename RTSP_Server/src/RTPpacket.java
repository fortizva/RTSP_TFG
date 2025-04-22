
//class RTPpacket

public class RTPpacket {

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

	class BinaryField {
		public int field, length;

		public BinaryField(int field, int length) {
			this.field = field;
			this.length = length;
		}
	}

	public static byte binaryBuilder(byte binary, BinaryField ...fields) {
		byte bin = binary;
		int current = 0;
		for(BinaryField i : fields) {
			bin = (byte)(bin | i.field << (8 - i.length - current));
			current += i.length;
		}
		return bin;
	}
	
	public static byte[] binarySplitter(long value) {
		byte[] bin = new byte[(int) Math.ceil(Long.toBinaryString(value).length()/8.0)];
		for(int i = bin.length-1; i >= 0; i--) {
			bin[bin.length-1 - i] = (byte) ((value >> (8*i)) & 0xFF);
			//System.out.println(String.format("%8s", Integer.toBinaryString(bin[i] & 0xFF)).replace(' ', '0'));
		}	
		
		return bin;
	}

	// --------------------------
	// Constructor of an RTPpacket object from header fields and payload bitstream
	// --------------------------
	public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
		// fill by default header fields:
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// fill changing header fields:
		SequenceNumber = Framenb;
		TimeStamp = Time;
		PayloadType = PType;

		// build the header bistream:
		// --------------------------
		header = new byte[HEADER_SIZE];

		/*
		 * 
		 * 0 1 2 3 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
		 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |V=2|P|X|
		 * CC |M| PT | sequence number |
		 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | timestamp
		 * | +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |
		 * synchronization source (SSRC) identifier |
		 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+ |
		 * contributing source (CSRC) identifiers | | .... |
		 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
		 * 
		 */

		// .............
		// TO COMPLETE
		// .............
		// fill the header array of byte with RTP header fields

		// header[0] = ...
		// .....

		// HEADER_SIZE = 12 -> 3bytes * 4

		header [0] = binaryBuilder(header[0],
             	new BinaryField(Version, 2),
				new BinaryField(Padding, 1),
				new BinaryField(Extension, 1),
				new BinaryField(CC, 4));
		header [1] = binaryBuilder(header[1],
		new BinaryField(Marker, 1),
		new BinaryField(PayloadType, 7));
		
		// Sequence number
		byte[] sequencebytes = binarySplitter(SequenceNumber);
		for(int i = 0; i < Math.min(sequencebytes.length, 2); i++) {
			header[2+1-i] = sequencebytes[(sequencebytes.length-1) - i];
		}
		
		// TimeStamp
		byte[] timestampbytes = binarySplitter(TimeStamp);
		for(int i = 0; i < Math.min(timestampbytes.length, 4); i++) {
			header[4+3-i] = timestampbytes[(timestampbytes.length-1) - i];
		}
		
		// SSRC
		byte[] ssrcbytes = binarySplitter(Ssrc);
		for(int i = 0; i < Math.min(ssrcbytes.length, 4); i++) {
			header[8+3-i] = ssrcbytes[(ssrcbytes.length-1) - i];
		}
		// fill the payload bitstream:
		// --------------------------
		payload_size = data_length;
		payload = new byte[data_length];

		// fill payload array of byte from data (given in parameter of the constructor)
		// ......
		for(int i = 0; i < data_length; i++) {
			payload[i] = data[i];
		}

		// ! Do not forget to uncomment method printheader() below !
		printheader();
	}

	// --------------------------
	// Constructor of an RTPpacket object from the packet bistream
	// --------------------------
	public RTPpacket(byte[] packet, int packet_size) {
		// fill default fields:
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// check if total packet size is lower than the header size
		if (packet_size >= HEADER_SIZE) {
			// get the header bitsream:
			header = new byte[HEADER_SIZE];
			for (int i = 0; i < HEADER_SIZE; i++)
				header[i] = packet[i];

			// get the payload bitstream:
			payload_size = packet_size - HEADER_SIZE;
			payload = new byte[payload_size];
			for (int i = HEADER_SIZE; i < packet_size; i++)
				payload[i - HEADER_SIZE] = packet[i];

			// interpret the changing fields of the header:
			PayloadType = header[1] & 127;
			SequenceNumber = unsigned_int(header[3]) + 256 * unsigned_int(header[2]);
			TimeStamp = unsigned_int(header[7]) + 256 * unsigned_int(header[6]) + 65536 * unsigned_int(header[5])
					+ 16777216 * unsigned_int(header[4]);
		}
	}

	// --------------------------
	// getpayload: return the payload bistream of the RTPpacket and its size
	// --------------------------
	public int getpayload(byte[] data) {

		for (int i = 0; i < payload_size; i++)
			data[i] = payload[i];

		return (payload_size);
	}

	// --------------------------
	// getpayload_length: return the length of the payload
	// --------------------------
	public int getpayload_length() {
		return (payload_size);
	}

	// --------------------------
	// getlength: return the total length of the RTP packet
	// --------------------------
	public int getlength() {
		return (payload_size + HEADER_SIZE);
	}

	// --------------------------
	// getpacket: returns the packet bitstream and its length
	// --------------------------
	public int getpacket(byte[] packet) {
		// construct the packet = header + payload
		for (int i = 0; i < HEADER_SIZE; i++)
			packet[i] = header[i];
		for (int i = 0; i < payload_size; i++)
			packet[i + HEADER_SIZE] = payload[i];

		// return total size of the packet
		return (payload_size + HEADER_SIZE);
	}

	// --------------------------
	// gettimestamp
	// --------------------------

	public int gettimestamp() {
		return (TimeStamp);
	}

	// --------------------------
	// getsequencenumber
	// --------------------------
	public int getsequencenumber() {
		return (SequenceNumber);
	}

	// --------------------------
	// getpayloadtype
	// --------------------------
	public int getpayloadtype() {
		return (PayloadType);
	}

	// --------------------------
	// print headers without the SSRC
	// --------------------------
	public void printheader() {
		// TO DO: uncomment
		System.out.print("[RTP-Header] ");
		System.out.println("Version: " + Version + ", Padding: " + Padding + ", Extension: " + Extension + ", CC: " + CC
				+ ", Marker: " + Marker + ", PayloadType: " + PayloadType + ", SequenceNumber: " + SequenceNumber
				+ ", TimeStamp: " + TimeStamp);
	}

	// return the unsigned value of 8-bit integer nb
	static int unsigned_int(int nb) {
		if (nb >= 0)
			return (nb);
		else
			return (256 + nb);
	}

}
