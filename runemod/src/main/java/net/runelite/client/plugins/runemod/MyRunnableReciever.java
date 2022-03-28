package net.runelite.client.plugins.runemod;

import lombok.SneakyThrows;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MyRunnableReciever implements Runnable {
    private final AtomicBoolean listen = new AtomicBoolean();
    private final AtomicBoolean send = new AtomicBoolean();
    AtomicReference atomicString = new AtomicReference("nothing yet");
    private boolean firstrun = true;

    private int port = 9999;
    private DatagramSocket serverSocketReciever;
    private byte[] sendData =  new byte[1024];
    private byte[] receiveData =  new byte[1024];
    private String stringToSend;
    DatagramPacket receivePacket = null;
    DatagramPacket sendPacket = null;
    public boolean clientInitialized = false;
    public static volatile boolean clientConnected = false;
    public int currentDataType;  //0 = lowLatencyData, 1 = meshBytesData
    private int bufferQueuePos = -1;

    public int curMouseX;
    public int curMouseY;

    public Component clientCanvas;
    public EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();

    public void setSendMessage(String string) {
        this.atomicString.set(new AtomicReference(string));
    }

    public void setToFalseSend() {
        this.send.set(false);
    }

    public void setToTrueSend() {
        this.send.set(true);
    }

    @SneakyThrows
    public void onStart() {
        serverSocketReciever = new DatagramSocket(port, InetAddress.getByName("127.0.0.200"));
        receiveData = new byte[1024];
        sendData = new byte[1024];
        serverSocketReciever.setSoTimeout(0);
        serverSocketReciever.setReceiveBufferSize(65507);
        serverSocketReciever.setSendBufferSize(65507);
        firstrun = false;
        System.out.println("server started");
    }

    @SneakyThrows
    public void waitForPacket() {
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        serverSocketReciever.receive(receivePacket);
    }

    public int getClientPort() {
        return receivePacket.getPort();
    }

    public InetAddress getClientIP() {
        return receivePacket.getAddress();
    }

    @SneakyThrows
    @Override
    public void run() {
        while (true) {
            if (firstrun == true)onStart();

            System.out.println("waiting for client connection");
            waitForPacket();

            byte[] bytes = receivePacket.getData();

            int opCode = bytes[0];

            switch (opCode) {
                case 1:
                    System.out.println("Client Connected");
                    MyRunnableSender.clientInitialized = true;
                    MyRunnableSender.clientConnected = true;
                    RuneMod.clientJustConnected = true;

                    receiveData = new byte[1024];
                    sendData = new byte[1024];
                    break;
                case 2:
                    System.out.println("Client Disconnected");
                    MyRunnableSender.clientInitialized = false;
                    MyRunnableSender.clientConnected = false;
                    RuneMod.clientJustConnected = false;

                    receiveData = new byte[1024];
                    sendData = new byte[1024];
                    break;
                case 3:

                    break;
                case 4:// Mouse Scroll
                    int WheelRotation = bytes[1]-1;
                    MouseWheelEvent mouseWheelEvent = new MouseWheelEvent(clientCanvas, MouseEvent.MOUSE_WHEEL ,System.currentTimeMillis(), 0, curMouseX, curMouseY, 0, false, 0, WheelRotation*3, WheelRotation );
                    eventQueue.postEvent(mouseWheelEvent);
                    break;
                case 5://Mouse Button
                    byte keyState = bytes[1];
                    byte keyChar = bytes[2];

                    int modifiers = -1;

                    switch (keyChar) {
                        case 1:
                            modifiers = 1024;
                            break;
                        case 2:
                            modifiers = 2048;
                            break;
                        case 3:
                            modifiers = 4096;
                            break;
                    }

                    if (keyState == 1){//pressed
                        MouseEvent mouseEventClick = new MouseEvent(clientCanvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), modifiers, curMouseX,curMouseY, 1, false, keyChar);
                        eventQueue.postEvent(mouseEventClick);
                    }

                    if (keyState == 0){//released
                        MouseEvent mouseEventClick = new MouseEvent(clientCanvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), modifiers, curMouseX,curMouseY, 1, false, keyChar);
                        eventQueue.postEvent(mouseEventClick);
                    }
                    break;
                case 6: //mouse Pos
                    curMouseX = (bytes[2] & 255) + ((bytes[1] & 255) << 8);
                    curMouseY = (bytes[4] & 255) + ((bytes[3] & 255) << 8);
                    MouseEvent mouseMoveEvent = new MouseEvent(clientCanvas, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, curMouseX,curMouseY, 0, false, 0);
                    eventQueue.postEvent(mouseMoveEvent);
                    break;
            }

            //String sentence = new String(receivePacket.getData());
            System.out.println("RECEIVED opcode: " + opCode);
			if (opCode == 2) { //disconnection indication

			} else {
				if (opCode == 1) { //connect indication

				}
			}
        }


    }
}
