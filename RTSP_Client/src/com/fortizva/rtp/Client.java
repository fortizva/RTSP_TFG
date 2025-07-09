package com.fortizva.rtp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.fortizva.packets.CommonValues;
import com.fortizva.packets.FECpacket;
import com.fortizva.packets.RTPpacket;

/**
 * BufferBar class
 * <br>
 * Custom JPanel to display the buffer states of received RTP packets.
 * Each frame in the buffer is represented by a colored rectangle.
 */
class BufferBar extends JPanel {
	/**
	 * Serial version UID for serialization. (Just to avoid warnings)
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * TreeMap to store the states of the buffer frames. Ordered and indexed by frame number.
	 */
    private TreeMap<Integer, FrameStatus> bufferStates;
    private static final int NUM_FRAMES_DISPLAYED = 60;

    public enum FrameStatus {
        RECEIVED, LOST, LATE, RECOVERED, EMPTY
    }
    
    /**
	 * Initializes the panel with a preferred size. Repaints the panel.
	 * @param states Initial buffer states.
	 */
    public void setBufferStates(TreeMap<Integer, FrameStatus> states) {
        this.bufferStates = states;
        repaint();
    }
    
    /**
     * Returns the current buffer states.
     * @return TreeMap of frame numbers and their statuses.
     */
    public TreeMap<Integer, FrameStatus> getBufferStates() {
		return bufferStates;
	}
    
    /**
	 * Clears the buffer states.
	 */
    public void clearBufferStates() {
		this.bufferStates = null;
	}
    
    /**
     * Adds or updates the state of a specific frame in the buffer.
     * @param frame Frame number to update.
     * @param state State to set for the frame.
     */
    public void putBufferState(int frame, FrameStatus state) {
		if (bufferStates == null) {
			bufferStates = new TreeMap<>();
		}
        if (bufferStates.size() >= NUM_FRAMES_DISPLAYED) {
            bufferStates.pollFirstEntry(); // Remove the oldest entry if we exceed the limit
        }
        bufferStates.put(frame, state);
    }
    
    /**
	 * Gets the status of a specific frame in the buffer.
	 * @param frame Frame number to check.
	 * @return The status of the frame, or EMPTY if not set.
	 */
    public FrameStatus getFrameStatus(int frame) {
    	return (bufferStates!=null) ? bufferStates.getOrDefault(frame, FrameStatus.EMPTY) : FrameStatus.EMPTY;
    }
    
