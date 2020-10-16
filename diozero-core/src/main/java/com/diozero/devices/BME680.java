package com.diozero.devices;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.tinylog.Logger;

import com.diozero.api.BarometerInterface;
import com.diozero.api.HygrometerInterface;
import com.diozero.api.I2CConstants;
import com.diozero.api.I2CDevice;
import com.diozero.api.ThermometerInterface;
import com.diozero.util.SleepUtil;

/*-
 * https://cdn-shop.adafruit.com/product-files/3660/BME680.pdf
 */
public class BME680 implements BarometerInterface, ThermometerInterface, HygrometerInterface {
	/**
	 * Chip vendor for the BME680
	 */
	private static final String CHIP_VENDOR = "Bosch";

	/**
	 * Chip name for the BME680
	 */
	private static final String CHIP_NAME = "BME680";

	/**
	 * Chip ID for the BME680
	 */
	private static final int CHIP_ID_BME680 = 0x61;

	/**
	 * Default I2C address for the sensor.
	 */
	private static final int DEFAULT_I2C_ADDRESS = 0x76;

	/**
	 * Alternative I2C address for the sensor.
	 */
	private static final int ALTERNATIVE_I2C_ADDRESS = 0x77;

	// Sensor constants from the datasheet.
	/**
	 * Mininum temperature in Celsius the sensor can measure.
	 */
	private static final float MIN_TEMP_C = -40f;
	
	/**
	 * Maximum temperature in Celsius the sensor can measure.
	 */
	private static final float MAX_TEMP_C = 85f;
	
	/**
	 * Minimum pressure in hPa the sensor can measure.
	 */
	private static final float MIN_PRESSURE_HPA = 300f;
	
	/**
	 * Maximum pressure in hPa the sensor can measure.
	 */
	private static final float MAX_PRESSURE_HPA = 1100f;
	
	/**
	 * Minimum humidity in percentage the sensor can measure.
	 */
	private static final float MIN_HUMIDITY_PERCENT = 0f;
	
	/**
	 * Maximum humidity in percentage the sensor can measure.
	 */
	private static final float MAX_HUMIDITY_PERCENT = 100f;
	
	/**
	 * Minimum humidity in percentage the sensor can measure.
	 */
	private static final float MIN_GAS_PERCENT = 10f;
	
	/**
	 * Maximum humidity in percentage the sensor can measure.
	 */
	private static final float MAX_GAS_PERCENT = 95f;
	
	/**
	 * Maximum power consumption in micro-amperes when measuring temperature.
	 */
	private static final float MAX_POWER_CONSUMPTION_TEMP_UA = 350f;
	
	/**
	 * Maximum power consumption in micro-amperes when measuring pressure.
	 */
	private static final float MAX_POWER_CONSUMPTION_PRESSURE_UA = 714f;
	
	/**
	 * Maximum power consumption in micro-amperes when measuring pressure.
	 */
	private static final float MAX_POWER_CONSUMPTION_HUMIDITY_UA = 340f;
	
	/**
	 * Maximum power consumption in micro-amperes when measuring volatile gases.
	 */
	private static final float MAX_POWER_CONSUMPTION_GAS_UA = 13f; // 12f
	
	// TODO: Fix this fake data from BME280
	/**
	 * Maximum frequency of the measurements.
	 */
	private static final float MAX_FREQ_HZ = 181f;
	
	/**
	 * Minimum frequency of the measurements.
	 */
	private static final float MIN_FREQ_HZ = 23.1f;

	/**
	 * Power mode.
	 */
	public enum PowerMode {
		SLEEP, FORCED;
	}

	/**
	 * Oversampling multiplier.
	 */
	public enum OversamplingMultiplier {
		NONE(0), X1(1), X2(2), X4(4), X8(8), X16(16);
		
		private int cycles;
		
		private OversamplingMultiplier(int cycles) {
			this.cycles = cycles;
		}
		
		public int getCycles() {
			return cycles;
		}
	}

	/**
	 * IIR filter size.
	 */
	public enum FilterSize {
		NONE, SIZE_1, SIZE_3, SIZE_7, SIZE_15, SIZE_31, SIZE_63, SIZE_127;
	}

	/**
	 * Gas heater profile.
	 */
	public enum HeaterProfile {
		PROFILE_0, PROFILE_1, PROFILE_2, PROFILE_3, PROFILE_4, PROFILE_5, PROFILE_6, PROFILE_7, PROFILE_8, PROFILE_9;
	}

	/**
	 * Gas heater duration.
	 */
	private static final int MIN_HEATER_DURATION = 1;
	private static final int MAX_HEATER_DURATION = 4032;

	// Registers
	private static final int CHIP_ID_ADDRESS = 0xD0;
	private static final int SOFT_RESET_ADDRESS = 0xe0;

	// Sensor configuration registers
	private static final int CONFIG_HEATER_CONTROL_ADDRESS = 0x70;
	private static final int CONFIG_ODR_RUN_GAS_NBC_ADDRESS = 0x71;
	private static final int CONFIG_OS_H_ADDRESS = 0x72;
	private static final int CONFIG_T_P_MODE_ADDRESS = 0x74;
	private static final int CONFIG_ODR_FILTER_ADDRESS = 0x75;

