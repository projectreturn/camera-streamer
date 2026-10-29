package de.local.tapoviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkUtilTest {
    @Test public void extractsSlash24Prefix() {
        assertEquals("192.0.2.", NetworkUtil.subnetPrefix("192.0.2.19"));
    }

    @Test public void rejectsInvalidAddresses() {
        assertFalse(NetworkUtil.isValidIpv4("192.0.2.999"));
        assertFalse(NetworkUtil.isValidIpv4("192.0.2.0"));
        assertFalse(NetworkUtil.isValidIpv4("hello"));
    }

    @Test public void acceptsCameraAddress() {
        assertTrue(NetworkUtil.isValidIpv4("192.0.2.25"));
    }
}
