
/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */


import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Server extends JFrame implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	
	//Verbose mode
	boolean verbose = false;
	
	//RTP variables:
	//----------------
	DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
	DatagramPacket senddp; //UDP packet containing the video frames
		
	InetAddress ClientIPAddr; //Client IP address
	int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)
		
	//GUI:
	//----------------
	JLabel label;
		
	// Video & audio variables
	//----------------
	Codec codec;
		
	//Video variables:
	//----------------
	int imagenb = 0; //image nb of the image currently transmitted
	static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
	int FRAME_PERIOD = 20; //Frame period of the video to stream, in ms
	int VIDEO_LENGTH; //length of the video in frames
		
	Timer timer; //timer used to send the images at the video frame rate
	byte[] buf; //buffer used to store the images to send to the client 
		
	//Audio variables
	boolean audio_mode = false;
	static int RAW_TYPE = 0;
		
	//RTSP variables
	//----------------
	//rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	//rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
		
	static int state; //RTSP Server state == INIT or READY or PLAY
	Socket RTSPsocket; //socket used to send/receive RTSP messages
	//input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; //video file requested from the client
	static int RTSP_ID = 123456; //ID of the RTSP session
	int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
		
	final static String CRLF = "\r\n";
	
	//--------------------------------
	//Constructor
	//--------------------------------
	public Server(){
	
	  //init Frame
	  super("Server");
	  //init Timer (TIMER DOES NOT LONGER GET INITIATED HERE)
	  /*timer = new Timer(FRAME_PERIOD, this);
	  timer.setInitialDelay(0);
	  timer.setCoalesce(true);
	   */
	  //allocate memory for the sending buffer
	  buf = new byte[15000]; 
	
	  //Handler to close the main window
	  addWindowListener(new WindowAdapter() {
	    public void windowClosing(WindowEvent e) {
		//stop the timer, close sockets and streams and exit
		cleanExit();
	    }});
	  
	  //GUI:
	  setBounds(0, 375, 390, 60);
	  setPreferredSize(new Dimension(390, 60));
	  //set the label
	  label = new JLabel("Send frame #        ", JLabel.LEFT);
	  getContentPane().add(label, BorderLayout.CENTER);
	}
	        
	//------------------------------------
	//main
	//------------------------------------
	public static void main(String argv[]) throws Exception
	{
	  //create a Server object
	  Server theServer = new Server();
	
	  //check the number of arguments// check for verbose
		if (argv.length >= 2 && argv[1].equals("-v")) {
			theServer.verbose = true;
			System.out.println("Verbose mode: ACTIVE");
		}
		
	  //show GUI:
	  theServer.pack();
	  theServer.setVisible(true);
	  //get RTSP socket port from the command line
	  int RTSPport = Integer.parseInt(argv[0]);
	 
	  //Initiate TCP connection with the client for the RTSP session
	  ServerSocket listenSocket = new ServerSocket(RTSPport);
	  theServer.RTSPsocket = listenSocket.accept();
	  listenSocket.close();
	
	  //Get Client IP address
	  theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();
	
	  //Initiate RTSPstate
	  state = INIT;
	
	  //Set input and output stream filters:
	  BufferedReader reader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()) );
	  RTSPBufferedReader = reader;
	  BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()) );
	  RTSPBufferedWriter = writer;
	
	  //Wait for the SETUP message from the client
	  int request_type;
	  boolean done = false;
	  while(!done)
	    {
		request_type = theServer.parse_RTSP_request(); //blocking
		
		if (request_type == SETUP)
		  {
		    done = true;
	
		    //update RTSP state
		    state = READY;
		    System.out.println("New RTSP state: READY\n");
	 
		    //Send response
		    theServer.send_RTSP_response();
	
		    // Initialize video
		    theServer.codec = new Codec(VideoFileName);
	
		    //  Init video properties
		    theServer.VIDEO_LENGTH = theServer.codec.getNumFrames();
		    theServer.FRAME_PERIOD = (int) (1000/theServer.codec.fps);
		    theServer.timer = new Timer(theServer.FRAME_PERIOD, theServer);
		    theServer.timer.setInitialDelay(0);
		    theServer.timer.setCoalesce(true);
		    if(!theServer.verbose)
		    	System.out.println("DEBUG: FPS: "+theServer.codec.fps+ " FRAME_PERIOD: "+theServer.FRAME_PERIOD);
		    
		    
		    //init RTP socket
		    theServer.RTPsocket = new DatagramSocket();
		  }
	    }
	
	   //loop to handle RTSP requests
	  while(true)
	    {
		//parse the request
		request_type = theServer.parse_RTSP_request(); //blocking
		
		if ((request_type == PLAY) && (state == READY))
		  {
		    //send back response
		    theServer.send_RTSP_response();
		    //start timer
		    theServer.timer.start();
		    //update state
		    state = PLAYING;
		    System.out.println("New RTSP state: PLAYING");
		  }
		else if ((request_type == PAUSE) && (state == PLAYING))
		  {
		    //send back response
		    theServer.send_RTSP_response();
		    //stop timer
		    theServer.timer.stop();
		    //update state
		    state = READY;
		    System.out.println("New RTSP state: READY");
		  }
		else if (request_type == TEARDOWN)
		  {
		    //send back response
		    theServer.send_RTSP_response();
	
		    // Clean exit
		    theServer.cleanExit();
		  }
	    }
	}
	
	
	//------------------------
	//Handler for timer
	//------------------------
	public void actionPerformed(ActionEvent e) {	
	     
		try {
			//if the current image nb is less than the length of the video
		    if (imagenb < VIDEO_LENGTH) {
				// --- Send video frame ---
				//update current imagenb
				imagenb++;
				int video_length = codec.getnextframe(buf);
				RTPpacket video_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb * FRAME_PERIOD, buf, video_length);
				byte[] video_bits = new byte[video_packet.getlength()];
				video_packet.getpacket(video_bits);
				senddp = new DatagramPacket(video_bits, video_bits.length, ClientIPAddr, RTP_dest_port);
				//DEBUG: Add random lost packets
				  //if((Math.random()*100d) < 95)
				RTPsocket.send(senddp);
				 //print the header bitstream
				  if(verbose)
					  video_packet.printheader();
				//update GUI
				  label.setText("Send frame #" + imagenb);
			  } else {
				//if we have reached the end of the video file, stop the timer
				timer.stop();
		      }
		    
			
		  //if the current image nb is less than the length of the video
		    if (imagenb < VIDEO_LENGTH) {
				// --- Send audio chunk ---
				//update current imagenb
				imagenb++;
				int audio_length = codec.getnextchunk(buf);
				RTPpacket audio_packet = new RTPpacket(RAW_TYPE, imagenb, imagenb * FRAME_PERIOD, buf, audio_length);
				byte[] audio_bits = new byte[audio_packet.getlength()];
				audio_packet.getpacket(audio_bits);
				senddp = new DatagramPacket(audio_bits, audio_bits.length, ClientIPAddr, RTP_dest_port);
				//DEBUG: Add random lost packets
				  //if((Math.random()*100d) < 95)
				RTPsocket.send(senddp);
				 //print the header bitstream
				  if(verbose)
					  audio_packet.printheader();
				//update GUI
				  label.setText("Send frame #" + imagenb);
		      } else {
	  		//if we have reached the end of the video file, stop the timer
	  		timer.stop();
		      }
		 
		  
		  //update GUI
		  //label.setText("Send frame #" + imagenb);
		} catch(Exception ex)
		  {
			if(verbose)
				System.out.println("DEBUG: actionPerformed()");
		    System.out.println("Exception caught: "+ex);
		    cleanExit();
		  }
	  
	}
	
	//------------------------------------
	//Parse RTSP Request
	//------------------------------------
	private int parse_RTSP_request()
	{
	  int request_type = -1;
	  try{
	    //parse request line and extract the request_type:
	    String RequestLine = RTSPBufferedReader.readLine();
	    System.out.println("RTSP Server - Received from Client:");
	    System.out.println("\t"+RequestLine);
	
	    StringTokenizer tokens = new StringTokenizer(RequestLine);
	    String request_type_string = tokens.nextToken();
	    
	    //convert to request_type structure:
	    if ((new String(request_type_string)).compareTo("SETUP") == 0)
		request_type = SETUP;
	    else if ((new String(request_type_string)).compareTo("PLAY") == 0)
		request_type = PLAY;
	    else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
		request_type = PAUSE;
	    else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
		request_type = TEARDOWN;
	
	    if (request_type == SETUP)
		{
		  //extract VideoFileName from RequestLine
		  VideoFileName = tokens.nextToken();
		}
	
	    //parse the SeqNumLine and extract CSeq field
	    String SeqNumLine = RTSPBufferedReader.readLine();
	    System.out.println("\t"+SeqNumLine);
	    tokens = new StringTokenizer(SeqNumLine);
	    tokens.nextToken();
	    RTSPSeqNb = Integer.parseInt(tokens.nextToken());
		
	    //get LastLine
	    String LastLine = RTSPBufferedReader.readLine();
	    System.out.println("\t"+LastLine);
	
	    if (request_type == SETUP)
		{
		  //extract RTP_dest_port from LastLine
		  tokens = new StringTokenizer(LastLine);
		  for (int i=0; i<3; i++)
		    tokens.nextToken(); //skip unused stuff
		  RTP_dest_port = Integer.parseInt(tokens.nextToken());
		}
	    //else LastLine will be the SessionId line ... do not check for now.
	  }
	  catch(Exception ex)
	    {
		if(verbose)
			System.out.println("DEBUG: parse_RTSP_request()");
		System.out.println("Exception caught: "+ex);
		cleanExit();
	    }
	  System.out.println();
	  return(request_type);
	}
	
	//------------------------------------
	//Send RTSP Response
	//------------------------------------
	private void send_RTSP_response()
	{
	  try{
	    RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
	    RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
	    RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
	    RTSPBufferedWriter.flush();
	    //System.out.println("RTSP Server - Sent response to Client.");
	  }
	  catch(Exception ex)
	    {
			if(verbose)
		    	System.out.println("DEBUG: send_RTSP_response()");
			System.out.println("Exception caught: "+ex);
			cleanExit();
	    }
	}
	
	// Cleanup method
	private void cleanExit() {
		try {
			// Stop the timer
			if (timer != null) {
				timer.stop();
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
			// Close codec
			if (codec != null) {
				codec.close();
			}
		} catch (IOException e) {
		  e.printStackTrace();
		} finally {
			// Exit the program
			System.exit(0);		
		}
	}
}
