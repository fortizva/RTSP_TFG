package com.fortizva.packets;

public class CommonValues {

	// Header and FEC sizes
	public static final int RTP_HEADER_SIZE = 12; // Size of the RTP header in bytes
	public static final int FEC_HEADER_SIZE = 10; // Size of the FEC header in bytes
	public static final int FEC_LEVEL_HEADER_SIZE = 4;
	
	// RTP packet constants
	public static final int RTP_RCV_PORT = 25000; // Port for sending and receiving RTP packets
	public static final int RTP_VERSION = 2; // RTP version
	public static final int RTP_PADDING = 0; // Padding flag
	public static final int RTP_EXTENSION = 0; // Extension flag
	public static final int RTP_CC = 0; // Contributing sources count
	public static final int RTP_MARKER = 0; // Marker bit
	public static final int RTP_SSRC = 0; // Synchronization source identifier
	
	// FEC packet constants
	public static final int FEC_PTYPE = 116; // Payload type for FEC packets
	public static final int FEC_E = 0; // FEC packet extension flag
	public static final int FEC_L = 0; // FEC packet long-mask 
	public static final int FEC_FREQUENCY = 5; // Frequency that the FEC packet is sent at (in video packets)

	// Timers
	public static final int STREAMING_FRAME_PERIOD = 35; // Frame period of the video to stream, in ms
	public static final int PLAYBACK_FRAME_PERIOD = 40; // Frame period of the video to playback, in ms
	public static final int STREAMING_AUDIO_FRAME_PERIOD = 40; // Frame period of the audio to stream, in ms (See Codec.java for more details)
	public static final int PLAYBACK_AUDIO_FRAME_PERIOD = 40; //Frame period of the audio to stream, in ms (See Codec.java for more details)
	
	// Types
	public static final int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	public static final int RAW_TYPE = 0; // RTP payload type for raw video

	// Misc
	public static final String CRLF = "\r\n";
}
