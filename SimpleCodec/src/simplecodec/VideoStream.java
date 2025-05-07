package simplecodec;


//VideoStream

import java.io.*;

public class VideoStream {

  FileInputStream fis; //video file
  int frame_nb; //current frame nb

  //-----------------------------------
  //constructor
  //-----------------------------------
  public VideoStream(String filename) throws Exception{

    //init variables
    int end=0;           
    fis = new FileInputStream(filename);
    frame_nb = 0;
    byte [] frame =new byte [150000];
    while (end!=-1){
          //read current frame length
        byte [] frame_length=new byte [5];
        end=fis.read(frame_length,0,5);

        if (end!=-1){
        //transform frame_length to integer
            String length_string = new String(frame_length);
            int length = Integer.parseInt(length_string);

            frame_nb++;
            end=fis.read(frame,0,length);
        }
        
    }
    fis.close();
    fis = new FileInputStream(filename);
  }
  
  public int getFrames(){
      return frame_nb;
  }

  //-----------------------------------
  // getnextframe
  //returns the next frame as an array of byte and the size of the frame
  //-----------------------------------
  public int getnextframe(byte[] frame, byte [] frame_length) throws Exception
  {
    int length = 0;
    String length_string;
    //frame_length = new byte[5];

    //read current frame length
    fis.read(frame_length,0,5);

	
    //transform frame_length to integer
    length_string = new String(frame_length);
    length = Integer.parseInt(length_string);
	
    int fin=fis.read(frame,0,length);
    if (fin==-1)
        return(fin);
    else
        return (length);
  }
  
}
