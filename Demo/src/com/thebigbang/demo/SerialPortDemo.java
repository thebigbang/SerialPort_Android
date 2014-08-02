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
package com.thebigbang.demo;

import java.io.IOException;
import java.util.TooManyListenersException;

import com.thebigbang.SerialPort;
import com.thebigbang.SerialPortConfig;
import com.thebigbang.SerialPortEvent;
import com.thebigbang.SerialPortEventListener;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class SerialPortDemo extends Activity {
	private SerialPort serial;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		//set configuration for the new serial port to open:
		SerialPortConfig config = new SerialPortConfig();
		config.baudRate = 19200;
		//initialize the new serial port. Give a try to AutomaticInit in case you have no idea of the chip used for the adapter.
		//but I would still advice to prefer the manufacturers specific one's (prolific and ftdevices)
		serial = SerialPort.AutomaticInit(this, config, null, null);
		if(serial==null)
		{
			//if no known devices are found we then return or manage our application in another way:
			Toast.makeText(this, "No Serial Port Found", Toast.LENGTH_LONG).show();
			return;
		}
		//tell the serial port to notify the main class of new data available 
		//(read purposes and special communication process)
		serial.notifyOnDataAvailable(true);
		try {
			serial.addEventListener(new SerialPortEventListener() {
				@Override
				public void serialEvent(SerialPortEvent ev) {
					//If we are here then it means some new data arrived: we can read it all or not.
					//it works like any Streams (ReadOnly)
					try {
						int bufSize;
						bufSize = serial.getInputStream().read();
						byte[] buffer = new byte[bufSize];
						serial.getInputStream().read(buffer, 0, bufSize);
						//you can now push to the design your answer, trigger a button, or directly writing data on the screen (not showed in that demo).
						Toast.makeText(SerialPortDemo.this, new String(buffer), Toast.LENGTH_LONG).show();
					} catch (IOException e) {
						e.printStackTrace();
					}
					//When reading is done internal serialPort buffer is empty. Then serial.getInputStream().read() will return -1;
				}
			});
		} catch (TooManyListenersException e) {
			e.printStackTrace();
			//for now only one listener instance is authorized, any more will throw that exception.
		}
		//now to write some data to the serial port you have to use:
		try {
			//here you can send data like for any Stream (Writing only, Output). Working with a buffer, size and all.
			serial.getOutputStream().write("This is the data to send over serial port [ACK] and equivalent are totally accepted".getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			//an IO error can occur, please keep in mind to check for exception as device may not have received the data.
		}
	}
}
