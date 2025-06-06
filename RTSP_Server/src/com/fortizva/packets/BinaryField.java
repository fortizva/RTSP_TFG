package com.fortizva.packets;

public class BinaryField {
	public int field, length;

	public BinaryField(int field, int length) {
		this.field = field;
		this.length = length;
	}
	
	public static byte binaryBuilder(byte binary, BinaryField ...fields) {
		byte bin = binary;
		int current = 0;
		for(BinaryField i : fields) {
			bin = (byte)(bin | i.field << (8 - i.length - current));
			current += i.length;
		}
		return bin;
	}
	
	public static byte[] binarySplitter(long value) {
		byte[] bin = new byte[(int) Math.ceil(Long.toBinaryString(value).length()/8.0)];
		for(int i = bin.length-1; i >= 0; i--) {
			bin[bin.length-1 - i] = (byte) ((value >> (8*i)) & 0xFF);
			//System.out.println(String.format("%8s", Integer.toBinaryString(bin[i] & 0xFF)).replace(' ', '0'));
		}	
		
		return bin;
	}
}