	// field_x related defines
	private static final int FIELD0_ADDRESS = 0x1d;
	private static final int FIELD_LENGTH = 15;
	private static final int FIELD_ADDRESS_OFFSET = 17;

	// Heater settings
	private static final int RESISTANCE_HEAT0_ADDRESS = 0x5a;
	private static final int GAS_WAIT0_ADDRESS = 0x64;

	// Commands
	private static final int SOFT_RESET_COMMAND = 0xb6;

	// BME680 coefficients related defines
	private static final int COEFFICIENT_ADDRESS1_LEN = 25;
	private static final int COEFFICIENT_ADDRESS2_LEN = 16;

	// Coefficient's address
	private static final int COEFFICIENT_ADDRESS1 = 0x89;
	private static final int COEFFICIENT_ADDRESS2 = 0xe1;

	// Other coefficient's address
	private static final int RESISTANCE_HEAT_VALUE_ADDRESS = 0x00;
	private static final int RESISTANCE_HEAT_RANGE_ADDRESS = 0x02;
	private static final int RANGE_SWITCHING_ERROR_ADDRESS = 0x04;
	private static final int SENSOR_CONFIG_START_ADDRESS = 0x5A;
	private static final int GAS_CONFIG_START_ADDRESS = 0x64;

	// Mask definitions
	private static final int GAS_MEASURE_MASK = 0b00110000;
	private static final int NBCONVERSION_MASK = 0b00001111;
	private static final int FILTER_MASK = 0b00011100;
	private static final int OVERSAMPLING_TEMPERATURE_MASK = 0b11100000;
	private static final int OVERSAMPLING_PRESSURE_MASK = 0b00011100;
	private static final int OVERSAMPLING_HUMIDITY_MASK = 0b00000111;
	private static final int HEATER_CONTROL_MASK = 0b00001000;
	private static final int RUN_GAS_MASK = 0b00010000;
	private static final int MODE_MASK = 0b00000011;
	private static final int RESISTANCE_HEAT_RANGE_MASK = 0b00110000;
	private static final int RANGE_SWITCHING_ERROR_MASK = 0b11110000;
	private static final int NEW_DATA_MASK = 0b10000000;
	private static final int GAS_INDEX_MASK = 0b00001111;
	private static final int GAS_RANGE_MASK = 0b00001111;
	private static final int GASM_VALID_MASK = 0b00100000;
	private static final int HEAT_STABLE_MASK = 0b00010000;
	private static final int MEM_PAGE_MASK = 0b00010000;
	private static final int SPI_RD_MASK = 0b10000000;
	private static final int SPI_WR_MASK = 0b01111111;
	private static final int BIT_H1_DATA_MASK = 0b00001111;

	// Bit position definitions for sensor settings
	private static final int GAS_MEASURE_POSITION = 4;
	private static final int NBCONVERSION_POSITION = 0;
	private static final int FILTER_POSITION = 2;
	private static final int OVERSAMPLING_TEMPERATURE_POSITION = 5;
	private static final int OVERSAMPLING_PRESSURE_POSITION = 2;
	private static final int OVERSAMPLING_HUMIDITY_POSITION = 0;
	private static final int HEATER_CONTROL_POSITION = 3;
	private static final int RUN_GAS_POSITION = 4;
	private static final int MODE_POSITION = 0;

	// Array Index to Field data mapping for Calibration Data
	private static final int T2_LSB_REGISTER = 1;
	private static final int T2_MSB_REGISTER = 2;
	private static final int T3_REGISTER = 3;
	private static final int P1_LSB_REGISTER = 5;
	private static final int P1_MSB_REGISTER = 6;
	private static final int P2_LSB_REGISTER = 7;
	private static final int P2_MSB_REGISTER = 8;
	private static final int P3_REGISTER = 9;
	private static final int P4_LSB_REGISTER = 11;
	private static final int P4_MSB_REGISTER = 12;
	private static final int P5_LSB_REGISTER = 13;
	private static final int P5_MSB_REGISTER = 14;
	private static final int P7_REGISTER = 15;
	private static final int P6_REGISTER = 16;
	private static final int P8_LSB_REGISTER = 19;
	private static final int P8_MSB_REGISTER = 20;
	private static final int P9_LSB_REGISTER = 21;
	private static final int P9_MSB_REGISTER = 22;
	private static final int P10_REGISTER = 23;
	private static final int H2_MSB_REGISTER = 25;
	private static final int H2_LSB_REGISTER = 26;
	private static final int H1_LSB_REGISTER = 26;
	private static final int H1_MSB_REGISTER = 27;
	private static final int H3_REGISTER = 28;
	private static final int H4_REGISTER = 29;
	private static final int H5_REGISTER = 30;
	private static final int H6_REGISTER = 31;
	private static final int H7_REGISTER = 32;
	private static final int T1_LSB_REGISTER = 33;
	private static final int T1_MSB_REGISTER = 34;
	private static final int GH2_LSB_REGISTER = 35;
	private static final int GH2_MSB_REGISTER = 36;
	private static final int GH1_REGISTER = 37;
	private static final int GH3_REGISTER = 38;

