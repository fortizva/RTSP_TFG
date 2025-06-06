package com.fortizva.media;
//VideoStream

import java.io.*;
import java.nio.ByteBuffer;

public class Codec {

  FileInputStream fis; //video file
  int frame_nb; //current frame nb
  
     
    byte Version;   
    byte fps;
    int numFrames;
    int width;
    int heigh;
    byte nAudioTracks;
    int SamplingRate;
    byte bitDepth;
    byte channelCount;

  //-----------------------------------
  //constructor
  //-----------------------------------
  public Codec(String filename) throws Exception{

    //init variables
    fis = new FileInputStream(filename);
    frame_nb = 0;
    this.readHeader();
        
  }
  
  
  public void readHeader() throws Exception{
             
    byte []_Version=new byte[1];   
    byte []_fps=new byte[1];
    byte []_numFrames=new byte[4];
    byte []_width=new byte[4];
    byte []_heigh=new byte[4];
    byte []_nAudioTracks=new byte[1];
    byte []_SamplingRate=new byte[4];
    byte []_bitDepth=new byte[1];
    byte []_channelCount=new byte[1];
      
            fis.read(_Version, 0, 1);
            Version=_Version[0];
            
            fis.read(_fps, 0, 1);
            fps=_fps[0];
            
            fis.read(_numFrames, 0, 4);
            numFrames=this.ByteArraytoInt(_numFrames);
            
            fis.read(_width, 0, 4);
            width=this.ByteArraytoInt(_width);
            
            fis.read(_heigh, 0, 4);
            heigh=this.ByteArraytoInt(_heigh);
            
            fis.read(_nAudioTracks, 0, 1);
            nAudioTracks=_nAudioTracks[0];

            
            fis.read(_SamplingRate, 0, 4);            
            SamplingRate=this.ByteArraytoInt(_SamplingRate);
            
            fis.read(_bitDepth, 0, 1);
            bitDepth=_bitDepth[0];
            
            fis.read(_channelCount, 0, 1);
            channelCount=_channelCount[0];
      
  }
    private int ByteArraytoInt (byte[] i){
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
        bb.put(i);
        bb.rewind();
        return bb.getInt();
    }
  //-----------------------------------
  // getnextframe
  //returns the next frame as an array of byte and the size of the frame
  //-----------------------------------
  public int getnextframe(byte[] frame) throws Exception
  {
    int length = 0;
    String length_string;
    byte[] frame_length = new byte[5];
    
    //read current frame length
    fis.read(frame_length,0,5);
	

    //transform frame_length to integer
    length_string = new String(frame_length);
    length = Integer.parseInt(length_string);
	
    return(fis.read(frame,0,length));
  }
  
    public int getnextchunk(byte[] frame) throws Exception
  {
    //int length = 0;
    //String length_string;
    int length =(int)(this.SamplingRate*(this.bitDepth/8.0)*this.channelCount/this.fps);//Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/10 (1 packet in 0,1 sec) 
    
  // returns the length of data copied in buffer
    int count = fis.read(frame, 0,length);
    return(count);

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


	public void close() throws IOException {
    	if(fis != null) {	
    		fis.close();
		}
    }
}
