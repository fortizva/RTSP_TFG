package com.fortizva.packets;

public class CommonPacketValues {

	// Header and FEC sizes
	public static final int RTP_HEADER_SIZE = 12; // Size of the RTP header in bytes
	public static final int FEC_HEADER_SIZE = 10; // Size of the FEC header in bytes
	public static final int FEC_LEVEL_HEADER_SIZE = 4;
	
	// RTP packet constants
	public static final int RTP_VERSION = 2; // RTP version
	public static final int RTP_PADDING = 0; // Padding flag
	public static final int RTP_EXTENSION = 0; // Extension flag
	public static final int RTP_CC = 0; // Contributing sources count
	public static final int RTP_MARKER = 0; // Marker bit
	public static final int RTP_SSRC = 0; // Synchronization source identifier
	
	// FEC packet constants
	public static final int FEC_PTYPE = 0xBE; // Payload type for FEC packets
	public static final int FEC_E = 0; // FEC packet extension flag
	public static final int FEC_L = 0; // FEC packet long-mask 

	// Timers
	public static final int FRAME_PERIOD = 20; //Frame period of the video to stream, in ms
	public static final int AUDIO_FRAME_PERIOD = 10; //Frame period of the audio to stream, in ms (See Codec.java for more details)
}
