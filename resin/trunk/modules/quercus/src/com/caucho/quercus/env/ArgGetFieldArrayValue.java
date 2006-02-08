/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

import java.io.IOException;

import java.util.Collection;
import java.util.IdentityHashMap;

import com.caucho.util.L10N;

import com.caucho.vfs.WriteStream;

import com.caucho.quercus.QuercusRuntimeException;

import com.caucho.quercus.program.AbstractFunction;

import com.caucho.quercus.gen.PhpWriter;

import com.caucho.quercus.expr.Expr;

/**
 * Represents an object-getField argument which might be a call to a reference
 * and which might create an array
 *
 * e.g. foo($a->x[0]) where foo($a) or foo(&amp;$a)
 */
public class ArgGetFieldArrayValue extends Value {
  private final Env _env;
  private final Value _obj;
  private final String _index;

  public ArgGetFieldArrayValue(Env env, Value obj, String index)
  {
    _env = env;
    _obj = obj;
    _index = index;
  }

  /**
   * Returns the value for a getField arg.
   */
  public Value getArg(Value index)
  {
    // php/3d1q
    return new ArgGetValue(this, index);
  }

  /**
   * Returns the value for a getField arg.
   */
  public Value getFieldArg(String index)
  {
    // quercus/3d2u
    return new ArgGetFieldObjectValue(_env, this, index);
  }

  /**
   * Converts to a ref var.
   */
  public Var toRefVar()
  {
    return _obj.getFieldArray(_env, _index).toRefVar();
  }

  /**
   * Converts to a var.
   */
  /*
  public Var toVar()
  {
    System.out.println("TO_VAR:");
    // quercus/3d52
    return _obj.getField(_index).toVar();
  }
  */

  /**
   * Returns the value, converting to an object if necessary.
   */
  public Value getArgRef(Value index)
  {
    // php/3d1q
    return _obj.getFieldArray(_env, _index).getArgRef(index);
  }

  /**
   * Returns the value, converting to an object if necessary.
   */
  /*
  public Value getFieldObject(Env env, Value index)
  {
    return _obj.getFieldObject(_env, _index).getFieldObject(env, index);
  }
  */
}

