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

package com.caucho.quercus.lib;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.caucho.util.L10N;
import com.caucho.util.Log;

import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.Optional;
import com.caucho.quercus.module.NotNull;
import com.caucho.quercus.resources.JdbcConnectionResource;

import com.caucho.quercus.resources.JdbcResultResource;
import com.caucho.quercus.resources.JdbcTableMetaData;
import com.caucho.quercus.resources.JdbcColumnMetaData;

/**
 * PHP mysql routines.
 */
public class QuercusMysqlModule extends AbstractQuercusModule {

  private static final Logger log = Log.open(QuercusMysqlModule.class);
  private static final L10N L = new L10N(QuercusMysqlModule.class);

  public static final int MYSQL_ASSOC = 0x1;
  public static final int MYSQL_NUM = 0x2;
  public static final int MYSQL_BOTH = 0x3;

  public static final int MYSQL_USE_RESULT = 0x0;
  public static final int MYSQL_STORE_RESULT = 0x1;

  public QuercusMysqlModule()
  {
  }

  /**
   * Returns true for the mysql extension.
   */
  public boolean isExtensionLoaded(String name)
  {
    return "mysql".equals(name);
  }

  /**
   * Returns the number of affected rows.
   */
  public int mysql_affected_rows(Env env, @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.affected_rows();
  }

  /**
   * Returns the client encoding
   */
  public String mysql_client_encoding(Env env,
				      @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.client_encoding();
  }

  /**
   * Closes a mysql connection.
   */
  public boolean mysql_close(Env env,
                             @Optional Mysqli conn)
    throws Exception
  {
    if (conn == null)
      conn = getConnection(env);

    if (conn != null) {
      if (conn == getConnection(env))
	env.removeSpecialValue("caucho.mysql");

      conn.close(env);

      return true;
    }
    else
      return false;
  }

  /**
   * Creates a database.
   * XXX: seems to be unsupported in PHP 5.0.4
   */
  public boolean mysql_create_db(Env env,
                                 String name,
                                 @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    Statement stmt = null;

    if (conn == null)
      return false;

    try {
      stmt = conn.validateConnection().getConnection().createStatement();
      stmt.executeUpdate("CREATE DATABASE " + name);
    } finally {
      if (stmt != null)
        stmt.close();
    }

    return true;
  }

  /**
   * Moves the intenal row pointer of the MySQL result to the
   * specified row number.
   * 0 <= rowNumber <= mysql_num_rows() - 1
   * Returns TRUE on success or FALSE on failure
   */
  public boolean mysql_data_seek(Env env,
                                 @NotNull MysqliResult result,
                                 int rowNumber)
  {
    if (result != null)
      return result.data_seek(env, rowNumber);
    else
      return false;
  }

  /**
   * Retrieves the database name from a call to mysql_list_dbs()
   * row 0 is the first row
   * only valid value from mixedFieldV is 'Database'
   */
  public Value mysql_db_name(Env env,
                             MysqliResult result,
                             int row,
                             @Optional("0") Value mixedFieldV)
  {
    return mysql_result(env, result, row, mixedFieldV);
  }

  /*
  **
  */
  public Value mysql_result(Env env,
                            MysqliResult result,
                            int row,
                            @Optional("0") Value mixedFieldV)
  {
    if (result == null)
      return BooleanValue.FALSE;
    else
      return result.validateResult().getResultField(row, mixedFieldV);
  }

  /**
   * deletes databaseName
   */
  public boolean mysql_drop_db(Env env,
                               String databaseName,
                               @Optional Mysqli conn)
    throws Throwable
  {
    return mysql_query(env, "DROP DATABASE " + databaseName, conn) != BooleanValue.FALSE;
  }