	private static final int HUMIDITY_REGISTER_SHIFT_VALUE = 4;
	private static final int RESET_PERIOD_MILLISECONDS = 10;
	private static final int POLL_PERIOD_MILLISECONDS = 10;

	private I2CDevice device;
	private int resistanceHeaterRange;
	private int resistanceHeaterValue;
	private int rangeSwitchingError;

	// Look up tables for the possible gas range values
	final long GAS_RANGE_LOOKUP_TABLE_1[] = { 2147483647L, 2147483647L, 2147483647L, 2147483647L, 2147483647L,
			2126008810L, 2147483647L, 2130303777L, 2147483647L, 2147483647L, 2143188679L, 2136746228L, 2147483647L,
			2126008810L, 2147483647L, 2147483647L };

	final long GAS_RANGE_LOOKUP_TABLE_2[] = { 4096000000L, 2048000000L, 1024000000L, 512000000L, 255744255L, 127110228L,
			64000000L, 32258064L, 16016016L, 8000000L, 4000000L, 2000000L, 1000000L, 500000L, 250000L, 125000L };

	private static final int DATA_GAS_BURN_IN = 50;

	private int chipId;
	private PowerMode powerMode;
	private Calibration calibration;
	private GasSettings gasSettings;
	private SensorSettings sensorSettings;
	private Data data;
	private LinkedBlockingQueue<Long> gasResistanceData = new LinkedBlockingQueue<>(DATA_GAS_BURN_IN);
	private int temperatureFine;
	private long ambientTemperature;
	private int offsetTemperature;
	
	public BME680() {
		this(I2CConstants.BUS_1, DEFAULT_I2C_ADDRESS);
	}

	/**
	 * Create a new BME680 sensor driver connected on the given bus.
	 *
	 * @param controller I2C bus the sensor is connected to.
	 * @throws IOException
	 */
	public BME680(final int controller) {
		this(controller, DEFAULT_I2C_ADDRESS);
	}

	/**
	 * Create a new BME680 sensor driver connected on the given bus and address.
	 *
	 * @param controller I2C bus the sensor is connected to.
	 * @param address    I2C address of the sensor.
	 * @throws IOException
	 */
	public BME680(final int controller, final int address) {
		connect(new I2CDevice(controller, address));
	}

	/**
	 * Create a new BME680 sensor driver connected to the given I2c device.
	 *
	 * @param device I2C device of the sensor.
	 * @throws IOException
	 */
	/* package */ BME680(I2CDevice device) {
		connect(device);
	}

	@Override
	public float getTemperature() {
		getSensorData();
	
		return data.temperature;
	}

	@Override
	public float getPressure() {
		getSensorData();
	
		return data.pressure;
	}

	@Override
	public float getRelativeHumidity() {
		getSensorData();
	
		return data.humidity;
	}

	public float getGasResistance() {
		getSensorData();
	
		return data.gasResistance;
	}

	public float getAirQuality() {
		getSensorData();
	
		return data.airQualityScore;
	}

	private void connect(I2CDevice device) {
		this.device = device;

		calibration = new Calibration();
		sensorSettings = new SensorSettings();
		gasSettings = new GasSettings();
		data = new Data();

		prefillGasDataResistance();

		chipId = device.readByte(CHIP_ID_ADDRESS);
		if (chipId != CHIP_ID_BME680) {
			throw new IllegalStateException(String.format("%s %s not found.", CHIP_VENDOR, CHIP_NAME));
		}

		softReset();
		setPowerMode(PowerMode.SLEEP);

		getCalibrationData();

		// It is highly recommended to set first osrs_h<2:0> followed by osrs_t<2:0> and
		// osrs_p<2:0> in one write command (see Section 3.3).
		setHumidityOversample(OversamplingMultiplier.X1); // 0x72
		setTemperatureOversample(OversamplingMultiplier.X1); // 0x74
		setPressureOversample(OversamplingMultiplier.X1); // 0x74

		setFilter(FilterSize.NONE);

		setHeaterEnabled(true);
		setGasMeasurementEnabled(true);

		setTemperatureOffset(0);
	}

	/**
	 * Close the driver and the underlying device.
	 */
	@Override
	public void close() {
		if (device != null) {
			try {
				device.close();
			} finally {
				sensorSettings = null;
				gasSettings = null;
				device = null;
			}
		}
	}

	// Initiate a soft reset
	private void softReset() {
		device.writeByte(SOFT_RESET_ADDRESS, (byte) SOFT_RESET_COMMAND);
	
		SleepUtil.sleepMillis(RESET_PERIOD_MILLISECONDS);
	}

	// Get power mode
	public PowerMode getPowerMode() {
		powerMode = PowerMode.values()[device.readByte(CONFIG_T_P_MODE_ADDRESS) & MODE_MASK];

		return powerMode;
	}

