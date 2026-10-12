package com.netsend.network;

import com.netsend.model.ProtocolPacket;
import java.net.InetAddress;

public record ReceivedDatagram(ProtocolPacket packet, InetAddress sourceAddress) {
}
