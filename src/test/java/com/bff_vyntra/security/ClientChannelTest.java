package com.bff_vyntra.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientChannelTest {

    @Test
    void parsesWebCaseInsensitively() {
        assertEquals(ClientChannel.WEB, ClientChannel.fromHeader("web"));
        assertEquals(ClientChannel.WEB, ClientChannel.fromHeader("WEB"));
        assertEquals(ClientChannel.WEB, ClientChannel.fromHeader(" Web "));
    }

    @Test
    void defaultsUnknownToMobile() {
        assertEquals(ClientChannel.MOBILE, ClientChannel.fromHeader(null));
        assertEquals(ClientChannel.MOBILE, ClientChannel.fromHeader("mobile"));
        assertEquals(ClientChannel.MOBILE, ClientChannel.fromHeader("ios"));
        assertTrue(ClientChannel.fromHeader("android") == ClientChannel.MOBILE);
    }
}