	// Set power mode
	public void setPowerMode(final PowerMode value) {
		setRegByte(CONFIG_T_P_MODE_ADDRESS, (byte) MODE_MASK, MODE_POSITION, value.ordinal());

		powerMode = value;

		while (powerMode != getPowerMode()) {
			SleepUtil.sleepMillis(POLL_PERIOD_MILLISECONDS);
		}
	}

	// Get temperature oversampling
	public OversamplingMultiplier getTemperatureOversample() {
		return OversamplingMultiplier.values()[(device.readByte(CONFIG_T_P_MODE_ADDRESS)
				& OVERSAMPLING_TEMPERATURE_MASK) >> OVERSAMPLING_TEMPERATURE_POSITION];
	}

	// Set temperature oversampling
	// A higher oversampling value means more stable sensor readings, with less
	// noise and jitter.
	// However each step of oversampling adds about 2ms to the latency, causing a
	// slower response time to fast transients.
	public void setTemperatureOversample(OversamplingMultiplier value) {
		setRegByte(CONFIG_T_P_MODE_ADDRESS, (byte) OVERSAMPLING_TEMPERATURE_MASK, OVERSAMPLING_TEMPERATURE_POSITION,
				value.ordinal());

		sensorSettings.oversamplingTemperature = value;
	}

	// Get humidity oversampling
	public OversamplingMultiplier getHumidityOversample() {
		return OversamplingMultiplier.values()[(device.readByte(CONFIG_OS_H_ADDRESS)
				& OVERSAMPLING_HUMIDITY_MASK) >> OVERSAMPLING_HUMIDITY_POSITION];
	}

	// Set humidity oversampling
	// A higher oversampling value means more stable sensor readings, with less
	// noise and jitter.
	// However each step of oversampling adds about 2ms to the latency, causing a
	// slower response time to fast transients.
	public void setHumidityOversample(final OversamplingMultiplier value) {
		setRegByte(CONFIG_OS_H_ADDRESS, (byte) OVERSAMPLING_HUMIDITY_MASK, OVERSAMPLING_HUMIDITY_POSITION,
				value.ordinal());

		sensorSettings.oversamplingHumidity = value;
	}

	// Get pressure oversampling
	public OversamplingMultiplier getPressureOversample() {
		return OversamplingMultiplier.values()[(device.readByte(CONFIG_T_P_MODE_ADDRESS)
				& OVERSAMPLING_PRESSURE_MASK) >> OVERSAMPLING_PRESSURE_POSITION];
	}

	// Set pressure oversampling
	// A higher oversampling value means more stable sensor readings, with less
	// noise and jitter.
	// However each step of oversampling adds about 2ms to the latency,
	// causing a slower response time to fast transients.
	public void setPressureOversample(final OversamplingMultiplier value) {
		setRegByte(CONFIG_T_P_MODE_ADDRESS, (byte) OVERSAMPLING_PRESSURE_MASK, OVERSAMPLING_PRESSURE_POSITION,
				value.ordinal());

		sensorSettings.oversamplingPressure = value;
	}

	// Get IIR filter size
	public FilterSize getFilter() {
		return FilterSize.values()[(device.readByte(CONFIG_ODR_FILTER_ADDRESS) & FILTER_MASK) >> FILTER_POSITION];
	}

	// Set IIR filter size
	// Optionally remove short term fluctuations from the temperature and pressure
	// readings,
	// increasing their resolution but reducing their bandwidth.
	// Enabling the IIR filter does not slow down the time a reading takes,
	// but will slow down the BME680s response to changes in temperature and
	// pressure.
	// When the IIR filter is enabled, the temperature and pressure resolution is
	// effectively 20bit.
	// When it is disabled, it is 16bit + oversampling-1 bits.
	public void setFilter(final FilterSize value) {
		setRegByte(CONFIG_ODR_FILTER_ADDRESS, (byte) FILTER_MASK, FILTER_POSITION, value.ordinal());

		sensorSettings.filter = value;
	}

	// Get gas sensor conversion profile: 0 to 9
	public HeaterProfile getGasHeaterProfile() {
		return HeaterProfile.values()[device.readByte(CONFIG_ODR_RUN_GAS_NBC_ADDRESS) & NBCONVERSION_MASK];
	}

	// Set current gas sensor conversion profile: 0 to 9. Select one of the 10
	// configured heating durations/set points.
	public void setGasHeaterProfile(final HeaterProfile value) {
		setRegByte(CONFIG_ODR_RUN_GAS_NBC_ADDRESS, (byte) NBCONVERSION_MASK, NBCONVERSION_POSITION, value.ordinal());

		gasSettings.heaterProfile = value;
	}

	// Set temperature and duration of gas sensor heater
	// Target temperature in degrees celsius, between 200 and 400
	// Target duration in milliseconds, between 1 and 4032
	// Target profile, between 0 and 9
	public void setGasConfig(final HeaterProfile profile, final int temperature, final int duration) {
		setGasHeaterTemperature(profile, temperature);
		setGasHeaterDuration(profile, duration);
	}

	// Set gas sensor heater temperature
	// Target temperature in degrees celsius, between 200 and 400
	// When setting a profile other than 0, make sure to select it with
	// setGasHeaterProfile.
	public void setGasHeaterTemperature(final HeaterProfile profile, final int value) {
		device.writeByte(RESISTANCE_HEAT0_ADDRESS + profile.ordinal(), (byte) calculateHeaterResistance(value));

		gasSettings.heaterTemperature = value;
	}

