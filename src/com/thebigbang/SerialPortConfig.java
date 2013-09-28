/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.thebigbang;

import tw.com.prolific.driver.pl2303.PL2303Driver;

/**
 * Simple class helping the SerialPort device configuration.
 *@since 1.2
 * @author thebigbang
 */
public class SerialPortConfig {

    public byte dataBits;
    public byte stopBits;
    public byte parity;
    public int baudRate;
    public short flowCtrlSetting;

    /**
     * simply convert quickly the baudRate value of our class into an enum used by prolifics drivers...
     * @return 
     */
    public PL2303Driver.BaudRate convertToProlificBaudRate() {
        switch (baudRate) {
            case 115200:
                return PL2303Driver.BaudRate.B115200;
            case 1200:
                return PL2303Driver.BaudRate.B1200;
            case 1228800:
                return PL2303Driver.BaudRate.B1228800;
            case 14400:
                return PL2303Driver.BaudRate.B14400;
            case 150:
                return PL2303Driver.BaudRate.B150;
            case 1800:
                return PL2303Driver.BaudRate.B1800;
            case 19200:
                return PL2303Driver.BaudRate.B19200;
            case 230400:
                return PL2303Driver.BaudRate.B230400;
            case 2400:
                return PL2303Driver.BaudRate.B2400;
            case 2457600:
                return PL2303Driver.BaudRate.B2457600;
            case 300:
                return PL2303Driver.BaudRate.B300;
            case 3000000:
                return PL2303Driver.BaudRate.B3000000;
            case 38400:
                return PL2303Driver.BaudRate.B38400;
            case 460800:
                return PL2303Driver.BaudRate.B460800;
            case 4800:
                return PL2303Driver.BaudRate.B4800;
            case 57600:
                return PL2303Driver.BaudRate.B57600;
            case 600:
                return PL2303Driver.BaudRate.B600;
            case 6000000:
                return PL2303Driver.BaudRate.B6000000;
            case 614400:
                return PL2303Driver.BaudRate.B614400;
            case 75:
                return PL2303Driver.BaudRate.B75;
            case 921600:
                return PL2303Driver.BaudRate.B921600;
            case 9600:
                return PL2303Driver.BaudRate.B9600;
            default:
                return PL2303Driver.BaudRate.B0;
        }
    }
/**
 * convert our dataBits into an enum used by prolifics driver
 * @param dataBits
 * @return 
 */
    public PL2303Driver.DataBits convertToProlificDataBits() {
        switch(dataBits)
        {
            default:return PL2303Driver.DataBits.D5;
            case 6:return PL2303Driver.DataBits.D6;
            case 7:return PL2303Driver.DataBits.D7;
            case 8:return PL2303Driver.DataBits.D8;
        }
    }

    /**
     * convert the flowcontrol value into one usable by Prolifics driver
     * todo: check correctness of the values.
     * @return 
     */
    public PL2303Driver.FlowControl convertToProlificFlowControl() {
        switch(flowCtrlSetting)
        {
           default:return PL2303Driver.FlowControl.OFF;
           case 1:return PL2303Driver.FlowControl.XONXOFF;
           case 2:return PL2303Driver.FlowControl.DTRDSR;
           case 3:return PL2303Driver.FlowControl.RFRCTS;
           case 4:return PL2303Driver.FlowControl.RTSCTS;
        }
    }

    /**
     * return the enum type used by Prolifics drivers
     * todo: check correctness of the values...
     * @return 
     */
    public PL2303Driver.Parity convertToProlificParity() {
        switch(parity)
        {
            default:return PL2303Driver.Parity.NONE;
            case 1:return PL2303Driver.Parity.EVEN;
            case 2:return PL2303Driver.Parity.ODD;
        }
    }

    /**
     * convert the stop bits into one enum used by prolific drivers
     * @return 
     */
    public PL2303Driver.StopBits convertToProlificStopBits() {
        switch(stopBits)
        {
            default:return PL2303Driver.StopBits.S1;
            case 2:return PL2303Driver.StopBits.S2;
        }
    }
}
