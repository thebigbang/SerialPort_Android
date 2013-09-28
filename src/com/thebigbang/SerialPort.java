/*This file is part of SerialPort_Android.
 * 
 * SerialPort_Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * CustomPages is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 *  
 * Copyright (c) Meï-Garino Jérémy 
 */
package com.thebigbang;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import tw.com.prolific.driver.pl2303.PL2303Driver;

/**
 * The objective of this library is to provide an unification of several serial
 * devices chip into one single/simple package. The goal is to offer an same
 * class/interface despite of the RS232 adaptator chip plugged behind in
 * reality, achieving the level of RXTX or more globally of Microsoft .Net
 * framework's serials. At the moment V1.0 allow us the connectivity with FTDI.
 * No root is needed on client's devices, which will be a limit not to explode.
 * TODO: add the support of Prolific devices in next version. Extention of
 * FT_Device helping us create events and stuff for a SerialPort. Take
 * inspiration from: http://code.google.com/p/nrjavaserial/source
 * /browse/trunk/nrjavaserial/src/main/java/gnu/io/RXTXPort.java Note: some
 * parts are directly copied from RXTX Library.
 * <br/>
 * todo: add static initializer detecting automatically driver to use.
 * <br/><b>Changelog: </b>
 * <br/>
 * v1.1: added support to prolific devices in a basic way but enabling full
 * support in the same way as ftdi chips.
 * <br/>
 * v1.2: add auto instantiation of the class selecting automatically the driver
 * and configuring the SerialPort object. Added {@link SerialPortConfig} class.
 *
 * @author Jeremy.Mei-Garino
 * @version 1.2
 */
@SuppressWarnings("CallToThreadYield")
public class SerialPort {

    private static final String Tag = "SerialPortLib";
    /**
     * Userfriendly name.
     * <br/>
     * We can give our serial port a name for debugging purpose or just human
     * readable identification.
     */
    public String portName;
    public int baudRate;
    private boolean monThreadisInterrupted = true;
    private boolean MonitorThreadAlive = false;
    // private boolean MonitorThreadLock = true;
    final private FT_Device self_ftdi;
    final private PL2303Driver self_prolific;
    // private SerialPort self;
    /**
     * Serial Port Event listener
     */
    private SerialPortEventListener SPEventListener;
    /**
     * Thread to monitor data
     */
    private MonitorThread monThread;

    /**
     * Automatic initializer, returning null at the moment.
     *
     * @since 1.2
     * @param ctx the Context
     * @param config the SerialPort's configuration object.
     * @param uDev unused right now
     * @param uDevCon unused right now
     * @return
     */
    public static SerialPort AutomaticInit(Context ctx, SerialPortConfig config, UsbDevice uDev, UsbDeviceConnection uDevCon) {
        //will first try for FTDI devices:
        try {
            D2xxManager ftdi_manager = D2xxManager.getInstance(ctx);
            if (!ftdi_manager.setVIDPID(0x0403, 0xada1)) {
                Log.i("ftd2xx-java", "setVIDPID Error");
                throw new D2xxManager.D2xxException();
            }
            //open-up devices with count:
            int DevCount = ftdi_manager.createDeviceInfoList(ctx);
            if (DevCount > 0) {

                FT_Device ftDev = ftdi_manager.openByIndex(ctx, 0);
                if (ftDev == null) {
                    throw new D2xxManager.D2xxException("ftdi devices are null");
                }

                if (true == ftDev.isOpen()) {
                    Toast.makeText(ctx,
                            "devCount:" + DevCount + " open index:" + 0,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, "Need to get permission!", Toast.LENGTH_SHORT)
                            .show();
                }
                ftDev.setBaudRate(config.baudRate);
                ftDev.setDataCharacteristics(config.dataBits, config.stopBits, config.parity);
                ftDev.setFlowControl(config.flowCtrlSetting, (byte) 0x0b, (byte) 0x0c);
                return new SerialPort(ftDev, config.baudRate);
            } else {
                Log.e("j2xx", "DevCount <= 0");
            }
        } catch (D2xxManager.D2xxException ex) {
            Log.i("SerialPort", "Auto initialisation failed for FTDI device");
            Logger.getLogger(SerialPort.class.getName()).log(Level.SEVERE, null, ex);
        }
        //end of FTDI auto instantiation.
        //begin of Profilic device instantiation.
        PL2303Driver prolific = new PL2303Driver((UsbManager) ctx.getSystemService(Context.USB_SERVICE), ctx, Tag);
        if (prolific.isConnected()) {
            try {
                prolific.setup(config.convertToProlificBaudRate(), config.convertToProlificDataBits(), config.convertToProlificStopBits(), config.convertToProlificParity(), config.convertToProlificFlowControl());
            } catch (IOException ex) {
                Logger.getLogger(SerialPort.class.getName()).log(Level.SEVERE, null, ex);
            }
            return new SerialPort(prolific, config.baudRate);
        }
        //no compatible devices found will return null.
        return null;
    }

