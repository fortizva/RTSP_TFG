package com.fortizva.rtp;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.fortizva.media.Codec;
import com.fortizva.packets.CommonValues;
import com.fortizva.packets.FECpacket;
import com.fortizva.packets.RTPpacket;

/**
 * Usage: java Server &ltRTSP listening port&gt [-v] [-f=FEC group size] [-s=Simulated packet loss]
 * <br>
 * Parameters:
 * <ul>
 * <li>&ltRTSP listening port&gt</li> <dd>Port number for RTSP connection (e.g., 1025)</dd>
 * <li>-v</li> <dd>Enable verbose mode for debugging output</dd>
 * <li>-f=groupSize</li> <dd>Set FEC group size (between 2 and 16, default is enabled with a value of 5)</dd>
 * <li>-s=packetLoss</li> <dd>Set simulated packet loss percentage (between 1 and 100, default is disabled with a value of 5)</dd>
 * </ul>
 * Disables FEC or simulated packet loss by setting their values to 0.
 */
public class Server extends JFrame {

	private static final long serialVersionUID = 1L;

	// Arguments
	// ----------------
	boolean verbose = false;
	final static int DEFAULT_PORT = 1025; // Default RTSP port
	final static int DEFAULT_FEC_GROUP_SIZE = 5; // Default FEC group size
	final static int DEFAULT_PACKET_LOSS = 5; // Default simulated packet loss percentage
	
	
	// RTP variables:
	// ----------------
	DatagramSocket VideoSocket; // socket to send video frames
	DatagramSocket AudioSocket; // socket to send audio frames
	DatagramSocket FecSocket; // socket to send FEC packets
	DatagramPacket vsenddp; // UDP packet containing the video frames
	DatagramPacket asenddp; // UDP packet containing the audio frames
	DatagramPacket fecSendDP; // UDP packet containing the FEC packets

	InetAddress ClientIPAddr; // Client IP address
	int RTP_dest_port = 0; // destination port for RTP packets (given by the RTSP Client)

	// GUI:
	// ----------------
	JPanel mainPanel;
	JPanel statsPanel;
	JPanel settingsPanel;
	private JLabel lblLastFrame;
	private JLabel lblLastChunk;
	private JLabel lblSimLost;
	private JCheckBox chkFEC;
	private JCheckBox chkSimLoss;
	private JSpinner spnFECGroup;
	private JSpinner spnPacketLoss;

	// Video & audio variables
	// ----------------
	Codec videoCodec;
	Codec audioCodec;

	// Video variables:
	// ----------------
	Thread videoThread; // Thread to handle video processing
	int imagenb = 0; // image nb of the image currently transmitted
	int videoSkips = 0; // Number of video skips (non-video frames)	
	int VIDEO_LENGTH; // length of the video in frames
	byte[] vBuf; // buffer used to store the images to send to the client

	// Audio variables
	Thread audioThread; // Thread to handle audio processing
	int audionb = 0; // audio chunk nb of the audio currently transmitted
	int audioSkips = 0; // Number of audio skips (non-audio frames)
	byte[] aBuf; // buffer used to store the chunks to send to the client
	
	// FEC variables
	// ----------------
	int fecnb = 0; // FEC packet number
	private LinkedList<RTPpacket> protectedPackets; // List to store RTP packets for FEC

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

	public int simLostPackets = 0; // Number of lost packets (simulated)
	
	final static String CRLF = "\r\n";
	/**
	 * Constructor of the Server class. Initializes the GUI and prepares the server to
	 * accept RTSP requests.
	 */
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
		setPreferredSize(new Dimension(500, 200));
		setLocation(0, 575);
		setResizable(false);

		mainPanel = new JPanel(new BorderLayout());

