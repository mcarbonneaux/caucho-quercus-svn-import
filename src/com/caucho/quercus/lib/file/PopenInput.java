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
 * @author Emil Ong
 */

package com.caucho.quercus.lib.file;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.StringBuilderValue;

import com.caucho.vfs.Path;
import com.caucho.vfs.FilePath;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.VfsStream;

/**
 * Represents an input stream for a popen'ed process.
 */
public class PopenInput extends ReadStreamInput {
  private static final Logger log
    = Logger.getLogger(FileInput.class.getName());

  private Env _env;
  private Process _process;

  public PopenInput(Env env, Process process)
    throws IOException
  {
    _env = env;
    
    _env.addClose(this);

    _process = process;

    init(new ReadStream(new VfsStream(_process.getInputStream(), null)));

    _process.getOutputStream().close();
  }

  /**
   * Opens a copy.
   */
  public BinaryInput openCopy()
    throws IOException
  {
    return new PopenInput(_env, _process);
  }

  /**
   * Returns the number of bytes available to be read, 0 if no known.
   */
  public long getLength()
  {
    return 0;
  }

  /**
   * Converts to a string.
   */
  public String toString()
  {
    return "PopenInput[" + _process + "]";
  }

  public void close()
  {
    pclose();
  }

  public int pclose() 
  {
    super.close();

    try {
      return _process.waitFor();
    } catch (Exception e) {
      return -1;
    } finally {
      _env.removeClose(this);
    }
  }
}

