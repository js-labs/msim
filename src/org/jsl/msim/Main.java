/*
 * Copyright (C) 2024 Sergey Zubarev, info@js-labs.org
 *
 * This file is a part of multicast simulator tool.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jsl.msim;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

public class Main {
    private static void printUsage() {
        System.out.println("Usage: mtest [-g destination address] [-i interface]");
    }

    private static void printErrorAndExit(String err) {
        System.out.println(err);
        printUsage();
        System.exit(-1);
    }

    public static void main(String [] args) {
        InetSocketAddress destinationAddr = null;
        NetworkInterface networkInterface = null;
        int idx = 0;
        while (idx < args.length) {
            if (args[idx].equals("-g")) {
                if (++idx == args.length) {
                    printErrorAndExit("Missing destination address value");
                }

                try {
                    final String [] parts = args[idx].split(":");
                    if (parts.length != 2) {
                        printErrorAndExit("Invalid destination address '" + args[idx] + "'");
                    }

                    final InetAddress addr = InetAddress.getByName(parts[0]);
                    final int port = Integer.parseInt(parts[1]);
                    destinationAddr = new InetSocketAddress(addr, port);
                } catch (NumberFormatException|UnknownHostException ex){
                    printErrorAndExit("Failed to parse address: " + ex.getMessage());
                }
            } else if (args[idx].equals("-i")) {
                if (++idx == args.length) {
                    printErrorAndExit("Missing local interface argument value");
                }

                try {
                    final InetAddress addr = InetAddress.getByName(args[idx]);
                    networkInterface = NetworkInterface.getByInetAddress(addr);
                } catch (UnknownHostException | SocketException ignored) {
                }

                if (networkInterface == null) {
                    try {
                        networkInterface = NetworkInterface.getByName(args[idx]);
                    } catch (SocketException ignored) {
                    }
                }

                if (networkInterface == null) {
                    printErrorAndExit("Invalid interface '" + args[idx] + "'");
                }

                try {
                    if (!networkInterface.isUp()) {
                        printErrorAndExit("Network interface '" + args[idx] + "' is down");
                    }
                } catch (SocketException ex) {
                    printErrorAndExit(ex.getMessage());
                }
            }
            idx++;
        }

        if (destinationAddr == null) {
            printErrorAndExit("Missing destination address");
        }

        System.out.println(destinationAddr);

        try {
            try (final DatagramChannel datagramChannel = DatagramChannel.open()) {
                if (networkInterface != null) {
                    datagramChannel.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
                }

                final ByteBuffer bb = ByteBuffer.allocateDirect(18);
                bb.order(ByteOrder.LITTLE_ENDIAN);

                int packetSN = 1;

                for (;;) {
                    final long currentTime = System.currentTimeMillis();
                    bb.putLong(0, currentTime * 1000000);
                    bb.putInt(8, packetSN++);
                    bb.putShort(12, (short)0);
                    bb.putShort(14, (short)0);
                    bb.putShort(16, (short)0);
                    bb.rewind();
                    datagramChannel.send(bb, destinationAddr);
                    bb.clear();

                    Thread.sleep(1000);
                    System.out.print(".");
                }
            } catch (final SocketException|InterruptedException ex) {
                System.out.println(ex.getMessage());
            }
        } catch (final IOException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
