/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package simplecodec;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import javax.swing.JFrame;

/**
 *
 * @author david
 */
public class SimpleCodec extends JFrame {
    
    private static final long serialVersionUID = 1L;
    
	byte Version;   
    byte fps;
    int numFrames;
    int width;
    int heigh;
    byte nAudioTracks;
    int SamplingRate;
    byte bitDepth;
    byte channelCount;
    VideoStream fvideo; //video file
    int frame_nb; //current frame nb
    AudioStream faudio; //Buffer to read file
    FileOutputStream foutput; //Buffer to read file

    
       

    
    
    public SimpleCodec (){
        
    }
            
          //      int lenght =(int)44100*(16/8)*1/10;//Be ware!!! [sampleRate * (bitDepth / 8) * channelCount (Bps)]/fps (1 packet in 0,1 sec) 
    
    public void mixFiles(){
        int fin=0;
        byte [] frame_video = new byte[150000]; 
        byte [] frame_audio = new byte[150000]; 
        byte[] byte_size=new byte[5];
        int frame=0;
        
        while (fin!=-1){
            try{
             fin=fvideo.getnextframe(frame_video, byte_size);             
             foutput.write(byte_size,0,5);             
             String length_string = new String(byte_size);
             int length = Integer.parseInt(length_string);
             foutput.write(frame_video, 0, length);
             faudio.getnextchunk(frame_audio);
                                        
             foutput.write(frame_audio, 0,(int)(this.SamplingRate*(this.bitDepth/8)*this.channelCount/(int)this.fps));
             frame++;
             System.out.println(frame);

            }catch (Exception e){
                e.printStackTrace();
            }
                                                            
        }
    }
     public void closeFile(){
        try{
            foutput.flush();
            foutput.close();
        
        }catch(Exception e){
            e.printStackTrace();
        }
            
    }

    
    private byte [] bigInttoByteArray (final int i){
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }
           
    public void videoFile(){
        String nombreArchivo;
            FileDialog fd = new FileDialog(SimpleCodec.this, "Abrir Fichero Video", FileDialog.LOAD); //Se crea uno nuevo 
       	
       fd.setVisible(true); //Y se muestra
            
       if (fd.getFile() != null) //Si el archivo seleccionado es correcto
            {
                nombreArchivo = new String(fd.getDirectory() + fd.getFile()); //Se obtiene su ruta completa
                //System.out.println(nombreArchivo);                
                try{
                    fvideo = new VideoStream(nombreArchivo);
                }catch(Exception e){
                    e.printStackTrace();
                }

            }
               
    }
    
        public void audioFile(){
        String nombreArchivo;
            FileDialog fd = new FileDialog(SimpleCodec.this, "Abrir Fichero Audio", FileDialog.LOAD); //Se crea uno nuevo 
       	
       fd.setVisible(true); //Y se muestra
            
       if (fd.getFile() != null) //Si el archivo seleccionado es correcto
            {
                nombreArchivo = new String(fd.getDirectory() + fd.getFile()); //Se obtiene su ruta completa
                //System.out.println(nombreArchivo);
                try{
                    faudio= new AudioStream(nombreArchivo);
                }catch(Exception e){
                    e.printStackTrace();
                }

       }
              
        
    }
            
    public void createHeader(String filename, byte _fps, int _width, int _heigh,
            byte _nAudioTracks, int _SamplingRate, byte _bitDepth, byte _channelCount){
        
        Version=1;
        fps=_fps;
        int intfps=(int)fps;
        faudio.setFPS(intfps);
        numFrames=fvideo.getFrames();
        width=_width;
        heigh=_heigh;
        nAudioTracks=_nAudioTracks;
        SamplingRate=_SamplingRate;
        bitDepth=_bitDepth;
        channelCount=_channelCount;
        faudio.setChannelCount(_channelCount);
        faudio.setbitDepth(bitDepth);
        faudio.setSampleRate(SamplingRate);
        
        File file = new File (filename);  
        if (file.exists() == false || file.isFile() == false)
                System.out.println("Error: No file to read");
        try{
            foutput= new  FileOutputStream(file);
            if (!file.exists()){
                file.createNewFile();                
            }
            
            foutput.write(Version);
            foutput.write(fps);
            foutput.write(this.bigInttoByteArray(numFrames));
            foutput.write(this.bigInttoByteArray(width));
            foutput.write(this.bigInttoByteArray(heigh));
            foutput.write(nAudioTracks);
            foutput.write(this.bigInttoByteArray(SamplingRate));
            foutput.write(bitDepth);
            foutput.write(channelCount);
        }catch(Exception e){
            e.printStackTrace();
        }


        
        
        
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
       SimpleCodec c=new SimpleCodec();
       c.videoFile();
       c.audioFile();
       c.createHeader("matrix.mjpeg",(byte)25,380,280,(byte)1, 44100, (byte)16,(byte)1);
       
      // c.audioFile();     
       c.mixFiles();
       c.closeFile();   
        // TODO code application logic here
    }
    
}
