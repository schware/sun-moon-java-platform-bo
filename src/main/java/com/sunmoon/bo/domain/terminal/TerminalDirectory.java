package com.sunmoon.bo.domain.terminal;

import java.util.List;

/**
 * Who is connected right now. The Device Server is the only thing that can
 * answer this — it is the service that holds the sockets — so BO asks it
 * rather than inferring presence from anything of its own.
 *
 * <p>Not to be confused with
 * {@link com.sunmoon.bo.domain.device.ConnectionChecker}, which dials an
 * IP and port. That answers "is something listening at that address"; this
 * answers "is that screen talking to us". A POS terminal is a browser tab
 * with no listening socket, so only this one is meaningful for it.
 */
public interface TerminalDirectory {

    List<ConnectedTerminal> connected();
}