	// Set gas sensor heater duration
	// Heating durations between 1 ms and 4032 ms can be configured.
	// Approximately 20 - 30 ms are necessary for the heater to reach the intended
	// target temperature.
	// Heating duration in milliseconds.
	// When setting a profile other than 0, make sure to select it with
	// setGasHeaterProfile.
	public void setGasHeaterDuration(final HeaterProfile profile, final int heaterDuration) {
		final int calculatedDuration = calculateGasHeaterDuration(heaterDuration);
		device.writeByte(GAS_WAIT0_ADDRESS + profile.ordinal(), (byte) calculatedDuration);

		gasSettings.heaterDuration = calculatedDuration;
	}

	public boolean isHeaterEnabled() {
		return ((device.readByte(CONFIG_HEATER_CONTROL_ADDRESS) & HEATER_CONTROL_MASK) >> HEATER_CONTROL_POSITION) == 1
				? false
				: true;
	}

	public void setHeaterEnabled(boolean heaterEnabled) {
		// Turn off current injected to heater by setting bit to one
		setRegByte(CONFIG_HEATER_CONTROL_ADDRESS, (byte) HEATER_CONTROL_MASK, HEATER_CONTROL_POSITION,
				heaterEnabled ? 0 : 1);

		gasSettings.heaterEnabled = heaterEnabled;
	}

	// Get the current gas status
	public boolean isGasMeasurementEnabled() {
		return ((device.readByte(CONFIG_ODR_RUN_GAS_NBC_ADDRESS) & RUN_GAS_MASK) >> RUN_GAS_POSITION) == 1 ? true
				: false;
	}

	// Enable/disable gas sensor
	public void setGasMeasurementEnabled(final boolean gasMeasurementsEnabled) {
		// The gas conversions are started only in appropriate mode if run_gas = '1'
		setRegByte(CONFIG_ODR_RUN_GAS_NBC_ADDRESS, (byte) RUN_GAS_MASK, RUN_GAS_POSITION,
				gasMeasurementsEnabled ? 1 : 0);

		gasSettings.gasMeasurementsEnabled = gasMeasurementsEnabled;
	}

	/**
	 * Set temperature offset in celsius. If set, the temperature t_fine will be
	 * increased by given value in celsius.
	 * 
	 * @param value temperature offset in Celsius, eg. 4, -8, 1.25
	 */
	public void setTemperatureOffset(final int value) {
		if (value == 0) {
			offsetTemperature = 0;
		} else {
			// self.offset_temp_in_t_fine = int(math.copysign((((int(abs(value) * 100)) <<
			// 8) - 128) / 5, value))
			offsetTemperature = (int) (Math.copySign(((Math.abs(value) * 100 << 8) - 128) / 5, value));
		}
	}

	private int calculateGasHeaterDuration(int duration) {
		int cycles = sensorSettings.oversamplingTemperature.getCycles();
		cycles += sensorSettings.oversamplingPressure.getCycles();
		cycles += sensorSettings.oversamplingHumidity.getCycles();
	
		/// TPH measurement duration calculated in microseconds [us]
		int tph_duration = cycles * 1963;
		tph_duration += (477 * 4); // TPH switching duration
		tph_duration += (477 * 5); // Gas measurement duration
		tph_duration += 500; // Get it to the closest whole number
		tph_duration /= 1000; // Convert to milisecond [ms]
		
		tph_duration += 1; // Wake up duration of 1ms
	
		// The remaining time should be used for heating
		return duration - tph_duration;
	}

	private int getProfileDuration() {
		int cycles = sensorSettings.oversamplingTemperature.getCycles();
		cycles += sensorSettings.oversamplingPressure.getCycles();
		cycles += sensorSettings.oversamplingHumidity.getCycles();
	
		/// Temperature, pressure and humidity measurement duration calculated in
		/// microseconds [us]
		int duration = cycles * 1963;
		duration += (477 * 4); // Temperature, pressure and humidity switching duration
		duration += (477 * 5); // Gas measurement duration
		duration += 500; // Get it to the closest whole number
		duration /= 1000; // Convert to milisecond [ms]
		
		duration += 1; // Wake up duration of 1ms
	
		// Get the gas duration only when gas measurements are enabled
		if (gasSettings.gasMeasurementsEnabled) {
			// The remaining time should be used for heating */
			duration += gasSettings.heaterDuration;
		}
	
		return duration;
	}