  /**
   * Returns the error number of the most recent error
   */
  public int mysql_errno(Env env,
                         @Optional Mysqli conn)
    throws Throwable
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.errno();
  }

  /**
   * Returns the most recent error
   */
  public String mysql_error(Env env,
                            @Optional Mysqli conn)
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.error();
  }

  public String mysql_escape_string(Env env,
                                    String unescapedString)
  {
    return mysql_real_escape_string(env, unescapedString, null);
  }

  /**
   * Escapes the following special character in unescapedString.
   *
   * @see String QuercusMysqliModule.mysqli_real_escape_string(JdbcConnectionResource, String)
   * @return the escaped string
   */

  public String mysql_real_escape_string(Env env,
                                         String unescapedString,
                                         @Optional Mysqli conn)
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.real_escape_string(unescapedString);
  }

  /**
   * Returns a row from the connection
   */
  public Value mysql_fetch_array(Env env,
                                 MysqliResult result,
                                 @Optional("MYSQL_BOTH") int type)
    throws Exception
  {
    if (result != null)
      return result.fetch_array(type);
    else
      return BooleanValue.FALSE;
  }

  /**
   * Returns a row from the connection
   */
  public Value mysql_fetch_assoc(Env env,
                                 MysqliResult result)
    throws Exception
  {
    if (result != null)
      return result.fetch_array(MYSQL_ASSOC);
    else
      return BooleanValue.FALSE;
  }

  /**
   * returns an object containing field information. The
   * 12 properties according to PHP documentation are:
   * <p/>
   * name, table, max_length, not_null, primary_key,
   * unique_key, multiple_key, numeric, blob, type,
   * unsigned and zerofill.
   * <p/>
   * This function does not return the unique_key flag.
   *
   * On success, this method increments result.fieldOffset
   */
  public Value mysql_fetch_field(Env env,
                                 MysqliResult result,
                                 @Optional("-1") int fieldOffset)
    throws Throwable
  {
    if (result == null) {
      env.invalidArgument("result", result);
      return BooleanValue.FALSE;
    }

    if (fieldOffset == -1)
      fieldOffset = result.field_tell(env);

    Value fieldTable = result.fetch_field_table(env, fieldOffset);
    Value fieldName = result.fetch_field_name(env, fieldOffset);
    Value fieldType = result.fetch_field_type(env, fieldOffset);
    Value fieldLength = result.fetch_field_length(env, fieldOffset);
    Value fieldCatalog = result.fetch_field_catalog(fieldOffset);

    if ((fieldTable == BooleanValue.FALSE)
        || (fieldName == BooleanValue.FALSE)
        || (fieldType == BooleanValue.FALSE)
        || (fieldLength == BooleanValue.FALSE)
        || (fieldCatalog == BooleanValue.FALSE)) {
      return BooleanValue.FALSE;
    }

    result.field_seek(env, fieldOffset + 1);

    JdbcConnectionResource conn = getConnection(env).validateConnection();

    JdbcTableMetaData tableMd = conn.getTableMetaData(fieldCatalog.toString(),
					       null,
					       fieldTable.toString());

    if (tableMd == null)
      return BooleanValue.FALSE;

    JdbcColumnMetaData columnMd = tableMd.getColumn(fieldName.toString());

    if (columnMd == null)
      return BooleanValue.FALSE;

    ObjectValue fieldResult = env.createObject();

    fieldResult.putField("name", columnMd.getName());
    fieldResult.putField("table", tableMd.getName());
    fieldResult.putField("def", "");
    fieldResult.putField("max_length", fieldLength.toInt());

    fieldResult.putField("not_null", columnMd.isNotNull() ? 1 : 0);

    fieldResult.putField("primary_key", columnMd.isPrimaryKey() ? 1 : 0);
    fieldResult.putField("multiple_key", columnMd.isIndex() && ! columnMd.isPrimaryKey() ? 1 : 0);
    fieldResult.putField("unique_key", columnMd.isUnique() ? 1 : 0);

    fieldResult.putField("numeric", columnMd.isNumeric() ? 1 : 0);
    fieldResult.putField("blob", columnMd.isBlob() ? 1 : 0);

    fieldResult.putField("type", fieldType.toString());

    fieldResult.putField("unsigned", columnMd.isUnsigned() ? 1 : 0);
    fieldResult.putField("zerofill", columnMd.isZeroFill() ? 1 : 0);

    return fieldResult;
  }

  /**
   * Returns a new mysql connection.
   */
  public Value mysql_query(Env env,
                           String sql,
                           @Optional Mysqli conn)
    throws Throwable
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.query(sql, MYSQL_STORE_RESULT);
  }

  /**
   * returns an array of lengths on success or FALSE on failure.
   */
  public Value mysql_fetch_lengths(Env env,
                                   @NotNull MysqliResult result)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_lengths();
  }

  /**
   * Returns an object with properties that correspond to the fetched row
   * and moves the data pointer ahead.
   */
  public Value mysql_fetch_object(Env env,
                                  @NotNull MysqliResult result)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_object(env);
  }

  /**
   * Returns a row from the connection
   */
  public Value mysql_fetch_row(Env env,
                               @NotNull MysqliResult result)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_row();
  }

  /**
   * Returns the field flags of the specified field.  The flags are reported as
   * a space separated list of words, the returned value can be split using explode().
   * <p/>
   * The following flages are reported, older version of MySQL may not report all flags:
   * <p/>
   * "not_null","primary_key", "multiple_key","blob","unsigned",
   * "zerofill","binary","enum","auto_increment",  "timestamp".
   */
  public Value mysql_field_flags(Env env,
                                 @NotNull MysqliResult result,
                                 int fieldOffset)
    throws Throwable
  {
    if (result == null)
      return BooleanValue.FALSE;

    Value fieldName = result.fetch_field_name(env, fieldOffset);

    if (fieldName == BooleanValue.FALSE)
      return BooleanValue.FALSE;

    Value fieldTable = result.fetch_field_table(env, fieldOffset);

    if (fieldTable == BooleanValue.FALSE)
      return BooleanValue.FALSE;

    String sql = "SHOW FULL COLUMNS FROM " + fieldTable.toString() + " LIKE \'" + fieldName.toString() + "\'";

    Mysqli conn = getConnection(env);

    Value metaResult = conn.validateConnection().query(sql);

    if (metaResult instanceof JdbcResultResource)
      return ((JdbcResultResource) metaResult).getFieldFlags();

    return BooleanValue.FALSE;
  }

  /**
   * Returns field name at given offset.
   *
   * @param env
   * @param result
   * @param fieldOffset
   * @return
   * @throws Exception
   */
  public Value mysql_field_name(Env env,
                                @NotNull MysqliResult result,
                                int fieldOffset)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_field_name(env, fieldOffset);
  }

  /**
   * seeks to the specified field offset.
   * If the next call to mysql_fetch_field() doesn't include
   * a field offset, the field offset specified in
   * mysql_field_seek() will be returned.
   */
  public boolean mysql_field_seek(Env env,
                                  MysqliResult result,
                                  int fieldOffset)
    throws Exception
  {
    if (result != null)
      return result.field_seek(env, fieldOffset);
    else
      return false;
  }

  /**
   * Returns the table corresponding to the field.
   */
  public Value mysql_field_table(Env env,
                                 MysqliResult result,
                                 int fieldOffset)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_field_table(env, fieldOffset);
  }

  /**
   * returns field type
   */
  public Value mysql_field_type(Env env,
                                @NotNull MysqliResult result,
                                int fieldOffset)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_field_type(env, fieldOffset);
  }

  /**
   * Deprecated alias for mysql_field_len
   */
  public Value mysql_fieldlen(Env env, @NotNull MysqliResult result,
			      int fieldOffset)
    throws Exception
  {
    return mysql_field_len(env, result, fieldOffset);
  }

  /**
   * returns the length of the specified field
   * XXX: returns a value of 10 for datatypes DEC and
   * NUMERIC where the actual PHP function
   * returns 11.
   */
  public Value mysql_field_len(Env env,
                               @NotNull MysqliResult result,
			       int fieldOffset)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_field_length(env, fieldOffset);
  }

  /**
   * Frees a mysql result.
   */
  public boolean mysql_free_result(MysqliResult result)
  {
    if (result != null) {
      result.close();

      return true;
    }
    else
      return false;
  }

  /**
   * returns the MySQL client version
   */
  public String mysql_get_client_info(Env env, @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.get_client_info();
  }

  /**
   * returns a string describing the type of MySQL connection in use
   */
  public String mysql_get_host_info(Env env, @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.get_host_info();
  }

  /**
   * returns an integer respresenting the MySQL protocol
   * version used by the connection.
   */
  public int mysql_get_proto_info(Env env, @Optional Mysqli conn)
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.get_proto_info();
  }

  /**
   * returns the MySQL server version on success
   */
  public String mysql_get_server_info(Env env, @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.get_server_info();
  }

  /**
   * returns ID generated for an AUTO_INCREMENT column by the previous
   * INSERT query on success, 0 if the previous query does not generate
   * an AUTO_INCREMENT value, or FALSE if no MySQL connection was established
   */
  public Value mysql_insert_id(Env env, @Optional Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.insert_id();
  }

  /*
  ** Returns a result pointer containing the databases available from the current mysql daemon.
  */
  public Value mysql_list_dbs(Env env,
                              @Optional Mysqli conn)
    throws Throwable
  {
    if (conn == null)
      conn = getConnection(env);

    try {
      return conn.validateConnection().getCatalogs();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);

      return BooleanValue.FALSE;
    }
  }

  /**
   * retrieves information about the given table name
   * This function is deprecated.  It is preferable to use
   * mysql_query to issue 'SHOW COLUMNS FROM table LIKE 'name'.
   *
   * In fact, this function is just a wrapper for mysql_db_query.
   */
  public Value mysql_list_fields(Env env,
                                 String databaseName,
                                 String tableName,
                                 @Optional Mysqli conn)
    throws Throwable
  {
    return mysql_db_query(env, databaseName,
			  "SHOW COLUMNS FROM " + tableName,
			  conn);
  }

  /**
   * returns result set or false on error
   */
  public Value mysql_db_query(Env env,
                              String databaseName,
                              String query,
                              @Optional Mysqli conn)
    throws Throwable
  {
    if (conn == null)
      conn = getConnection(env);

    if (conn.select_db(databaseName))
      return conn.query(query, 0);
    else
      return BooleanValue.FALSE;
  }

  /**
   * Selects the database
   */
  public boolean mysql_select_db(Env env,
                                 String name,
                                 @Optional Mysqli conn)
    throws Throwable
  {
    if (conn == null)
      conn = getConnection(env);

    if (conn != null)
      return conn.select_db(name);
    else
      return false;
  }

  /**
   * Retrieves a list of table names from a MySQL database.
   */
  public Value mysql_list_tables(Env env,
                                 String databaseName,
                                 @Optional Mysqli conn)
    throws Throwable
  {
    return mysql_query(env, "SHOW TABLES FROM " + databaseName, conn);
  }

  public int mysql_num_fields(Env env,
			      @NotNull MysqliResult result)
  {
    if (result == null)
      return -1;

    return result.num_fields();
  }

  /**
   * Retrieves number of rows from a result set.
   */
  public Value mysql_num_rows(Env env,
                              @NotNull MysqliResult result)
    throws SQLException
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.num_rows();
  }

  /**
   * Returns a new mysql connection.
   */
  public Value mysql_pconnect(Env env,
                              @Optional String server,
                              @Optional String user,
                              @Optional String password,
                              @Optional Value newLinkV,
                              @Optional Value flagsV)
  {
    return mysql_connect(env, server, user, password,
			 newLinkV, flagsV);
  }

  /**
   * Returns a new mysql connection.
   */
  public Value mysql_connect(Env env,
                             @Optional String host,
                             @Optional String userName,
                             @Optional String password,
                             @Optional Value newLinkV,
                             @Optional Value flagsV)
  {
    try {
      // XXX: check host for port?

      Mysqli mysqli = new Mysqli(env, host, userName, password,
				 "", 3306, "");

      Value value = env.wrapJava(mysqli);

      env.setSpecialValue("caucho.mysql", mysqli);

      return value;
    } catch (Exception e) {
      env.warning("A link to the server could not be established. " + e.toString());
      log.log(Level.FINE, e.toString(), e);
      return BooleanValue.FALSE;
    }
  }

  /**
   * Checks if the connection is still valid
   */
  public boolean mysql_ping(Env env, @Optional Mysqli conn)
    throws Exception
  {
    if (conn == null)
      conn = getConnection(env);

    return conn.ping();
  }

  /**
   * returns a string with the status of the connection
   * or NULL if error
   */
  public Value mysql_stat(Env env, Mysqli conn)
    throws SQLException
  {
    if (conn == null)
      conn = getConnection(env);

    Value result = conn.stat(env);

    if (result == BooleanValue.FALSE)
      return NullValue.NULL;
    else
      return result;
  }

  /**
   * Retrieves the table name corresponding to a field, using
   * a result return by {@link #mysql_list_tables}.
   */
  public Value mysql_tablename(Env env,
                               @NotNull MysqliResult result,
                               int i)
    throws Exception
  {
    if (result == null)
      return BooleanValue.FALSE;

    return result.fetch_field_table(env, i);
  }

  /**
   * Queries the database.
   */
  public Value mysql_unbuffered_query(Env env,
                                      String name,
                                      @Optional Mysqli conn)
    throws Throwable
  {
    return mysql_query(env, name, conn);
  }

  //@todo mysql_change_user()
  //@todo mysql_info()
  //@todo mysql_list_processes()
  //@todo mysql_thread_id()

  /**
   * returns JdbcConnectionResource and creates one if not there already
   */
  private Mysqli getConnection(Env env)
  {
    Mysqli conn = (Mysqli) env.getSpecialValue("caucho.mysql");

    if (conn != null)
      return conn;

    conn = new Mysqli(env, "localhost", "", "", "", 3306, "");

    env.setSpecialValue("caucho.mysql", conn);

    return conn;
  }
}