    /**
     * FTDI devices initializer. Will return null if no device. should be
     * deprecated but we will see that when we will be on v1.4
     *
     * @param d
     * @param baudRate
     * @return
     */
    public static SerialPort Init(FT_Device d, int baudRate) {
        if (d == null) {
            return null;
        }
        return new SerialPort(d, baudRate);
    }

    /**
     * Prolific devices initializer. Will return null if no device. should be
     * deprecated but we will see that when we will be on v1.4
     *
     * @param d
     * @param baudRate
     * @return
     */
    public static SerialPort Init(PL2303Driver d, int baudRate) {
        if (d == null) {
            return null;
        }
        return new SerialPort(d, baudRate);
    }

    /**
     * FTDI device serial instantiation.
     *
     * @param d device
     * @param b baudRate
     */
    private SerialPort(FT_Device d, int b) {
        // super(parentContext, usbManager, dev, itf);
        portName = d.getDeviceInfo().location + "";
        self_ftdi = d;
        self_prolific = null;
        baudRate = b;
    }

    /**
     * prolific device serial instantiation.
     *
     * @param d
     * @param b
     */
    private SerialPort(PL2303Driver d, int b) {
        // super(parentContext, usbManager, dev, itf);
        portName = "Prolific Serial";
        self_prolific = d;
        self_ftdi = null;
        baudRate = b;
    }

    public boolean setBaudRate(int b) {
        baudRate = b;
        return self_ftdi.setBaudRate(b);
    }

    public void notifyOnDataAvailable(boolean enable) {
        monThread.Data = enable;
        self_ftdi.setEventNotification(D2xxManager.FT_EVENT_RXCHAR);
    }

    /**
     * @param lsnr
     * @throws TooManyListenersException
     */
    public void addEventListener(SerialPortEventListener lsnr)
            throws TooManyListenersException {
        /*
         * Don't let any notification requests happen until the
         * 
         * Eventloop is ready
         */
        if (SPEventListener != null) {
            throw new TooManyListenersException();
        }
        SPEventListener = lsnr;
        if (!MonitorThreadAlive) {
            // MonitorThreadLock = true;
            monThread = new MonitorThread();
            monThread.execute();// start();
            // native:
            // waitForTheNativeCodeSilly();
            MonitorThreadAlive = true;
        }
    }

    /**
     * Asynchronous thread permanently reading, in a discreet way, the serial
     * port. If we subscribe to one of the events, will trigger it when
     * necessary
     *
     * @author Jeremy.Mei-Garino
     *
     */
    class MonitorThread extends AsyncTask<Void, Void, Void> {

        private static final String TAG = "monitorThread";
        /**
         * Note: these have to be separate boolean flags because the
         * SerialPortEvent constants are NOT bit-flags, they are just defined as
         * integers from 1 to 10 -DPL
         */
        private volatile boolean CTS = false;
        private volatile boolean DSR = false;
        private volatile boolean RI = false;
        private volatile boolean CD = false;
        private volatile boolean OE = false;
        private volatile boolean PE = false;
        private volatile boolean FE = false;
        private volatile boolean BI = false;
        private volatile boolean Data = false;
        private volatile boolean Output = false;
        private volatile Thread bgThread;
        private boolean isPaused = false;

        MonitorThread() {
        }

        @Override
        protected Void doInBackground(Void... params) {
            bgThread = Thread.currentThread();
            bgThread.setName("serialMonThread");
            monThreadisInterrupted = false;
            while (!monThreadisInterrupted) {
                if (isPaused) {
                    continue;
                }
                long event;
                synchronized (self_ftdi) {
                    event = self_ftdi.getEventStatus();
                }
                if (event < 0) {
                    // error in here...
                } else if (Data
                        && event == D2xxManager.FT_EVENT_RXCHAR) {
                    publishProgress();
                }
                // send event dataAvaiable.

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Void... v) {
            if (SPEventListener != null) {
                SPEventListener.serialEvent(new SerialPortEvent(this,
                        SerialPortEvent.DATA_AVAILABLE));
            }
        }

        /**
         * Pause the thread and put it as minimum priority state.
         */
        public void Pause() {
            synchronized (this) {
                // Log.i(TAG,"low priority on: "+bgThread.getName());
                bgThread.setPriority(Thread.MIN_PRIORITY);
                this.isPaused = true;
                //	Log.i(TAG, "thread paused!");
            }
        }

        /**
         * Continue back execution of the thread and put it back at normal
         * priority too.
         */
        public void Resume() {
            synchronized (this) {
                // Log.i(TAG,"normal priority on: "+bgThread.getName());
                bgThread.setPriority(Thread.NORM_PRIORITY);
                this.isPaused = false;
                //	Log.i(TAG, "thread resumed!");
            }
        }
    }