	private void getCalibrationData() {
		// Read the raw calibration data
		final byte[] calibration_data = readCalibrationData();

		/* Temperature related coefficients */
		calibration.temperature[0] = bytesToWord(calibration_data[T1_MSB_REGISTER], calibration_data[T1_LSB_REGISTER],
				false);
		calibration.temperature[1] = bytesToWord(calibration_data[T2_MSB_REGISTER], calibration_data[T2_LSB_REGISTER],
				true);
		calibration.temperature[2] = calibration_data[T3_REGISTER];

		/* Pressure related coefficients */
		calibration.pressure[0] = bytesToWord(calibration_data[P1_MSB_REGISTER], calibration_data[P1_LSB_REGISTER],
				false);
		calibration.pressure[1] = bytesToWord(calibration_data[P2_MSB_REGISTER], calibration_data[P2_LSB_REGISTER],
				true);
		calibration.pressure[2] = calibration_data[P3_REGISTER];
		calibration.pressure[3] = bytesToWord(calibration_data[P4_MSB_REGISTER], calibration_data[P4_LSB_REGISTER],
				true);
		calibration.pressure[4] = bytesToWord(calibration_data[P5_MSB_REGISTER], calibration_data[P5_LSB_REGISTER],
				true);
		calibration.pressure[5] = calibration_data[P6_REGISTER];
		calibration.pressure[6] = calibration_data[P7_REGISTER];
		calibration.pressure[7] = bytesToWord(calibration_data[P8_MSB_REGISTER], calibration_data[P8_LSB_REGISTER],
				true);
		calibration.pressure[8] = bytesToWord(calibration_data[P9_MSB_REGISTER], calibration_data[P9_LSB_REGISTER],
				true);
		calibration.pressure[9] = calibration_data[P10_REGISTER] & 0xFF;

		/* Humidity related coefficients */
		calibration.humidity[0] = (((calibration_data[H1_MSB_REGISTER] & 0xffff) << HUMIDITY_REGISTER_SHIFT_VALUE)
				| (calibration_data[H1_LSB_REGISTER] & BIT_H1_DATA_MASK)) & 0xffff;
		calibration.humidity[1] = (((calibration_data[H2_MSB_REGISTER] & 0xffff) << HUMIDITY_REGISTER_SHIFT_VALUE)
				| (calibration_data[H2_LSB_REGISTER] >> HUMIDITY_REGISTER_SHIFT_VALUE)) & 0xffff;
		calibration.humidity[2] = calibration_data[H3_REGISTER];
		calibration.humidity[3] = calibration_data[H4_REGISTER];
		calibration.humidity[4] = calibration_data[H5_REGISTER];
		calibration.humidity[5] = calibration_data[H6_REGISTER] & 0xFF;
		calibration.humidity[6] = calibration_data[H7_REGISTER];

		// Gas heater related coefficients
		calibration.gasHeater[0] = calibration_data[GH1_REGISTER];
		calibration.gasHeater[1] = bytesToWord(calibration_data[GH2_MSB_REGISTER], calibration_data[GH2_LSB_REGISTER],
				true);
		calibration.gasHeater[2] = calibration_data[GH3_REGISTER];

		/* Other coefficients */
		// Read other heater calibration data
		// res_heat_range is the heater range stored in register address 0x02 <5:4>
		resistanceHeaterRange = (device.readByte(RESISTANCE_HEAT_RANGE_ADDRESS) & RESISTANCE_HEAT_RANGE_MASK) / 16;
		// res_heat_val is the heater resistance correction factor stored in register
		// address 0x00
		// (signed, value from -128 to 127)
		resistanceHeaterValue = device.readByte(RESISTANCE_HEAT_VALUE_ADDRESS);

		// Range switching error from register address 0x04 <7:4> (signed 4 bit)
		rangeSwitchingError = (device.readByte(RANGE_SWITCHING_ERROR_ADDRESS) & RANGE_SWITCHING_ERROR_MASK) / 16;
	}

	// Read calibration array
	private byte[] readCalibrationData() {
		final byte[] part1 = device.readBytes(COEFFICIENT_ADDRESS1, COEFFICIENT_ADDRESS1_LEN);
		final byte[] part2 = device.readBytes(COEFFICIENT_ADDRESS2, COEFFICIENT_ADDRESS2_LEN);

		final byte[] calibration_data = new byte[COEFFICIENT_ADDRESS1_LEN + COEFFICIENT_ADDRESS2_LEN];
		System.arraycopy(part1, 0, calibration_data, 0, part1.length);
		System.arraycopy(part2, 0, calibration_data, part1.length, part2.length);

		return calibration_data;
	}

