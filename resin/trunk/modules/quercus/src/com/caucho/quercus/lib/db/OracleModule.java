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
 * @author Rodrigo Westrupp
 */

package com.caucho.quercus.lib.db;

import com.caucho.quercus.env.ArrayValue;
import com.caucho.quercus.env.ArrayValueImpl;
import com.caucho.quercus.env.BinaryBuilderValue;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.LongValue;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.ResourceValue;
import com.caucho.quercus.env.StringBuilderValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.NotNull;
import com.caucho.quercus.module.Optional;
import com.caucho.quercus.module.Reference;
import com.caucho.quercus.module.ReturnNullAsFalse;

import com.caucho.quercus.UnimplementedException;

import com.caucho.util.Log;

import java.lang.reflect.Method;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.SQLException;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Quercus oracle routines.
 *
 * NOTE from php.net:
 *
 * "...
 * These functions allow you to access Oracle 10, Oracle 9, Oracle 8 and Oracle 7
 * databases using the Oracle Call Interface (OCI). They support binding of PHP
 * variables to Oracle placeholders, have full LOB, FILE and ROWID support, and
 * allow you to use user-supplied define variables.
 *
 * Requirements
 *
 * You will need the Oracle client libraries to use this extension.
 * Windows users will need libraries with version at least 10 to use the php_oci8.dll.
 *
 * ..."
 *
 * PS: We use the Thin driver for the oci_xxx functions as opposed to the OCI driver
 * so you don't need the Oracle native client libraries.
 *
 */
public class OracleModule extends AbstractQuercusModule {
  private static final Logger log = Log.open(OracleModule.class);

  public static final int OCI_DEFAULT = 0x01;
  public static final int OCI_DESCRIBE_ONLY = 0x02;
  public static final int OCI_COMMIT_ON_SUCCESS = 0x03;
  public static final int OCI_EXACT_FETCH = 0x04;
  public static final int OCI_SYSDATE = 0x05;
  public static final int OCI_B_BFILE = 0x06;
  public static final int OCI_B_CFILEE = 0x07;
  public static final int OCI_B_CLOB = 0x08;
  public static final int OCI_B_BLOB = 0x09;
  public static final int OCI_B_ROWID = 0x0A;
  public static final int OCI_B_CURSOR = 0x0B;
  public static final int OCI_B_NTY = 0x0C;
  public static final int OCI_B_BIN = 0x0D;
  public static final int SQLT_BFILEE = 0x0E;
  public static final int SQLT_CFILEE = 0x0F;
  public static final int SQLT_CLOB = 0x10;
  public static final int SQLT_BLOB = 0x11;
  public static final int SQLT_RDD = 0x12;
  public static final int SQLT_NTY = 0x13;
  public static final int SQLT_LNG = 0x14;
  public static final int SQLT_LBI = 0x15;
  public static final int SQLT_BIN = 0x16;
  public static final int SQLT_NUM = 0x17;
  public static final int SQLT_INT = 0x18;
  public static final int SQLT_AFC = 0x19;
  public static final int SQLT_CHR = 0x1A;
  public static final int SQLT_VCS = 0x1B;
  public static final int SQLT_AVC = 0x1C;
  public static final int SQLT_STR = 0x1D;
  public static final int SQLT_LVC = 0x1E;
  public static final int SQLT_FLT = 0x1F;
  public static final int SQLT_ODT = 0x20;
  public static final int SQLT_BDOUBLE = 0x21;
  public static final int SQLT_BFLOAT = 0x22;
  public static final int OCI_FETCHSTATEMENT_BY_COLUMN = 0x23;
  public static final int OCI_FETCHSTATEMENT_BY_ROW = 0x24;
  public static final int OCI_ASSOC = 0x25;
  public static final int OCI_NUM = 0x26;
  public static final int OCI_BOTH = 0x27;
  public static final int OCI_RETURN_NULLS = 0x28;
  public static final int OCI_RETURN_LOBS = 0x29;
  public static final int OCI_DTYPE_FILE = 0x2A;
  public static final int OCI_DTYPE_LOB = 0x2B;
  public static final int OCI_DTYPE_ROWID = 0x2C;
  public static final int OCI_D_FILE = 0x2D;
  public static final int OCI_D_LOB = 0x2E;
  public static final int OCI_D_ROWID = 0x2F;
  public static final int OCI_SYSOPER = 0x30;
  public static final int OCI_SYSDBA = 0x31;
  public static final int OCI_LOB_BUFFER_FREE = 0x32;
  public static final int OCI_TEMP_CLOB = 0x33;
  public static final int OCI_TEMP_BLOB = 0x34;

