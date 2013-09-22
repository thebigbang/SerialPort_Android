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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

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
 *
 * @author Jeremy.Mei-Garino
 * @version 1.0
 */
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
    final private FT_Device self;
    // private SerialPort self;
    /**
     * Serial Port Event listener
     */
    private SerialPortEventListener SPEventListener;
    /**
     * Thread to monitor data
     */
    private MonitorThread monThread;

    public static SerialPort Init(FT_Device d, int baudRate) {
        if (d == null) {
            return null;
        }
        return new SerialPort(d, baudRate);
    }

    private SerialPort(FT_Device d, int b) {
        // super(parentContext, usbManager, dev, itf);
        portName = d.getDeviceInfo().location + "";
        self = d;
        baudRate = b;
    }

    public boolean setBaudRate(int b) {
        baudRate = b;
        return self.setBaudRate(b);
    }

    public void notifyOnDataAvailable(boolean enable) {
        monThread.Data = enable;
        self.setEventNotification(D2xxManager.FT_EVENT_RXCHAR);
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
                long event = 0;
                synchronized (self) {
                    event = self.getEventStatus();
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
         * Always throw IOException /!\Not working in Android yet.
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
            synchronized (self) {
                throw new IOException();
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
         * @param b []
         * @param off
         * @param len
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
                        synchronized (self) {
                            Log.i(Tag, "writing on " + Thread.currentThread().getName() + " thread");
                            self.write(_fullData, _length);
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
                self.write(send, len);
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
            synchronized (self) {
                self.purge((byte) 2);
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
         * @param len
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
                int a = self.getQueueStatus();// nativeavailable();
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
                        _result = self.read(_fullData, _Minimum);
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
                result = self.read(fullData, Minimum);
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
                int a = self.getQueueStatus();// nativeavailable();
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
         * Simply call {@link self.#getQueueStatus() selfgetQueueStatus()}
         *
         * @return int bytes available
         * @throws IOException
         */
        @Override
        public synchronized int available() throws IOException {
            if (monThreadisInterrupted == true) {
                return (0);
            }
            return self.getQueueStatus();
        }
    }

    /**
     * Close our SerialPort device.
     */
    public void close() {
        self.close();
    }
    /**
     * Always At 0... because not used at the moment.
     */
    private int threshold = 0;
}