    public String getName() {
        return portName;
    }
    SerialOutputStream out = new SerialOutputStream();

    public OutputStream getOutputStream() {
        return out;
    }

    /**
     * Inner class for SerialOutputStream
     */
    class SerialOutputStream extends OutputStream {

        /**
         * Write an int as a 4 values byte array into the serial Port.
         *
         * @param b
         * @throws IOException
         */
        @Override
        public void write(int b) throws IOException {
            if (baudRate == 0) {
                return;
            }
            if (monThreadisInterrupted == true) {
                return;
            }
            synchronized (self_ftdi) {
                write(ByteBuffer.allocate(4).putInt(b).array(), 0, 4);
            }
        }

        /**
         * do a simple call to:
         *
         * @see {@link #write(byte[], int, int) write(byte b[], int off, int
         *      len)}
         * @param b []
         * @throws IOException
         */
        @Override
        public void write(byte b[]) throws IOException {

            if (baudRate == 0) {
                return;
            }
            if (monThreadisInterrupted == true) {
                return;
            }
            write(b, 0, b.length);
        }

        /**
         * Write the byte array into the serial port.
         *
         * @param b [] the array to write
         * @param off the starting index
         * @param len the length to write (will be from startingIndex + length)
         * @throws IOException
         */
        @Override
        public void write(byte b[], int off, int len) throws IOException {
            if (baudRate == 0) {
                Log.i(Tag, "baudrate was 0 nothing will be done.");
                return;
            }
            if (off + len > b.length) {
                throw new IndexOutOfBoundsException(
                        "Invalid offset/length passed to read");
            }

            byte send[] = new byte[len];
            System.arraycopy(b, off, send, 0, len);
            if (monThreadisInterrupted == true) {
                return;
            }
            monThread.Pause();
            Log.i(Tag, "bytes.write:" + send.length + " length, ");
            if (Looper.getMainLooper() == Looper.myLooper()) {
                // if we are in here then we are on UIThread/MainThread.
                // Will start a new one to work on for writing...
                /**
                 * simple thread helper
                 *
                 * @author Jeremy.Mei-Garino
                 *
                 */
                class InternalWritingThread extends Thread {

                    InternalWritingThread(Runnable r) {
                        super(r);
                        setName("writingSerialThread");
                    }
                }
                /**
                 * Runnable logic for reading our SerialPort from another
                 * thread.
                 *
                 * @author Jeremy.Mei-Garino
                 *
                 */
                class InternalWritingLogik implements Runnable {

                    byte[] _fullData;
                    int _length;

                    InternalWritingLogik(byte[] dataHolder, int lengthToWrite) {
                        super();
                        _fullData = dataHolder;
                        _length = lengthToWrite;
                    }

                    /**
                     * Will run our logic and yield the thread when it's done.
                     */
                    @Override
                    public void run() {
                        synchronized (self_ftdi) {
                            Log.i(Tag, "writing on " + Thread.currentThread().getName() + " thread");
                            if (self_ftdi != null) {
                                self_ftdi.write(_fullData, _length);
                            } else if (self_prolific != null) {
                                self_prolific.write(_fullData, _length);
                            }
                        }
                        Thread.yield();
                    }
                }
                InternalWritingThread d = new InternalWritingThread(
                        new InternalWritingLogik(send, len));
                d.start();
                while (d.isAlive()) {
                }
            } else {
                if (Looper.myLooper() != null) {
                    Log.i(Tag, "writing on " + Looper.myLooper().getThread().getName());
                } else {
                    Log.i(Tag, "writing on a null Looper Thread");
                }
                // we seems to already be on our own thread and should then be
                // able to directly write...
                if (self_ftdi != null) {
                    self_ftdi.write(send, len);
                } else if (self_prolific != null) {
                    self_prolific.write(send, len);
                }
            }
            monThread.Resume();
        }

