package com.fortizva.rtp;
/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import com.fortizva.media.Codec;
import com.fortizva.packets.CommonPacketValues;
import com.fortizva.packets.RTPpacket;

public class Server extends JFrame {

	private static final long serialVersionUID = 1L;

	// Verbose mode
	boolean verbose = false;

	// RTP variables:
	// ----------------
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
	DatagramPacket vsenddp; // UDP packet containing the video frames
	DatagramPacket asenddp; // UDP packet containing the audio frames

	InetAddress ClientIPAddr; // Client IP address
	int RTP_dest_port = 0; // destination port for RTP packets (given by the RTSP Client)

	// GUI:
	// ----------------
	JLabel label;

	class UpdateLabel implements Runnable {
		public void run() {
			label.setText("Send frame #" + imagenb + "\nSend audio chunk #" + audionb);
		}
	}

	// Video & audio variables
	// ----------------
	Codec videoCodec;
	Codec audioCodec;

	// Video variables:
	// ----------------
	Thread videoThread; // Thread to handle video processing
	int imagenb = 0; // image nb of the image currently transmitted
	int videoSkips = 0; // Number of video skips (non-video frames)
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	int FRAME_PERIOD = 40; // Frame period of the video to stream, in ms
	int VIDEO_LENGTH; // length of the video in frames
	byte[] vBuf; // buffer used to store the images to send to the client

	// Audio variables
	Thread audioThread; // Thread to handle audio processing
	int audionb = 0; // audio chunk nb of the audio currently transmitted
	int audioSkips = 0; // Number of audio skips (non-audio frames)
	static int RAW_TYPE = 0;
	byte[] aBuf; // buffer used to store the chunks to send to the client

	// Thread handling
	private volatile boolean running = false; // Flag to control the running state of the threads
	private volatile boolean paused = false; // Flag to control the pause state of the threads
	private final Object pauseLock = new Object(); // Lock for thread synchronization

	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	// rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;

	static int state; // RTSP Server state == INIT or READY or PLAY
	Socket RTSPsocket; // socket used to send/receive RTSP messages
	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file requested from the client
	static int RTSP_ID = 123456; // ID of the RTSP session
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";

