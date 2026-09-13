package com.sunmoon.bo.infrastructure.network;

import com.sunmoon.bo.domain.device.ConnectionChecker;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A plain TCP connect, nothing more. This answers "is something listening
 * on that address", not "is the terminal software running" — a POS/KDS/DID
 * screen is a browser tab with no listening socket of its own, so this is
 * meaningful for equipment that runs a real service (a printer, a kiosk's
 * remote-management port) and only incidentally useful for a terminal.
 * The Device Server's own connection registry is still the authority on
 * whether a terminal is actually connected.
 */
@Component
public class SocketConnectionChecker implements ConnectionChecker {

    private static final int TIMEOUT_MS = 2000;

    @Override
    public boolean isReachable(String ipAddress, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
