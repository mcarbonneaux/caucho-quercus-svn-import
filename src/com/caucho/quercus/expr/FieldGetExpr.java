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

package com.caucho.quercus.expr;

import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.program.AnalyzeInfo;

import com.caucho.quercus.gen.PhpWriter;

import com.caucho.util.L10N;

/**
 * Represents a PHP field reference.
 */
public class FieldGetExpr extends AbstractVarExpr {
  private static final L10N L = new L10N(FieldGetExpr.class);

  private final Expr _objExpr;
  private final StringValue _name;

  public FieldGetExpr(Expr objExpr, String name)
  {
    _objExpr = objExpr;
    
    _name = new StringValue(name);
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
    Value value = _objExpr.evalArgObject(env);

    return value.getArg(_name);
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
    // quercus/0d1k
    Value value = _objExpr.evalObject(env);

    return value.getRef(_name);
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
    Value obj = _objExpr.eval(env);

    return obj.get(_name);
  }
  
  /**
   * Evaluates the expression as a copyable  value.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalCopy(Env env)
    throws Throwable
  {
    Value obj = _objExpr.eval(env);

    return obj.get(_name).copy();
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
    Value obj = _objExpr.evalObject(env);

    obj.put(_name, value);
  }

  /**
   * Evaluates the expression, creating an array if the field is unset.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalArray(Env env)
    throws Throwable
  {
    Value obj = _objExpr.evalObject(env);

    return obj.getArray(_name);
  }

  /**
   * Evaluates the expression, creating an object if the field is unset.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalObject(Env env)
    throws Throwable
  {
    Value obj = _objExpr.evalObject(env);

    return obj.getObject(env, _name);
  }

  /**
   * Evaluates the expression, creating an object if the field is unset.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalArgObject(Env env)
    throws Throwable
  {
    Value obj = _objExpr.evalObject(env);

    return obj.getArgObject(env, _name);
  }

  /**
   * Evaluates the expression, creating an array if the field is unset.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value evalArgArray(Env env)
    throws Throwable
  {
    throw new UnsupportedOperationException();
    /*
    Value obj = _objExpr.evalArgObject(env);

    return obj.getArgArray(env, _name);
    */
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
    Value obj = _objExpr.eval(env);

    obj.remove(_name);
  }

  //
  // Java code generation
  //

  /**
   * Analyze the statement
   */
  public void analyze(AnalyzeInfo info)
  {
    _objExpr.analyze(info);
  }

  /**
   * Analyze the statement as an assignement
   */
  public void analyzeAssign(AnalyzeInfo info)
  {
    _objExpr.analyze(info);

    // php/3a6e
    _objExpr.analyzeSetReference(info);
    _objExpr.analyzeSetModified(info);
  }

  /**
   * Analyze the statement as modified
   */
  public void analyzeSetModified(AnalyzeInfo info)
  {
    _objExpr.analyzeSetModified(info);
  }

  /**
   * Analyze the statement as a reference
   */
  public void analyzeSetReference(AnalyzeInfo info)
  {
    // php/3a6f
    _objExpr.analyzeSetReference(info);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    _objExpr.generate(out);
    out.print(".get(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateArg(PhpWriter out)
    throws IOException
  {
    _objExpr.generateArgObject(out);
    out.print(".getArg(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateRef(PhpWriter out)
    throws IOException
  {
    _objExpr.generateObject(out);
    out.print(".getRef(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression, where the result is copied.
   *
   * @param out the writer to the Java source code.
   */
  public void generateCopy(PhpWriter out)
    throws IOException
  {
    generate(out);
    out.print(".copy()"); // php/3a5n
  }

  /**
   * Generates code to assign the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateAssign(PhpWriter out, Expr value, boolean isTop)
    throws IOException
  {
    _objExpr.generateObject(out);
    out.print(".put(");
    out.print(out.addValue(_name));
    out.print(", ");
    value.generateCopy(out); // php/3a82
    out.print(")");
  }

  /**
   * Generates code to assign the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateAssignRef(PhpWriter out, Expr value, boolean isTop)
    throws IOException
  {
    _objExpr.generateObject(out);
    out.print(".put(");
    out.print(out.addValue(_name));
    out.print(", ");
    value.generateRef(out);
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateObject(PhpWriter out)
    throws IOException
  {
    _objExpr.generateObject(out);
    out.print(".getObject(env, ");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateArgObject(PhpWriter out)
    throws IOException
  {
    _objExpr.generateArgObject(out);
    out.print(".getArgObject(env, ");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateArray(PhpWriter out)
    throws IOException
  {
    _objExpr.generateObject(out);
    out.print(".getArray(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateArgArray(PhpWriter out)
    throws IOException
  {
    _objExpr.generateArgObject(out);
    out.print(".getArgArray(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to assign the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateUnset(PhpWriter out)
    throws IOException
  {
    _objExpr.generate(out);
    out.print(".remove(");
    out.print(out.addValue(_name));
    out.print(")");
  }

  /**
   * Generates code to recreate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateExpr(PhpWriter out)
    throws IOException
  {
    out.print("new FieldRefExpr(");

    _objExpr.generateExpr(out);
    
    out.print(", ");
    
    _name.generate(out);
    
    out.print(")");
  }
  
  public String toString()
  {
    return _objExpr + "->" + _name;
  }
}

