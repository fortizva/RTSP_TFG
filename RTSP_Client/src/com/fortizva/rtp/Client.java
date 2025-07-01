package com.fortizva.rtp;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.StringTokenizer;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import com.fortizva.packets.CommonValues;
import com.fortizva.packets.RTPpacket;

/**
 * Usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
 */
public class Client {

	// GUI
	// ----
	JFrame f = new JFrame("Client");
	JButton setupButton = new JButton("Setup");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton tearButton = new JButton("Teardown");
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;

	JFrame stats = new JFrame("Stats");
	JPanel statsPanel = new JPanel();
	JLabel statsLabel = new JLabel("Sample Text");

	// Stats
	// ---
	static int received_bytes = 0;
	static int last_packet_nb = 0;
	static int last_video_nb = 0;
	static int lost_packets = 0;
	static int packet_loss = 0;
	// Retardo y jitter
	static long packet_delay = 0l;
	static long last_packet_time = 0l;
	static long last_packet_delay = 0l;
	static long jitter = 0l;
	// FPS
	long last_fps_update_time = 0;
	int last_fps_video_nb = 0;
	double current_fps = 0;
	// ----

	// DEBUG
	// ----
	static boolean verbose = false;

	// Threading
	// ----------------
	private volatile boolean running = false; // Flag to indicate if the client is running
	private volatile boolean paused = false; // Flag to indicate if the client is paused
	private final Object pauseLock = new Object(); // Lock object to synchronize pause and resume

	Thread rtpSocketListener; // thread used to receive data from the UDP socket
	Thread videoThread; // thread used to process video frames
	Thread audioThread; // thread used to process audio frames
	// ----------------

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets

	// RTP packet buffer
	final static int BUFFER_TIMEOUT = 60; // Timeout for the buffer
	PriorityBlockingQueue<RTPpacket> videoBuffer;
	PriorityBlockingQueue<RTPpacket> audioBuffer;

	byte[] buf; // buffer used to store data received from the server

	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	static int state; // RTSP state == INIT or READY or PLAYING
	
	Socket RTSPsocket; // socket used to send/receive RTSP messages
	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file to request to the server
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
	int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

	// Audio variables
	SourceDataLine speaker; // Audio datasource to speaker

