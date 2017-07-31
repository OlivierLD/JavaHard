/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Copyright (c) The Processing Foundation 2015
  Hardware I/O library developed by Gottfried Haider as part of GSoC 2015

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package jhard.io;

import jhard.io.JHardNativeInterface;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;


/**
 *  @webref
 */
public class LED {

  protected String dev;
  protected int maxBrightness;
  protected int prevBrightness;
  protected String prevTrigger;

  /**
   *  Opens a LED device
   *  @param dev device name
   *  @see list
   *  @webref
   */
  public LED(String dev) {
    JHardNativeInterface.loadLibrary();
    this.dev = dev;

    if (JHardNativeInterface.isSimulated()) {
      return;
    }

    // read maximum brightness
    try {
      Path path = Paths.get("/sys/class/leds/" + dev + "/max_brightness");
      String tmp = new String(Files.readAllBytes(path));
      maxBrightness = Integer.parseInt(tmp.trim());
    } catch (Exception e) {
      System.err.println(e.getMessage());
      throw new RuntimeException("Unable to read maximum brightness");
    }

    // read current trigger setting to be able to restore it later
    try {
      Path path = Paths.get("/sys/class/leds/" + dev + "/trigger");
      String tmp = new String(Files.readAllBytes(path));
      int start = tmp.indexOf('[');
      int end = tmp.indexOf(']', start);
      if (start != -1 && end != -1) {
        prevTrigger = tmp.substring(start+1, end);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
      throw new RuntimeException("Unable to read trigger setting");
    }

    // read current brightness to be able to restore it later
    try {
      Path path = Paths.get("/sys/class/leds/" + dev + "/brightness");
      String tmp = new String(Files.readAllBytes(path));
      prevBrightness = Integer.parseInt(tmp.trim());
    } catch (Exception e) {
      System.err.println(e.getMessage());
      throw new RuntimeException("Unable to read current brightness");
    }

    // disable trigger
    String fn = "/sys/class/leds/" + dev + "/trigger";
    int ret = JHardNativeInterface.writeFile(fn, "none");
    if (ret < 0) {
      if (ret == -13) {     // EACCES
        System.err.println("You might need to install a custom udev rule to allow regular users to modify /sys/class/leds/*.");
      }
      throw new RuntimeException(JHardNativeInterface.getError(ret));
    }
  }


  /**
   *  Sets the brightness
   *  @param bright 0.0 (off) to 1.0 (maximum)
   *  @webref
   */
  public void brightness(float bright) {
    if (bright < 0.0 || 1.0 < bright) {
      System.err.println("Brightness must be between 0.0 and 1.0.");
      throw new IllegalArgumentException("Illegal argument");
    }
    if (JHardNativeInterface.isSimulated()) {
      return;
    }

    String fn = "/sys/class/leds/" + dev + "/brightness";
    int ret = JHardNativeInterface.writeFile(fn, Integer.toString((int)(bright * maxBrightness)));
    if (ret < 0) {
      throw new RuntimeException(fn + ": " + JHardNativeInterface.getError(ret));
    }
  }

  /**
   *  Restores the previous state
   *  @webref
   */
  public void close() {
    if (JHardNativeInterface.isSimulated()) {
      return;
    }

    // restore previous settings
    String fn = "/sys/class/leds/" + dev + "/brightness";
    int ret = JHardNativeInterface.writeFile(fn, Integer.toString(prevBrightness));
    if (ret < 0) {
      throw new RuntimeException(fn + ": " + JHardNativeInterface.getError(ret));
    }

    fn = "/sys/class/leds/" + dev + "/trigger";
    ret = JHardNativeInterface.writeFile(fn, prevTrigger);
    if (ret < 0) {
      throw new RuntimeException(fn + ": " + JHardNativeInterface.getError(ret));
    }
  }

  /**
   *  Lists all available LED devices
   *  @return String array
   *  @webref
   */
  public static String[] list() {
    if (JHardNativeInterface.isSimulated()) {
      // as on the Raspberry Pi
      return new String[]{ "led0", "led1" };
    }

    List<String> devs = new ArrayList<>();
    File dir = new File("/sys/class/leds");
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        devs.add(file.getName());
      }
    }
    // listFiles() does not guarantee ordering
    String[] tmp = devs.toArray(new String[devs.size()]);
    Arrays.sort(tmp);
    return tmp;
  }
}