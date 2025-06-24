package com.fortizva.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fortizva.packets.FECpacket;
import com.fortizva.packets.RTPpacket;

class FECpacketTest {

	@Test
    public void testFECpacketConstructorAndByteArray() {
        // Prepare dummy RTP packets with known values
        byte[] payload1 = new byte[] {0x01, 0x02, 0x03, 0x04};
        byte[] payload2 = new byte[] {0x05, 0x06, 0x07, 0x08};
        byte[] payload3 = new byte[] {0x09, 0x0A, 0x0B, 0x0C};
        int seq1 = 1000;
        int seq2 = 1001;
        int seq3 = 1002;
        int ts1 = 0x12345678;
        int ts2 = 0x87654321;
        int ts3 = 0x11223344;
        int ptype = 96;

        RTPpacket rtp1 = new RTPpacket(ptype, seq1, ts1, payload1, payload1.length);
        RTPpacket rtp2 = new RTPpacket(ptype, seq2, ts2, payload2, payload2.length);
        RTPpacket rtp3 = new RTPpacket(ptype, seq3, ts3, payload3, payload3.length);

        RTPpacket[] rtpPackets = new RTPpacket[] {rtp1, rtp2, rtp3};
        FECpacket fec = new FECpacket(rtpPackets);

        // Get the FEC packet bytes
        byte[] fecBytes = new byte[fec.getFECPacketSize()];
        fecBytes = fec.getFECPacket();

        // Check flags (first byte should be 0x00)
        assertEquals(0x00, fecBytes[0]); // E, L, P, X, CC, M bits
        
        // Check PT (payload type, should match RTP packets XOR) (We can ignore the M, as it is not used in this implementation)
        // XORing ptype 3 times should result in ptype itself, since ptype is the same for all packets. But improves readability.
        assertEquals(ptype^ptype^ptype, fecBytes[1] & 0x7F); // Masking the M for payload type
        
        // Check SN base (2 bytes)
        // Shift right to discard lower bits and mask with 0xFF to discard the upper bits and any sign extension
        assertEquals((byte)((seq1 >> 8) & 0xFF), fecBytes[2]);
        assertEquals((byte)(seq1 & 0xFF), fecBytes[3]);

        
        //  Check TS recovery (4 bytes - XOR of timestamps) 
        int tsRecovery = ts1 ^ ts2 ^ ts3;
        assertEquals((byte)((tsRecovery >> 24) & 0xFF), fecBytes[4]);
        assertEquals((byte)((tsRecovery >> 16) & 0xFF), fecBytes[5]);
        assertEquals((byte)((tsRecovery >> 8) & 0xFF), fecBytes[6]);
        assertEquals((byte)(tsRecovery & 0xFF), fecBytes[7]);

        // Check length recovery (2 bytes, XOR of payload lengths)
        int lenRecovery = payload1.length ^ payload2.length ^ payload3.length;
        assertEquals((byte)((lenRecovery >> 8) & 0xFF), fecBytes[8]);
        assertEquals((byte)(lenRecovery & 0xFF), fecBytes[9]);

        // Check protection length (next 2 bytes, big endian, max payload length)
        int protLen = Math.max(Math.max(payload1.length, payload2.length), payload3.length);
        assertEquals((byte)((protLen >> 8) & 0xFF), fecBytes[10]);
        assertEquals((byte)(protLen & 0xFF), fecBytes[11]);

        // Check protection mask (next 2 bytes, should be 0b11100000_00000000 for 3 packets)
        assertEquals((byte)0xE0, fecBytes[12]);
        assertEquals((byte)0x00, fecBytes[13]);

        // Check XOR payload (should be payload1 ^ payload2, rest zero)
        for (int i = 0; i < protLen; i++) {
            byte expected = (byte)(payload1[i] ^ payload2[i] ^ payload3[i]);
            assertEquals(expected, fecBytes[14 + i]);
        }
    }

	@Test
    void testFECRecoveryOfAllPackets() {
        // Prepare dummy RTP packets with known values
        byte[] payload1 = new byte[] {0x11, 0x22, 0x33, 0x44};
        byte[] payload2 = new byte[] {0x55, 0x66, 0x77, 0x00};
        byte[] payload3 = new byte[] {0x10, 0x20, 0x30, 0x40};
        int seq1 = 100;
        int seq2 = 101;
        int seq3 = 102;
        int ts1 = 0x12345678;
        int ts2 = 0x87654321;
        int ts3 = 0x11223344;
        int ptype = 96;

        RTPpacket[] orig = new RTPpacket[] {
            new RTPpacket(ptype, seq1, ts1, payload1, payload1.length),
            new RTPpacket(ptype, seq2, ts2, payload2, payload2.length),
            new RTPpacket(ptype, seq3, ts3, payload3, payload3.length)
        };

        FECpacket fec = new FECpacket(orig);

        // Try to recover each packet by removing it from the array and using the other two
        for (int lostIdx = 0; lostIdx < 3; lostIdx++) {
            RTPpacket[] received = new RTPpacket[2];
            int idx = 0;
            for (int i = 0; i < 3; i++) {
                if (i != lostIdx) {
                    received[idx++] = orig[i];
                }
            }
            RTPpacket recovered = fec.recoverPacket(received, lostIdx);

            // Check all fields
            assertEquals(orig[lostIdx].getpayloadtype(), recovered.getpayloadtype(), "Payload type mismatch");
            assertEquals(orig[lostIdx].getsequencenumber(), recovered.getsequencenumber(), "Sequence number mismatch");
            assertEquals(orig[lostIdx].gettimestamp(), recovered.gettimestamp(), "Timestamp mismatch");
            assertEquals(orig[lostIdx].getpayload_length(), recovered.getpayload_length(), "Payload length mismatch");

            byte[] expectedPayload = new byte[orig[lostIdx].getpayload_length()];
            byte[] actualPayload = new byte[recovered.getpayload_length()];
            orig[lostIdx].getpayload(expectedPayload);
            recovered.getpayload(actualPayload);
            assertArrayEquals(expectedPayload, actualPayload, "Payload data mismatch");
        }
    }
	
}
