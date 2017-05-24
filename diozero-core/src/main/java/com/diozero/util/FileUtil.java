package com.diozero.util;

/*
 * #%L
 * Device I/O Zero - Core
 * %%
 * Copyright (C) 2016 - 2017 mattjlewis
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */


import java.io.FileDescriptor;
import java.lang.reflect.Field;

import org.pmw.tinylog.Logger;

public class FileUtil {
	private static boolean initialised;
	private static Field fdField;
	
	public static synchronized int getNativeFileDescriptor(FileDescriptor fd) {
		if (! initialised) {
			try {
				fdField = FileDescriptor.class.getDeclaredField("fd");
				fdField.setAccessible(true);
				
				initialised = true;
			} catch (NoSuchFieldException | SecurityException e) {
				throw new RuntimeIOException("Error getting native file descriptor declared field: " + e, e);
			}
		}

		try {
			return ((Integer) fdField.get(fd)).intValue();
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeIOException("Error accessing private fd attribute: " + e, e);
		}
	}
}