	// Get sensor data
	private void getSensorData() {
		setPowerMode(PowerMode.FORCED);
		
		int tries = 10;
		do {
			final byte[] buffer = device.readBytes(FIELD0_ADDRESS, FIELD_LENGTH);
			
			// Set to 1 during measurements, goes to 0 when measurements are completed
			boolean new_data = (buffer[0] & NEW_DATA_MASK) == 0 ? true : false;
			
			if (new_data) {
				data.newData = new_data;
				data.gasMeasurementIndex = buffer[0] & GAS_INDEX_MASK;
				data.measureIndex = buffer[1];
				
				// read the raw data from the sensor
				final int pressure = ((buffer[2] & 0xff) << 12) | ((buffer[3] & 0xff) << 4) | ((buffer[4] & 0xff) >> 4);
				final int temperature = ((buffer[5] & 0xff) << 12) | ((buffer[6] & 0xff) << 4) | ((buffer[7] & 0xff) >> 4);
				final int humidity = (buffer[8] << 8) | (buffer[9] & 0xff);
				final int gas_resistance = ((buffer[13] & 0xff) << 2) | ((buffer[14] & 0xff) >> 6);
				final int gas_range = buffer[14] & GAS_RANGE_MASK;

				ambientTemperature = temperature;

				data.gasMeasurementValid = (buffer[14] & GASM_VALID_MASK) == 0 ? false : true;
				data.heaterTempStable = (buffer[14] & HEAT_STABLE_MASK) == 0 ? false : true;

				data.temperature = calculateTemperature(temperature) / 100.0f;
				data.pressure = calculatePressure(pressure) / 100.0f;
				data.humidity = compensateHumidity(humidity) / 1000.0f;
				data.gasResistance = compensateGasResistance(gas_resistance, gas_range);
				data.airQualityScore = calculateAirQuality(gas_resistance, data.humidity);
				
				break;
			}
			
			/* Delay to poll the data */
			SleepUtil.sleepMillis(POLL_PERIOD_MILLISECONDS);
		} while (--tries > 0);
	}

	private int calculateTemperature(final int rawTemperature) {
		// Convert the raw temperature to degrees C using calibration_data.
		int var1 = (rawTemperature >> 3) - (calibration.temperature[0] << 1);
		int var2 = (var1 * calibration.temperature[1]) >> 11;
		int var3 = ((var1 >> 1) * (var1 >> 1)) >> 12;
		var3 = ((var3) * (calibration.temperature[2] << 4)) >> 14;

		// Save temperature data for pressure calculations
		temperatureFine = (var2 + var3) + offsetTemperature;

		return ((temperatureFine * 5) + 128) >> 8;
	}

	private int calculatePressure(final int rawPressure) {
		// Convert the raw pressure using calibration data.
		int var1 = (temperatureFine >> 1) - 64000;
		int var2 = ((((var1 >> 2) * (var1 >> 2)) >> 11) * calibration.pressure[5]) >> 2;
		var2 = var2 + ((var1 * calibration.pressure[4]) << 1);
		var2 = (var2 >> 2) + (calibration.pressure[3] << 16);
		var1 = (((((var1 >> 2) * (var1 >> 2)) >> 13) * (calibration.pressure[2] << 5)) >> 3)
				+ ((calibration.pressure[1] * var1) >> 1);
		var1 = var1 >> 18;

		var1 = ((32768 + var1) * calibration.pressure[0]) >> 15;
		int calculated_pressure = 1048576 - rawPressure;
		calculated_pressure = (calculated_pressure - (var2 >> 12)) * 3125;

		final int var4 = (1 << 31);
		if (calculated_pressure >= var4) {
			calculated_pressure = ((calculated_pressure / var1) << 1);
		} else {
			calculated_pressure = ((calculated_pressure << 1) / var1);
		}

		var1 = (calibration.pressure[8] * (((calculated_pressure >> 3) * (calculated_pressure >> 3)) >> 13)) >> 12;
		var2 = ((calculated_pressure >> 2) * calibration.pressure[7]) >> 13;
		final int var3 = ((calculated_pressure >> 8) * (calculated_pressure >> 8) * (calculated_pressure >> 8)
				* calibration.pressure[9]) >> 17;

		calculated_pressure = calculated_pressure + ((var1 + var2 + var3 + (calibration.pressure[6] << 7)) >> 4);

		return calculated_pressure;
	}

	private long compensateHumidity(final int humidity) {
		final int temp_scaled = ((temperatureFine * 5) + 128) >> 8;
		final int var1 = humidity - calibration.humidity[0] * 16
				- (((temp_scaled * calibration.humidity[2]) / 100) >> 1);
		final int var2 = (calibration.humidity[1] * (((temp_scaled * calibration.humidity[3]) / 100)
				+ (((temp_scaled * ((temp_scaled * calibration.humidity[4]) / 100)) >> 6) / 100) + (1 << 14))) >> 10;
		final int var3 = var1 * var2;
		int var4 = calibration.humidity[5] << 7;
		var4 = (var4 + ((temp_scaled * calibration.humidity[6]) / 100)) >> 4;
		final int var5 = ((var3 >> 14) * (var3 >> 14)) >> 10;
		final int var6 = (var4 * var5) >> 1;
		final int calc_hum = (((var3 + var6) >> 10) * 1000) >> 12;

		// Cap at 100%rH
		return Math.min(Math.max(calc_hum, 0), 100000);
	}

	private int compensateGasResistance(final int gas_resistance, final int gas_range) {
		final long var1 = (1340 + (5 * (long) rangeSwitchingError)) * GAS_RANGE_LOOKUP_TABLE_1[gas_range] >> 16;
		final long var2 = (((((long) gas_resistance) << 15) - 16777216L) + var1);
		final long var3 = ((GAS_RANGE_LOOKUP_TABLE_2[gas_range] * var1) >> 9);

		return (int) ((var3 + (var2 >> 1)) / var2);
	}

