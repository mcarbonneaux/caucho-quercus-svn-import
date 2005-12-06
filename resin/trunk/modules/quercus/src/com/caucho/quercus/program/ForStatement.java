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

package com.caucho.quercus.program;

import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.BreakValue;
import com.caucho.quercus.env.ContinueValue;

import com.caucho.quercus.expr.Expr;

import com.caucho.quercus.gen.PhpWriter;

/**
 * Represents a for statement.
 */
public class ForStatement extends Statement {
  private final Expr _init;
  private final Expr _test;
  private final Expr _incr;
  private final Statement _block;

  public ForStatement(Expr init, Expr test, Expr incr, Statement block)
  {
    _init = init;
    _test = test;
    _incr = incr;
    
    _block = block;
  }
  
  public Value execute(Env env)
    throws Throwable
  {
    if (_init != null)
      _init.eval(env);
    
    while (_test == null || _test.evalBoolean(env)) {
      env.checkTimeout();
      
      Value value = _block.execute(env);

      if (value == null || value instanceof ContinueValue) {
      }
      else if (value instanceof BreakValue)
	return null;
      else
	return value;

      if (_incr != null)
	_incr.eval(env);
    }

    return null;
  }

  //
  // Java code generation
  //

  /**
   * Analyze the statement
   */
  public boolean analyze(AnalyzeInfo info)
  {
    if (_init != null)
      _init.analyze(info);

    if (_test != null)
      _test.analyze(info);

    AnalyzeInfo contInfo = info.copy();
    AnalyzeInfo breakInfo = info;

    AnalyzeInfo loopInfo = info.createLoop(contInfo, breakInfo);
    
    _block.analyze(loopInfo);

    if (_incr != null)
      _incr.analyze(loopInfo);

    if (_test != null)
      _test.analyze(loopInfo);

    loopInfo.merge(contInfo);

    // handle loop values
    
    _block.analyze(loopInfo);

    loopInfo.merge(contInfo);

    if (_incr != null)
      _incr.analyze(loopInfo);

    if (_test != null)
      _test.analyze(loopInfo);

    info.merge(loopInfo);

    return true;
  }

  /**
   * Generates the Java code for the statement.
   *
   * @param out the writer to the generated Java source.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    out.print("for (");
    if (_init != null)
      _init.generateTop(out);

    out.println(";");
    out.print("     ");
    
    if (_test != null)
      _test.generateBoolean(out);
    else
      out.print("true");

    out.println(";");
    out.print("     ");

    if (_incr != null)
      _incr.generateTop(out);

    out.println(") {");
    out.pushDepth();
    out.println("env.checkTimeout();");
    
    _block.generate(out);
    out.popDepth();
    out.println("}");
  }
  
  public String toString()
  {
    return "Statement[]";
  }
}

