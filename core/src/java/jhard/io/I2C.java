package jhard.io;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *  Generic I2C communication.
 */
public class I2C {

  protected String bus;
  protected int handle;
  protected int slave;
  protected byte[] out;
  protected boolean transmitting;

  private final static int EIO = -5;

  /**
   *  Opens an I2C interface as master
   *  @param bus interface name
   *  @see #list
   */
  public I2C(String bus) {
    JHardNativeInterface.loadLibrary();
    this.bus = bus;

    if (JHardNativeInterface.isSimulated()) {
      return;
    }

    handle = JHardNativeInterface.openDevice("/dev/" + bus);
    if (handle < 0) {
      throw new RuntimeException(JHardNativeInterface.getError(handle));
    }
  }
	
  /**
   *  Begins a transmission to an attached device
   *  @see #write
   *  @see #read
   *  @see #endTransmission
   */
  public void beginTransmission(int slave) {
    // addresses 120 (0x78) to 127 are additionally reserved
    if (0x78 <= slave) {
      System.err.println("beginTransmission expects a 7 bit address, try shifting one bit to the right");
      throw new IllegalArgumentException("Illegal address");
    }
    this.slave = slave;
    transmitting = true;
    out = null;
  }

  /**
   *  Closes the I2C device
   */
  public void close() {
    if (JHardNativeInterface.isSimulated()) {
      return;
    }
    JHardNativeInterface.closeDevice(handle);
    handle = 0;
  }

  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }

  /**
   *  Ends the current transmissions
   *  @see #beginTransmission
   *  @see #write
   *  @webref
   */
  public void endTransmission() {
    if (!transmitting) {
      // silently ignore this case
      return;
    }
    if (JHardNativeInterface.isSimulated()) {
      return;
    }
    // implement these flags if needed: https://github.com/raspberrypi/linux/blob/rpi-patches/Documentation/i2c/i2c-protocol
    int ret = JHardNativeInterface.transferI2c(handle, slave, out, null);
    transmitting = false;
    out = null;
    if (ret < 0) {
      if (ret == EIO) {
        System.err.println("The device did not respond. Check the cabling and whether you are using the correct address.");
      }
      throw new RuntimeException(JHardNativeInterface.getError(ret));
    }
  }

  /**
   *  Lists all available I2C interfaces
   *  @return String array
   *  @webref
   */
  public static String[] list() {
    if (JHardNativeInterface.isSimulated()) {
      // as on the Raspberry Pi
      return new String[]{ "i2c-1" };
    }

    ArrayList<String> devs = new ArrayList<String>();
    File dir = new File("/dev");
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().startsWith("i2c-")) {
          devs.add(file.getName());
        }
      }
    }
    // listFiles() does not guarantee ordering
    String[] tmp = devs.toArray(new String[devs.size()]);
    Arrays.sort(tmp);
    return tmp;
  }

  /**
   *  Reads bytes from the attached device
   *  @param len number of bytes to read
   *  @return bytes read from device
   *  @see #beginTransmission
   *  @see #write
   *  @see #endTransmission
   */
  public byte[] read(int len) {
    if (!transmitting) {
      throw new RuntimeException("beginTransmisson has not been called");
    }
    byte[] in = new byte[len];

    if (JHardNativeInterface.isSimulated()) {
      return in;
    }

    int ret = JHardNativeInterface.transferI2c(handle, slave, out, in);
    transmitting = false;
    out = null;
    if (ret < 0) {
      if (ret == EIO) {
        System.err.println("The device did not respond. Check the cabling and whether you are using the correct address.");
      }
      throw new RuntimeException(JHardNativeInterface.getError(ret));
    }
    return in;
  }

  /**
   *  Adds bytes to be written to the device
   *  @param out bytes to be written
   *  @see #beginTransmission
   *  @see #read
   *  @see #endTransmission
   */
  public void write(byte[] out) {
    if (!transmitting) {
      throw new RuntimeException("beginTransmisson has not been called");
    }

    if (this.out == null) {
      this.out = out;
    } else {
      byte[] tmp = new byte[this.out.length + out.length];
      System.arraycopy(this.out, 0, tmp, 0, this.out.length);
      System.arraycopy(out, 0, tmp, this.out.length, out.length);
      this.out = tmp;
    }
  }

  /**
   *  Adds bytes to be written to the attached device
   *  @param out string to be written
   *  @see #beginTransmission
   *  @see #read
   *  @see #endTransmission
   */
  public void write(String out) {
    write(out.getBytes());
  }

  /**
   *  Adds a byte to be written to the attached device
   *  @param out single byte to be written, e.g. numeric literal (0 to 255, or -128 to 127)
   *  @see #beginTransmission
   *  @see #read
   *  @see #endTransmission
   */
  public void write(int out) {
    if (out < -128 || out > 255) {
      System.err.println("The write function can only operate on a single byte at a time. Call it with a value from 0 to 255, or -128 to 127.");
      throw new RuntimeException("Argument does not fit into a single byte");
    }
//    byte[] tmp = new byte[1];
//    tmp[0] = (byte)out;
    write(new byte[] {(byte)out});
  }

  /**
   *  Adds a byte to be written to the attached device
   *  @param out single byte to be written
   *  @see #beginTransmission
   *  @see #read
   *  @see #endTransmission
   */
  public void write(byte out) {
    // cast to (unsigned) int
    write(out & 0xff);
  }
}
