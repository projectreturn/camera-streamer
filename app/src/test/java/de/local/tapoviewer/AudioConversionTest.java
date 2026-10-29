package de.local.tapoviewer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.*;
import androidx.media3.common.C;
public class AudioConversionTest {
    @Test public void g711ReferenceCodesDecodeCorrectly(){assertEquals(0,AudioToAac.decodeMulaw(0xff));assertEquals(0,AudioToAac.decodeMulaw(0x7f));assertEquals(-32124,AudioToAac.decodeMulaw(0));assertEquals(32124,AudioToAac.decodeMulaw(0x80));assertEquals(8,AudioToAac.decodeAlaw(0xd5));assertEquals(-8,AudioToAac.decodeAlaw(0x55));assertEquals(32256,AudioToAac.decodeAlaw(0xaa));}
    @Test public void convertsPcmWithoutChangingInputPosition()throws Exception{ByteBuffer input=ByteBuffer.wrap(new byte[]{0,(byte)128,(byte)255});ByteBuffer pcm=AudioToAac.pcm16(input,C.ENCODING_PCM_8BIT);assertEquals(0,input.position());assertEquals(-32768,pcm.getShort());assertEquals(0,pcm.getShort());assertEquals(32512,pcm.getShort());ByteBuffer floats=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);floats.putFloat(-1).putFloat(0).putFloat(1).flip();pcm=AudioToAac.pcm16(floats,C.ENCODING_PCM_FLOAT);assertEquals(-32767,pcm.getShort());assertEquals(0,pcm.getShort());assertEquals(32767,pcm.getShort());}
}
