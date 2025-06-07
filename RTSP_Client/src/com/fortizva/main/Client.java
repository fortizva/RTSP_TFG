package com.fortizva.main;
/* ------------------
   Client
   usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
   ---------------------- */

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import javax.swing.Timer;

import com.fortizva.packets.FECpacket;
import com.fortizva.packets.RTPpacket;

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
	// 	FPS
	long last_fps_update_time = 0;
	int last_fps_video_nb = 0;
	double current_fps = 0;
	// ----

	// DEBUG
	// ----
	static boolean verbose = false;

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
	static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

	// RTP packet buffer
	final static int BUFFER_TIMEOUT = 100; // Timeout for the buffer
	BlockingQueue<RTPpacket> videoBuffer;
	BlockingQueue<RTPpacket> audioBuffer;
	
	// Timer variables:
	static int video_frame_period = 40; // video frame period in msec
	static int audio_frame_period = 40; // audio frame period in msec
	
	Thread rtpSocketListener; // thread used to receive data from the UDP socket
	Timer videoTimer; // timer used to receive data from the UDP socket
	Timer audioTimer; // timer used to receive data from the UDP socket
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
		videoBuffer = new LinkedBlockingQueue<RTPpacket>(500);
		audioBuffer = new LinkedBlockingQueue<RTPpacket>(500);
		
		// init timer
		// --------------------------
		rtpSocketListener = new Thread(new RTPSocketListener());
		
		videoTimer = new Timer(video_frame_period, new videoTimerListener());
		videoTimer.setInitialDelay(0);
		videoTimer.setCoalesce(true);
		
		audioTimer = new Timer(audio_frame_period, new audioTimerListener());
		audioTimer.setInitialDelay(0);
		audioTimer.setCoalesce(true);

		
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

	private static void testFECpacket() {
	    // Create dummy RTP packets with distinct payloads, sequence numbers, and timestamps
	    RTPpacket[] rtpPackets = new RTPpacket[3];
	    rtpPackets[0] = new RTPpacket(0, 100, 1000, new byte[] {0x01, 0x02, 0x03}, 3);
	    rtpPackets[1] = new RTPpacket(0, 101, 2000, new byte[] {0x04, 0x05, 0x06}, 3);
	    rtpPackets[2] = new RTPpacket(0, 102, 3000, new byte[] {0x07, 0x08, 0x09}, 3);
	
	    // Create FECpacket
	    FECpacket fecPacket = new FECpacket(rtpPackets);
	
	    // Retrieve FEC packet bytes
	    byte[] fecPacketBytes = new byte[fecPacket.getFECPacketSize()];
	    fecPacket.getFECPacket(fecPacketBytes);
	
	    // Print FEC packet bytes in hex format
	    System.out.println("FEC Packet Bytes:");
	    for (int i = 0; i < fecPacketBytes.length; i++) {
			System.out.print(String.format("%02X ", fecPacketBytes[i]));
		}
	    System.out.println();
	    
	    // Print FEC packet fields with expected values
	    System.out.println("FEC Packet Fields:");
	
	    // Flags
	    System.out.println("Flags: " + String.format("%02X %02X", fecPacketBytes[0], fecPacketBytes[1]) + 
	        " | Expected: 00 00");
	
	    // Base Sequence Number
	    int baseSequenceNumber = (fecPacketBytes[2] << 8) | (fecPacketBytes[3] & 0xFF);
	    System.out.println("Base Sequence Number: " + baseSequenceNumber + 
	        " | Bytes: " + String.format("%02X %02X", fecPacketBytes[2], fecPacketBytes[3]) + 
	        " | Expected: 100");
	
	    // Timestamp Recovery
	    int timestampRecovery = (fecPacketBytes[4] << 24) | ((fecPacketBytes[5] & 0xFF) << 16) | 
	                            ((fecPacketBytes[6] & 0xFF) << 8) | (fecPacketBytes[7] & 0xFF);
	    System.out.println("Timestamp Recovery: " + timestampRecovery + 
	        " | Bytes: " + String.format("%02X %02X %02X %02X", fecPacketBytes[4], fecPacketBytes[5], fecPacketBytes[6], fecPacketBytes[7]) + 
	        " | Expected: 1000 ^ 2000 ^ 3000 = 3968");
	
	    // Length Recovery
	    int lengthRecovery = (fecPacketBytes[8] << 8) | (fecPacketBytes[9] & 0xFF);
	    System.out.println("Length Recovery: " + lengthRecovery + 
	        " | Bytes: " + String.format("%02X %02X", fecPacketBytes[8], fecPacketBytes[9]) + 
	        " | Expected: 3 ^ 3 ^ 3 = 3");
	
	    // Protection Length
	    int protectionLength = (fecPacketBytes[10] << 8) | (fecPacketBytes[11] & 0xFF);
	    System.out.println("Protection Length: " + protectionLength + 
	        " | Bytes: " + String.format("%02X %02X", fecPacketBytes[10], fecPacketBytes[11]) + 
	        " | Expected: 3");
	
	    // Protection Mask
	    System.out.println("Protection Mask: " + String.format("%02X %02X", fecPacketBytes[12], fecPacketBytes[13]) + 
	        " | Expected: E0 00");
	
	    // XOR Payload
	    System.out.print("XOR Payload: ");
	    for (int i = 14; i < fecPacketBytes.length; i++) {
	        System.out.print(String.format("%02X ", fecPacketBytes[i]));
	    }
	    System.out.println("| Expected: XOR of payloads {01, 02, 03}, {04, 05, 06}, {07, 08, 09} = 02 0F 0C");
	}

	
	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {
		// DEBUG: Create a test FEC packet and try to recover the original RTP packets
		testFECpacket();

		// DEBUG: End of FECpacket test

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

	// Handler for SETUP button
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

	// Handler for PLAY button
	// -----------------------
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

					// start the timer
					videoTimer.start();
					audioTimer.start();
					// Check if the thread is already running
					if (!rtpSocketListener.isAlive()) {
						rtpSocketListener.start(); // start the RTP socket listener thread
					}
				}
			} // else if state != READY then do nothing
		}
	}

	// Handler for PAUSE button
	// -----------------------
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

					// stop the timer
					videoTimer.stop();
					audioTimer.stop();
					// RTPSocketListener should pause by itself on state change
				}
			}
			// else if state != PLAYING then do nothing
		}
	}

	// Handler for TEARDOWN button
	// -----------------------
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

				// stop the timer
				videoTimer.stop();
				audioTimer.stop();
				// RTPSocketListener should stop by itself on state change

				// exit
				cleanExit();
			}
		}
	}

	// ------------------------------------
	// Handler for RTP socket thread
	// ------------------------------------
	
	class RTPSocketListener implements Runnable {
		public void run() {
			// Keep listening to the RTP socket until the state is INIT or the thread is interrupted (End of program)
			while(state != Client.INIT && !Thread.currentThread().isInterrupted()) {
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
							System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber() + " TimeStamp "
									+ rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());
	
							// print header bitstream:
							rtp_packet.printheader();
						}
						
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
						// Update stats text (invokeLater to avoid deadlock)
						SwingUtilities.invokeLater(()->statsLabel.setText(getStats()));
						// ------------------------------
						
						// Check if the packet is audio or video
						if(rtp_packet.getpayloadtype() == RAW_TYPE) {
							audioBuffer.put(rtp_packet);
						} else if(rtp_packet.getpayloadtype() == MJPEG_TYPE) {
							videoBuffer.put(rtp_packet);
						} else {
							System.out.println("Unknown payload type: " + rtp_packet.getsequencenumber());
						}
					} catch (InterruptedIOException iioe) {
						// We can ignore this exception as it is just a timeout
						// System.out.println("Nothing to read");
					} catch (InterruptedException ie) {
						System.out.println("[RTPSocketListener] InterruptedException caught: " + ie);
						break; // Exit the loop if interrupted
					} catch (SocketException se) {
						if(verbose)
							System.out.println("[RTPSocketListener] Socket closed, exiting thread...");
						break; // Exit the loop if socket is closed
					} catch (IOException ioe) {
						System.out.println("[RTPSocketListener] Exception caught: " + ioe);
					}
				}
			}
		}
	}
	
	
	// ------------------------------------
	// Handler for timer
	// ------------------------------------

	class videoTimerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			try {
				RTPpacket rtp_packet = videoBuffer.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS); // Blocking call
				if(rtp_packet != null) {				
					// get the payload bitstream from the RTPpacket object
					int payload_length = rtp_packet.getpayload_length();
					byte[] payload = new byte[payload_length];
					rtp_packet.getpayload(payload);
					
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
					icon = new ImageIcon(image);
					iconLabel.setIcon(icon);
					
				} else {
				}
			} catch (InterruptedException ie) {
				System.out.println("[videoTimerListener] Exception caught: " + ie);
			}
		}
	}
	
	class audioTimerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			try {
				RTPpacket rtp_packet = audioBuffer.poll(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS); // Blocking call
				if(rtp_packet != null) {				
					// get the payload bitstream from the RTPpacket object
					int payload_length = rtp_packet.getpayload_length();
					byte[] payload = new byte[payload_length];
					rtp_packet.getpayload(payload);
					
					// write the data to the speaker
					speaker.write(payload, 0, payload_length);
					
					
				} else {
				}
			} catch (InterruptedException ie) {
				System.out.println("[audioTimerListener] Exception caught: " +ie);
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
			System.out.println("[parseServerResponse] Exception caught: " + ex);
			cleanExit();
		}

		return (reply_code);
	}

	// ------------------------------------
	// Send RTSP Request
	// ------------------------------------

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
			RTSPBufferedWriter.write(request_line + CRLF);
			if (verbose)
				System.out.println("C: " + request_line);

			// write the CSeq line
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			if (verbose)
				System.out.println("C: " + "CSeq: " + RTSPSeqNb + CRLF);
			/*
			 * If the request type is "SETUP", 
			 * write the Transport header to inform the server of the client's RTP receiving port.
			 * For all other request types, write the Session header with the current RTSP session ID.
			*/
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
			System.out.println("[sendRTSPRequest] Exception caught: " + ex);
			cleanExit();
		}
	}
	
	// ------------------------------------
	// Clean exit
	// ------------------------------------
	private void cleanExit() {
		try {
			// Stop RTP socket listener thread
			if (rtpSocketListener != null && rtpSocketListener.isAlive()) {
				rtpSocketListener.interrupt();
			}
			// Stop timers and close soundcard
			if (videoTimer != null) {
				videoTimer.stop();
			}
			if (audioTimer != null) {
				audioTimer.stop();
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

}// end of Class Client
