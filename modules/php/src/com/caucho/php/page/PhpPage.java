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

package com.caucho.php.page;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.caucho.java.gen.GenClass;

import com.caucho.php.Php;

import com.caucho.php.env.Env;
import com.caucho.php.env.Value;
import com.caucho.php.env.PhpClass;

import com.caucho.php.program.AbstractFunction;
import com.caucho.php.program.AbstractClassDef;

import com.caucho.vfs.Path;
import com.caucho.vfs.WriteStream;

/**
 * Represents a compiled PHP program.
 */
abstract public class PhpPage {
  private HashMap<String,AbstractFunction> _funMap
    = new HashMap<String,AbstractFunction>();
  
  private HashMap<String,AbstractFunction> _funMapLowerCase
    = new HashMap<String,AbstractFunction>();
  
  private HashMap<String,AbstractClassDef> _classMap
    = new HashMap<String,AbstractClassDef>();

  /**
   * Returns true if the page is modified.
   */
  public boolean isModified()
  {
    return false;
  }

  /**
   * Returns the page's path.
   */
  abstract public Path getSelfPath(Env env);
  
  /**
   * Finds a function.
   */
  public AbstractFunction findFunction(String name)
  {
    AbstractFunction fun = _funMap.get(name);

    if (fun != null)
      return fun;

    fun = _funMapLowerCase.get(name.toLowerCase());

    return fun;
  }

  /**
   * Finds a function.
   */
  public AbstractClassDef findClass(String name)
  {
    return _classMap.get(name);
  }

  /**
   * Execute the program as top-level, i.e. not included.
   *
   * @param env the calling environment
   * @throws Throwable
   */
  public Value executeTop(Env env)
    throws Throwable
  {
    Path oldPwd = env.getPwd();

    Path pwd = getPwd(env);

    env.setPwd(pwd);
    try {
      return execute(env);
    } finally {
      env.setPwd(oldPwd);
    }
  }

  /**
   * Returns the pwd according to the source page.
   */
  public Path getPwd(Env env)
  {
    return getSelfPath(env).getParent();
  }


  /**
   * Execute the program
   *
   * @param env the calling environment
   * @throws Throwable
   */
  abstract public Value execute(Env env)
    throws Throwable;

  /**
   * Initialize the program
   *
   * @param php the owning engine
   */
  public void init(Php php)
    throws Throwable
  {
  }

  /**
   * Initialize the environment
   *
   * @param php the owning engine
   */
  public void init(Env env)
  {
  }

  /**
   * Imports the page definitions.
   */
  public void importDefinitions(Env env)
    throws Throwable
  {
    for (Map.Entry<String,AbstractFunction> entry : _funMap.entrySet()) {
      AbstractFunction fun = entry.getValue();

      if (fun.isGlobal())
	env.addFunction(entry.getKey(), entry.getValue());
    }
    
    for (Map.Entry<String,AbstractClassDef> entry : _classMap.entrySet()) {
      env.addClassDef(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Adds a function.
   */
  protected void addFunction(String name, AbstractFunction fun)
  {
    _funMap.put(name, fun);
    _funMapLowerCase.put(name.toLowerCase(), fun);
  }

  /**
   * Adds a class.
   */
  protected void addClass(String name, AbstractClassDef cl)
  {
    _classMap.put(name, cl);
  }
  
  public String toString()
  {
    return "PhpPage[]";
  }
}

