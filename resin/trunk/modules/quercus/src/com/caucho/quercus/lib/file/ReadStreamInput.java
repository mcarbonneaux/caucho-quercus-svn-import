/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.quercus.lib.file;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.caucho.quercus.QuercusModuleException;

import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.StringBuilderValue;
import com.caucho.quercus.env.BinaryValue;
import com.caucho.quercus.env.BinaryBuilderValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.BooleanValue;

import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;

/**
 * Represents a Quercus file open for reading
 */
public class ReadStreamInput extends InputStream implements BinaryInput {
  private static final Logger log
    = Logger.getLogger(ReadStreamInput.class.getName());

  private ReadStream _is;

  public ReadStreamInput()
  {
  }

  public ReadStreamInput(ReadStream is)
  {
    init(is);
  }

  public void init(ReadStream is)
  {
    _is = is;
  }

  /**
   * Returns the input stream.
   */
  public InputStream getInputStream()
  {
    return _is;
  }

  /**
   * Opens a copy.
   */
  public BinaryInput openCopy()
    throws IOException
  {
    return new ReadStreamInput(_is.getPath().openRead());
  }

  public void setEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    if (_is != null)
      _is.setEncoding(encoding);
  }

  /**
   *
   */
  public void unread()
    throws IOException
  {
    if (_is != null)
      _is.unread();
  }

  /**
   * Reads a character from a file, returning -1 on EOF.
   */
  public int read()
    throws IOException
  {
    if (_is != null)
      return _is.read();
    else
      return -1;
  }

  /**
   * Reads a buffer from a file, returning -1 on EOF.
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    if (_is != null) {
      return _is.read(buffer, offset, length);
    }
    else
      return -1;
  }

  /**
   * Reads into a binary builder.
   */
  public BinaryValue read(int length)
    throws IOException
    {
      if (_is == null)
        return null;

      BinaryBuilderValue bb = new BinaryBuilderValue();

      while (length > 0) {
        bb.prepareReadBuffer();

        int sublen = bb.getLength() - bb.getOffset();

        if (length < sublen)
          sublen = length;

        sublen = read(bb.getBuffer(), bb.getOffset(), sublen);

        if (sublen > 0) {
          bb.setOffset(bb.getOffset() + sublen);
          length -= sublen;
        }
        else
          return bb;
      }

      return bb;
    }

  /**
   * Reads the optional linefeed character from a \r\n
   */
  public boolean readOptionalLinefeed()
    throws IOException
  {
    if (_is == null)
      return false;
    
    int ch = _is.read();

    if (ch == '\n') {
      return true;
    }
    else {
      _is.unread();
      return false;
    }
  }

  public void writeToStream(OutputStream os, int length)
    throws IOException
  {
    if (_is != null) {
      _is.writeToStream(os, length);
    }
  }

  /**
   * Reads a line from a file, returning null on EOF.
   */
  public StringValue readLine(long length)
    throws IOException
  {
    if (_is == null)
      return null;
    
    StringBuilderValue sb = new StringBuilderValue();

    int ch;

    for (; length > 0 && (ch = _is.readChar()) >= 0; length--) {
      if (ch == '\n') {
	sb.append((char) ch);
	return sb;
      }
      else if (ch == '\r') {
	sb.append('\r');
	
	int ch2 = _is.read();

	if (ch == '\n')
	  sb.append('\n');
	else
	  _is.unread();
	
	return sb;
      }
      else
	sb.append((char) ch);
    }

    if (sb.length() == 0)
      return null;
    else
      return sb;
  }

  /**
   * Returns true on the EOF.
   */
  public boolean isEOF()
  {
    if (_is == null)
      return true;
    else {
      try {
        // XXX: not quite right for sockets
        return  _is.available() <= 0;
      } catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);

        return true;
      }
    }
  }

  /**
   * Returns the current location in the file.
   */
  public long getPosition()
  {
    if (_is == null)
      return -1;
    else
      return _is.getPosition();
  }

  /**
   * Returns the current location in the file.
   */
  public boolean setPosition(long offset)
  {
    if (_is == null)
      return false;

    try {
      _is.setPosition(offset);

      return true;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  public long seek(long offset, int whence)
  {
    switch (whence) {
      case BinaryInput.SEEK_CUR:
        offset = getPosition() + offset;
        break;
      case BinaryInput.SEEK_END:
        // don't necessarily have an end
        offset = getPosition();
        break;
      case SEEK_SET:
        break;
      default:
        break;
    }

    setPosition(offset);

    return offset;
  }

  public Value stat()
  {
    return BooleanValue.FALSE;
  }

  /**
   * Closes the stream for reading.
   */
  public void closeRead()
  {
    close();
  }

  /**
   * Closes the file.
   */
  public void close()
  {
    try {
      ReadStream is = _is;
      _is = null;

      if (is != null)
        is.close();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  public Object toJavaObject()
  {
    return this;
  }

  public String getResourceType()
  {
    return "stream";
  }

  /**
   * Converts to a string.
   */
  public String toString()
  {
    return "ReadStreamInput[" + _is.getPath() + "]";
  }
}