	// --------------------------------
	// Constructor
	// --------------------------------
	public Server() {

		// init Frame
		super("Server");
		// allocate memory for the sending buffers
		vBuf = new byte[15000]; // buffer for video frames
		aBuf = new byte[15000]; // buffer for audio frames

		// Handler to close the main window
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				// stop the timer, close sockets and streams and exit
				cleanExit();
			}
		});

		// GUI:
		setBounds(0, 375, 390, 60);
		setPreferredSize(new Dimension(390, 60));
		// set the label
		label = new JLabel("Send frame #        ", JLabel.LEFT);
		getContentPane().add(label, BorderLayout.CENTER);
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {
		// create a Server object
		Server theServer = new Server();

		// check the number of arguments// check for verbose
		if (argv.length >= 2 && argv[1].equals("-v")) {
			theServer.verbose = true;
			System.out.println("Verbose mode: ACTIVE");
		}

		// show GUI:
		theServer.pack();
		theServer.setVisible(true);
		// get RTSP socket port from the command line
		int RTSPport = Integer.parseInt(argv[0]);

		// Initiate TCP connection with the client for the RTSP session
		ServerSocket listenSocket = new ServerSocket(RTSPport);
		theServer.RTSPsocket = listenSocket.accept();
		listenSocket.close();

		// Get Client IP address
		theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

		// Initiate RTSPstate
		state = INIT;
		// Set input and output stream filters:
		BufferedReader reader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()));
		RTSPBufferedReader = reader;
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()));
		RTSPBufferedWriter = writer;

		// Wait for the SETUP message from the client
		int request_type;
		boolean done = false;
		while (!done) {
			request_type = theServer.parse_RTSP_request(); // blocking

			if (request_type == SETUP) {
				done = true;

				// update RTSP state
				state = READY;
				System.out.println("New RTSP state: READY\n");

				// Send response
				theServer.send_RTSP_response();

				// Initialize video
				theServer.videoCodec = new Codec(VideoFileName);

				// Init video properties
				theServer.VIDEO_LENGTH = theServer.videoCodec.getNumFrames();
				theServer.FRAME_PERIOD = (int) (1000 / theServer.videoCodec.getFPS());
				theServer.videoThread = new Thread(theServer.new VideoSender());
				if (!theServer.verbose)
					System.out.println("DEBUG: FPS: " + theServer.videoCodec.getFPS() + " FRAME_PERIOD: "
							+ theServer.FRAME_PERIOD);

				// Add a frame per second to send FEC packets
				// theServer.FRAME_PERIOD = 1000 / (theServer.codec.getFPS()+1);
				// theServer.timer.setDelay(theServer.FRAME_PERIOD);
				if (theServer.verbose)
					System.out.println("DEBUG: Adding an aditional frame per second to send FEC packets: FRAME_PERIOD="
							+ theServer.FRAME_PERIOD);

				// Init audio properties
				theServer.audioCodec = new Codec(VideoFileName); // Use different codec for audio to read audio data
																	// separately
				theServer.audioThread = new Thread(theServer.new AudioSender());
				if (!theServer.verbose)
					System.out.println("DEBUG: AUDIO_FRAME_PERIOD: " + CommonPacketValues.AUDIO_FRAME_PERIOD);

				// init RTP socket
				theServer.RTPsocket = new DatagramSocket();
			}
		}

		// loop to handle RTSP requests
		while (true) {
			// parse the request
			request_type = theServer.parse_RTSP_request(); // blocking

			if ((request_type == PLAY) && (state == READY)) {
				// send back response
				theServer.send_RTSP_response();
				
				// Restart the video and audio threads if they were paused
				theServer.running = true; // Set running flag to true to start threads
				theServer.paused = false; // Ensure paused is false when starting playback
				synchronized (theServer.pauseLock) {
					theServer.pauseLock.notifyAll(); // Notify any paused threads to continue
				}

				// Create the threads if they don't exist anymore
				if (!theServer.videoThread.isAlive() || !theServer.audioThread.isAlive()) {
					theServer.videoThread = new Thread(theServer.new VideoSender());
					theServer.audioThread = new Thread(theServer.new AudioSender());
					theServer.videoThread.start();
					theServer.audioThread.start();
				}

				// update state
				state = PLAYING;
				System.out.println("New RTSP state: PLAYING");
			} else if ((request_type == PAUSE) && (state == PLAYING)) {
				// send back response
				theServer.send_RTSP_response();

				// Pause the video and audio threads
				theServer.paused = true; // Set paused flag to true to pause threads

				// update state
				state = READY;
				System.out.println("New RTSP state: READY");
			} else if (request_type == TEARDOWN) {
				// send back response
				theServer.send_RTSP_response();

				// Clean exit
				theServer.cleanExit();
			}
		}
	}

	// ------------------------
	// Handler for video thread
	// ------------------------
	class VideoSender implements Runnable {
		public void run() {
			// if the current image nb is less than the length of the video keep going
			while (running && imagenb < VIDEO_LENGTH) {
				synchronized (pauseLock) {
					while (paused && running) {
						try {
							pauseLock.wait(); // Wait until the pause is lifted
						} catch (InterruptedException e) {
							if (verbose)
								System.out.println("DEBUG: VideoSender interrupted while paused.");
						}
					}
				}
				if(!running) break; // Exit if running is false
				try {
					// --- Send video frame ---
					// update current imagenb
					imagenb++; // Increment video frame number (Counted separately for GUI purposes)
					videoSkips += (videoCodec.isNextFrameAudio()) ? 1 : 0; // Count adds if the next frame is not video
					int video_length = videoCodec.getnextframe(vBuf);
					RTPpacket video_packet = new RTPpacket(MJPEG_TYPE, (imagenb + videoSkips),
							(imagenb + videoSkips) * FRAME_PERIOD, vBuf, video_length);
					byte[] video_bits = new byte[video_packet.getlength()];
					video_packet.getpacket(video_bits);
					vsenddp = new DatagramPacket(video_bits, video_bits.length, ClientIPAddr, RTP_dest_port);
					// DEBUG: Add random lost packets
					// if((Math.random()*100d) < 95)
					RTPsocket.send(vsenddp);
					// print the header bitstream
					if (verbose)
						video_packet.printheader();
					// update GUI
					SwingUtilities.invokeLater(new UpdateLabel());
					// Sleep for the video frame period
					Thread.sleep(FRAME_PERIOD);
				} catch (Exception ex) {
					if (verbose)
						System.out.println("DEBUG: VIDEOactionPerformed()");
					System.out.println("Exception caught: " + ex);
					cleanExit();
				}
			}
		}
	}

	// ------------------------
	// Handler for audio thread
	// ------------------------
	class AudioSender implements Runnable {
		public void run() {
			// if the current audionb is less than the length of the video keep going
			while (running && (audionb + audioSkips) < VIDEO_LENGTH) {
				synchronized (pauseLock) {
					while (paused && running) {
						try {
							pauseLock.wait(); // Wait until the pause is lifted
						} catch (InterruptedException e) {
							if (verbose)
								System.out.println("DEBUG: VideoSender interrupted while paused.");
						}
					}
				}
				if(!running) break; // Exit if running is false
				try {
					// --- Send audio chunk ---
					// update current audionb
					audionb++; // Increment audio chunk number (Counted separately for GUI purposes)
					audioSkips += (audioCodec.isNextFrameAudio()) ? 0 : 1; // Count adds if the next frame is not audio
					int audio_length = audioCodec.getnextchunk(aBuf);
					RTPpacket audio_packet = new RTPpacket(RAW_TYPE, (audionb + audioSkips),
							(audionb + audioSkips) * FRAME_PERIOD, aBuf, audio_length);
					byte[] audio_bits = new byte[audio_packet.getlength()];
					audio_packet.getpacket(audio_bits);
					asenddp = new DatagramPacket(audio_bits, audio_bits.length, ClientIPAddr, RTP_dest_port);

					// DEBUG: Add random lost packets
					// if((Math.random()*100d) < 95)
					RTPsocket.send(asenddp);

					// print the header bitstream
					if (verbose)
						audio_packet.printheader();
					// update GUI
					SwingUtilities.invokeLater(new UpdateLabel());
					// Sleep for the audio frame period
					Thread.sleep(CommonPacketValues.AUDIO_FRAME_PERIOD);
				} catch (Exception ex) {
					if (verbose)
						System.out.println("DEBUG: AUDIOactionPerformed()");
					System.out.println("Exception caught: " + ex);
					cleanExit();
				}
			}
		}
	}

	// ------------------------------------
	// Parse RTSP Request
	// ------------------------------------
	private int parse_RTSP_request() {
		int request_type = -1;
		try {
			// parse request line and extract the request_type:
			String RequestLine = RTSPBufferedReader.readLine();
			System.out.println("RTSP Server - Received from Client:");
			System.out.println("\t" + RequestLine);

			StringTokenizer tokens = new StringTokenizer(RequestLine);
			String request_type_string = tokens.nextToken();

			// convert to request_type structure:
			if ((new String(request_type_string)).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((new String(request_type_string)).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;

			if (request_type == SETUP) {
				// extract VideoFileName from RequestLine
				VideoFileName = tokens.nextToken();
			}

			// parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println("\t" + SeqNumLine);
			tokens = new StringTokenizer(SeqNumLine);
			tokens.nextToken();
			RTSPSeqNb = Integer.parseInt(tokens.nextToken());

			// get LastLine
			String LastLine = RTSPBufferedReader.readLine();
			System.out.println("\t" + LastLine);

			if (request_type == SETUP) {
				// extract RTP_dest_port from LastLine
				tokens = new StringTokenizer(LastLine);
				for (int i = 0; i < 3; i++)
					tokens.nextToken(); // skip unused stuff
				RTP_dest_port = Integer.parseInt(tokens.nextToken());
			}
			// else LastLine will be the SessionId line ... do not check for now.
		} catch (Exception ex) {
			if (verbose)
				System.out.println("DEBUG: parse_RTSP_request()");
			System.out.println("Exception caught: " + ex);
			cleanExit();
		}
		System.out.println();
		return (request_type);
	}

	// ------------------------------------
	// Send RTSP Response
	// ------------------------------------
	private void send_RTSP_response() {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			RTSPBufferedWriter.flush();
			// System.out.println("RTSP Server - Sent response to Client.");
		} catch (Exception ex) {
			if (verbose)
				System.out.println("DEBUG: send_RTSP_response()");
			System.out.println("Exception caught: " + ex);
			cleanExit();
		}
	}

	// Cleanup method
	private void cleanExit() {
		try {
			// Stop the threads
			running = false; // Stop the video and audio threads
			paused = false; // Ensure paused is false to stop any waiting threads
			synchronized (pauseLock) {
				pauseLock.notifyAll(); // Notify any paused threads to continue
			}
			if (videoThread != null && videoThread.isAlive()) {
				// Wait the thread to finish if timeout then interrupt it
				try {
					videoThread.join(100); // Wait for 100 miliseconds for the video thread to finish
				} catch (InterruptedException e) {
					if (verbose)
						System.out.println("DEBUG: VideoSender interrupted during join.");
				}
			}
			if (audioThread != null && audioThread.isAlive()) {
				// Wait the thread to finish if timeout then interrupt it
				try {
					audioThread.join(100); // Wait for 100 miliseconds for the audio thread to finish
				} catch (InterruptedException e) {
					if (verbose)
						System.out.println("DEBUG: AudioSender interrupted during join.");
				}
			}
			// Close sockets
			if (RTSPsocket != null) {
				RTSPsocket.close();
			}
			if (RTPsocket != null) {
				RTPsocket.close();
			}
			// Close input and output stream filters
			if (RTSPBufferedReader != null) {
				RTSPBufferedReader.close();
			}
			if (RTSPBufferedWriter != null) {
				RTSPBufferedWriter.close();
			}
			// Close codecs
			if (videoCodec != null) {
				videoCodec.close();
			}
			if (audioCodec != null) {
				audioCodec.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// Exit the program
			System.exit(0);
		}
	}
}
