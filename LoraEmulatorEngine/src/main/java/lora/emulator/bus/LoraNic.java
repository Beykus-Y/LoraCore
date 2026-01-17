package lora.emulator.bus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;

public class LoraNic implements IMemoryMappedDevice, ITickable {
    private final int bufferSize = 2048;
    private final byte[] rxBuffer = new byte[bufferSize];
    private final byte[] txBuffer = new byte[bufferSize];

    private static final int OFFSET_RX = 0x0000;
    private static final int OFFSET_TX = 0x0800;
    private static final int OFFSET_REGS = 0x1000;

    // Состояния REG_STATUS
    private static final int STATUS_IDLE = 0;
    private static final int STATUS_BUSY = 1;     // Соединение устанавливается
    private static final int STATUS_CONNECTED = 2;
    private static final int STATUS_HAS_DATA = 3;
    private static final int STATUS_ERROR = 4;

    private static final int REG_STATUS  = 0;
    private static final int REG_CMD     = 1;
    private static final int REG_TX_LEN  = 2;
    private static final int REG_RX_LEN  = 3;
    private static final int REG_PORT    = 4;

    private final int[] registers = new int[8];
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected = false;
    private final Object lock = new Object();
    private int incomingBytesCount = 0;

    @Override
    public int getSize() { return 0x1100; }

    @Override
    public byte read(int offset) {
        if (offset >= OFFSET_RX && offset < OFFSET_RX + bufferSize) {
            synchronized (lock) { return rxBuffer[offset - OFFSET_RX]; }
        }
        if (offset >= OFFSET_REGS) {
            int idx = (offset - OFFSET_REGS) >>> 2;
            if (idx < registers.length) {
                return (byte) (registers[idx] >>> ((offset & 3) << 3));
            }
        }
        return 0;
    }

    @Override
    public void write(int offset, byte value) {
        if (offset >= OFFSET_TX && offset < OFFSET_TX + bufferSize) {
            txBuffer[offset - OFFSET_TX] = value;
            return;
        }
        if (offset >= OFFSET_REGS) {
            int idx = (offset - OFFSET_REGS) >>> 2;
            if (idx >= registers.length) return;
            int shift = (offset & 3) << 3;
            registers[idx] = (registers[idx] & ~(0xFF << shift)) | ((value & 0xFF) << shift);
            // Триггер по записи последнего байта в CMD
            if (idx == REG_CMD && (offset & 3) == 3) executeCommand();
        }
    }

    @Override
    public void writeInt(int offset, int value) {
        if (offset >= OFFSET_TX && offset < OFFSET_TX + bufferSize) {
            int off = offset - OFFSET_TX;
            if (off + 3 < bufferSize) {
                txBuffer[off] = (byte) value;
                txBuffer[off+1] = (byte)(value >> 8);
                txBuffer[off+2] = (byte)(value >> 16);
                txBuffer[off+3] = (byte)(value >> 24);
            }
            return;
        }
        if (offset >= OFFSET_REGS) {
            int idx = (offset - OFFSET_REGS) >>> 2;
            if (idx < registers.length) {
                registers[idx] = value;
                if (idx == REG_CMD) executeCommand();
            }
        }
    }

    private void executeCommand() {
        int cmd = registers[REG_CMD];
        registers[REG_CMD] = 0;

        switch (cmd) {
            case 1 -> sendData();
            case 2 -> startConnectThread();
            case 3 -> clearRx();
        }
    }

    private void startConnectThread() {
        if (registers[REG_STATUS] == STATUS_BUSY) return;

        int port = registers[REG_PORT] == 0 ? 8080 : registers[REG_PORT];
        registers[REG_STATUS] = STATUS_BUSY; // Выставляем BUSY немедленно

        new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress("localhost", port), 5000); // 5 сек таймаут
                s.setTcpNoDelay(true);
                synchronized (lock) {
                    this.socket = s;
                    this.in = s.getInputStream();
                    this.out = s.getOutputStream();
                    this.connected = true;
                    registers[REG_STATUS] = STATUS_CONNECTED;
                }
                listenLoop();
            } catch (IOException e) {
                System.err.println("[NIC] Connection failed: " + e.getMessage());
                registers[REG_STATUS] = STATUS_ERROR;
            }
        }, "NIC-Connector").start();
    }

    private void sendData() {
        if (!connected) return;
        int len = Math.min(registers[REG_TX_LEN], bufferSize);
        try {
            out.write(txBuffer, 0, len);
            out.flush();
        } catch (IOException e) { connected = false; registers[REG_STATUS] = STATUS_ERROR; }
    }

    private void clearRx() {
        synchronized (lock) {
            incomingBytesCount = 0;
            registers[REG_RX_LEN] = 0;
            if (connected) registers[REG_STATUS] = STATUS_CONNECTED;
        }
    }

    private void listenLoop() {
        byte[] tempBuf = new byte[bufferSize];
        try {
            while (connected) {
                int read = in.read(tempBuf);
                if (read == -1) break;
                synchronized (lock) {
                    if (incomingBytesCount + read <= bufferSize) {
                        System.arraycopy(tempBuf, 0, rxBuffer, incomingBytesCount, read);
                        incomingBytesCount += read;
                        registers[REG_RX_LEN] = incomingBytesCount;
                        registers[REG_STATUS] = STATUS_HAS_DATA;
                    }
                }
            }
        } catch (IOException ignored) {}
        connected = false;
        registers[REG_STATUS] = STATUS_IDLE;
    }

    @Override
    public void tick(long cycles) {}
}