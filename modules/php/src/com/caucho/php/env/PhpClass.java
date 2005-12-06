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

package com.caucho.php.env;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

import java.io.IOException;

import com.caucho.util.L10N;

import com.caucho.php.PhpRuntimeException;

import com.caucho.php.program.AbstractFunction;
import com.caucho.php.program.AbstractClassDef;

import com.caucho.php.expr.Expr;

import com.caucho.php.gen.PhpWriter;

/**
 * Represents a PHP class.
 */
public class PhpClass {
  private final L10N L = new L10N(PhpClass.class);

  private final AbstractClassDef _classDef;
  
  private AbstractClassDef []_classDefList;

  private PhpClass _parent;

  public PhpClass(AbstractClassDef classDef, PhpClass parent)
  {
    _classDef = classDef;
    _parent = parent;

    AbstractClassDef []classDefList;
    
    if (_parent != null) {
      classDefList = new AbstractClassDef[parent._classDefList.length + 1];

      System.arraycopy(parent._classDefList, 0, classDefList, 1,
		       parent._classDefList.length);

      classDefList[0] = classDef;
    }
    else {
      classDefList = new AbstractClassDef[] { classDef };
    }
    
    _classDefList = classDefList;
  }

  /**
   * Returns the name.
   */
  public String getName()
  {
    return _classDef.getName();
  }

  /**
   * Returns the parent class.
   */
  public PhpClass getParent()
  {
    return _parent;
  }

  /**
   * Creates a new instance.
   */
  public Value evalNew(Env env, Expr []expr)
    throws Throwable
  {
    ObjectValue object = newInstance(env);

    AbstractFunction fun = findConstructor();

    if (fun != null) {
      fun.evalMethod(env, object, expr);
    }
    else {
      //  if expr
    }

    return object;
  }

  /**
   * Creates a new instance.
   */
  public Value evalNew(Env env, Value []args)
    throws Throwable
  {
    ObjectValue object = newInstance(env);

    AbstractFunction fun = findConstructor();

    if (fun != null)
      fun.evalMethod(env, object, args);
    else {
      //  if expr
    }

    return object;
  }

  /**
   * Creates a new instance.
   */
  public ObjectValue newInstance(Env env)
    throws Throwable
  {
    ObjectValue object = new ObjectValue(this);

    for (int i = _classDefList.length - 1; i >= 0; i--)
      _classDefList[i].initInstance(env, object);

    return object;
  }

  /**
   * Finds the matching constructor.
   */
  public AbstractFunction findConstructor()
  {
    // XXX: cache method
    for (int i = 0; i < _classDefList.length; i++) {
      AbstractFunction fun = _classDefList[i].findConstructor();

      if (fun != null)
	return fun;
    }
    
    return null;
  }

  /**
   * Finds the matching function.
   */
  public AbstractFunction findFunction(String name)
  {
    // XXX: cache method
    for (int i = 0; i < _classDefList.length; i++) {
      AbstractFunction fun = _classDefList[i].findFunction(name);

      if (fun != null)
	return fun;
      
    }
    
    return null;
  }

  /**
   * Finds the matching function.
   */
  public AbstractFunction findFunctionLowerCase(String name)
  {
    // XXX: cache method
    for (int i = 0; i < _classDefList.length; i++) {
      AbstractFunction fun = _classDefList[i].findFunctionLowerCase(name);

      if (fun != null)
	return fun;
    }
    
    return null;
  }

  /**
   * Finds the matching function.
   */
  public final AbstractFunction getFunction(String name)
  {
    AbstractFunction fun = findFunction(name);

    if (fun != null)
      return fun;

    fun = findFunctionLowerCase(name.toLowerCase());
    
    if (fun != null)
      return fun;
    else {
      throw new PhpRuntimeException(L.l("{0}::{1} is an unknown method",
					getName(), name));
    }
  }

  public String toString()
  {
    return "PhpClass[" + getName() + "]";
  }
}

