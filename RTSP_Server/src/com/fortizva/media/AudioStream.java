//AudioStream
package com.fortizva.media;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

public class AudioStream {

  //InputStream fis; //audio file
  BufferedInputStream fis; //Buffer to read file
  int frame_nb; //current frame nb

  //-----------------------------------
  //constructor
  //-----------------------------------
  public AudioStream(String filename) throws Exception{

    File file = new File (filename);  
    if (file.exists() == false || file.isFile() == false)
        System.out.println("Error: No file to read");
     fis= new BufferedInputStream (new FileInputStream(file));
                
  }

  //-----------------------------------
  // getnextchunk
  //returns the next chunk of audio as an array of byte and the size of the frame
  //-----------------------------------
  public int getnextchunk(byte[] frame) throws Exception
  {
    int length = 0;
    length =(int)44100*(16/8)*1/10;//Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/10 (1 packet in 0,1 sec) 
    
  // returns the length of data copied in buffer
    int count = fis.read(frame, 0,length);

    return(count);
  }
}