  public OracleModule()
  {
  }

  /**
   * Returns true for the oracle extension.
   */
  public String []getLoadedExtensions()
  {
    return new String[] { "oracle" };
  }

  /**
   * Binds PHP array to Oracle PL/SQL array by name.
   *
   * oci_bind_array_by_name() binds the PHP array
   * varArray to the Oracle placeholder name, which
   * points to Oracle PL/SQL array. Whether it will
   * be used for input or output will be determined
   * at run-time. The maxTableLength parameter sets
   * the maximum length both for incoming and result
   * arrays. Parameter maxItemLength sets maximum
   * length for array items. If maxItemLength was
   * not specified or equals to -1,
   * oci_bind_array_by_name() will find the longest
   * element in the incoming array and will use it as
   * maximum length for array items. type parameter
   * should be used to set the type of PL/SQL array
   * items. See list of available types below.
   *
   * @param env the PHP executing environment
   * @param stmt the Oracle statement
   * @param name the Oracle placeholder
   * @param varArray the array to be binded
   * @param maxTableLength maximum table length
   * @param maxItemLength maximum item length
   * @param type one of the following types:
   * <br/>
   * SQLT_NUM - for arrays of NUMBER.
   * <br/>
   * SQLT_INT - for arrays of INTEGER
   * (Note: INTEGER it is actually a synonym for
   *  NUMBER(38), but SQLT_NUM type won't work in
   *  this case even though they are synonyms).
   * <br/>
   * SQLT_FLT - for arrays of FLOAT.
   * <br/>
   * SQLT_AFC - for arrays of CHAR.
   * <br/>
   * SQLT_CHR - for arrays of VARCHAR2.
   * <br/>
   * SQLT_VCS - for arrays of VARCHAR.
   * <br/>
   * SQLT_AVC - for arrays of CHARZ.
   * <br/>
   * SQLT_STR - for arrays of STRING.
   * <br/>
   * SQLT_LVC - for arrays of LONG VARCHAR.
   * <br/>
   * SQLT_ODT - for arrays of DATE.
   *
   * @return true on success of false on failure
   */
  public static boolean oci_bind_array_by_name(Env env,
                                               @NotNull OracleStatement stmt,
                                               @NotNull String name,
                                               @NotNull String varArray,
                                               @NotNull int maxTableLength,
                                               @Optional("0") int maxItemLength,
                                               @Optional("0") int type)
  {
    throw new UnimplementedException("oci_bind_array_by_name");
  }

  /**
   * Binds the PHP variable to the Oracle placeholder
   */
  public static Value oci_bind_by_name(Env env,
                                       @NotNull OracleStatement stmt,
                                       @NotNull String variable,
                                       @NotNull Value value,
                                       @Optional("0") int maxLength,
                                       @Optional("0") int type)
  {
    try {
      Integer index = (Integer)stmt.removeBindingVariable(variable);
      stmt.getPreparedStatement().setString(index.intValue(), value.toString());
      return BooleanValue.TRUE;
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);

      try {
        stmt.resetBindingVariables();
      } catch (Exception ex2) {
        log.log(Level.FINE, ex2.toString(), ex2);
      }
    }

