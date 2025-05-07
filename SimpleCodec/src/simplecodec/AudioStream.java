package simplecodec;


//AudioStream

import java.io.*;
import java.net.URL;
import java.nio.*;
import javax.sound.sampled.*;

public class AudioStream {

  //InputStream fis; //audio file
  BufferedInputStream fis; //Buffer to read file
  int frame_nb; //current frame nb
  int fps;
  int SamplingRate;
  int bitDepth;
  int channelCount;

  //-----------------------------------
  //constructor
  //-----------------------------------
  public AudioStream(String filename) throws Exception{

     File file = new File (filename);  
            if (file==null)
                System.out.println("Error: No file to read");
             fis= new BufferedInputStream (new FileInputStream(file));
                
  }
  public void setFPS(int _fps){
    this.fps=_fps;
  }
  
  public void setbitDepth(int _bit){
    this.bitDepth=_bit;
    }
  public void setSampleRate(int _sample){
      this.SamplingRate=_sample;
  }

  public void setChannelCount (int _channel){
      this.channelCount=_channel;
  }
  //-----------------------------------
  // getnextchunk
  //returns the next chunk of audio as an array of byte and the size of the frame
  //-----------------------------------
  public int getnextchunk(byte[] frame) throws Exception
  {
    int length = 0;
    String length_string;
    int lenght =(int)(this.SamplingRate*(this.bitDepth/8)*this.channelCount/this.fps);//Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/10 (1 packet in 0,1 sec) 
    
  // returns the length of data copied in buffer
    int count = fis.read(frame, 0,lenght);

    return(count);
  }
}
