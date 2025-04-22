/* ------------------
   Client
   usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
   ---------------------- */

import java.io.*;
import java.net.*;
import java.sql.Time;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Array;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;

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
	static int lost_packets = 0;
	static int packet_loss = 0;
	// Retardo y jitter
	static long packet_delay = 0l;
	static long last_packet_time = 0l;
	static long last_packet_delay = 0l;
	static long jitter = 0l;
	// ---

	// DEBUG
	// ----
	static boolean verbose = false;

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
	static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

	Timer timer; // timer used to receive data from the UDP socket
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

	final static String CRLF = "\r\n";

	SourceDataLine speaker; // Audio datasource to speaker

	// Player mode
	static boolean audio_mode = false;

	// Video constants:
	// ------------------
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

	// Audio constants:
	static int RAW_TYPE = 0;

	// --------------------------
	// Constructor
	// --------------------------
	public Client() {

		// build GUI
		// --------------------------

		// Frame
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		/*
		 * stats.addWindowListener(new WindowAdapter() { public void
		 * windowClosing(WindowEvent e) { System.exit(0); } });
		 */

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
		statsLabel.setBounds(0, 0, 140, 210);
		statsLabel.setVisible(true);
		statsPanel.add(statsLabel);
		stats.getContentPane().add(statsPanel, BorderLayout.CENTER);
		// stats.setSize(new Dimension(150, 220));
		stats.setBounds(400, 0, 180, 220);
		stats.setVisible(true);

		// init timer
		// --------------------------
		timer = new Timer(20, new timerListener());
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		// allocate enough memory for the buffer used to receive data from the server
		buf = new byte[15000];
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
		s += "<br>Last packet #: " + last_packet_nb + "</br>";
		s += "<br>Lost packets: " + lost_packets + "</br>";
		s += "<br>Packet loss: " + packet_loss + "%</br>";
		s += "<br>Packet delay (milis): " + packet_delay + "</br>";
		s += "<br>Received bytes: " + received_bytes + "</br>";
		s += "<br>Jitter (milis): " + ((jitter >= 0) ? "+" : "") + jitter + "</br>";
		s += "</html>";
		return s;
	}

	// ------------------------------------
	// main
	// ------------------------------------
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

		// Initalize audio if extension is RAW
		if (VideoFileName.endsWith(".raw")) {
			audio_mode = true;
			if (verbose)
				System.out.println("Audio file extension detected, initializing audio line...");
			theClient.audio_initialization();
		}

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

	// .............
	// TO COMPLETE
	// .............

	// Handler for Setup button
	// -----------------------
	class setupButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Setup Button pressed !");

			if (state == INIT) {
				// Init non-blocking RTPsocket that will be used to receive data
				try {
					// construct a new DatagramSocket to receive RTP packets from the server, on
					// port RTP_RCV_PORT
					RTPsocket = new DatagramSocket(RTP_RCV_PORT);
					// set TimeOut value of the socket to 5msec.
					RTPsocket.setSoTimeout(5000);
					// ....

				} catch (SocketException se) {
					System.out.println("Socket exception: " + se);
					System.exit(0);
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

	// Handler for Play button
	// -----------------------
	class playButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Play Button pressed !");

			if (state == READY) {
				// increase RTSP sequence number
				// .....
				RTSPSeqNb++;

				// Send PLAY message to the server
				send_RTSP_request("PLAY");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					// .....
					state = Client.PLAYING;
					if (verbose)
						System.out.println("New RTSP state: PLAYING (" + state + ")");

					// start the timer
					timer.start();
				}
			} // else if state != READY then do nothing
		}
	}

	// Handler for Pause button
	// -----------------------
	class pauseButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Pause Button pressed !");

			if (state == PLAYING) {
				// increase RTSP sequence number
				// ........
				RTSPSeqNb++;

				// Send PAUSE message to the server
				send_RTSP_request("PAUSE");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					// ........
					state = Client.READY;
					if (verbose)
						System.out.println("New RTSP state: READY (" + state + ")");

					// stop the timer
					timer.stop();
				}
			}
			// else if state != PLAYING then do nothing
		}
	}

	// Handler for Teardown button
	// -----------------------
	class tearButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (verbose)
				System.out.println("Teardown Button pressed !");

			// increase RTSP sequence number
			// ..........
			RTSPSeqNb++;

			// Send TEARDOWN message to the server
			send_RTSP_request("TEARDOWN");

			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// change RTSP state and print out new state
				// ........
				state = Client.INIT;
				if (verbose)
					System.out.println("New RTSP state: INIT(" + state + ")");

				// stop the timer
				timer.stop();

				// exit
				System.exit(0);
			}
		}
	}

	// ------------------------------------
	// Handler for timer
	// ------------------------------------

	class timerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			// Construct a DatagramPacket to receive data from the UDP socket
			rcvdp = new DatagramPacket(buf, buf.length);

			try {
				// receive the DP from the socket:
				RTPsocket.receive(rcvdp);

				// create an RTPpacket object from the DP
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

				// -----------------------------
				// Update stats
				// -----------------------------
				// Packet loss
				if (rtp_packet.getsequencenumber() > last_packet_nb + 1) {
					lost_packets += (rtp_packet.getsequencenumber() - last_packet_nb);
					last_packet_nb = rtp_packet.getsequencenumber();
					packet_loss = (lost_packets * 100) / (last_packet_nb + 1);
				} else
					last_packet_nb = rtp_packet.getsequencenumber();

				// Packet delay
				last_packet_delay = packet_delay; // Save last packet delay to calculate jitter
				packet_delay = (last_packet_time == 0) ? 0 : System.currentTimeMillis() - last_packet_time;
				last_packet_time = System.currentTimeMillis();

				// Jitter
				jitter = last_packet_delay - packet_delay;

				// Bytes received
				received_bytes += rcvdp.getLength();
				// -----------------------------
				statsLabel.setText(getStats());

				// print important header fields of the RTP packet received:
				if (verbose)
					System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber() + " TimeStamp "
							+ rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());

				// print header bitstream:
				rtp_packet.printheader();

				// get the payload bitstream from the RTPpacket object
				int payload_length = rtp_packet.getpayload_length();
				byte[] payload = new byte[payload_length];
				rtp_packet.getpayload(payload);

				if (audio_mode) {
					// Write bytes to audio line
					speaker.write(payload, 0, payload_length);
				} else {
					// get an Image object from the payload bitstream
					Toolkit toolkit = Toolkit.getDefaultToolkit();
					Image image = toolkit.createImage(payload, 0, payload_length);

					// display the image as an ImageIcon object
					icon = new ImageIcon(image);
					iconLabel.setIcon(icon);
				}
			} catch (InterruptedIOException iioe) {
				// System.out.println("Nothing to read");
			} catch (IOException ioe) {
				System.out.println("Exception caught: " + ioe);
			}
		}
	}

	// ------------------------------------
	// Parse Server Response
	// ------------------------------------
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
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}

		return (reply_code);
	}

	// ------------------------------------
	// Send RTSP Request
	// ------------------------------------

	// .............
	// TO COMPLETE
	// .............

	private void send_RTSP_request(String request_type) {
		try {
			// Use the RTSPBufferedWriter to write to the RTSP socket

			// write the request line:
			// RTSPBufferedWriter.write(...);
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
			RTSPBufferedWriter.write(request_line + CRLF);
			if (verbose)
				System.out.println("C: " + request_line);

			// write the CSeq line:
			// ......
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			if (verbose)
				System.out.println("C: " + "CSeq: " + RTSPSeqNb + CRLF);

			// check if request_type is equal to "SETUP" and in this case write the
			// Transport: line advertising to the server the port used to receive the RTP
			// packets RTP_RCV_PORT
			// if ....
			// otherwise, write the Session line from the RTSPid field
			// else ....
			if (request_type.equals("SETUP")) {
				RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
				if (verbose)
					System.out.println("C: " + "Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
			} else {
				RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
				if (verbose)
					System.out.println("C: " + "Session: " + RTSPid + CRLF);
			}

			RTSPBufferedWriter.flush();
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
	}

}// end of Class Client
