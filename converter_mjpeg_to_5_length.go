package main

import (
	"flag"
	"fmt"
	"log"
	"os"
)

var c *bool = flag.Bool("c", false, "convert only")
var in *string = flag.String("i", "", "input video")
var out *string = flag.String("o", "", "output mjpg video")

func init() {
	flag.Parse()
}

func main() {
	if len(os.Args) < 3 {
		fmt.Println("converter -i in.mjpeg -o o.mjpg")
	}
	src := *in
	log.Println(*c, *in, *out)
	conv(src, *out)
}

func conv(in, out string) {
	var previo byte
	var nolectura bool
	inF, err := os.OpenFile(in, os.O_RDONLY, 0644)
	if err != nil {
		log.Fatalln(err)
	}
	defer inF.Close()
	outF, err := os.OpenFile(out, os.O_CREATE|os.O_WRONLY, 0644)
	defer outF.Close()
	if err != nil {
		log.Fatalln(err)
	}
	twoBytes := make([]byte, 2)
	buf := make([]byte, 0, 80000)
	start := false
	n := int64(0)
	index := 0
	previo = 0x00
	nolectura = false
	for {
		if nolectura == false {
			_, err := inF.Read(twoBytes)
			if err != nil {
				fmt.Print("\n")
				log.Println(err)
				break
			}
		} else {
			nolectura = false
		}

		if twoBytes[0] == 0xFF {
			if twoBytes[1] == 0xd8 {
				start = true
				previo = twoBytes[1]
			} else if (twoBytes[1] == 0xd9) && start {
				index = index + 1
				fmt.Println("frame ", index, "len ", len(buf))
				buf = append(buf, twoBytes...)
				num := fmt.Sprintf("%d", int32(len(buf)))
				// fmt.Println(num, []byte(num))
				if len(num) <= 5 {
					s := prefixWithZeroes(num, 5)
					// length := int32(len(buf))
					// header := make([]byte, 5)
					// header[1] = byte(length >> 24)
					// header[2] = byte(length >> 16)
					// header[3] = byte(length >> 8)
					// header[4] = byte(length & 0xFF)
					k, err := outF.Write([]byte(s))
					if err != nil {
						log.Fatalln(err)
					}
					n += int64(k)
					k, err = outF.Write(buf)
					if err != nil {
						log.Fatalln(err)
					}
					n += int64(k)
					fmt.Printf("\rbytes: %d", n)
					// log.Printf("Write bytes: %d\n", n)
				}
				buf = make([]byte, 0, 80000)
				start = false
			}
		} else if (twoBytes[0] == 0xD9 && previo == 0xFF) && start {
			index = index + 1
			fmt.Println("frame ", index, "len ", len(buf))
			buf = append(buf, twoBytes...)
			num := fmt.Sprintf("%d", int32(len(buf)))
			// fmt.Println(num, []byte(num))
			if len(num) <= 5 {
				s := prefixWithZeroes(num, 5)
				// length := int32(len(buf))
				// header := make([]byte, 5)
				// header[1] = byte(length >> 24)
				// header[2] = byte(length >> 16)
				// header[3] = byte(length >> 8)
				// header[4] = byte(length & 0xFF)
				k, err := outF.Write([]byte(s))
				if err != nil {
					log.Fatalln(err)
				}
				n += int64(k)
				k, err = outF.Write(buf)
				if err != nil {
					log.Fatalln(err)
				}
				n += int64(k)
				fmt.Printf("\rbytes: %d", n)
				// log.Printf("Write bytes: %d\n", n)
			}
			buf = make([]byte, 0, 80000)
			start = false
			nolectura = true
			twoBytes[0] = twoBytes[1]
			oneByte := make([]byte, 1)
			_, err := inF.Read(oneByte)
			if err != nil {
				fmt.Print("\n")
				log.Println(err)
				break
			}
			twoBytes[1] = oneByte[0]
		}
		if start {
			buf = append(buf, twoBytes...)
			previo = twoBytes[1]
		}
	}
}

func prefixWithZeroes(s string, n int) string {
	ans := ""
	for i := 0; i < n-len(s); i++ {
		ans += "0"
	}
	ans += s
	return ans
}
