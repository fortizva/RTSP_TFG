package com.fortizva.rtp;

/**
 * StreamStats class holds statistics for a media stream, including
 * received bytes, packet loss, delay, jitter, and video-specific metrics.
 */
public class StreamStats {
	/** Total number of bytes received in the stream. */
    public int receivedBytes = 0;
    /** Total number of packets received in the stream. */
    public int receivedPackets = 0;
    /** Initial packet number received in the stream. */
    public int initialPacketNb = 0;
    /** Last packet number received in the stream. */
    public int lastPacketNb = 0;
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
    
    // Used for video streams only
    /** Current frames per second (FPS). */
    public double currentFps = 0.0;
    /** Number of frames received in the current second. */
    public int framesSinceUpdate = 0;
    /** Last time the FPS was updated in milliseconds since epoch. */
    public long lastFpsUpdateTime = 0L;
}