		// Stats Panel (Left side)
		statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
		statsPanel.setBorder(BorderFactory.createCompoundBorder(
		    BorderFactory.createTitledBorder("Stats:"),
		    BorderFactory.createEmptyBorder(10, 10, 10, 10)
		));
		statsPanel.setPreferredSize(new Dimension(225, 200)); // Fixed width
		statsPanel.setMaximumSize(new Dimension(225, Integer.MAX_VALUE)); // Allows vertical expansion only

		lblLastFrame = new JLabel("Last video frame: #0");
		lblLastChunk = new JLabel("Last audio chunk: #0");
		lblSimLost = new JLabel("Simulated lost packets: 0");
		lblSimLost.setVisible(false);

		statsPanel.add(lblLastFrame);
		statsPanel.add(Box.createVerticalStrut(5));
		statsPanel.add(lblLastChunk);
		statsPanel.add(Box.createVerticalStrut(5));
		statsPanel.add(lblSimLost);

		// Settings Panel (Right side)
		settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBorder(BorderFactory.createCompoundBorder(
		    BorderFactory.createTitledBorder("Settings:"),
		    BorderFactory.createEmptyBorder(10, 10, 10, 10)
		));
		settingsPanel.setPreferredSize(new Dimension(275, 200)); // Fixed width
		settingsPanel.setMaximumSize(new Dimension(275, Integer.MAX_VALUE)); // Allows vertical expansion only

		chkFEC = new JCheckBox("Forward Error Correction");
		chkFEC.setEnabled(true);
		chkFEC.setSelected(true);

		JPanel FECPanel = new JPanel();
		FECPanel.setLayout(new BoxLayout(FECPanel, BoxLayout.X_AXIS));
		FECPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		spnFECGroup = new JSpinner(new SpinnerNumberModel(5, 2, 16, 1));
		JLabel lblFECGroup = new JLabel("FEC Group Size (max 16):");
		lblFECGroup.setFont(lblFECGroup.getFont().deriveFont(Font.ITALIC, 12f));
		spnFECGroup.setEnabled(chkFEC.isSelected());
		spnFECGroup.setMaximumSize(new Dimension(60, 20));
		chkFEC.addActionListener(e -> spnFECGroup.setEnabled(chkFEC.isSelected()));

		FECPanel.add(lblFECGroup);
		FECPanel.add(spnFECGroup);

		JPanel simLossPanel = new JPanel();
		simLossPanel.setLayout(new BoxLayout(simLossPanel, BoxLayout.X_AXIS));
		simLossPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkSimLoss = new JCheckBox("DEBUG: Simulate Video Packet Loss");
		spnPacketLoss = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
		JLabel lblPacketLoss = new JLabel("Packet loss (%):");
		lblPacketLoss.setFont(lblPacketLoss.getFont().deriveFont(Font.PLAIN, 12f));
		spnPacketLoss.setEnabled(chkSimLoss.isSelected());
		spnPacketLoss.setMaximumSize(new Dimension(60, 20));

		chkSimLoss.addActionListener(e -> {
		    spnPacketLoss.setEnabled(chkSimLoss.isSelected());
		    lblSimLost.setVisible(chkSimLoss.isSelected());
		});

		simLossPanel.add(lblPacketLoss);
		simLossPanel.add(spnPacketLoss);

		settingsPanel.add(chkFEC);
		settingsPanel.add(FECPanel);
		settingsPanel.add(Box.createVerticalStrut(10));
		settingsPanel.add(chkSimLoss);
		settingsPanel.add(simLossPanel);

		// Add panels to the main panel
		mainPanel.add(statsPanel, BorderLayout.CENTER);
		mainPanel.add(settingsPanel, BorderLayout.EAST);