        /**
         */
        @Override
        public void flush() throws IOException {
            if (baudRate == 0) {
                return;
            }
            if (monThreadisInterrupted == true) {
                return;
            }
            synchronized (self_ftdi) {
                self_ftdi.purge((byte) 2);
            }
        }
    }
    /**
     * Input stream
     */
    private final SerialInputStream in = new SerialInputStream();

    public InputStream getInputStream() {
        return in;
    }

    /**
     * Inner class for SerialInputStream
     */
    class SerialInputStream extends InputStream {

        /**
         * Android: always throw a IOException.
         *
         * @return int the int read
         * @throws IOException
         * @see java.io.InputStream
         *
         * timeout threshold Behavior --------------------------------------
         * ---------------------------------- 0 0 blocks until 1 byte is
         * available timeout > 0, threshold = 0, blocks until timeout occurs,
         * returns -1 on timeout >0 >0 blocks until timeout, returns - 1 on
         * timeout, magnitude of threshold doesn't play a role. 0 >0 Blocks
         * until 1 byte, magnitude of threshold doesn't play a role
         */
        @Override
        public synchronized int read() throws IOException {
            throw new IOException();
        }

        /**
         * Just call:
         *
         * @see {@link #read(byte, int, int) read(byte b[], int off, int len)}
         * @param b []
         * @return int number of bytes read
         * @throws IOException
         *
         * timeout threshold Behavior --------------------------------
         * ---------------------------------------- 0 0 blocks until 1 byte is
         * available >0 0 blocks until timeout occurs, returns 0 on timeout >0
         * >0 blocks until timeout or reads threshold bytes, returns 0 on
         * timeout 0 >0 blocks until reads threshold bytes
         */
        @Override
        public synchronized int read(byte b[]) throws IOException {
            if (monThreadisInterrupted == true) {
                return (0);
            }
            return read(b, 0, b.length);
        }

        /*
         * read(byte b[], int, int) Documentation is at
         * http://java.sun.com/products
         * /jdk/1.2/docs/api/java/io/InputStream.html#read(byte[], int, int)
         */
        /**
         * @param b []
         * @param off
         * @param len the length to read. We warned that this parameter can be
         * ignored depending on host device.
         * @return int number of bytes read
         * @throws IOException
         *
         * timeout threshold Behavior --------------------------------
         * ---------------------------------------- 0 0 blocks until 1 byte is
         * available >0 0 blocks until timeout occurs, returns 0 on timeout >0
         * >0 blocks until timeout or reads threshold bytes, returns 0 on
         * timeout 0 >0 blocks until either threshold # of bytes or len bytes,
         * whichever was lower.
         */
        @Override
        public synchronized int read(byte b[], int off, int len)
                throws IOException {
            int result;
            if (b == null) {
                Log.e(Tag + ":SerialInputStream:read() b == null",
                        "NullPointerException thrown...");
                throw new NullPointerException();
            }

            if ((off < 0) || (len < 0) || (off + len > b.length)) {
                Log.e(Tag + ":SerialInputStream:read() off <0",
                        "IndexOutOfBoundsException thrown...");
                throw new IndexOutOfBoundsException();
            }

            /*
             * Return immediately if len==0
             */
            if (len == 0) {
                return 0;
            }
            /*
             * See how many bytes we should read
             */
            int Minimum = len;

            if (threshold == 0) {
                /*
                 * If threshold is disabled, read should return as soon as data
                 * are available (up to the amount of available bytes in order
                 * to avoid blocking) Read may return earlier depending of the
                 * receive time out.
                 */
                int a = self_ftdi.getQueueStatus();// nativeavailable();
                if (a == 0) {
                    Minimum = 1;
                } else {
                    Minimum = Math.min(Minimum, a);
                }
            } else {
                /*
                 * Threshold is enabled. Read should return when 'threshold'
                 * bytes have been received (or when the receive timeout
                 * expired)
                 */
                Minimum = Math.min(Minimum, threshold);
            }
            if (monThreadisInterrupted == true) {
                return (0);
            }
            byte[] fullData = new byte[Minimum];
            monThread.Pause();
            if (Looper.getMainLooper() == Looper.myLooper()) {
                // if we are in here then we are on UIThread/MainThread.
                // Will start a new one to work on for reading...
                /**
                 * thread from which we can get back a int "FinalResult".
                 *
                 * @author Jeremy.Mei-Garino
                 *
                 */
                class InternalReadingThread extends Thread {

                    int FinalResult;

                    InternalReadingThread(Runnable r) {
                        super(r);
                        setName("readingSerialThread");
                    }
                }
                /**
                 * Runnable logic for reading our SerialPort from another
                 * thread.
                 *
                 * @author Jeremy.Mei-Garino
                 *
                 */
                class InternalReadingLogik implements Runnable {

                    byte[] _fullData;
                    int _Minimum;
                    int _result;

                    InternalReadingLogik(byte[] dataHolder, int lengthToRead) {
                        super();
                        _fullData = dataHolder;
                        _Minimum = lengthToRead;
                    }

                    /**
                     * Will run our logic and yield the thread when it's done.
                     */
                    @Override
                    public void run() {
                        if (self_ftdi != null) {
                            _result = self_ftdi.read(_fullData, _Minimum);
                        } else if (self_prolific != null) {
                            _result = self_prolific.read(_fullData);
                        }
                        InternalReadingThread r = (InternalReadingThread) Thread
                                .currentThread();
                        r.FinalResult = _result;
                        Thread.yield();
                    }
                }
                InternalReadingThread d = new InternalReadingThread(
                        new InternalReadingLogik(fullData, Minimum));
                d.start();
                while (d.isAlive()) {
                }
                result = d.FinalResult;
            } else {
                // we seems to already be on our own thread and should then be
                // able to directly read...
                if (self_ftdi != null) {
                    result = self_ftdi.read(fullData, Minimum);
                } else if (self_prolific != null) {
                    result = self_prolific.read(fullData);
                } //defaulting to 0 if no devices...
                else {
                    result = 0;
                }
            }
            monThread.Resume();
            for (int i = 0; i < Minimum; i++) {
                if (i < off) {
                    continue;
                }
                b[i - off] = fullData[i];
            }
            return result;
        }

