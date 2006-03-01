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

package com.caucho.quercus.expr;

import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.program.AnalyzeInfo;
import com.caucho.quercus.program.InterpretedClassDef;

import com.caucho.quercus.gen.PhpWriter;

import com.caucho.util.L10N;

/**
 * Represents the 'this' expression.
 */
public class ThisExpr extends AbstractVarExpr {
  private static final L10N L = new L10N(ThisExpr.class);

  private final InterpretedClassDef _quercusClass;
  
  public ThisExpr(InterpretedClassDef quercusClass)
  {
    _quercusClass = quercusClass;
  }

  /**
   * Creates a field ref
   */
  public Expr createFieldGet(String name)
  {
    return new ThisFieldExpr(_quercusClass, name);
  }
  
  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value eval(Env env)
    throws Throwable
  {
    return env.getThis();
  }
  
  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalArg(Env env)
    throws Throwable
  {
    return env.getThis();
  }
  
  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalRef(Env env)
    throws Throwable
  {
    return env.getThis();
  }
  
  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public void evalAssign(Env env, Value value)
    throws Throwable
  {
    env.error("can't assign $this");
  }
  
  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public void evalUnset(Env env)
    throws Throwable
  {
    env.error("can't unset $this");
  }

  //
  // Java code generation
  //
  
  /**
   * Analyze the expression
   */
  public void analyze(AnalyzeInfo info)
  {
  }

  /**
   * Generates code to recreate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    out.print("quercus_this");
  }

  /**
   * Generates code to evaluate the expression
   *
   * @param out the writer to the Java source code.
   */
  public void generateAssign(PhpWriter out, Expr value, boolean isTop)
    throws IOException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Generates code to evaluate the expression
   *
   * @param out the writer to the Java source code.
   */
  public void generateAssignRef(PhpWriter out, Expr value, boolean isTop)
    throws IOException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Generates code to evaluate the expression
   *
   * @param out the writer to the Java source code.
   */
  public void generateUnset(PhpWriter out)
    throws IOException
  {
    throw new UnsupportedOperationException();
  }
  
  public String toString()
  {
    return "$this";
  }
}