	private float calculateAirQuality(final long gasResistance, final float humidity) {
		// Set the humidity baseline to 40%, an optimal indoor humidity.
		final float humidityBaseline = 40.0f;
		// This sets the balance between humidity and gas reading in the calculation of
		// airQualityScore (25:75, humidity:gas)
		final float humidityWeighting = 0.25f;

		try {
			gasResistanceData.take();
			gasResistanceData.put(Long.valueOf(gasResistance));

			// Collect gas resistance burn-in values, then use the average of the last n
			// values to set the upper limit for calculating gasBaseline.
			final int gasBaseline = Math.round(sumQueueValues(gasResistanceData) / (float) DATA_GAS_BURN_IN);

			final long gasOffset = gasBaseline - gasResistance;

			final float humidityOffset = humidity - humidityBaseline;

			// Calculate humidityScore as the distance from the humidityBaseline
			final float humidityScore;
			if (humidityOffset > 0) {
				humidityScore = (100.0f - humidityBaseline - humidityOffset) / (100.0f - humidityBaseline)
						* (humidityWeighting * 100.0f);
			} else {
				humidityScore = (humidityBaseline + humidityOffset) / humidityBaseline * (humidityWeighting * 100.0f);
			}

			// Calculate gasScore as the distance from the gasBaseline
			final float gasScore;
			if (gasOffset > 0) {
				gasScore = (gasResistance / gasBaseline) * (100.0f - (humidityWeighting * 100.0f));
			} else {
				gasScore = 100.0f - (humidityWeighting * 100.0f);
			}

			return humidityScore + gasScore;
		} catch (InterruptedException e) {
			Logger.error(e, "Error: {}", e.getMessage());
			return data.airQualityScore;
		}
	}

	private int calculateHeaterResistance(final int temperature) {
		/* Cap temperature */
		final int normalizedTemperature = Math.min(Math.max(temperature, 200), 400);

		final long var1 = ((ambientTemperature * calibration.gasHeater[2]) / 1000) * 256;
		final int var2 = (calibration.gasHeater[0] + 784)
				* (((((calibration.gasHeater[1] + 154009) * normalizedTemperature * 5) / 100) + 3276800) / 10);
		final long var3 = var1 + (var2 / 2);
		final long var4 = (var3 / (resistanceHeaterRange + 4));
		final int var5 = (131 * resistanceHeaterValue) + 65536;
		final long heater_res_x100 = ((var4 / var5) - 250) * 34;

		return (short) ((heater_res_x100 + 50) / 100);
	}

	private void prefillGasDataResistance() {
		for (int i = 0; i < DATA_GAS_BURN_IN; i++) {
			gasResistanceData.add(Long.valueOf(0));
		}
	}

	private void setRegByte(final int address, final byte mask, final int position, final int value) {
		final byte oldData = device.readByte(address);

		byte newData;
		if (position == 0) {
			newData = (byte) ((oldData & ~mask) | (value & mask));
		} else {
			newData = (byte) ((oldData & ~mask) | ((value << position) & mask));
		}
		device.writeByte(address, newData);
	}

	private static int bytesToWord(final int msb, final int lsb, final boolean isSigned) {
		if (isSigned) {
			return (msb << 8) | (lsb & 0xff); // keep the sign of msb but not of lsb
		}
		return ((msb & 0xff) << 8) | (lsb & 0xff);
	}

	private static long sumQueueValues(final LinkedBlockingQueue<Long> queue) {
		long sum = 0;

		for (int i = 0; i < queue.size(); i++) {
			final Long n = queue.remove();
			sum += n.longValue();
			queue.add(n);
		}

		return sum;
	}

	public static class Calibration {
		final int[] temperature = new int[3];
		final int[] pressure = new int[10];
		final int[] humidity = new int[7];
		final int[] gasHeater = new int[3];
	}

	public static class GasSettings {
		// Variable to store nb conversion
		HeaterProfile heaterProfile;
		// Variable to store heater control
		boolean heaterEnabled;
		// Run gas enable value
		boolean gasMeasurementsEnabled;
		// Store heater temperature
		int heaterTemperature;
		// Store duration profile
		int heaterDuration;
	}

	public static class SensorSettings {
		// Humidity oversampling
		OversamplingMultiplier oversamplingHumidity = OversamplingMultiplier.NONE;
		// Temperature oversampling
		OversamplingMultiplier oversamplingTemperature = OversamplingMultiplier.NONE;
		// Pressure oversampling
		OversamplingMultiplier oversamplingPressure = OversamplingMultiplier.NONE;
		// Filter coefficient
		FilterSize filter = FilterSize.NONE;
	}

	public static class Data {
		// Contains new_data, gasm_valid & heat_stab
		public boolean newData;
		public boolean heaterTempStable;
		public boolean gasMeasurementValid;
		// The index of the heater profile used
		public int gasMeasurementIndex = -1;
		// Measurement index to track order
		public byte measureIndex = -1;
		// Temperature in degree celsius x100
		public float temperature;
		// Pressure in Pascal
		public float pressure;
		// Humidity in % relative humidity x1000
		public float humidity;
		// Gas resistance in Ohms
		public int gasResistance = 0;
		// Indoor air quality score index
		public float airQualityScore = 0.0f;
	}
}