        /**
         * Do a simple call to:
         *
         * @see {@link #read(byte[], int, int) read(byte[] b,int i1,int i2)} And
         * then clean and loop the b[] to find t[]
         * @param b []
         * @param off
         * @param len
         * @param t []
         * @return int number of bytes read
         * @throws IOException
         *
         * We are trying to catch the terminator in the native code Right now it
         * is assumed that t[] is an array of 2 bytes.
         *
         * if the read encounters the two bytes, it will return and the array
         * will contain the terminator. Otherwise read behavior should be the
         * same as read( b[], off, len ). Timeouts have not been well tested.
         */
        public synchronized int read(byte b[], int off, int len, byte t[])
                throws IOException {
            int result;

            if (b == null) {
                throw new NullPointerException();
            }

            if ((off < 0) || (len < 0) || (off + len > b.length)) {
                throw new IndexOutOfBoundsException();
            }

            /*
             * Return immediately if len==0
             */
            if (len == 0) {
                return 0;
            }
            /*
             * See how many bytes we should read
             */
            int Minimum = len;

            if (threshold == 0) {
                /*
                 * If threshold is disabled, read should return as soon as data
                 * are available (up to the amount of available bytes in order
                 * to avoid blocking) Read may return earlier depending of the
                 * receive time out.
                 */
                int a = self_ftdi != null ? self_ftdi.getQueueStatus() : 0;

                if (a == 0) {
                    Minimum = 1;
                } else {
                    Minimum = Math.min(Minimum, a);
                }
            } else {
                /*
                 * Threshold is enabled. Read should return when 'threshold'
                 * bytes have been received (or when the receive timeout
                 * expired)
                 */
                Minimum = Math.min(Minimum, threshold);
            }
            if (monThreadisInterrupted == true) {
                return (0);
            }
            byte[] fullData = new byte[Minimum];
            result = read(fullData, 0, Minimum);
            for (int i = 0; i < Minimum; i++) {
                if (i < off) {
                    continue;
                }
                b[i - off] = fullData[i];
                if (b[i - off] == t[1] && b[i - off - 1] == t[0]) {
                    break;
                }
            }
            return result;
        }

        /**
         * Simply call {@link self.#getQueueStatus() selfgetQueueStatus()} on
         * possible devices. otherwise will return 0 in case of error and a -1
         * in case of unsupported function for current device.
         *
         * @return int bytes available
         * @throws IOException
         */
        @Override
        public synchronized int available() throws IOException {
            if (monThreadisInterrupted == true) {
                return (0);
            }
            return self_ftdi != null ? self_ftdi.getQueueStatus() : -1;
        }
    }

    /**
     * Close our SerialPort device.
     */
    public void close() {
        if (self_ftdi != null) {
            self_ftdi.close();
        }
        if (self_prolific != null) {
            self_prolific.end();
        }
    }
    /**
     * Always At 0... because not used at the moment.
     */
    private int threshold = 0;
}
