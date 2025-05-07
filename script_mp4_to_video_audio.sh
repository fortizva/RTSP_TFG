 ffmpeg -i matrix.mp4 -filter:v fps=fps=10 matrix_fps.mp4
 ffmpeg -i matrix_fps.mp4 -vcodec mjpeg -vf scale=380:280 -an matrix.mjpeg
 ffmpeg -i matrix_fps.mp4 -vn -acodec copy audio.aac
 ffmpeg -y -i audio.aac -vn -acodec pcm_s16le -f s16le -ac 1 -ar 44100 audio.pcm 
 go run ./converter_mjpeg_to_5_length.go -i matrix.mjpeg -o smatrix.mjpeg
