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

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;
import com.caucho.vfs.FilePath;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.logging.*;

/**
 * Represents a Quercus file open for reading
 */
public class FileInput extends ReadStreamInput implements LockableStream {
  private static final Logger log
    = Logger.getLogger(FileInput.class.getName());

  private Env _env;
  private Path _path;

  private FileLock _fileLock;
  private FileChannel _fileChannel;

  public FileInput(Env env, Path path)
    throws IOException
  {
    super(env);
    
    _env = env;
    
    env.addClose(this);
    
    _path = path;

    init(path.openRead());
  }

  /**
   * Returns the path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Opens a copy.
   */
  public BinaryInput openCopy()
    throws IOException
  {
    return new FileInput(_env, _path);
  }

  /**
   * Returns the number of bytes available to be read, 0 if no known.
   */
  public long getLength()
  {
    return getPath().getLength();
  }

  public long seek(long offset, int whence)
  {
    long position;

    switch (whence) {
      case BinaryInput.SEEK_CUR:
        position = getPosition() + offset;
        break;
      case BinaryInput.SEEK_END:
        position = getLength() + offset;
        break;
      case BinaryInput.SEEK_SET:
      default:
        position = offset;
        break;
    }

    if (! setPosition(position))
      return -1L;
    else
      return position;
  }

  /**
   * Lock the shared advisory lock.
   */
  public boolean lock(boolean shared, boolean block)
  {
    if (! (getPath() instanceof FilePath))
      return true;

    unlock();

    if (!shared) {
      // Invalid request for an exclusive "write" lock on a read only stream.

      return false;
    }

    try {
      if (_fileChannel == null) {
        _fileChannel = FilePath.getFileChannel((ReadStream) getInputStream());
      }

      if (block)
        _fileLock = _fileChannel.lock(0, Long.MAX_VALUE, true);
      else
        _fileLock = _fileChannel.tryLock(0, Long.MAX_VALUE, true);

      return _fileLock != null;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return false;
    }
  }

  /**
   * Unlock the advisory lock.
   */
  public boolean unlock()
  {
    try {
      FileLock lock = _fileLock;
      _fileLock = null;

      if (lock != null) {
        lock.release();
      }

      // flock($fd, LOCK_UN) returns true
      // even when no lock is held.

      return true;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }

  public Value stat()
  {
    return FileModule.statImpl(_env, getPath());
  }

  public void close()
  {
    _env.removeClose(this);

    unlock();

    _fileChannel = null;

    super.close();
  }

  /**
   * Converts to a string.
   */
  public String toString()
  {
    return "FileInput[" + getPath() + "]";
  }
}