		add(mainPanel);
		pack();

	}

	/**
	 * Main method to start the media server.
	 * 
	 * @param argv Command line arguments: &ltRTSP listening port&gt [-v for verbose mode] [-f=number for FEC group size] [-s=number for simulated packet loss]
	 */
	public static void main(String argv[]) throws Exception {
		// create a Server object
		Server theServer = new Server();

		/* Check for launch arguments, allowed arguments:
		 * argv[0] = RTSP listening port
		 * -v : verbose mode
		 * -f=number : FEC group size (Enabled with a value of 5 by default)
		 * -s=number : Simulated packet loss percentage (Disabled with value of 5 by default)
		 * 
		 * Example: java Server 1025 -v -f=10 -s=10
		 * 
		 * Note: The FEC group size must be between 2 and 16.
		 * Note: The simulated packet loss percentage must be between 1 and 100.
		 * Note: Using 0 for the FEC Group Size or Simulated Packet Loss will disable the feature.
		 */
		
		if (argv.length < 1) {
			System.out.println("Usage: java Server [RTSP listening port] [-v for verbose mode] [-f=number for FEC group size] [-s=number for simulated packet loss]");
			System.exit(1);
		}

		// get RTSP socket port from the command line
		int RTSPport = 1025;
		try{
			RTSPport = Integer.parseInt(argv[0]);
		} catch (NumberFormatException e) {
			System.out.println("Invalid RTSP port number. Please provide a valid integer.");
			System.exit(1);
		}
		
		// Default values for FEC group size and simulated packet loss
		theServer.chkFEC.setSelected(true); // Default value is FEC enabled
		theServer.spnFECGroup.setEnabled(true); // Enable FEC group size spinner by default
		theServer.spnFECGroup.setValue(DEFAULT_FEC_GROUP_SIZE); // Default FEC group size
		theServer.chkSimLoss.setSelected(false); // Default value is no simulated packet loss
		theServer.spnPacketLoss.setEnabled(false); // Disable packet loss spinner by default
		theServer.spnPacketLoss.setValue(DEFAULT_PACKET_LOSS); // Default simulated packet loss percentage
		
		if(argv.length >= 2) {
			// Loop through arguments to check for verbose mode and other options
			String arg;
			// Loop through all arguments starting from index 1
			for (int i = 1; i < argv.length; i++) {
				arg = argv[i];
				if (arg.equals("-v")) {
					theServer.verbose = true;
					System.out.println("Verbose mode: ACTIVE");
				} else if (arg.startsWith("-f=")) {
					String fecGroupSizeStr = arg.substring(3);
					try {
						int fecGroupSize = Integer.parseInt(fecGroupSizeStr);
						if (fecGroupSize == 0) {
							theServer.chkFEC.setSelected(false); // Disable FEC if group size is 0
							theServer.spnFECGroup.setEnabled(false); // Disable FEC group size spinner
						} else if(fecGroupSize < 2 || fecGroupSize > 16) {
							System.out.println("FEC group size must be between 2 and 16. Using default value of "+ DEFAULT_FEC_GROUP_SIZE +".");
						} else 
							theServer.spnFECGroup.setValue(fecGroupSize);
					} catch (NumberFormatException e) {
						System.out.println("Invalid FEC group size. Using default value of "+ DEFAULT_FEC_GROUP_SIZE +".");
						theServer.spnFECGroup.setValue(5);
					}
				} else if (arg.startsWith("-s=")) {
					
					String packetLossStr = arg.substring(3);
					try {
						int packetLoss = Integer.parseInt(packetLossStr);
						if (packetLoss < 0 || packetLoss > 100) {
							System.out.println("Simulated packet loss must be between 1 and 100. Disabling by default.");
						} else {
							theServer.chkSimLoss.setSelected(true); // Enable simulated packet loss
							theServer.spnPacketLoss.setEnabled(true); // Enable packet loss spinner
							theServer.spnPacketLoss.setValue(packetLoss); // Set the simulated packet loss percentage
							theServer.lblSimLost.setVisible(true); // Show the simulated lost packets label
						}
						
					} catch (NumberFormatException e) {
						System.out.println("Invalid simulated packet loss. Disabling by default.");
					}
				} else {
					System.out.println("Unknown argument: \"" + arg+"\". Ignoring it.");
				}
			}
		}		
		
		// show GUI:
		theServer.pack();
		theServer.setVisible(true);

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

		// Initialize buffer
		theServer.protectedPackets = new LinkedList<RTPpacket>();
		
		// Wait for the SETUP message from the client
		int request_type;
		boolean done = false;
		while (!done) {
			request_type = theServer.parse_RTSP_request(); // blocking

			if (request_type == SETUP) {
				done = true;
				
				// Disable settings panel after setup (Just once)
				if(theServer.settingsPanel.isEnabled()) {
					SwingUtilities.invokeLater(() -> {
						Queue <Component> components = new LinkedList<>();
						components.add(theServer.settingsPanel);
						while (!components.isEmpty()) {
							Component comp = components.poll();
							if (comp instanceof JPanel) {
								JPanel panel = (JPanel) comp;
								panel.setEnabled(false);
								for (Component child : panel.getComponents()) {
									components.add(child);
								}
							} else {
								comp.setEnabled(false);
							}
						}
					});
				}
				
				// update RTSP state
				state = READY;
				System.out.println("New RTSP state: READY\n");

				// Send response
				theServer.send_RTSP_response();

				// Initialize video
				theServer.videoCodec = new Codec(VideoFileName);

				// Init video properties
				theServer.VIDEO_LENGTH = theServer.videoCodec.getNumFrames();
				//theServer.FRAME_PERIOD = (int) (1000 / theServer.videoCodec.getFPS());
				theServer.videoThread = new Thread(theServer.new VideoSender());
				if (theServer.verbose)
					System.out.println("DEBUG: FPS: " + theServer.videoCodec.getFPS() + " PLAYBACK_FRAME_PERIOD: "
							+ CommonValues.PLAYBACK_FRAME_PERIOD + " STREAMING_FRAME_PERIOD: " + CommonValues.STREAMING_FRAME_PERIOD);

				// Init audio properties
				// Use different codec for audio to read audio data separately
				theServer.audioCodec = new Codec(VideoFileName);
				theServer.audioThread = new Thread(theServer.new AudioSender());
				if (theServer.verbose)
					System.out.println("DEBUG: AUDIO_FRAME_PERIOD: " + CommonValues.PLAYBACK_AUDIO_FRAME_PERIOD 
							+ " STREAMING_AUDIO_FRAME_PERIOD: " + CommonValues.STREAMING_AUDIO_FRAME_PERIOD);

				// init RTP sockets
				theServer.VideoSocket = new DatagramSocket();
				theServer.AudioSocket = new DatagramSocket();
				theServer.FecSocket = new DatagramSocket();
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

	/**
	 * Thread to handle video sending.
	 * Sends video frames to the client at a specified rate.
	 */
	class VideoSender implements Runnable {
		public void run() {
			// if the current image nb is less than the length of the video keep going
			while (running && imagenb+videoSkips < VIDEO_LENGTH) {
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
					RTPpacket video_packet = new RTPpacket(CommonValues.MJPEG_TYPE, (imagenb),
							(int) (System.currentTimeMillis() % Integer.MAX_VALUE), vBuf, video_length);
					byte[] video_bits = new byte[video_packet.getSize()];
					video_bits = video_packet.getPacket();
					vsenddp = new DatagramPacket(video_bits, video_bits.length, ClientIPAddr, RTP_dest_port);
					
					// DEBUG: Add random lost packets
					 if(!chkSimLoss.isSelected() || (Math.random()*100d) > (int) spnPacketLoss.getModel().getValue()) {
						 SwingUtilities.invokeLater(() -> lblLastFrame.setText("Last video frame: #" + imagenb));
						 VideoSocket.send(vsenddp);
					 } else {
						 simLostPackets++;
						 SwingUtilities.invokeLater(() -> lblSimLost.setText("Simulated lost packets: " + simLostPackets));
					 	 //System.out.println("DEBUG: Video packet lost! Total lost packets: " + lost);
					 }
					// print the header bitstream
					if (verbose)
						video_packet.printHeader();
					
					// FEC Packet sending
					if(chkFEC.isSelected()) {
						// Add the current video packet to the protected packets list
						protectedPackets.add(video_packet);
						// Send FEC packet when packets list is full or if the video length is reached
						if (protectedPackets.size() >= (int) spnFECGroup.getModel().getValue() || imagenb+videoSkips == VIDEO_LENGTH && protectedPackets.size() > 0) {	
							// Create FEC packet
							FECpacket fecPacket = new FECpacket(protectedPackets.toArray(RTPpacket[]::new));
							RTPpacket fecRtpPacket = new RTPpacket(CommonValues.FEC_PTYPE, fecnb,
									(int) (System.currentTimeMillis() % Integer.MAX_VALUE), fecPacket.getFecPacket(),
									fecPacket.getFecPacketSize());
							
							// Send FEC packet
							byte[] fec_bits = new byte[fecRtpPacket.getSize()];
							fec_bits = fecRtpPacket.getPacket();
							fecSendDP = new DatagramPacket(fec_bits, fec_bits.length, ClientIPAddr, RTP_dest_port);
							try {
								FecSocket.send(fecSendDP);
							} catch (IOException e) {
								System.out.println("[VideoSender] Error sending FEC packet: " + e.getStackTrace());
							} finally {
								fecnb++; // Increment FEC packet number						
								// Clear the protected packets list after sending FEC
								protectedPackets.clear();
							}
						}
					}
					
					// Sleep for the video frame period
					Thread.sleep(CommonValues.STREAMING_FRAME_PERIOD);
				} catch (Exception ex) {
					if (verbose)
						System.out.println("DEBUG: VIDEOactionPerformed()");
					System.out.println("Exception caught: " + ex);
					cleanExit();
				}
			}
		}
	}

	/**
	 * Thread to handle audio sending.
	 * Sends audio chunks to the client at a specified rate.
	 */
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
					RTPpacket audio_packet = new RTPpacket(CommonValues.RAW_TYPE, audionb,
							(int) (System.currentTimeMillis() % Integer.MAX_VALUE), aBuf, audio_length);
					byte[] audio_bits = new byte[audio_packet.getSize()];
					audio_bits = audio_packet.getPacket();
					asenddp = new DatagramPacket(audio_bits, audio_bits.length, ClientIPAddr, RTP_dest_port);

					// DEBUG: Add random lost packets
					// if((Math.random()*100d) < 95)
					AudioSocket.send(asenddp);

					// print the header bitstream
					if (verbose)
						audio_packet.printHeader();
					// update GUI
					SwingUtilities.invokeLater(() -> lblLastChunk.setText("Last audio chunk: #" + audionb));
					// Sleep for the audio frame period
					Thread.sleep(CommonValues.STREAMING_AUDIO_FRAME_PERIOD);
				} catch (Exception ex) {
					if (verbose)
						System.out.println("DEBUG: AUDIOactionPerformed()");
					System.out.println("Exception caught: " + ex);
					cleanExit();
				}
			}
		}
	}

	/**
	 * Parse the RTSP request from the client.
	 * 
	 * @return The type of RTSP request (SETUP, PLAY, PAUSE, TEARDOWN).
	 */
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

	/**
	 * Send a response to the RTSP client.
	 * The response includes the RTSP version, CSeq, and Session ID.
	 */
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

	/**
	 * Cleanly exit the server by stopping threads, closing sockets, and exiting the program.
	 * This method is called when the server window is closed or when a TEARDOWN request is received.
	 */
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
			if (VideoSocket != null) {
				VideoSocket.close();
			}
			if (AudioSocket != null) {
				AudioSocket.close();
			}
			if (FecSocket != null) {
				FecSocket.close();
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
