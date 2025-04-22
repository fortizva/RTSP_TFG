
//AudioStream

import java.io.*;
import java.net.URL;
import java.nio.*;
import javax.sound.sampled.*;

public class AudioStream {

  //InputStream fis; //audio file
  BufferedInputStream fis; //Buffer to read file
  int frame_nb; //current frame nb

  //-----------------------------------
  //constructor
  //-----------------------------------
  public AudioStream(String filename) throws Exception{

     File file = new File (filename);  
            if (file==null)
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
    String length_string;
    int lenght =(int)44100*(16/8)*1/10;//Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/10 (1 packet in 0,1 sec) 
    
  // returns the length of data copied in buffer
    int count = fis.read(frame, 0,lenght);

    return(count);
  }
}
