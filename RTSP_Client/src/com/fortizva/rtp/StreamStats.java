package com.fortizva.rtp;

/**
 * StreamStats class holds statistics for a media stream, including
 * received bytes, packet loss, delay, jitter, and video-specific metrics.
 */
public class StreamStats {
	// Concurrency locks
	public final Object bufferLock = new Object();
	public final Object fecLock = new Object();
	public final Object protectionLock = new Object();
	
	/** Total number of bytes received in the stream. */
    public int receivedBytes = 0;
    /** Total number of packets received in the stream. */
    public int receivedPackets = 0;
    /** Initial packet number received in the stream. */
    public int initialPacketNb = 0;
    /** Last packet number received in the stream. */
    public int lastReceivedPacketNb = 0;
    /** Last played packet number in the stream. */
    public int lastPlayedPacketNb = -1;
    /** Total number of packets lost in the stream. */
    public int lostPackets = 0;
    /** Packet loss percentage. */
    public int packetLoss = 0;
    /** Packet delay in milliseconds. */
    public long packetDelay = 0L;
    /** Last packet time in milliseconds since epoch. */
    public long lastPacketTime = 0L;
    /** Last packet delay in milliseconds. */
    public long lastPacketDelay = 0L;
    /** Jitter in milliseconds. */
    public long jitter = 0L;
    /** Size of the buffer in packets. */
    public int bufferSize = 0;
    /** Number of recovered packets that were received late. */
    public int latePackets = 0;
    /** Sequence number expected by the player. Forces the player to play at a specific frequency. */
    public int expectedPacketNb = -1;
    
    // Used for video streams only
    /** [Video] Current frames per second (FPS). */
    public double currentFps = 0.0;
    /** [Video] Number of frames received in the current second. */
    public int framesSinceUpdate = 0;
    /** [Video] Last time the FPS was updated in milliseconds since epoch. */
    public long lastFpsUpdateTime = 0L;
   /** [Video] Number of successfully recovered packets using FEC. */
    public int recoveredPackets = 0;
    /** [Video] Size of the FEC buffer in packets. */
    public int fecBufferSize = 0;
    /** [Video] Size of the FEC protection buffer in packets. */
    public int protectionBufferSize = 0;
}