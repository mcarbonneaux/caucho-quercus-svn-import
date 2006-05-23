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

package com.caucho.quercus.env;

/**
 * Represents a Quercus object value.
 */
abstract public class ObjectValue extends Value {
  /**
   * Returns the class name.
   */
  abstract public String getName();

  /**
   * Returns the parent class
   */
  abstract public String getParentName();

  /**
   * Adds a new value.
   */
  public Value putField(String key, String value)
  {
    return putField(null, key, new StringValueImpl(value));
  }

  /**
   * Adds a new value.
   */
  public Value putField(String key, long value)
  {
    return putField(null, key, LongValue.create(value));
  }
  
  /**
   * Adds a new value.
   */
  public Value putFieldInit(Env env, String key, Value value)
  {
    return putField(env, key, value);
  }

  /**
   * Adds a new value.
   */
  public Value putField(String key, double value)
  {
    return putField(null, key, DoubleValue.create(value));
  }

  /**
   * Compare two objects
   */
  public int cmpObject(ObjectValue rValue)
  {
    throw new RuntimeException("not implemented");
  }

}