    return BooleanValue.FALSE;
  }

  /**
   * Cancels reading from cursor
   */
  public static boolean oci_cancel(Env env,
                                   @NotNull OracleStatement stmt)
  {
    return oci_free_statement(env, stmt);
  }

  /**
   * Closes Oracle connection
   */
  public static Value oci_close(Env env,
                                @NotNull Oracle conn)
  {
    if (conn == null)
      conn = getConnection(env);

    if (conn != null) {
      if (conn == getConnection(env))
        env.removeSpecialValue("caucho.oracle");

      conn.close(env);

      return BooleanValue.TRUE;
    }
    else
      return BooleanValue.FALSE;
  }

  /**
   * Commits outstanding statements
   */
  public static Value oci_commit(Env env,
                                 @NotNull Oracle conn)
  {
    try {
      return BooleanValue.create(conn.commit());
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Establishes a connection to the Oracle server
   */
  public static Value oci_connect(Env env,
                                  @NotNull String username,
                                  @NotNull String password,
                                  @Optional String db,
                                  @Optional String charset,
                                  @Optional("0") int sessionMode)
  {
    // Note:  The second and subsequent calls to oci_connect() with the same parameters
    // will return the connection handle returned from the first call. This means that
    // queries issued against one handle are also applied to the other handles, because
    // they are the same handle. (source: php.net)

    if (!((charset == null) || charset.length() == 0)) {
      throw new UnimplementedException("oci_connect with charset");
    }

    if ((sessionMode == OCI_DEFAULT) ||
        (sessionMode == OCI_SYSOPER) ||
        (sessionMode == OCI_SYSDBA)) {
      throw new UnimplementedException("oci_connect with session mode");
    }

    return connectInternal(env, true, username, password, db, charset, sessionMode);
  }

  /**
   * Uses a PHP variable for the define-step during a SELECT
   */
  public static Value oci_define_by_name(Env env,
                                         @NotNull OracleStatement stmt,
                                         @NotNull String columnName,
                                         @NotNull @Reference Value variable,
                                         @Optional("0") int type)
  {
    try {
      stmt.putByNameVariable(columnName, variable);
      return BooleanValue.TRUE;
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the last error found
   */
  public static String oci_error(Env env,
                                 @Optional Value resource)
  {
    JdbcConnectionResource conn = null;

    if (resource == null) {
      conn = getConnection(env).validateConnection();
    } else {
      Object object = resource.toJavaObject();

      if (object instanceof Oracle) {
        conn = ((Oracle) object).validateConnection();
      } else {
        conn = ((OracleStatement) object).validateConnection();
      }
    }

    return conn.getErrorMessage();
  }

  /**
   * Executes a statement
   */
  public static Value oci_execute(Env env,
                                  @NotNull OracleStatement stmt,
                                  @Optional("0") int mode)
  {
    try {
      if (mode == OCI_COMMIT_ON_SUCCESS) {
        throw new UnimplementedException("oci_execute with mode OCI_COMMIT_ON_SUCCESS");
      }

      stmt.execute(env);

      return BooleanValue.TRUE;
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      try {
        stmt.resetBindingVariables();
      } catch (Exception ex2) {
        log.log(Level.FINE, ex2.toString(), ex2);
      }

      return BooleanValue.FALSE;
    }
  }

  /**
   * Fetches all rows of result data into an array
   */
  public static Value oci_fetch_all(Env env,
                                    @NotNull OracleStatement stmt,
                                    @NotNull Value output,
                                    @Optional int skip,
                                    @Optional int maxrows,
                                    @Optional int flags)
  {
    JdbcResultResource resource = null;

    ArrayValueImpl newArray = new ArrayValueImpl();

    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      Value value = resource.fetchArray(JdbcResultResource.FETCH_ASSOC);

      int curr = 0;

      while(value != NullValue.NULL) {
        newArray.put(LongValue.create(curr), value);

        curr++;

        value = resource.fetchArray(JdbcResultResource.FETCH_ASSOC);
      }
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }

    return newArray;
  }

  /**
   * Returns the next row from the result data as an associative or numeric array, or both
   */
  public static Value oci_fetch_array(Env env,
                                      @NotNull OracleStatement stmt,
                                      @Optional("OCI_BOTH") int mode)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);
      return resource.fetchArray(mode);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the next row from the result data as an associative array
   */
  public static Value oci_fetch_assoc(Env env,
                                      @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);
      return resource.fetchArray(JdbcResultResource.FETCH_ASSOC);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the next row from the result data as an object
   */
  public static Value oci_fetch_object(Env env,
                                       @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);
      return resource.fetchObject(env);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the next row from the result data as a numeric array
   */
  public static Value oci_fetch_row(Env env,
                                    @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);
      return resource.fetchArray(JdbcResultResource.FETCH_NUM);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Fetches the next row into result-buffer
   */
  public static Value oci_fetch(Env env,
                                @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      Value result = resource.fetchArray(OCI_BOTH);

      stmt.setResultBuffer(result);

      if (!(result instanceof ArrayValue)) {
        return BooleanValue.FALSE;
      }

      ArrayValue arrayValue = (ArrayValue) result;

      for (Map.Entry<String,Value> entry : stmt.getByNameVariables().entrySet()) {
        String fieldName = entry.getKey();
        Value var = entry.getValue();

        Value newValue = arrayValue.get(StringValue.create(fieldName));
        var.set(newValue);
      }

      return BooleanValue.TRUE;
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Checks if the field is NULL
   */
  public static Value oci_field_is_null(Env env,
                                        @NotNull OracleStatement stmt,
                                        @NotNull Value field)
  {
    if (stmt == null)
      return BooleanValue.FALSE;

    try {
      ResultSet rs = stmt.getResultSet();
      ResultSetMetaData metaData = rs.getMetaData();

      int columnNumber = 0;

      try {
        columnNumber = field.toInt();
      } catch(Exception ex2) {
        log.log(Level.FINE, ex2.toString(), ex2);
      }

      if (columnNumber <= 0) {
        String fieldName = field.toString();

        int n = metaData.getColumnCount();

        for (int i=1; i<=n; i++) {
          if (metaData.getColumnName(i).equals(fieldName)) {
            columnNumber = i;
          }
        }
      }

      boolean isNull = metaData.isNullable(columnNumber) == ResultSetMetaData.columnNullable;
      return BooleanValue.create( isNull );

    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);

      return BooleanValue.FALSE;

    }
  }

  /**
   * Returns the name of a field from the statement
   */
  public static Value oci_field_name(Env env,
                                     @NotNull OracleStatement stmt,
                                     @NotNull int fieldNumber)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      return resource.getFieldName(env, fieldNumber);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Tell the precision of a field
   */
  public static Value oci_field_precision(Env env,
                                          @NotNull OracleStatement stmt,
                                          @NotNull int field)
  {
    if (stmt == null)
      return BooleanValue.FALSE;

    try {
      ResultSet rs = stmt.getResultSet();
      ResultSetMetaData metaData = rs.getMetaData();

      int precision = metaData.getPrecision(field);
      return LongValue.create(precision);

    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);

      return BooleanValue.FALSE;

    }
  }

  /**
   * Tell the scale of the field
   */
  public static Value oci_field_scale(Env env,
                                      @NotNull OracleStatement stmt,
                                      @NotNull int field)
  {
    if (stmt == null)
      return BooleanValue.FALSE;

    try {
      ResultSet rs = stmt.getResultSet();
      ResultSetMetaData metaData = rs.getMetaData();

      int precision = metaData.getScale(field);
      return LongValue.create(precision);

    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);

      return BooleanValue.FALSE;

    }
  }

  /**
   * Returns field's size
   */
  public static Value oci_field_size(Env env,
                                     @NotNull OracleStatement stmt,
                                     @Optional Value field)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      ResultSet rs = stmt.getResultSet();
      ResultSetMetaData metaData = rs.getMetaData();

      JdbcResultResource resource = new JdbcResultResource(null, rs, null);

      int columnNumber = 0;

      try {
        columnNumber = field.toInt();
      } catch(Exception ex2) {
      }

      if (columnNumber <= 0) {
        String fieldName = field.toString();

        int n = metaData.getColumnCount();

        for (int i=1; i<=n; i++) {
          if (metaData.getColumnName(i).equals(fieldName)) {
            columnNumber = i;
          }
        }
      }

      return resource.getFieldLength(env, columnNumber);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Tell the raw Oracle data type of the field
   */
  public static Value oci_field_type_raw(Env env,
                                         @NotNull OracleStatement stmt,
                                         @Optional int field)
  {
    throw new UnimplementedException("oci_field_type_raw");
  }

  /**
   * Returns field's data type
   */
  public static Value oci_field_type(Env env,
                                     @NotNull OracleStatement stmt,
                                     @Optional int fieldNumber)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      return resource.getFieldType(env, fieldNumber);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   *  Frees all resources associated with statement or cursor
   */
  public static boolean oci_free_statement(Env env,
                                           @NotNull OracleStatement stmt)
  {
    try {

      stmt.close();

      return true;

    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return false;
    }
  }

  /**
   * Enables or disables internal debug output
   */
  public static void oci_internal_debug(Env env,
                                        @NotNull int onoff)
  {
    throw new UnimplementedException("oci_internal_debug");
  }

  /**
   * Copies large object
   */
  public static Value oci_lob_copy(Env env,
                                   @NotNull Value lobTo,
                                   @NotNull Value lobFrom,
                                   @Optional("-1") int length)
  {
    throw new UnimplementedException("oci_lob_copy");
  }

  /**
   * Compares two LOB/FILE locators for equality
   */
  public static Value oci_lob_is_equal(Env env,
                                       @NotNull Value lob1,
                                       @NotNull Value lob2)
  {
    throw new UnimplementedException("oci_lob_is_equal");
  }

  /**
   * Allocates new collection object
   */
  public static Value oci_new_collection(Env env,
                                         @NotNull Oracle conn,
                                         @NotNull String tdo,
                                         @Optional String schema)
  {
    throw new UnimplementedException("oci_new_collection");
  }

  /**
   * Establishes a new connection to the Oracle server
   */
  public static Value oci_new_connect(Env env,
                                      @NotNull String username,
                                      @NotNull String password,
                                      @Optional String db,
                                      @Optional String charset,
                                      @Optional("0") int sessionMode)
  {
    if (!((charset == null) || charset.length() == 0)) {
      throw new UnimplementedException("oci_new_connect with charset");
    }

    if ((sessionMode == OCI_DEFAULT) ||
        (sessionMode == OCI_SYSOPER) ||
        (sessionMode == OCI_SYSDBA)) {
      throw new UnimplementedException("oci_new_connect with session mode");
    }

    return connectInternal(env, false, username, password, db, charset, sessionMode);
  }

  /**
   * Allocates and returns a new cursor (statement handle)
   */
  public static Value oci_new_cursor(Env env,
                                     @NotNull Oracle conn)
  {
    throw new UnimplementedException("oci_new_cursor");
  }

  /**
   * Initializes a new empty LOB or FILE descriptor
   */
  public static Value oci_new_descriptor(Env env,
                                         @NotNull Oracle conn,
                                         @Optional("-1") int type)
  {
    throw new UnimplementedException("oci_new_descriptor");
  }

  /**
   *  Returns the number of result columns in a statement
   */
  public static Value oci_num_fields(Env env,
                                     @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      return LongValue.create(resource.getFieldCount());
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns number of rows affected during statement execution
   */
  @ReturnNullAsFalse
  public static Integer oci_num_rows(Env env,
                                     @NotNull OracleStatement stmt)
  {
    try {
      if (stmt == null)
        return null;

      JdbcResultResource resource = new JdbcResultResource(null, stmt.getResultSet(), null);

      return new Integer(resource.getNumRows());
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return null;
    }
  }

  /**
   * Prepares Oracle statement for execution
   */
  public static Value oci_parse(Env env,
                                @NotNull Oracle conn,
                                String query)
  {
    try {
      // Make the PHP query a JDBC like query replacing (:mydata -> ?) with question marks.
      // Store binding names for future reference (see oci_execute)
      String regex = ":[a-zA-Z0-9]+";
      String jdbcQuery = query.replaceAll(regex, "?");
      OracleStatement pstmt = conn.prepare(env, jdbcQuery);

      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(query);
      int i = 0;
      while (matcher.find()) {
        String group = matcher.group();
        pstmt.putBindingVariable(group, new Integer(++i));
      }

      return env.wrapJava(pstmt);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Changes password of Oracle's user
   */
  public static Value oci_password_change(Env env,
                                          @NotNull Oracle conn,
                                          @NotNull String username,
                                          @NotNull String oldPassword,
                                          @NotNull String newPassword)
  {
    throw new UnimplementedException("oci_password_change");
  }

  /**
   * Connect to an Oracle database using a persistent connection
   */
  public static Value oci_pconnect(Env env,
                                   @NotNull String username,
                                   @NotNull String password,
                                   @Optional String db,
                                   @Optional String charset,
                                   @Optional("0") int sessionMode)
  {
    if (!((charset == null) || charset.length() == 0)) {
      throw new UnimplementedException("oci_pconnect with charset");
    }

    if ((sessionMode == OCI_DEFAULT) ||
        (sessionMode == OCI_SYSOPER) ||
        (sessionMode == OCI_SYSDBA)) {
      throw new UnimplementedException("oci_pconnect with session mode");
    }

    return connectInternal(env, true, username, password, db, charset, sessionMode);
  }

  /**
   * Returns field's value from the fetched row
   */
  public static Value oci_result(Env env,
                                 @NotNull OracleStatement stmt,
                                 @NotNull Value field)
  {
    try {
      if (stmt == null)
        return BooleanValue.FALSE;

      Value result = stmt.getResultBuffer();

      return ((ArrayValueImpl)result).get(field);
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Rolls back outstanding transaction
   */
  public static Value oci_rollback(Env env,
                                   @NotNull Oracle conn)
  {
    try {
      return BooleanValue.create(conn.rollback());
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns server version
   */
  @ReturnNullAsFalse
  public static String oci_server_version(Env env,
                                          @NotNull Oracle conn)
  {
    try {
      if (conn == null)
        conn = getConnection(env);

      return conn.getServerInfo();
    } catch (Exception ex) {
      log.log(Level.FINE, ex.toString(), ex);
      return null;
    }
  }

  /**
   * Sets number of rows to be prefetched
   */
  public static Value oci_set_prefetch(Env env,
                                       @NotNull OracleStatement stmt,
                                       @Optional("1") int rows)
  {
    throw new UnimplementedException("oci_set_prefetch");
  }

  /**
   * Returns the type of an OCI statement
   */
  public static String oci_statement_type(Env env,
                                          @NotNull OracleStatement stmt)
  {
    return stmt.getStatementType();
  }

  /**
   * Alias of oci_bind_by_name()
   */
  public static Value ocibindbyname(Env env,
                                    @NotNull OracleStatement stmt,
                                    @NotNull String variable,
                                    @NotNull Value value,
                                    @Optional("0") int maxLength,
                                    @Optional("0") int type)
  {
    return oci_bind_by_name(env, stmt, variable, value, maxLength, type);
  }

  /**
   * Alias of oci_cancel()
   */
  public static boolean ocicancel(Env env,
                                  @NotNull OracleStatement stmt)
  {
    return oci_cancel(env, stmt);
  }

  /**
   * Alias of OCI-Lob->close
   */
  public static Value ocicloselob(Env env,
                                  @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicloselob");
  }

  /**
   * Alias of OCI-Collection->append
   */
  public static Value ocicollappend(Env env,
                                    @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollappend");
  }

  /**
   * Alias of OCI-Collection->assign
   */
  public static Value ocicollassign(Env env,
                                    @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollassign");
  }

  /**
   * Alias of OCI-Collection->assignElem
   */
  public static Value ocicollassignelem(Env env,
                                        @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollassignelem");
  }

  /**
   * Alias of OCI-Collection->getElem
   */
  public static Value ocicollgetelem(Env env,
                                     @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollgetelem");
  }

  /**
   * Alias of OCI-Collection->max
   */
  public static Value ocicollmax(Env env,
                                 @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollmax");
  }

  /**
   * Alias of OCI-Collection->size
   */
  public static Value ocicollsize(Env env,
                                  @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicollsize");
  }

  /**
   * Alias of OCI-Collection->trim
   */
  public static Value ocicolltrim(Env env,
                                  @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocicolltrim");
  }

  /**
   * Alias of oci_field_is_null()
   */
  public static Value ocicolumnisnull(Env env,
                                      @NotNull OracleStatement stmt,
                                      @NotNull Value field)
  {
    return oci_field_is_null(env, stmt, field);
  }

  /**
   * Alias of oci_field_name()
   */
  public static Value ocicolumnname(Env env,
                                    @NotNull OracleStatement stmt,
                                    @NotNull int fieldNumber)
  {
    return oci_field_name(env, stmt, fieldNumber);
  }

  /**
   * Alias of oci_field_precision()
   */
  public static Value ocicolumnprecision(Env env,
                                         @NotNull OracleStatement stmt,
                                         @NotNull int field)
  {
    return oci_field_precision(env, stmt, field);
  }

  /**
   * Alias of oci_field_scale()
   */
  public static Value ocicolumnscale(Env env,
                                     @NotNull OracleStatement stmt,
                                     @NotNull int field)
  {
    return oci_field_scale(env, stmt, field);
  }

  /**
   * Alias of oci_field_size()
   */
  public static Value ocicolumnsize(Env env,
                                    @NotNull OracleStatement stmt,
                                    @Optional Value field)
  {
    return oci_field_size(env, stmt, field);
  }

  /**
   * Alias of oci_field_type()
   */
  public static Value ocicolumntype(Env env,
                                    @NotNull OracleStatement stmt,
                                    @Optional int fieldNumber)
  {
    return oci_field_type(env, stmt, fieldNumber);
  }

  /**
   * Alias of oci_field_type_raw()
   */
  public static Value ocicolumntyperaw(Env env,
                                       @NotNull OracleStatement stmt,
                                       @Optional int field)
  {
    return oci_field_type_raw(env, stmt, field);
  }

  /**
   * Alias of oci_commit()
   */
  public static Value ocicommit(Env env,
                                @NotNull Oracle conn)
  {
    return oci_commit(env, conn);
  }

  /**
   * Alias of oci_define_by_name()
   */
  public static Value ocidefinebyname(Env env,
                                      @NotNull OracleStatement stmt,
                                      @NotNull String columnName,
                                      @NotNull Value variable,
                                      @Optional("0") int type)
  {
    return oci_define_by_name(env, stmt, columnName, variable, type);
  }

  /**
   * Alias of oci_error()
   */
  public static String ocierror(Env env,
                                @Optional Value resource)
  {
    return oci_error(env, resource);
  }

  /**
   * Alias of oci_execute()
   */
  public static Value ociexecute(Env env,
                                 @NotNull OracleStatement stmt,
                                 @Optional("0") int mode)
  {
    return oci_execute(env, stmt, mode);
  }

  /**
   * Alias of oci_fetch()
   */
  public static Value ocifetch(Env env,
                               @NotNull OracleStatement stmt)
  {
    return oci_fetch(env, stmt);
  }

  /**
   * Fetches the next row into an array
   */
  public static Value ocifetchinto(Env env,
                                   @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocifetchinto");
  }

  /**
   * Alias of oci_fetch_all()
   */
  public static Value ocifetchstatement(Env env,
                                        @NotNull OracleStatement stmt,
                                        @NotNull Value output,
                                        @Optional int skip,
                                        @Optional int maxrows,
                                        @Optional int flags)
  {
    return oci_fetch_all(env, stmt, output, skip, maxrows, flags);
  }

  /**
   * Alias of OCI-Collection->free
   */
  public static Value ocifreecollection(Env env,
                                        @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocifreecollection");
  }

  /**
   * Alias of oci_free_statement()
   */
  public static boolean ocifreecursor(Env env,
                                      @NotNull OracleStatement stmt)
  {
    return oci_free_statement(env, stmt);
  }

  /**
   * Alias of OCI-Lob->free
   */
  public static Value ocifreedesc(Env env,
                                  @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocifreedesc");
  }

  /**
   * Alias of oci_free_statement()
   */
  public static boolean ocifreestatement(Env env,
                                         @NotNull OracleStatement stmt)
  {
    return oci_free_statement(env, stmt);
  }

  /**
   * Alias of oci_internal_debug()
   */
  public static void ociinternaldebug(Env env,
                                      @NotNull int onoff)
  {
    oci_internal_debug(env, onoff);
  }

  /**
   * Alias of OCI-Lob->load
   */
  public static Value ociloadlob(Env env,
                                 @NotNull Oracle conn)
  {
    throw new UnimplementedException("ociloadlob");
  }

  /**
   * Alias of oci_close()
   */
  public static Value ocilogoff(Env env,
                                @NotNull Oracle conn)
  {
    return oci_close(env, conn);
  }

  /**
   * Alias of oci_connect()
   */
  public static Value ocilogon(Env env,
                               @NotNull String username,
                               @NotNull String password,
                               @Optional String db,
                               @Optional String charset,
                               @Optional("0") int sessionMode)
  {
    return oci_connect(env, username, password, db, charset, sessionMode);
  }

  /**
   * Alias of oci_new_collection()
   */
  public static Value ocinewcollection(Env env,
                                       @NotNull Oracle conn,
                                       @NotNull String tdo,
                                       @Optional String schema)
  {
    return oci_new_collection(env, conn, tdo, schema);
  }

  /**
   * Alias of oci_new_cursor()
   */
  public static Value ocinewcursor(Env env,
                                   @NotNull Oracle conn)
  {
    return oci_new_cursor(env, conn);
  }

  /**
   * Alias of oci_new_descriptor()
   */
  public static Value ocinewdescriptor(Env env,
                                       @NotNull Oracle conn,
                                       @Optional("-1") int type)
  {
    return oci_new_descriptor(env, conn, type);
  }

  /**
   * Alias of oci_new_connect()
   */
  public static Value ocinlogon(Env env,
                                @NotNull String username,
                                @NotNull String password,
                                @Optional String db,
                                @Optional String charset,
                                @Optional("0") int sessionMode)
  {
    return oci_new_connect(env, username, password, db, charset, sessionMode);
  }

  /**
   * Alias of oci_num_fields()
   */
  public static Value ocinumcols(Env env,
                                 @NotNull OracleStatement stmt)
  {
    return oci_num_fields(env, stmt);
  }

  /**
   * Alias of oci_parse()
   */
  public static Value ociparse(Env env,
                               @NotNull Oracle conn,
                               @NotNull String query)
  {
    return oci_parse(env, conn, query);
  }

  /**
   * Alias of oci_pconnect()
   */
  public static Value ociplogon(Env env,
                                @NotNull String username,
                                @NotNull String password,
                                @Optional String db,
                                @Optional String charset,
                                @Optional("0") int sessionMode)
  {
    return oci_pconnect(env, username, password, db, charset, sessionMode);
  }

  /**
   * Alias of oci_result()
   */
  public static Value ociresult(Env env,
                                @NotNull OracleStatement stmt,
                                @NotNull Value field)
  {
    return oci_result(env, stmt, field);
  }

  /**
   * Alias of oci_rollback()
   */
  public static Value ocirollback(Env env,
                                  @NotNull Oracle conn)
  {
    return oci_rollback(env, conn);
  }

  /**
   * Alias of oci_num_rows()
   */
  @ReturnNullAsFalse
  public static Integer ocirowcount(Env env,
                                    @NotNull OracleStatement stmt)
  {
    return oci_num_rows(env, stmt);
  }

  /**
   * Alias of OCI-Lob->save
   */
  public static Value ocisavelob(Env env,
                                 @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocisavelob");
  }

  /**
   * Alias of OCI-Lob->import
   */
  public static Value ocisavelobfile(Env env,
                                     @NotNull Oracle conn)
  {
    throw new UnimplementedException("ocisavelobfile");
  }

  /**
   * Alias of oci_server_version()
   */
  public static String ociserverversion(Env env,
                                        @NotNull Oracle conn)
  {
    return oci_server_version(env, conn);
  }

  /**
   * Alias of oci_set_prefetch()
   */
  public static Value ocisetprefetch(Env env,
                                     @NotNull OracleStatement stmt,
                                     @Optional("1") int rows)
  {
    return oci_set_prefetch(env, stmt, rows);
  }

  /**
   * Alias of oci_statement_type()
   */
  public static String ocistatementtype(Env env,
                                        @NotNull OracleStatement stmt)
  {
    return oci_statement_type(env, stmt);
  }

  /**
   * Alias of OCI-Lob->export
   */
  public static Value ociwritelobtofile(Env env,
                                        @NotNull Oracle conn)
  {
    throw new UnimplementedException("ociwritelobtofile");
  }

  /**
   * Alias of OCI-Lob->writeTemporary
   */
  public static Value ociwritetemporarylob(Env env,
                                           @NotNull Oracle conn)
  {
    throw new UnimplementedException("ociwritetemporarylob");
  }

  private static Oracle getConnection(Env env)
  {
    Oracle conn = null;

    ConnectionInfo connectionInfo = (ConnectionInfo) env.getSpecialValue("caucho.oracle");

    if (connectionInfo != null) {
      // Reuse the cached connection
      conn = connectionInfo.getConnection();
      return conn;
    }

    String driver = "oracle.jdbc.driver.OracleDriver";
    String url = "jdbc:oracle:thin:@localhost:1521";

    conn = new Oracle(env, "localhost", "", "", "", 1521, driver, url);

    env.setSpecialValue("caucho.oracle", conn);

    return conn;
  }

  private static Value connectInternal(Env env,
                                       boolean reuseConnection,
                                       String username,
                                       String password,
                                       String db,
                                       String charset,
                                       int sessionMode)
  {
    String host = "localhost";
    int port = 1521;

    String driver = "oracle.jdbc.driver.OracleDriver";

    String url;

    if (db.indexOf("//") == 0) {
      // db is the url itself: "//db_host[:port]/database_name"
      url = "jdbc:oracle:thin:@" + db.substring(2);
      url = url.replace('/', ':');
    } else {
      url = "jdbc:oracle:thin:@" + host + ":" + port + ":" + db;
    }

    Oracle conn = null;

    ConnectionInfo connectionInfo = (ConnectionInfo)env.getSpecialValue("caucho.oracle");

    if (reuseConnection && (connectionInfo != null) && url.equals(connectionInfo.getUrl())) {
      // Reuse the cached connection
      conn = connectionInfo.getConnection();
    } else {
      conn = new Oracle(env, host, username, password, db, port, driver, url);

      connectionInfo = new ConnectionInfo(url, conn);

      env.setSpecialValue("caucho.oracle", connectionInfo);
    }

    Value value = env.wrapJava(conn);

    return value;
  }

  private static class ConnectionInfo {
    private String _url;
    private Oracle _conn;

    public ConnectionInfo(String url, Oracle conn)
    {
      _url = url;
      _conn = conn;
    }

    public String getUrl()
    {
      return _url;
    }

    public Oracle getConnection()
    {
      return _conn;
    }
  }
}
