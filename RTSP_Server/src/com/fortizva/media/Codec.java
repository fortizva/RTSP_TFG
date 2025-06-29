package com.fortizva.media;
//VideoStream

import java.io.*;
import java.nio.ByteBuffer;

public class Codec {

	FileInputStream fis; // video file
	int frame_nb; // current frame nb

	byte Version;
	byte fps;
	int numFrames;
	int width;
	int heigh;
	byte nAudioTracks;
	int SamplingRate;
	byte bitDepth;
	byte channelCount;

	private boolean nextFrameIsAudio = false; // Flag to indicate if the next frame is audio data

	// -----------------------------------
	// constructor
	// -----------------------------------
	public Codec(String filename) throws Exception {

		// init variables
		fis = new FileInputStream(filename);
		frame_nb = 0;
		this.readHeader();
		this.nextFrameIsAudio = false; // Initialize the flag to false

	}

	public void readHeader() throws Exception {

		byte[] _Version = new byte[1];
		byte[] _fps = new byte[1];
		byte[] _numFrames = new byte[4];
		byte[] _width = new byte[4];
		byte[] _heigh = new byte[4];
		byte[] _nAudioTracks = new byte[1];
		byte[] _SamplingRate = new byte[4];
		byte[] _bitDepth = new byte[1];
		byte[] _channelCount = new byte[1];

		fis.read(_Version, 0, 1);
		Version = _Version[0];

		fis.read(_fps, 0, 1);
		fps = _fps[0];

		fis.read(_numFrames, 0, 4);
		numFrames = this.ByteArraytoInt(_numFrames);

		fis.read(_width, 0, 4);
		width = this.ByteArraytoInt(_width);

		fis.read(_heigh, 0, 4);
		heigh = this.ByteArraytoInt(_heigh);

		fis.read(_nAudioTracks, 0, 1);
		nAudioTracks = _nAudioTracks[0];

		fis.read(_SamplingRate, 0, 4);
		SamplingRate = this.ByteArraytoInt(_SamplingRate);

		fis.read(_bitDepth, 0, 1);
		bitDepth = _bitDepth[0];

		fis.read(_channelCount, 0, 1);
		channelCount = _channelCount[0];

	}

	private int ByteArraytoInt(byte[] i) {
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
		bb.put(i);
		bb.rewind();
		return bb.getInt();
	}

	/**
	 * Reads the next frame from the video file (skipping audio data if necessary).
	 * 
	 * @param frame byte array to store the frame data
	 * @return the number of bytes read into the frame array
	 * @throws Exception if an error occurs while reading the file
	 */
	public int getnextframe(byte[] frame) throws Exception {
		// Check if the next frame is audio data
		if (nextFrameIsAudio) {
			// If it is audio data, skip to video data
			skipAudioData();
		}
		
		int length = 0;
		String length_string;
		byte[] frame_length = new byte[5];

		// read current frame length
		fis.read(frame_length, 0, 5);

		// transform frame_length to integer
		length_string = new String(frame_length);
		length = Integer.parseInt(length_string);
		
		nextFrameIsAudio = true; // Set the flag for the next frame
		return (fis.read(frame, 0, length));
	}

	/**
	 * Returns the next audio chunk of data (skipping video data if necessary).
	 * 
	 * @param frame byte array to store the audio data
	 * @return the number of bytes read into the frame
	 * @throws Exception if an error occurs while reading the file
	 */
	public int getnextchunk(byte[] frame) throws Exception {
		// Check if the next frame is audio data
		if (!nextFrameIsAudio) {
			// If it is video data, skip to audio data
			skipVideoData();
		}

		// Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/10 (1 packet in
		// 0,1 sec)
		int length = (int) (this.SamplingRate * (this.bitDepth / 8.0) * this.channelCount / this.fps);

		// returns the length of data copied in buffer
		int count = fis.read(frame, 0, length);

		nextFrameIsAudio = false; // Reset the flag for the next frame
		return (count);
	}

	/**
	 * Skips audio data to the next frame
	 * 
	 * @throws IOException
	 */
	private void skipAudioData() throws IOException {
		// Calculate the size of the audio data for one frame
		int audioDataSize = (int) (this.SamplingRate * (this.bitDepth / 8.0) * this.channelCount / this.fps);
		// Skip the audio data
		fis.skip(audioDataSize);
	}

	/**
	 * Skips video data to the next frame
	 * 
	 * @throws IOException
	 */
	private void skipVideoData() throws IOException {
		// Calculate the size of the video data for one frame
		int length = 0;
		String length_string;
		byte[] frame_length = new byte[5];

		// read current frame length
		fis.read(frame_length, 0, 5);

		// transform frame_length to integer
		length_string = new String(frame_length);
		length = Integer.parseInt(length_string);

		// Skip the video data
		fis.skip(length);
	}

	/**
	 * @return the version
	 */
	public byte getVersion() {
		return Version;
	}

	/**
	 * @return the fps
	 */
	public byte getFPS() {
		return fps;
	}

	/**
	 * @return the numFrames
	 */
	public int getNumFrames() {
		return numFrames;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @return the heigh
	 */
	public int getHeigh() {
		return heigh;
	}

	/**
	 * @return the nAudioTracks
	 */
	public byte getnAudioTracks() {
		return nAudioTracks;
	}

	/**
	 * @return the samplingRate
	 */
	public int getSamplingRate() {
		return SamplingRate;
	}

	/**
	 * @return the bitDepth
	 */
	public byte getBitDepth() {
		return bitDepth;
	}

	/**
	 * @return the channelCount
	 */
	public byte getChannelCount() {
		return channelCount;
	}

	/**
	 * @return the nextFrameIsAudio
	 */
	public boolean isNextFrameAudio() {
		return nextFrameIsAudio;
	}

	public void close() throws IOException {
		if (fis != null) {
			fis.close();
		}
	}
}
