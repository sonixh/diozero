package com.diozero.sampleapps;

import org.pmw.tinylog.Logger;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;

/**
 * Test switching between input and output modes. To run:
 * <ul>
 * <li>JDK Device I/O 1.0:<br>
 *  {@code sudo java -cp tinylog-1.0.3.jar:diozero-core-$DIOZERO_VERSION.jar:diozero-provider-jdkdio10-$DIOZERO_VERSION.jar:dio-1.0.1-dev-linux-armv6hf.jar -Djava.library.path=. com.diozero.sampleapps.InOutTest 12}</li>
 * <li>JDK Device I/O 1.1:<br>
 *  {@code sudo java -cp tinylog-1.0.3.jar:diozero-core-$DIOZERO_VERSION.jar:diozero-provider-jdkdio11-$DIOZERO_VERSION.jar:dio-1.1-dev-linux-armv6hf.jar -Djava.library.path=. com.diozero.sampleapps.InOutTest 12}</li>
 * <li>Pi4j:<br>
 *  {@code sudo java -cp tinylog-1.0.3.jar:diozero-core-$DIOZERO_VERSION.jar:diozero-provider-pi4j-$DIOZERO_VERSION.jar:pi4j-core-1.1-SNAPSHOT.jar com.diozero.sampleapps.InOutTest 12}</li>
 * <li>wiringPi:<br>
 *  {@code sudo java -cp tinylog-1.0.3.jar:diozero-core-$DIOZERO_VERSION.jar:diozero-provider-wiringpi-$DIOZERO_VERSION.jar:pi4j-core-1.1-SNAPSHOT.jar com.diozero.sampleapps.InOutTest 12}</li>
 * <li>pigpgioJ:<br>
 *  {@code sudo java -cp tinylog-1.0.3.jar:diozero-core-$DIOZERO_VERSION.jar:diozero-provider-pigpio-$DIOZERO_VERSION.jar:pigpioj-java-1.0.0.jar com.diozero.sampleapps.InOutTest 12}</li>
 * </ul>
 */
public class InOutTest {
	public static void main(String[] args) {
		if (args.length < 1) {
			Logger.error("Usage: {} <BCM pin number>", InOutTest.class.getName());
			System.exit(1);
		}
		test(Integer.parseInt(args[0]));
	}
	
	public static void test(int pin) {
		try (DigitalInputDevice in = new DigitalInputDevice(pin)) {
			Logger.info("in.getValue(): " + in.getValue());
		}
		
		try (DigitalOutputDevice out = new DigitalOutputDevice(pin)) {
			out.on();
			Logger.info("out.isOn(): " + out.isOn());
		}
	}
}