    /** 
     * Fills the buffer states between two frames with a specific status.
     * @param startFrame Start frame number. (inclusive)
     * @param endFrame End frame number. (exclusive)
     * @param status Status to set for the frames between startFrame (inclusive) and endFrame (exclusive).
     */
    public void fillBetweenFrames(int startFrame, int endFrame, FrameStatus status) {
		if (bufferStates == null) {
			bufferStates = new TreeMap<>();
		}
		for (int i = startFrame; i < endFrame; i++) {
			bufferStates.put(i, status);
		}
	}

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(bufferStates == null || bufferStates.isEmpty()) {
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, getWidth(), getHeight());
			return; // No states to display
		}
        int width = getWidth() / NUM_FRAMES_DISPLAYED;
        int totalWidth = width * NUM_FRAMES_DISPLAYED;
        int xOffset = (getWidth() - totalWidth) / 2; // Center the bar
        List<FrameStatus> states = new ArrayList<>(bufferStates.values());
        for (int i = 0; i < NUM_FRAMES_DISPLAYED; i++) {
            FrameStatus status = (i < states.size()) ? states.get(i) : FrameStatus.EMPTY;
            switch (status) {
                case RECEIVED: g.setColor(Color.GREEN); break;
                case LOST: g.setColor(Color.RED); break;
                case RECOVERED: g.setColor(Color.YELLOW); break;
                case LATE: g.setColor(Color.ORANGE); break;
                case EMPTY: g.setColor(Color.GRAY); break;
            }
            int x = xOffset + i * width;
            g.fillRect(x, 0, width, getHeight());
            g.setColor(Color.BLACK);
            g.drawRect(x, 0, width, getHeight());
        }
    }
}

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
	JEditorPane statsPane = new JEditorPane();
	
	JFrame bufferBarFrame = new JFrame("Video Buffer");
	BufferBar videoBufferBar = new BufferBar();
	
	// Stream statistics
	// ---------------
	StreamStats videoStats = new StreamStats();
	StreamStats audioStats = new StreamStats();
	
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
	Thread fecThread; // thread used to handle FEC packets
	Timer statsTimer; // Timer used to update stats periodically
	// ----------------

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets

	// RTP packet buffer
	final static int BUFFER_TIMEOUT = 60; // Timeout for the buffer
	PriorityBlockingQueue<RTPpacket> videoBuffer;
	PriorityBlockingQueue<RTPpacket> audioBuffer;
	PriorityBlockingQueue<FECpacket> fecQueue; // FEC queue used to store FEC packets for processing
	PriorityBlockingQueue<RTPpacket> protectionBuffer; // FEC buffer used to store protected packets

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
		statsPane.setContentType("text/html");
		statsPane.setEditable(false);
		// Force the JEditorPane to not have a scroll pane and to use the full size
		statsPane.setPreferredSize(null);
		statsPane.setMinimumSize(null);
		
		statsPanel.setLayout(new BorderLayout());
		statsPanel.add(statsPane, BorderLayout.CENTER);
		
		statsPane.setText(getStats());
		statsPane.setVisible(true);
		stats.getContentPane().add(statsPanel, BorderLayout.CENTER);
		stats.pack();

		// Set preferred size for statsLabel and stats window
		statsPane.setSize(new Dimension(600, 370)); // width, height
		statsPanel.setPreferredSize(new Dimension(600, 370));
		stats.setSize(new Dimension(620, 370)); // width, height
		

		stats.setResizable(false);
		stats.setLocation(400, 0);
		stats.setVisible(true);
		
		// Buffer bar layout
		bufferBarFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		bufferBarFrame.getContentPane().add(videoBufferBar, BorderLayout.CENTER);
		videoBufferBar.setPreferredSize(new Dimension(380, 40));
		bufferBarFrame.pack();
		bufferBarFrame.setResizable(false);
		// Position below the main player window
		bufferBarFrame.setLocation(f.getX(), f.getY() + f.getHeight() + 10);
		bufferBarFrame.setVisible(true);
		
		// Update bufferBarFrame position if main window moves
		f.addComponentListener(new java.awt.event.ComponentAdapter() {
		    public void componentMoved(java.awt.event.ComponentEvent evt) {
		        bufferBarFrame.setLocation(f.getX(), f.getY() + f.getHeight() + 10);
		    }
		});
		
		// allocate enough memory for the buffer used to receive data from the server
		buf = new byte[15000];
		videoBuffer = new PriorityBlockingQueue<RTPpacket>(1000);
		audioBuffer = new PriorityBlockingQueue<RTPpacket>(1000);
		fecQueue = new PriorityBlockingQueue<FECpacket>(100); // FEC queue for FEC packets
		/** Buffer used to store FEC-protected packets. Size is set to 2 times the FEC Frequency */
		protectionBuffer = new PriorityBlockingQueue<RTPpacket>(2*CommonValues.FEC_FREQUENCY);

		// Init RTP socket listener thread
		rtpSocketListener = new Thread(new RTPSocketListener());

		// Set the thread as a daemon so it will not block the application from exiting
		rtpSocketListener.setDaemon(true);

		videoThread = new Thread(new videoTimerListener());
		audioThread = new Thread(new audioTimerListener());
		fecThread = new Thread(new FECListener());
		videoThread.setDaemon(true);
		audioThread.setDaemon(true);
		fecThread.setDaemon(true);
		
		statsTimer = new Timer();
		statsTimer.scheduleAtFixedRate(new TimerTask() {
		    public void run() {
		        String html = getStats(); // Run outside EDT!
		        SwingUtilities.invokeLater(() -> statsPane.setText(html)); // Fast GUI update
		        videoBufferBar.repaint(); // Repaint the buffer bar
		    }
		}, 0, 200); // every 500ms
			
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
	    String s = "<html>\n" +
	    "<body style=\"font-family: Arial, sans-serif; font-size: 1em; margin: 0px; padding: 0px 10px 10px 10px; width: 100%; box-sizing: border-box\">\n" +
	    "  <h3 style=\"color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 2px; margin-bottom: 1px;\">\n" +
	    "    📊 Stream Statistics\n" +
	    "  </h3>\n" +
	    "  <table width=\"100%\"><tr><td valign=\"top\" style=\"min-width:250px;\">\n" +
	    "    <h4 style=\"color: #e74c3c; margin: 1px 10px 0px 10px; padding-bottom:1px;\">🔉 Audio Stream:</h4>\n" +
	    "    <div style=\"margin-left: 10px; width: 100%\">\n";
	s += String.format("      <b>Received bytes:</b> %d<br>\n", audioStats.receivedBytes);
	s += String.format("      <b>Received packets:</b> %d<br>\n", audioStats.receivedPackets);
	synchronized(audioStats.bufferLock) {
		audioStats.bufferSize = (audioBuffer==null) ? 0 : audioBuffer.size(); // Get FEC buffer size
	}
	s += String.format("	  <b>Buffer size:</b> %d packets<br>\n", audioStats.bufferSize);
	s += String.format("      <b>Initial packet #:</b> %d<br>\n", audioStats.initialPacketNb);
	s += String.format("      <b>Last packet #:</b> %d<br>\n", audioStats.lastReceivedPacketNb);
	s += String.format("      <b>Last played packet #:</b> %d<br>\n", audioStats.lastPlayedPacketNb);
	s += String.format("      <b>Lost packets:</b> %d<br>\n", audioStats.lostPackets);
	s += String.format("      <b>Packet loss:</b> %d%%<br>\n", audioStats.packetLoss);
	s += String.format("      <b>Packet delay (ms):</b> %d<br>\n", audioStats.packetDelay);
	s += String.format("      <b>Jitter (ms):</b> %+d<br>\n", audioStats.jitter);
	s += "    </div>\n" +
	     "  </td>\n" +
	     "  <td valign=\"top\" style=\"min-width:250px;\">\n" +
	     "    <h4 style=\"color: #2ecc71; margin: 0px 10px 0px 10px; padding-bottom:1px;\">🎥 Video Stream:</h4>\n" +
	     "    <div style=\"margin-left: 10px; width: 100%;\">\n";
	s += String.format("      <b>Received bytes:</b> %d<br>\n", videoStats.receivedBytes);
	s += String.format("      <b>Received packets:</b> %d<br>\n", videoStats.receivedPackets);
	synchronized(videoStats.bufferLock) {
		videoStats.bufferSize = (videoBuffer==null) ? 0 : videoBuffer.size(); // Get FEC buffer size
	}
	s += String.format("	  <b>Buffer size:</b> %d packets<br>\n", videoStats.bufferSize);
	s += String.format("      <b>Initial packet #:</b> %d<br>\n", videoStats.initialPacketNb);
	s += String.format("      <b>Last packet #:</b> %d<br>\n", videoStats.lastReceivedPacketNb);
	s += String.format("      <b>Last played packet #:</b> %d<br>\n", videoStats.lastPlayedPacketNb);
	s += String.format("      <b>Lost packets:</b> %d<br>\n", videoStats.lostPackets);
	s += String.format("      <b>Packet loss:</b> %d%%<br>\n", videoStats.packetLoss);
	s += String.format("      <b>Packet delay (ms):</b> %d<br>\n", videoStats.packetDelay);
	s += String.format("      <b>Jitter (ms):</b> %+d<br>\n", videoStats.jitter);
	s += String.format("      <b>Current FPS:</b> %.2f<br>\n", videoStats.currentFps);
	s += "      <b>Frames since update #:</b> " + videoStats.framesSinceUpdate + "<br>\n";
	s += "      <b>Last FPS update time:</b> " + videoStats.lastFpsUpdateTime + "<br>\n";
	synchronized(videoStats.fecLock) {
		videoStats.fecBufferSize = (fecQueue==null) ? 0 : fecQueue.size(); // Get FEC buffer size
	}
	s += "      <b>FEC buffer size:</b> " + videoStats.fecBufferSize + " packets<br>\n";
	synchronized (videoStats.protectionLock) {
		videoStats.protectionBufferSize = (protectionBuffer==null) ? 0 : protectionBuffer.size(); // Get FEC protection buffer size
	}
	s += String.format("      <b>Protection buffer size:</b> %d packets<br>\n", videoStats.protectionBufferSize);
	s += String.format("      <b>Recovered packets:</b> %d<br>\n", videoStats.recoveredPackets);
	s += String.format("      <b>Late packets:</b> %d<br>\n", videoStats.latePackets);
	s += "    </div>\n" +
	     "  </td></tr></table>\n" +
	     "</body>\n" +
	     "</html>";

	    return s;
	}

	
	private void updateStats(StreamStats stats, RTPpacket rtp_packet) {
		// Initialize stats if this is the first packet
		if(stats.receivedPackets == 0) {
			stats.initialPacketNb = rtp_packet.getSequenceNumber();
		}
		
		// Update packet loss taking into account initial and last packet numbers.
		if (rtp_packet.getSequenceNumber() > stats.lastReceivedPacketNb + 1 && stats.initialPacketNb != rtp_packet.getSequenceNumber()) {
			int calculatedLostPackets = rtp_packet.getSequenceNumber() - stats.lastReceivedPacketNb - 1;
			stats.lostPackets += calculatedLostPackets;
			if(calculatedLostPackets > 0) {
				if (verbose) {
					System.out.println("Lost " + calculatedLostPackets + " packets between " + stats.lastReceivedPacketNb
							+ " and " + rtp_packet.getSequenceNumber());
				}
				// Update buffer state for lost packets
				videoBufferBar.fillBetweenFrames(stats.lastReceivedPacketNb + 1, rtp_packet.getSequenceNumber() - 1,
						BufferBar.FrameStatus.LOST);
			}
			stats.packetLoss = (stats.lostPackets * 100) / (rtp_packet.getSequenceNumber() - stats.initialPacketNb + 1);
		}
		
		// Update packet delay and jitter
		stats.lastPacketDelay = stats.packetDelay; // Save last packet delay to calculate jitter
		stats.packetDelay = (stats.lastPacketTime == 0) ? 0
				: System.currentTimeMillis() - stats.lastPacketTime;
		stats.lastPacketTime = System.currentTimeMillis();
		
		stats.jitter = stats.lastPacketDelay - stats.packetDelay;
		
		// Update received bytes and packets
		stats.receivedBytes += rtp_packet.getSize();
		stats.receivedPackets++;
		stats.lastReceivedPacketNb = rtp_packet.getSequenceNumber();
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
					if (!rtpSocketListener.isAlive()) {
						rtpSocketListener.start(); // start the RTP socket listener thread
					}
					if (!fecThread.isAlive()) {
						fecThread.start(); // start the FEC handler thread
					}
					if (!videoThread.isAlive()) {
						videoThread.start(); // start the video timer thread
					}
					if (!audioThread.isAlive()) {
						audioThread.start(); // start the audio timer thread
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
				if (fecThread != null && fecThread.isAlive()) {
					fecThread.interrupt(); // Interrupt the FEC handler thread
				}
				// Stop stats timer
				if (statsTimer != null) {
					statsTimer.cancel(); // Cancel the stats timer
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
						
						// Check if the packet is audio or video
						if (rtp_packet.getPayloadType() == CommonValues.RAW_TYPE) {
							audioBuffer.offer(rtp_packet);
							
							// -----------------------------
							// Update audio stats
							// -----------------------------
							updateStats(audioStats, rtp_packet);
							
						} else if (rtp_packet.getPayloadType() == CommonValues.MJPEG_TYPE) {
							videoBuffer.offer(rtp_packet);
							protectionBuffer.offer(rtp_packet); // Add to FEC buffer as well
							
							// -----------------------------
							// Update video stats
							// -----------------------------
							updateStats(videoStats, rtp_packet);
							videoBufferBar.putBufferState(rtp_packet.getSequenceNumber(), BufferBar.FrameStatus.RECEIVED);
							
							
						} else if (rtp_packet.getPayloadType() == CommonValues.FEC_PTYPE) {
							// FEC packet handling
							if(verbose)
								System.out.println("Received FEC packet with SeqNum # " + rtp_packet.getSequenceNumber());
							// Create FECpacket object from the received packet
							FECpacket fec_packet = new FECpacket(rtp_packet.getPayload());
							
							// Add the FEC packet to the queue for processing
							fecQueue.offer(fec_packet);
							
						} else {
							System.out.println("Unknown payload type: " + rtp_packet.getPayloadType() + " - SequenceNumber: "
									+ rtp_packet.getSequenceNumber());
						}
						
						/*// -----------------------------
						// Update stats text (invokeLater to avoid deadlock)
						String statsStr = getStats();
						SwingUtilities.invokeLater(() -> statsPane.setText(statsStr));
						// ------------------------------
						*/
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

	class FECListener implements Runnable {
	    public void run() {
	        while (running) {
	        	// We don't need to check for pause here, as the FEC thread should always be running.

	        	try {
		            FECpacket fec_packet = fecQueue.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS); // Blocking call to get FEC packet
		            if (fec_packet != null) { // If we got a FEC packet, we can process it. Active wait so we can safely stop the thread if needed.
			            if (verbose) {
			                System.out.println("[FECListener] Processing FEC packet with BaseSeqNum # " + fec_packet.getBaseSequenceNumber());
			            }
			            
			            // Look for missing packets in the protection buffer using FEC packet's base sequence number and mask
						int baseSeqNum = fec_packet.getBaseSequenceNumber(); // Initial sequence number of the protected packets
						int maskLength = fec_packet.getMaskLength(); // Total number of protected packets
						
						int pp = 0; // Index for protected packets array
						boolean found = false; // Flag to indicate if a packet was found
						int lostSeqNum = -1; // Sequence number of the last checked lost packet
						RTPpacket[] protectedPackets = new RTPpacket[maskLength]; // Array to store protected packets found in the buffer
						RTPpacket lostPacket = null;
						
						
						// Iterate through the FECpacket's protected packets sequence numbers and look for them in the protection buffer
						int[] protectedSequenceNb = fec_packet.getProtectedSequenceNumbers();
						int seqNum = 0; // Sequence number of the packet being checked

						for(int i = 0; i < protectedSequenceNb.length; i++) {
							seqNum = protectedSequenceNb[i]; // Get the sequence number from the FEC packet
							found = false; // Reset found flag for each sequence number
							// Check if the packet is in the protection buffer
							for (RTPpacket packet : protectionBuffer) {
								if (packet.getSequenceNumber() == seqNum) {
									protectedPackets[pp] = packet; // Store the found packet
									pp++; // Increment the index for protected packets
									found = true; // Mark that we found the packet
									break; // Exit the inner loop (no need to check further)
								}
							}
							if (!found) {
								// If we reached the end of the loop and did not find any packet, it means we have lost packets
								lostSeqNum = seqNum; // Store the sequence number of the lost packet
							}
						}
						
						// Now that the protection buffer has been searched, we can get the number of missing packets using the array length
						if (pp < maskLength) {
							if(maskLength-pp == 1) {
								// If we found exactly one packet missing, we can recover it
								lostPacket = fec_packet.recoverPacket(protectedPackets, lostSeqNum-baseSeqNum); // (lostSeqNum - baseSeqNum) is the index of the lost packet in the protected packets array
								videoBuffer.offer(lostPacket); // Add the recovered packet to the video buffer (it should be ordered by sequence number automatically)
								
								// Update video buffer state
								videoBufferBar.putBufferState(lostPacket.getSequenceNumber(), BufferBar.FrameStatus.RECOVERED);
								
								// Remove the FEC group packets from the protection buffer
								for (int i = 0; i < protectedPackets.length; i++) {
									if (protectedPackets[i] != null) {
										protectionBuffer.remove(protectedPackets[i]);
									}
								}
								
								videoStats.recoveredPackets++; // Increment lost packets count
								
								// Update stats text (invokeLater to avoid deadlock)
								/*String statsStr = getStats();
								SwingUtilities.invokeLater(() -> statsPane.setText(statsStr));*/
								
							} else {
								// If we found more than one packet missing, we cannot recover them
								System.out.println("Warning: More than one packet lost, FEC cannot recover them. Lost packets: " + (maskLength - pp));
							}
						}
		            }
	            } catch (InterruptedException ie) {
	                if (running) {
	                    System.out.println("[FECListener] Exception caught: " + ie);
	                }
	            } catch (Exception e) {
	                System.out.println("[FECListener] Exception caught: " + e);
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
						
						// Update buffer size stats (synchronized)
						synchronized (videoStats.bufferLock) {
							videoStats.bufferSize = videoBuffer.size();
						}
						
						// get the payload bitstream from the RTPpacket object
						int payload_length = rtp_packet.getPayloadLength();
						byte[] payload = new byte[payload_length];
						payload = rtp_packet.getPayload();
						
						// get an Image object from the payload bitstream
						Toolkit toolkit = Toolkit.getDefaultToolkit();
						Image image = toolkit.createImage(payload, 0, payload_length);
						
						// Check if current rtp_packet sequence number is lower than the last packet number
						if (rtp_packet.getSequenceNumber() < videoStats.lastPlayedPacketNb) {
							// If the packet is older than the last packet, print a warning
							System.out.println("Warning: Received an old video packet with SeqNum # " + rtp_packet.getSequenceNumber() +
									" - Last played packet SeqNum # " + videoStats.lastPlayedPacketNb+ ". Skipping frame...");
							videoStats.latePackets++; // Increment late packet count
							// Update buffer state for late packets
							videoBufferBar.putBufferState(rtp_packet.getSequenceNumber(), BufferBar.FrameStatus.LATE);
						} else {
							// Update video-specific stats
							videoStats.lastPlayedPacketNb = rtp_packet.getSequenceNumber(); // Update last played packet number
							videoStats.framesSinceUpdate++;
							// Update FPS calculation
							long now = System.currentTimeMillis();
							if (now - videoStats.lastFpsUpdateTime >= 1000) {
								videoStats.currentFps = videoStats.framesSinceUpdate / ((now - videoStats.lastFpsUpdateTime) / 1000.0);
								videoStats.framesSinceUpdate = 0;
								videoStats.lastFpsUpdateTime = now;							
							}
							// display the image as an ImageIcon object
							SwingUtilities.invokeLater(() -> {
								icon = new ImageIcon(image);
								iconLabel.setIcon(icon);
							});
						}
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

						// Check if current rtp_packet sequence number is lower than the last packet number
						if (rtp_packet.getSequenceNumber() < audioStats.lastPlayedPacketNb) {
							// If the packet is older than the last packet, print a warning
							System.out.println("Warning: Received an old audio packet with SeqNum # " + rtp_packet.getSequenceNumber() +
									" - Last played packet SeqNum # " + audioStats.lastPlayedPacketNb + ". Skipping frame...");
							audioStats.latePackets++; // Increment late packet count
						} else {
							audioStats.lastPlayedPacketNb = rtp_packet.getSequenceNumber(); // Update last received packet number
							// write the data to the speaker
							speaker.write(payload, 0, payload_length);
						}
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
			if (fecThread != null && fecThread.isAlive()) {
				fecThread.interrupt();
			}
			// Stop stats timer
			if (statsTimer != null) {
				statsTimer.cancel(); // Cancel the stats timer
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
			if (fecQueue != null) {
				fecQueue.clear();
			}
			if (protectionBuffer != null) {
				protectionBuffer.clear();
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