	/**
	 * Constructor of Client class. Initializes the GUI and sets up the event listeners.
	 */
	public Client() {

		// Build GUI

		// Frame
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				cleanExit();
			}
		});

		// Buttons
		buttonPanel.setLayout(new GridLayout(1, 0));
		buttonPanel.add(setupButton);
		buttonPanel.add(playButton);
		buttonPanel.add(pauseButton);
		buttonPanel.add(tearButton);
		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		tearButton.addActionListener(new tearButtonListener());

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(buttonPanel);
		iconLabel.setBounds(0, 0, 380, 280);
		buttonPanel.setBounds(0, 280, 380, 50);

		f.getContentPane().add(mainPanel, BorderLayout.CENTER);
		f.setSize(new Dimension(390, 370));
		f.setVisible(true);

		// stats layout
		statsPanel.setLayout(null);
		statsLabel.setText(getStats());
		statsLabel.setBounds(0, 0, 140, 370);
		statsLabel.setVerticalAlignment(SwingConstants.TOP);
		statsLabel.setHorizontalAlignment(SwingConstants.LEFT);
		statsLabel.setVisible(true);
		statsPanel.add(statsLabel);
		stats.getContentPane().add(statsPanel, BorderLayout.CENTER);
		// stats.setSize(new Dimension(150, 220));
		stats.setBounds(400, 0, 180, 370);
		stats.setVisible(true);

		// allocate enough memory for the buffer used to receive data from the server
		buf = new byte[15000];
		videoBuffer = new PriorityBlockingQueue<RTPpacket>(1000);
		audioBuffer = new PriorityBlockingQueue<RTPpacket>(1000);

		// Init RTP socket listener thread
		rtpSocketListener = new Thread(new RTPSocketListener());

		// Set the thread as a daemon so it will not block the application from exiting
		rtpSocketListener.setDaemon(true);

		videoThread = new Thread(new videoTimerListener());
		audioThread = new Thread(new audioTimerListener());
		videoThread.setDaemon(true);
		audioThread.setDaemon(true);
			
	}

	// ------------------------------------
	// Soundcard initialization
	// ------------------------------------
	private void audio_initialization() throws LineUnavailableException {

		// specifying the audio format

		AudioFormat af = new AudioFormat(44100, 16, 1, true, false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
		speaker = (SourceDataLine) AudioSystem.getLine(info);

		speaker.open(af);
		speaker.start();

	}

	// -----------------------------------
	// Print stats
	// -----------------------------------
	public String getStats() {
		String s = new String("<html><br>Stream statistics:</br>");
		s += "<br>Video file: " + VideoFileName + "</br>";
		s += String.format("<br>FPS: %.2f</br>", current_fps);
		s += "<br>Last packet #: " + last_packet_nb + "</br>";
		s += "<br>Lost packets: " + lost_packets + "</br>";
		s += "<br>Packet loss: " + packet_loss + "%</br>";
		s += "<br>Packet delay (milis): " + packet_delay + "</br>";
		s += "<br>Received bytes: " + received_bytes + "</br>";
		s += "<br>Jitter (milis): " + ((jitter >= 0) ? "+" : "") + jitter + "</br>";
		s += "</html>";
		return s;
	}

	class UpdateStats implements Runnable {
		public void run() {
			statsLabel.setText(getStats());
		}
	}

	/**
	 * Main method to start the client application.
	 * 
	 * @param argv Command line arguments:
	 * 	[Server hostname] [Server RTSP port] [Video file requested] [-v (optional for verbose mode)]
	 */
	public static void main(String argv[]) throws Exception {
		// Create a Client object
		Client theClient = new Client();

		// get server RTSP port and IP address from the command line
		// ------------------
		int RTSP_server_port = Integer.parseInt(argv[1]);
		String ServerHost = argv[0];
		InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

		// get video filename to request:
		VideoFileName = argv[2];

		// check for verbose
		if (argv.length >= 4 && argv[3].equals("-v")) {
			verbose = true;
			System.out.println("Verbose mode: ACTIVE");
		}

		// Initialize soundcard
		theClient.audio_initialization();

		// Establish a TCP connection with the server to exchange RTSP messages
		// ------------------
		theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

		// Set input and output stream filters:
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

		// init RTSP state:
		state = INIT;
	}

	// ------------------------------------
	// Handler for buttons
	// ------------------------------------

	/**
	 * Handler for SETUP button
	 */
	class setupButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Setup Button pressed !");

			if (state == INIT) {
				// Init non-blocking RTPsocket that will be used to receive data
				try {
					// construct a new DatagramSocket to receive RTP packets from the server, on
					// port CommonPacketValues.RTP_RCV_PORT
					RTPsocket = new DatagramSocket(CommonValues.RTP_RCV_PORT);
					// set TimeOut value of the socket to 5msec.
					RTPsocket.setSoTimeout(5000);

				} catch (SocketException se) {
					System.out.println("Socket exception: " + se);
					cleanExit();
				}

				// init RTSP sequence number
				RTSPSeqNb = 1;

				// Send SETUP message to the server
				send_RTSP_request("SETUP");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print new state
					// state = ....
					state = READY;
					if (verbose)
						System.out.println("New RTSP state: READY (" + state + ")");
				}
			} // else if state != INIT then do nothing
		}
	}

	/**
	 *  Handler for PLAY button
	 */
	class playButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Play Button pressed !");

			if (state == READY) {
				// increase RTSP sequence number
				RTSPSeqNb++;

				// Send PLAY message to the server
				send_RTSP_request("PLAY");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					state = Client.PLAYING;
					if (verbose)
						System.out.println("New RTSP state: PLAYING (" + state + ")");

					running = true; // Set running flag to true
					paused = false; // Set paused flag to false
					synchronized (pauseLock) {
						pauseLock.notifyAll(); // Notify any waiting threads to continue
					}

					// Start the video, audio and RTP threads
					if (!videoThread.isAlive()) {
						videoThread.start(); // start the video timer thread
					}
					if (!audioThread.isAlive()) {
						audioThread.start(); // start the audio timer thread
					}
					if (!rtpSocketListener.isAlive()) {
						rtpSocketListener.start(); // start the RTP socket listener thread
					}
				}
			} // else if state != READY then do nothing
		}
	}

	/**
	 *  Handler for PAUSE button
	 */
	class pauseButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Pause Button pressed !");

			if (state == PLAYING) {
				// increase RTSP sequence number
				RTSPSeqNb++;

				// Send PAUSE message to the server
				send_RTSP_request("PAUSE");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					state = Client.READY;
					if (verbose)
						System.out.println("New RTSP state: READY (" + state + ")");

					paused = true; // Set paused flag to true

					// RTPSocketListener should pause by itself on state change
				}
			}
			// else if state != PLAYING then do nothing
		}
	}

	/**
	 *  Handler for TEARDOWN button
	 */
	class tearButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Teardown Button pressed !");

			// increase RTSP sequence number
			RTSPSeqNb++;

			// Send TEARDOWN message to the server
			send_RTSP_request("TEARDOWN");

			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// change RTSP state and print out new state
				state = Client.INIT;
				if (verbose)
					System.out.println("New RTSP state: INIT(" + state + ")");

				running = false; // Set running flag to false
				paused = false; // Set paused flag to false
				synchronized (pauseLock) {
					pauseLock.notifyAll(); // Notify any waiting threads to stop
				}
				// Stop the video, audio and RTP threads
				if (videoThread != null && videoThread.isAlive()) {
					videoThread.interrupt(); // Interrupt the video thread
				}
				if (audioThread != null && audioThread.isAlive()) {
					audioThread.interrupt(); // Interrupt the audio thread
				}
				// RTPSocketListener should stop by itself on state change

				// exit
				cleanExit();
			}
		}
	}

	/**
	 * RTPSocketListener class
	 * <br>
	 * Thread to listen for incoming RTP packets on the RTP socket.
	 * Queues packets and updates stats.
	 */
	class RTPSocketListener implements Runnable {
		public void run() {
			// Keep listening to the RTP socket until the state is INIT or the thread is
			// interrupted (End of program)
			while (state != Client.INIT && !Thread.currentThread().isInterrupted()) {
				// If player is paused ignore timeouts and keep waiting for the state to change
				if (state == Client.PLAYING) {
					// Construct a DatagramPacket to receive data from the UDP socket
					rcvdp = new DatagramPacket(buf, buf.length);
					try {
						RTPsocket.receive(rcvdp); // Block until a packet is received
						// create an RTPpacket object from the DP
						RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
						// print important header fields of the RTP packet received:
						if (verbose) {
							System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getSequenceNumber()
									+ " TimeStamp "
									+ rtp_packet.getTimeStamp() + " ms, of type " + rtp_packet.getPayloadType());

							// print header bitstream:
							rtp_packet.printHeader();
						}

						// -----------------------------
						// Update stats
						// -----------------------------
						// Packet loss
						if (rtp_packet.getSequenceNumber() > last_packet_nb + 1) {
							lost_packets += (rtp_packet.getSequenceNumber() - last_packet_nb);
							last_packet_nb = rtp_packet.getSequenceNumber();
							packet_loss = (lost_packets * 100) / (last_packet_nb + 1);
						} else
							last_packet_nb = rtp_packet.getSequenceNumber();

						// Packet delay
						last_packet_delay = packet_delay; // Save last packet delay to calculate jitter
						packet_delay = (last_packet_time == 0) ? 0 : System.currentTimeMillis() - last_packet_time;
						last_packet_time = System.currentTimeMillis();

						// Jitter
						jitter = last_packet_delay - packet_delay;

						// Bytes received
						received_bytes += rcvdp.getLength();
						// -----------------------------
						// Update stats text (invokeLater to avoid deadlock)
						SwingUtilities.invokeLater(() -> statsLabel.setText(getStats()));
						// ------------------------------

						// Check if the packet is audio or video
						if (rtp_packet.getPayloadType() == CommonValues.RAW_TYPE) {
							audioBuffer.offer(rtp_packet);
						} else if (rtp_packet.getPayloadType() == CommonValues.MJPEG_TYPE) {
							videoBuffer.offer(rtp_packet);
						} else {
							System.out.println("Unknown payload type: " + rtp_packet.getSequenceNumber());
						}
					} catch (InterruptedIOException iioe) {
						// We can ignore this exception as it is just a timeout
						// System.out.println("Nothing to read");
					} catch (SocketException se) {
						if (verbose)
							System.out.println("[RTPSocketListener] Socket closed, exiting thread...");
						break; // Exit the loop if socket is closed
					} catch (IOException ioe) {
						System.out.println("[RTPSocketListener] Exception caught: " + ioe);
					}
				}
			}
		}
	}

	/**
	 * videoTimerListener class
	 * <br>
	 * Thread to process video frames from the video buffer.
	 * Displays the frames in the GUI.
	 */
	class videoTimerListener implements Runnable {
		public void run() {
			while (running) {
				synchronized (pauseLock) {
					while (paused && running) {
						try {
							pauseLock.wait(); // Wait until the pauseLock is notified
						} catch (InterruptedException e) {
							System.out.println("[videoTimerListener] Exception caught: " + e);
						}
					}
				}
				if (!running)
					break; // Exit if the running flag is false
				try {
					RTPpacket rtp_packet = videoBuffer.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS); // Blocking call
					if (rtp_packet != null) {
						// get the payload bitstream from the RTPpacket object
						int payload_length = rtp_packet.getPayloadLength();
						byte[] payload = new byte[payload_length];
						payload = rtp_packet.getPayload();

						// Increment the video frame number
						last_video_nb++;

						// [Stats] FPS calculation
						// -----------------
						long now = System.currentTimeMillis();
						if (now - last_fps_update_time >= 1000) {
							current_fps = (last_video_nb - last_fps_video_nb) / ((now - last_fps_update_time) / 1000.0);
							last_fps_video_nb = last_video_nb;
							last_fps_update_time = now;
						}
						// -----------------

						// get an Image object from the payload bitstream
						Toolkit toolkit = Toolkit.getDefaultToolkit();
						Image image = toolkit.createImage(payload, 0, payload_length);

						// display the image as an ImageIcon object
						SwingUtilities.invokeLater(() -> {
							icon = new ImageIcon(image);
							iconLabel.setIcon(icon);
						});
					}
					Thread.sleep(CommonValues.PLAYBACK_FRAME_PERIOD); // Sleep for the frame period
				} catch (InterruptedException ie) {
					if (running)
						System.out.println("[videoTimerListener] Exception caught: " + ie);
				}
			}
		}
	}

	/**
	 * audioTimerListener class
	 * <br>
	 * Thread to process audio frames from the audio buffer.
	 * Plays the audio frames through the speaker.
	 */
	class audioTimerListener implements Runnable {
		public void run() {
			while (running) {
				synchronized (pauseLock) {
					while (paused && running) {
						try {
							pauseLock.wait(); // Wait until the pauseLock is notified
						} catch (InterruptedException e) {
							System.out.println("[audioTimerListener] Exception caught: " + e);
						}
					}
				}
				if (!running)
					break; // Exit if the running flag is false
				try {
					RTPpacket rtp_packet = audioBuffer.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS); // Blocking call
					if (rtp_packet != null) {
						// get the payload bitstream from the RTPpacket object
						int payload_length = rtp_packet.getPayloadLength();
						byte[] payload = new byte[payload_length];
						payload = rtp_packet.getPayload();

						// write the data to the speaker
						speaker.write(payload, 0, payload_length);

					}
					Thread.sleep(CommonValues.PLAYBACK_AUDIO_FRAME_PERIOD); // Sleep for the frame period
				} catch (InterruptedException ie) {
					if (running)
						System.out.println("[audioTimerListener] Exception caught: " + ie);
				}
			}
		}
	}

	/**
	 * Parse the RTSP server response.
	 * @return the reply code from the server response
	 */
	private int parse_server_response() {
		int reply_code = 0;

		try {
			// parse status line and extract the reply_code:
			String StatusLine = RTSPBufferedReader.readLine();
			if (verbose)
				System.out.println(StatusLine);

			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); // skip over the RTSP version
			reply_code = Integer.parseInt(tokens.nextToken());

			// if reply code is OK get and print the 2 other lines
			if (reply_code == 200) {
				String SeqNumLine = RTSPBufferedReader.readLine();
				if (verbose)
					System.out.println(SeqNumLine);

				String SessionLine = RTSPBufferedReader.readLine();
				if (verbose)
					System.out.println(SessionLine);

				// if state == INIT gets the Session Id from the SessionLine
				tokens = new StringTokenizer(SessionLine);
				tokens.nextToken(); // skip over the Session:
				RTSPid = Integer.parseInt(tokens.nextToken());
			}
		} catch (Exception ex) {
			System.out.println("[parseServerResponse] Exception caught: " + ex);
			cleanExit();
		}

		return (reply_code);
	}

	/**
	 * Send an RTSP request to the server.
	 * @param request_type the type of RTSP request (SETUP, PLAY, PAUSE, TEARDOWN)
	 */
	private void send_RTSP_request(String request_type) {
		try {
			// Use the RTSPBufferedWriter to write to the RTSP socket

			String request_line = VideoFileName + " RTSP/1.0";
			switch (request_type) {
				case "SETUP":
					request_line = "SETUP " + request_line;
					break;
				case "PLAY":
					request_line = "PLAY " + request_line;
					break;
				case "PAUSE":
					request_line = "PAUSE " + request_line;
					break;
				case "TEARDOWN":
					request_line = "TEARDOWN " + request_line;
					break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + state);
			}
			RTSPBufferedWriter.write(request_line + CommonValues.CRLF);
			if (verbose)
				System.out.println("C: " + request_line);

			// write the CSeq line
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CommonValues.CRLF);
			if (verbose)
				System.out.println("C: " + "CSeq: " + RTSPSeqNb + CommonValues.CRLF);
			/*
			 * If the request type is "SETUP",
			 * write the Transport header to inform the server of the client's RTP receiving
			 * port.
			 * For all other request types, write the Session header with the current RTSP
			 * session ID.
			 */
			if (request_type.equals("SETUP")) {
				RTSPBufferedWriter
						.write("Transport: RTP/UDP; client_port= " + CommonValues.RTP_RCV_PORT + CommonValues.CRLF);
				if (verbose)
					System.out.println("C: " + "Transport: RTP/UDP; client_port= " + CommonValues.RTP_RCV_PORT
							+ CommonValues.CRLF);
			} else {
				RTSPBufferedWriter.write("Session: " + RTSPid + CommonValues.CRLF);
				if (verbose)
					System.out.println("C: " + "Session: " + RTSPid + CommonValues.CRLF);
			}

			RTSPBufferedWriter.flush();
		} catch (Exception ex) {
			System.out.println("[sendRTSPRequest] Exception caught: " + ex);
			cleanExit();
		}
	}

	/**
	 * Clean exit method to stop all threads, close sockets, and dispose of GUI.
	 * This method is called when the application is closing or when an error occurs.
	 */
	private void cleanExit() {
		try {
			// Stop RTP socket listener thread
			if (rtpSocketListener != null && rtpSocketListener.isAlive()) {
				rtpSocketListener.interrupt();
			}
			// Stop threads and close soundcard
			if (videoThread != null && videoThread.isAlive()) {
				videoThread.interrupt();
			}
			if (audioThread != null && audioThread.isAlive()) {
				audioThread.interrupt();
			}
			// Close RTP and RTSP sockets
			if (RTPsocket != null) {
				RTPsocket.close();
			}
			if (RTSPsocket != null) {
				RTSPsocket.close();
			}
			// Close RTSP input and output streams
			if (RTSPBufferedReader != null) {
				RTSPBufferedReader.close();
			}
			if (RTSPBufferedWriter != null) {
				RTSPBufferedWriter.close();
			}
			// Clear buffers
			if (videoBuffer != null) {
				videoBuffer.clear();
			}
			if (audioBuffer != null) {
				audioBuffer.clear();
			}
			// Stop and close the speaker
			if (speaker != null) {
				speaker.drain();
				speaker.flush();
				speaker.stop();
				speaker.close();
			}
			// Dispose GUI
			if (f != null) {
				f.dispose();
			}
			if (stats != null) {
				stats.dispose();
			}
		} catch (IOException e) {
			System.out.println("[cleanExit] IOException caught: " + e);
		} catch (Exception e) {
			System.out.println("[cleanExit] Exception caught: " + e);
		} finally {
			System.exit(0);
		}
	}

}