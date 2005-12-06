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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.zip.CRC32;

import java.io.InputStream;
import java.io.IOException;

import java.security.MessageDigest;

import javax.crypto.Cipher;

import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.LongValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.DefaultValue;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.ArrayValue;
import com.caucho.quercus.env.ArrayValueImpl;
import com.caucho.quercus.env.VarMap;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.ChainedMap;

import com.caucho.quercus.module.Reference;
import com.caucho.quercus.module.Optional;
import com.caucho.quercus.module.PhpModule;
import com.caucho.quercus.module.AbstractPhpModule;

import com.caucho.util.L10N;
import com.caucho.util.RandomUtil;

import com.caucho.vfs.ByteToChar;
import com.caucho.vfs.Path;

/**
 * PHP functions implemented from the string module
 */
public class PhpStringModule extends AbstractPhpModule {
  private static final Logger log =
    Logger.getLogger(PhpStringModule.class.getName());
  
  private static final L10N L = new L10N(PhpStringModule.class);

  public static final int CRYPT_SALT_LENGTH = 2;
  public static final int CRYPT_STD_DES = 0;
  public static final int CRYPT_EXT_DES = 0;
  public static final int CRYPT_MD5 = 0;
  public static final int CRYPT_BLOWFISH = 0;
  
  public static final int CHAR_MAX = 1;
  
  public static final int LC_CTYPE = 1;
  public static final int LC_NUMERIC = 2;
  public static final int LC_TIME = 3;
  public static final int LC_COLLATE = 4;
  public static final int LC_MONETARY = 5;
  public static final int LC_ALL = 6;
  public static final int LC_MESSAGES = 7;
  
  public static final int STR_PAD_LEFT = 1;
  public static final int STR_PAD_RIGHT = 0;
  public static final int STR_PAD_BOTH = 2;

  /**
   * Escapes a string using C syntax.
   *
   * @param env the quercus calling environment
   * @param s the source string to convert
   * @param charset the set of characters to convert
   * @return the escaped string
   */
  public static Value addcslashes(Env env, Value s, Value charset)
    throws Throwable
  {
    String source = s.toString(env);

    boolean []bitmap = parseCharsetBitmap(charset.toString(env));

    StringBuilder sb = new StringBuilder();
    int length = source.length();
    for (int i = 0; i < length; i++) {
      char ch = source.charAt(i);

      if (ch >= 256 || ! bitmap[ch]) {
        sb.append(ch);
        continue;
      }

      switch (ch) {
      case 0x07:
        sb.append("\\a");
        break;
      case '\b':
        sb.append("\\b");
        break;
      case '\t':
        sb.append("\\t");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case 0xb:
        sb.append("\\v");
        break;
      case '\f':
        sb.append("\\f");
        break;
      case '\r':
        sb.append("\\r");
        break;
      default:
        if (ch < 0x20 || ch >= 0x7f) {
          // save as octal
          sb.append("\\");
          sb.append((char) ('0' + ((ch >> 6) & 7)));
          sb.append((char) ('0' + ((ch >> 3) & 7)));
          sb.append((char) ('0' + ((ch) & 7)));
          break;
        }
        else {
          sb.append("\\");
          sb.append((char) ch);
          break;
        }
      }
    }

    return new StringValue(sb.toString());
  }

  /**
   * Parses the cslashes bitmap returning an actual bitmap.
   *
   * @param charset the bitmap string
   * @return  the actual bitmap
   */
  private static boolean []parseCharsetBitmap(String charset)
  {
    boolean []bitmap = new boolean[256];

    int length = charset.length();
    for (int i = 0; i < length; i++) {
      char ch = charset.charAt(i);

      // XXX: the bitmap eventual might need to deal with unicode
      if (ch >= 256)
        continue;

      bitmap[ch] = true;

      if (length <= i + 3)
        continue;

      if (charset.charAt(i + 1) != '.' || charset.charAt(i + 2) != '.')
        continue;

      char last = charset.charAt(i + 3);

      if (last < ch) {
        // XXX: exception type
        throw new RuntimeException(L.l("Invalid range."));
      }

      i += 3;
      for (; ch <= last; ch++) {
        bitmap[ch] = true;
      }

      // XXX: handling of '@'?
    }

    return bitmap;
  }

  /**
   * Escapes a string for db characters.
   *
   * @param env the quercus calling environment
   * @param s the source string to convert
   * @return the escaped string
   */
  public static Value addslashes(Env env, Value s)
    throws Throwable
  {
    // XXX: sybase?

    String source = s.toString(env);

    StringBuilder sb = new StringBuilder();
    int length = source.length();
    for (int i = 0; i < length; i++) {
      char ch = source.charAt(i);

      switch (ch) {
      case 0x0:
        sb.append("\\0");
        break;
      case '\'':
        sb.append("\\'");
        break;
      case '\"':
        sb.append("\\\"");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      default:
        sb.append(ch);
        break;
      }
    }

    return new StringValue(sb.toString());
  }

  /**
   * Converts a binary value to a hex value.
   */
  public static String bin2hex(String strValue)
    throws Throwable
  {
    StringBuffer sb = new StringBuffer();

    for (int i = 0; i < strValue.length(); i++) {
      char ch = strValue.charAt(i);

      int d = (ch >> 4) & 0xf;

      if (d < 10)
        sb.append((char) (d + '0'));
      else
        sb.append((char) (d + 'a' - 10));

      d = (ch) & 0xf;

      if (d < 10)
        sb.append((char) (d + '0'));
      else
        sb.append((char) (d + 'a' - 10));
    }

    return sb.toString();
  }

  /**
   * Alias of rtrim.  Removes trailing whitespace.
   *
   * @param env the quercus environment
   * @param str the string to be trimmed
   * @param charset optional set of characters to trim
   * @return the trimmed string
   */
  public static Value chop(Env env, Value str,
                           @Optional Value charset)
    throws Throwable
  {
    return rtrim(env, str, charset);
  }

  /**
   * converts a number to its character equivalent
   *
   * @param value the integer value
   *
   * @return the string equivalent
   */
  public static String chr(long value)
  {
    return String.valueOf((char) value);
  }

  /**
   * Splits a string into chunks
   *
   * @param env the quercus environment
   * @param bodyValue the body string
   * @param chunkLenValue the optional chunk length, defaults to 76
   * @param endValue the optional end value, defaults to \r\n
   * @return
   */
  public static String chunk_split(String body,
				   @Optional("76") int chunkLen,
				   @Optional("\"\\r\\n\"") String end)
    throws Throwable
  {
    if (chunkLen < 1) // XXX: real exn
      throw new IllegalArgumentException(L.l("bad value {0}", chunkLen));

    StringBuilder sb = new StringBuilder();

    int i = 0;

    for (; i + chunkLen <= body.length(); i += chunkLen) {
      sb.append(body.substring(i, i + chunkLen));
      sb.append(end);
    }

    if (i < body.length()) {
      sb.append(body.substring(i));
      sb.append(end);
    }

    return sb.toString();
  }

  /**
   * Converts from one cyrillic set to another.
   *
   * In the Java implementation, this doesn't matter since
   * the underlying encoding is already 16 bits, unless we attach
   * an encoding to the StringValue object..
   */
  public static Value convert_cyr_string(Value str,
                                         Value from,
                                         Value to)
  {
    return str;
  }

  public static Value convert_uudecode(Env env, String source)
    throws java.io.IOException
  {
    if (source == null || source.length() == 0)
      return BooleanValue.FALSE;

    ByteToChar byteToChar = env.getByteToChar();

    int length = source.length();
    
    int i = 0;
    while (i < length) {
      int ch1 = source.charAt(i++);

      if (ch1 == 0x60 || ch1 == 0x20)
	break;
      else if (ch1 < 0x20 || 0x5f < ch1)
	continue;

      int sublen = ch1 - 0x20;

      while (sublen > 0) {
	int code = 0;
	
	code = ((source.charAt(i++) - 0x20) & 0x3f) << 18;
	code += ((source.charAt(i++) - 0x20) & 0x3f) << 12;
	code += ((source.charAt(i++) - 0x20) & 0x3f) << 6;
	code += ((source.charAt(i++) - 0x20) & 0x3f);

	byteToChar.addByte(code >> 16);
	
	if (sublen > 1)
	  byteToChar.addByte(code >> 8);
	
	if (sublen > 2)
	  byteToChar.addByte(code);

	sublen -= 3;
      }
    }

    return new StringValue(byteToChar.getConvertedString());
  }

  /**
   * uuencode a string.
   */
  public static Value convert_uuencode(String source)
  {
    if (source == null || source.length() == 0)
      return BooleanValue.FALSE;

    StringBuilder result = new StringBuilder();
    
    int i = 0;
    int length = source.length();
    while (i < length) {
      int sublen = length - i;

      if (45 < sublen)
	sublen = 45;

      result.append((char) (sublen + 0x20));

      int end = i + sublen;

      while (i < end) {
	int code = source.charAt(i++) << 16;

	if (i < length)
	  code += source.charAt(i++) << 8;
	
	if (i < length)
	  code += source.charAt(i++);

	result.append(toUUChar(((code >> 18) & 0x3f)));
	result.append(toUUChar(((code >> 12) & 0x3f)));
	result.append(toUUChar(((code >> 6) & 0x3f)));
	result.append(toUUChar(((code) & 0x3f)));
      }

      result.append('\n');
    }

    result.append((char) 0x60);
    result.append('\n');

    return new StringValue(result.toString());
  }

  /**
   * Converts an integer digit to a uuencoded char.
   */
  private static char toUUChar(int d)
  {
    if (d == 0)
      return (char) 0x60;
    else
      return (char) (0x20 + (d & 0x3f));
  }

  /**
   * Returns an array of information about the characters.
   */
  public static Value count_chars(String data,
                                  @Optional("0") int mode)
  {
    if (data == null)
      data = "";

    int []count = new int[256];

    int length = data.length();

    for (int i = 0; i < length; i++) {
      count[data.charAt(i) & 0xff] += 1;
    }

    switch (mode) {
    case 0:
      {
	ArrayValue result = new ArrayValueImpl();

	for (int i = 0; i < count.length; i++) {
	  result.put(new LongValue(i), new LongValue(count[i]));
	}
	
	return result;
      }
      
    case 1:
      {
	ArrayValue result = new ArrayValueImpl();

	for (int i = 0; i < count.length; i++) {
	  if (count[i] > 0)
	    result.put(new LongValue(i), new LongValue(count[i]));
	}
	
	return result;
      }
      
    case 2:
      {
	ArrayValue result = new ArrayValueImpl();

	for (int i = 0; i < count.length; i++) {
	  if (count[i] == 0)
	    result.put(new LongValue(i), new LongValue(count[i]));
	}
	
	return result;
      }
      
    case 3:
      {
	StringBuilder sb = new StringBuilder();

	for (int i = 0; i < count.length; i++) {
	  if (count[i] > 0)
	    sb.append((char) i);
	}
	
	return new StringValue(sb.toString());
      }
      
    case 4:
      {
	StringBuilder sb = new StringBuilder();

	for (int i = 0; i < count.length; i++) {
	  if (count[i] == 0)
	    sb.append((char) i);
	}
	
	return new StringValue(sb.toString());
      }

    default:
      return BooleanValue.FALSE;
    }
  }

  /**
   * Calculates the crc32 value for a string
   *
   * @param str the string value
   *
   * @return the crc32 hash
   */
  public static int crc32(String str)
    throws Throwable
  {
    CRC32 crc = new CRC32();

    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);

      crc.update((byte) ch);
    }

    return (int) crc.getValue();
  }

  public static String crypt(String string, @Optional String salt)
    throws Exception
  {
    return Crypt.crypt(string, salt);
  }

  /**
   * explodes a string into an array
   *
   * @param env the quercus environment
   * @param separator the separator string
   * @param string the string to be exploded
   * @param limit the max number of elements
   * @return an array of exploded values
   */
  public static Value explode(Env env,
                              String separator,
                              String string,
                              @Optional("0x7fffffff") long limit)
    throws Throwable
  {
    if (separator.equals(""))
      return BooleanValue.FALSE;

    ArrayValue array = new ArrayValueImpl();

    int head = 0;
    int tail;

    int i = 0;
    while ((tail = string.indexOf(separator, head)) >= 0) {
      if (limit <= i + 1)
        break;

      LongValue key = new LongValue(i++);

      StringValue chunk = new StringValue(string.substring(head, tail));

      array.put(key, chunk);

      head = tail + separator.length();
    }

    LongValue key = new LongValue(i++);

    StringValue chunk = new StringValue(string.substring(head));

    array.put(key, chunk);

    return array;
  }

  public static Value fprintf(Env env,
                              Value fdV,
                              Value formatV,
                              Value []argsV)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * implodes an array into a string
   *
   * @param glueV the separator string
   * @param piecesV the array to be imploded
   *
   * @return a string of imploded values
   */
  public static Value implode(Env env,
                              Value glueV,
                              Value piecesV)
    throws Throwable
  {
    String glue;
    ArrayValue pieces;
      
    if (piecesV instanceof ArrayValue) {
      pieces = (ArrayValue) piecesV;
      glue = glueV.toString();
    }
    else if (glueV instanceof ArrayValue) {
      pieces = (ArrayValue) glueV;
      glue = piecesV.toString();
    }
    else {
      // XXX: handled wrong?
      throw new IllegalStateException(L.l("neither arg is an array"));
    }

    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;

    for (Map.Entry<Value,Value> entry : pieces.entrySet()) {
      if (! isFirst)
	sb.append(glue);
      isFirst = false;

      sb.append(entry.getValue().toString());
    }

    return new StringValue(sb.toString());
  }

  /**
   * implodes an array into a string
   *
   * @param env the calling environment
   * @param glueV the separator string
   * @param arrayV the array to be imploded
   *
   * @return a string of imploded values
   */
  public static Value join(Env env,
			   Value glueV,
                           Value piecesV)
    throws Throwable
  {
    return implode(env, glueV, piecesV);
  }

  /**
   * returns the md5 hash
   *
   * @param string the string
   * @param rawOutput if true, return the raw binary
   *
   * @return a string of imploded values
   */
  public static String md5(String source,
			   @Optional boolean rawOutput)
    throws Throwable
  {
    MessageDigest md = MessageDigest.getInstance("MD5");

    // XXX: iso-8859-1

    for (int i = 0; i < source.length(); i++) {
      char ch = source.charAt(i);

      md.update((byte) ch);
    }

    byte []digest = md.digest();

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < digest.length; i++) {
      int d1 = (digest[i] >> 4) & 0xf;
      int d2 = (digest[i] & 0xf);

      sb.append(toHex(d1));
      sb.append(toHex(d2));
    }

    return sb.toString();
  }

  /**
   * returns the md5 hash
   *
   * @param path the string
   * @param rawOutput if true, return the raw binary
   *
   * @return a string of imploded values
   */
  public static Value md5_file(Path source,
			       @Optional boolean rawOutput)
    throws Throwable
  {
    MessageDigest md = MessageDigest.getInstance("MD5");
    InputStream is = null;

    try {
      is = source.openRead();
      int d;

      while ((d = is.read()) >= 0) {
	md.update((byte) d);
      }

      return digestToString(md.digest());
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);

      return BooleanValue.FALSE;
    } finally {
      try {
	if (is != null)
	  is.close();
      } catch (IOException e) {
      }
    }
  }

  private static Value digestToString(byte []digest)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < digest.length; i++) {
      int d1 = (digest[i] >> 4) & 0xf;
      int d2 = (digest[i] & 0xf);

      sb.append(toHex(d1));
      sb.append(toHex(d2));
    }

    return new StringValue(sb.toString());
  }

  private static char toHex(int d)
  {
    if (d < 10)
      return (char) (d + '0');
    else
      return (char) (d - 10 + 'a');
  }

  /**
   * returns the formatted number
   *
   * @param value the value
   * @param decimals the number of decimals
   * @param decPoint the decimal point string
   * @param thousandsSep the thousands separator
   *
   * @return a string of imploded values
   */
  public static String number_format(double value,
				     @Optional int decimals,
				     @Optional String point,
				     @Optional String group)
  {
    // XXX: stub
    return String.valueOf(value);
  }

 /**
   * Converts the first charater to an integer.
   *
   * @param env the quercus environment
   * @param stringV the string to be converted
   *
   * @return the integer value
   */
  public static long ord(String string)
    throws Throwable
  {
    if (string.length() == 0)
      return 0;
    else
      return string.charAt(0);
  }

 /**
  * Prints the string.
   *
   * @param env the quercus environment
   * @param string the string to print
   */
  public static long print(Env env, String string)
    throws Throwable
  {
    env.getOut().print(string);

    return 1;
  }

  /**
   * Escapes meta characters.
   *
   * @param env the quercus environment
   * @param stringV the string to be quoted
   *
   * @return the quoted
   */
  public static Value quotemeta(Env env,
                                Value stringV)
    throws Throwable
  {
    String string = stringV.toString(env);
    StringBuilder sb = new StringBuilder();

    int len = string.length();
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      switch (ch) {
      case '.': case '\\': case '+': case '*': case '?':
      case '[': case '^': case ']': case '(': case ')': case '$':
        sb.append("\\");
        sb.append(ch);
        break;
      default:
        sb.append(ch);
        break;
      }
    }

    return new StringValue(sb.toString());
  }

  private static final boolean[]TRIM_WHITESPACE = new boolean[256];

  static {
    TRIM_WHITESPACE['\0'] = true;
    TRIM_WHITESPACE['\b'] = true;
    TRIM_WHITESPACE[' '] = true;
    TRIM_WHITESPACE['\t'] = true;
    TRIM_WHITESPACE['\r'] = true;
    TRIM_WHITESPACE['\n'] = true;
  }

  /**
   * Removes leading whitespace.
   *
   * @param strValue the string to be trimmed
   * @param charset optional set of characters to trim
   * @return the trimmed string
   */
  public static Value ltrim(Env env,
                            Value strValue,
                            @Optional Value charset)
    throws Throwable
  {
    String str = strValue.toString(env);
    boolean []trim;

    if (charset instanceof DefaultValue)
      trim = TRIM_WHITESPACE;
    else
      trim = parseCharsetBitmap(charset.toString(env));

    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);

      if (ch >= 256 || ! trim[ch]) {
        if (i == 0)
          return strValue;
        else
          return new StringValue(str.substring(i));
      }
    }

    return new StringValue("");
  }

  /**
   * Removes trailing whitespace.
   *
   * @param env the quercus environment
   * @param strValue the string to be trimmed
   * @param charset optional set of characters to trim
   * @return the trimmed string
   */
  public static Value rtrim(Env env, Value strValue, @Optional Value charset)
    throws Throwable
  {
    String str = strValue.toString(env);
    boolean []trim;

    if (charset instanceof DefaultValue)
      trim = TRIM_WHITESPACE;
    else
      trim = parseCharsetBitmap(charset.toString(env));

    for (int i = str.length() - 1; i >= 0; i--) {
      char ch = str.charAt(i);

      if (ch >= 256 || ! trim[ch]) {
        if (i == str.length())
          return strValue;
        else
          return new StringValue(str.substring(0, i + 1));
      }
    }

    return new StringValue("");
  }

  /**
   * returns the md5 hash
   *
   * @param string the string
   * @param rawOutput if true, return the raw binary
   *
   * @return a string of imploded values
   */
  public static String sha1(String source,
			    @Optional boolean rawOutput)
    throws Throwable
  {
    MessageDigest md = MessageDigest.getInstance("SHA1");

    // XXX: iso-8859-1

    for (int i = 0; i < source.length(); i++) {
      char ch = source.charAt(i);

      md.update((byte) ch);
    }

    byte []digest = md.digest();

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < digest.length; i++) {
      int d1 = (digest[i] >> 4) & 0xf;
      int d2 = (digest[i] & 0xf);

      sb.append(toHex(d1));
      sb.append(toHex(d2));
    }

    return sb.toString();
  }

  /**
   * returns the md5 hash
   *
   * @param path the string
   * @param rawOutput if true, return the raw binary
   *
   * @return a string of imploded values
   */
  public static Value sha1_file(Path source,
			       @Optional boolean rawOutput)
    throws Throwable
  {
    MessageDigest md = MessageDigest.getInstance("SHA1");
    InputStream is = null;

    try {
      is = source.openRead();
      int d;

      while ((d = is.read()) >= 0) {
	md.update((byte) d);
      }

      return digestToString(md.digest());
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);

      return BooleanValue.FALSE;
    } finally {
      try {
	if (is != null)
	  is.close();
      } catch (IOException e) {
      }
    }
  }

  /**
   * scans a string
   *
   * @param env the quercus environment
   * @param format the format string
   * @param args the format arguments
   *
   * @return the formatted string
   */
  public static Value sscanf(Env env,
			     String string,
			     String format,
			     @Optional Value []args)
  {
    // quercus/113-
    
    int fmtLen = format.length();
    int strlen = string.length();

    int sIndex = 0;
    int fIndex = 0;

    ArrayValue array = new ArrayValueImpl();

    while (fIndex < fmtLen && sIndex < strlen) {
      char ch = format.charAt(fIndex++);

      if (isWhitespace(ch)) {
	for (;
	     (fIndex < fmtLen &&
	      isWhitespace(ch = format.charAt(fIndex)));
	     fIndex++) {
	}

	ch = string.charAt(sIndex);
	if (! isWhitespace(ch)) {
	  return array; // XXX: return false?
	}

	for (sIndex++;
	     sIndex < strlen && isWhitespace(string.charAt(sIndex));
	     sIndex++) {
	}
      }
      else if (ch == '%') {
	int maxLen = -1;
	boolean suppressAssignment = false;

	loop:
	while (fIndex < fmtLen) {
	  ch = format.charAt(fIndex++);

	  switch (ch) {
	  case '%':
	    if (string.charAt(sIndex) != '%')
	      return array;
	    else
	      break loop;

	  case '0': case '1': case '2': case '3': case '4':
	  case '5': case '6': case '7': case '8': case '9':
	    if (maxLen < 0)
	      maxLen = 0;

	    maxLen = 10 * maxLen + ch - '0';
	    break;
	    
	  case 's':
	    sIndex = sscanfString(string, sIndex, maxLen, array);
	    break loop;

	  default:
	    log.fine(L.l("'{0}' is a bad sscanf string.", format));
	    return array;
	  }
	}
      }
      else if (ch == string.charAt(sIndex)) {
	sIndex++;
      }
      else
	return array;
    }

    return array;
  }

  /**
   * Scans a string with a given length.
   */
  private static int sscanfString(String string, int sIndex, int maxLen,
				  ArrayValue array)
  {
    // quercus/1131
    int strlen = string.length();

    if (maxLen < 0)
      maxLen = Integer.MAX_VALUE;

    StringBuilder sb = new StringBuilder();

    for (; sIndex < strlen && maxLen-- > 0; sIndex++) {
      char ch = string.charAt(sIndex);

      if (isWhitespace(ch)) {
	array.append(new StringValue(sb.toString()));
	return sIndex;
      }
      else
	sb.append(ch);
    }
    
    array.append(new StringValue(sb.toString()));

    return sIndex;
  }

  /**
   * print to the output with a formatter
   *
   * @param env the quercus environment
   * @param format the format string
   * @param args the format arguments
   *
   * @return the formatted string
   */
  public static Value printf(Env env, String format, Value []args)
    throws Throwable
  {
    env.getOut().print(sprintf(env, format, args));

    return NullValue.NULL;
  }

  /**
   * print to a string with a formatter
   *
   * @param env the quercus environment
   * @param format the format string
   * @param args the format arguments
   *
   * @return the formatted string
   */
  public static String sprintf(Env env, String format, Value []args)
    throws Throwable
  {
    Object []values = new Object[args.length];

    parsePrintfFormat(format, args, values);
    
    return String.format(format, values);
  }

  private static void parsePrintfFormat(String format,
					Value []args,
					Object []values)
  {
    int k = 0;
    int strlen = format.length();

    for (int i = 0; i < strlen; i++) {
      char ch = format.charAt(i);

      if (ch == '%') {
	loop:
	for (i++; i < strlen; i++) {
	  ch = format.charAt(i);

	  switch (ch) {
	  case '%':
	    break loop;
	  case 's': case 'c':
	    values[k] = args[k].toString();
	    k++;
	    break loop;
	  case 'd': case 'x': case 'o': case 'X':
	    values[k] = args[k].toLong();
	    k++;
	    break loop;
	  case 'e': case 'f': case 'g':
	    values[k] = args[k].toDouble();
	    k++;
	    break loop;
	  default:
	    break;
	  }
	}
      }
    }
  }

  /**
   * Removes tags from a string.
   *
   * @param str the string to remove
   * @param allowable_tags the allowable tags
   */
  public static String strip_tags(String string, @Optional String allowTags)
  {
    // XXX: allowTags is stubbed
    
    StringBuilder result = new StringBuilder();

    int len = string.length();
    
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      if (ch != '<') {
	result.append(ch);
	continue;
      }

      for (i++; i < len; i++) {
	ch = string.charAt(i);

	if (ch == '>')
	  break;
      }
    }

    return result.toString();
  }

  /**
   * Removes leading and trailing whitespace.
   *
   * @param env the quercus environment
   * @param strValue the string to be trimmed
   * @param charset optional set of characters to trim
   * @return the trimmed string
   */
  public static Value trim(Env env, String str, @Optional String charset)
    throws Throwable
  {
    boolean []trim;

    if (charset == null || charset.equals(""))
      trim = TRIM_WHITESPACE;
    else
      trim = parseCharsetBitmap(charset.toString());

    int len = str.length();

    int head = 0;
    for (; head < len; head++) {
      char ch = str.charAt(head);

      if (ch >= 256 || ! trim[ch]) {
        break;
      }
    }

    int tail = len - 1;
    for (; tail >= 0; tail--) {
      char ch = str.charAt(tail);

      if (ch >= 256 || ! trim[ch]) {
        break;
      }
    }

    if (tail < head)
      return StringValue.EMPTY;
    else {
      return new StringValue(str.substring(head, tail + 1));
    }
  }

  /**
   * pads strings
   *
   * @param env the calling environment
   * @param stringV string
   * @param lengthV length
   * @param padV padding string
   * @param typeV padding type
   */
  public static String str_pad(Env env,
			       String string,
			       int length,
			       @Optional("' '") String pad,
			       @Optional("STR_PAD_RIGHT") int type)
    throws Throwable
  {
    int strLen = string.length();
    int padLen = length - strLen;

    if (padLen <= 0)
      return string;

    if (pad.length() == 0)
      pad = " ";

    int leftPad = 0;
    int rightPad = 0;

    switch (type) {
    case STR_PAD_LEFT:
      leftPad = padLen;
      break;
    case STR_PAD_RIGHT:
    default:
      rightPad = padLen;
      break;
    case STR_PAD_BOTH:
      leftPad = padLen / 2;
      rightPad = padLen - leftPad;
      break;
    }

    StringBuilder sb = new StringBuilder();

    int padStringLen = pad.length();

    for (int i = 0; i < leftPad; i++)
      sb.append(pad.charAt(i % padStringLen));

    sb.append(string);

    for (int i = 0; i < rightPad; i++)
      sb.append(pad.charAt(i % padStringLen));

    return sb.toString();
  }

  /**
   * repeats a string
   *
   * @param env the calling environment
   * @param stringV string to repeat
   * @param countV number of times to repeat
   */
  public static Value str_repeat(Env env, Value stringV, Value countV)
    throws Throwable
  {
    String string = stringV.toString(env);
    int count = countV.toInt();

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < count; i++)
      sb.append(string);

    return new StringValue(sb.toString());
  }

  /**
   * replaces substrings.
   *
   * @param searchV search string
   * @param replaceV replacement string
   * @param subjectV replacement
   * @param countV return value
   */
  public static Value str_replace(Value searchV,
				  Value replaceV,
				  Value subjectV,
				  @Reference @Optional Value countV)
    throws Throwable
  {
    countV.set(LongValue.ZERO);
    
    if (subjectV instanceof ArrayValue) {
      ArrayValue subjectArray = (ArrayValue) subjectV;
      ArrayValue resultArray = new ArrayValueImpl();

      for (Value value : subjectArray.values()) {
	Value result = str_replace_impl(searchV, replaceV,
					value.toString(), countV);

	resultArray.append(value);
      }

      return resultArray;
    }
    else {
      return str_replace_impl(searchV, replaceV, subjectV.toString(), countV);
    }
  }

  /**
   * replaces substrings.
   *
   * @param searchV search string
   * @param replaceV replacement string
   * @param subjectV replacement
   * @param countV return value
   */
  private static Value str_replace_impl(Value searchV,
					Value replaceV,
					String subject,
					Value countV)
    throws Throwable
  {
    if (! searchV.isArray()) {
      subject = str_replace_impl(searchV.toString(),
				 replaceV.toString(),
				 subject,
				 countV);
    }
    else if (replaceV instanceof ArrayValue) {
      ArrayValue searchArray = (ArrayValue) searchV;
      ArrayValue replaceArray = (ArrayValue) replaceV;
	
      Iterator<Value> searchIter = searchArray.values().iterator();
      Iterator<Value> replaceIter = replaceArray.values().iterator();

      while (searchIter.hasNext()) {
	Value searchItem = searchIter.next();
	Value replaceItem = replaceIter.next();

	if (replaceItem == null)
	  replaceItem = NullValue.NULL;

	subject = str_replace_impl(searchItem.toString(),
				   replaceItem.toString(),
				   subject,
				   countV);
      }
    }
    else {
      ArrayValue searchArray = (ArrayValue) searchV;
	
      Iterator<Value> searchIter = searchArray.values().iterator();

      while (searchIter.hasNext()) {
	Value searchItem = searchIter.next();

	subject = str_replace_impl(searchItem.toString(),
				   replaceV.toString(),
				   subject,
				   countV);
      }
    }

    return new StringValue(subject);
  }

  /**
   * replaces substrings.
   *
   * @param search search string
   * @param replace replacement string
   * @param subject replacement
   * @param countV return value
   */
  private static String str_replace_impl(String search,
					 String replace,
					 String subject,
					 Value countV)
  {
    long count = countV.toLong();
    
    int head = 0;
    int next;

    int searchLen = search.length();
    int replaceLen = replace.length();

    StringBuilder result = new StringBuilder();

    while ((next = subject.indexOf(search, head)) >= head) {
      result.append(subject, head, next);
      result.append(replace);

      if (head < next + searchLen)
	head = next + searchLen;
      else
	head += 1;

      count++;
    }

    if (count != 0) {
      countV.set(new LongValue(count));

      if (head > 0 && head < subject.length())
	result.append(subject, head, subject.length());

      return result.toString();
    }
    else
      return subject;
  }

  /**
   * rot13 conversion
   *
   * @param env the calling environment
   * @param stringV string to convert
   */
  public static Value str_rot13(Env env, Value stringV)
    throws Throwable
  {
    String string = stringV.toString(env);

    StringBuilder sb = new StringBuilder();

    int len = string.length();
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      if ('a' <= ch && ch <= 'z') {
        int off = ch - 'a';

        sb.append((char) ('a' + (off + 13) % 26));
      }
      else if ('A' <= ch && ch <= 'Z') {
        int off = ch - 'A';

        sb.append((char) ('A' + (off + 13) % 26));
      }
      else {
        sb.append(ch);
      }
    }

    return new StringValue(sb.toString());
  }

  /**
   * shuffles a string
   */
  public static String str_shuffle(String string)
  {
    char []chars = string.toCharArray();

    int length = chars.length;

    for (int i = 0; i < length; i++) {
      int rand = RandomUtil.nextInt(length);

      char temp = chars[rand];
      chars[rand] = chars[i];
      chars[i] = temp;
    }

    return new String(chars);
  }

  /**
   * split into an array
   *
   * @param env the calling environment
   * @param stringV string to split
   * @param chunkV chunk size
   */
  public static Value str_split(Env env,
				Value stringV,
                                @Optional Value chunkV)
    throws Throwable
  {
    String string = stringV.toString(env);

    int chunk = 1;

    if (! (chunkV instanceof DefaultValue))
      chunk = chunkV.toInt();

    if (chunk < 1)
      return BooleanValue.FALSE;

    ArrayValue array = new ArrayValueImpl();

    int strLen = string.length();

    for (int i = 0; i < strLen; i += chunk) {
      Value value;

      if (i + chunk <= strLen) {
        value = new StringValue(string.substring(i, i + chunk));
      } else {
        value = new StringValue(string.substring(i));
      }

      array.put(new LongValue(i), value);
    }

    return array;
  }

  /**
   * Case-insensitive comparison
   *
   * @param env
   * @param aValue left value
   * @param bValue right value
   * @return -1, 0, or 1
   */
  public static int strcasecmp(String a, String b)
    throws Throwable
  {
    int aLen = a.length();
    int bLen = b.length();

    for (int i = 0; i < aLen && i < bLen; i++) {
      char chA = a.charAt(i);
      char chB = b.charAt(i);

      if (chA == chB)
        continue;

      if (Character.isUpperCase(chA))
        chA = Character.toLowerCase(chA);

      if (Character.isUpperCase(chB))
        chB = Character.toLowerCase(chB);

      if (chA == chB)
        continue;
      else if (chA < chB)
        return -1;
      else
        return 1;
    }

    if (aLen == bLen)
      return 0;
    else if (aLen < bLen)
      return -1;
    else
      return 1;
  }

  /**
   * Case-sensitive comparison
   *
   * @param a left value
   * @param b right value
   * @return -1, 0, or 1
   */
  public static int strcmp(String a, String b)
    throws Throwable
  {
    int cmp = a.compareTo(b);

    if (cmp == 0)
      return 0;
    else if (cmp < 0)
      return -1;
    else
      return 1;
  }

  /**
   * Locale-based comparison
   * XXX: i18n
   *
   * @param env
   * @param aValue left value
   * @param bValue right value
   * @return -1, 0, or 1
   */
  public static Value strcoll(Env env, Value aValue, Value bValue)
    throws Throwable
  {
    String a = aValue.toString(env);
    String b = bValue.toString(env);

    int cmp = a.compareTo(b);

    if (cmp == 0)
      return LongValue.ZERO;
    else if (cmp < 0)
      return LongValue.MINUS_ONE;
    else
      return LongValue.ONE;
  }

  /**
   * Finds the index of a substring
   *
   * @param env the calling environment
   */
  public static Value strchr(Env env, Value haystack, Value needle)
    throws Throwable
  {
    return strstr(env, haystack, needle);
  }

  /**
   * Returns the length of a string.
   *
   * @param env the calling environment
   * @param v the argument value
   */
  public static long strlen(String string)
    throws Throwable
  {
    return string.length();
  }

  /**
   * Case-insensitive comparison
   *
   * @param a left value
   * @param b right value
   * @return -1, 0, or 1
   */
  public static int strnatcasecmp(String a, String b)
    throws Throwable
  {
    return strcasecmp(a, b);
  }

  /**
   * Case-sensitive comparison
   *
   * @param a left value
   * @param b right value
   * @return -1, 0, or 1
   */
  public static int strnatcmp(String a, String b)
    throws Throwable
  {
    return strcmp(a, b);
  }

  /**
   * Case-sensitive comparison
   *
   * @param aValue left value
   * @param bValue right value
   * @return -1, 0, or 1
   */
  public static int strncmp(String a, String b, int length)
    throws Throwable
  {
    if (length < a.length())
      a = a.substring(0, length);
    
    if (length < b.length())
      b = b.substring(0, length);
    
    int cmp = a.compareTo(b);

    if (cmp == 0)
      return 0;
    else if (cmp < 0)
      return -1;
    else
      return 1;
  }

  /**
   * Returns the position of a substring.
   *
   * @param env the calling environment
   * @param haystackV the string to search in
   * @param needV the string to search for
   */
  public static Value strpos(Env env,
			     Value haystackV,
			     Value needleV,
			     @Optional Value offsetV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue)
      needle = needleV.toString(env);
    else
      needle = String.valueOf((char) needleV.toInt());

    int offset = offsetV.toInt();

    int pos = haystack.indexOf(needle, offset);

    if (pos < 0)
      return BooleanValue.FALSE;
    else
      return new LongValue(pos);
  }

  /**
   * Returns the position of a substring, testing case insensitive.
   *
   * @param env the calling environment
   * @param haystackV the full argument to check
   * @param needleV the substring argument to check
   * @param offsetV optional starting position
   */
  public static Value stripos(Env env,
			      Value haystackV,
			      Value needleV,
			      @Optional Value offsetV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue)
      needle = needleV.toString(env);
    else
      needle = String.valueOf((char) needleV.toInt());

    int offset = offsetV.toInt();

    haystack = haystack.toLowerCase();
    needle = needle.toLowerCase();

    int pos = haystack.indexOf(needle, offset);

    if (pos < 0)
      return BooleanValue.FALSE;
    else
      return new LongValue(pos);
  }

  /**
   * Strips out the backslashes.
   *
   * @param string the string to clean
   */
  public static String stripslashes(String string)
    throws Throwable
  {
    StringBuilder sb = new StringBuilder();
    int len = string.length();

    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      if (ch == '\\' && i + 1 < len) {
	sb.append(string.charAt(i + 1));
	i++;
      }
      else
	sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Finds the first instance of a substring, testing case insensitively
   *
   * @param env the calling environment
   * @param haystackV the string to search in
   * @param needleV the string to search for
   * @return the trailing match or FALSE
   */
  public static Value stristr(Env env,
			      Value haystackV,
			      Value needleV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue) {
      needle = needleV.toString(env);
    }
    else {
      needle = String.valueOf((char) needleV.toLong());
    }

    String haystackLower = haystack.toLowerCase();
    String needleLower = needle.toLowerCase();

    int i = haystackLower.indexOf(needleLower);

    if (i > 0)
      return new StringValue(haystack.substring(i));
    else
      return BooleanValue.FALSE;
  }

  /**
   * Finds the last instance of a substring
   *
   * @param env the calling environment
   * @param haystackV the string to search in
   * @param needleV the string to search for
   * @return the trailing match or FALSE
   */
  public static Value strrchr(Env env,
			      Value haystackV,
			      Value needleV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue) {
      needle = needleV.toString(env);
    }
    else {
      needle = String.valueOf((char) needleV.toLong());
    }

    int i = haystack.lastIndexOf(needle);

    if (i > 0)
      return new StringValue(haystack.substring(i));
    else
      return BooleanValue.FALSE;
  }

  /**
   * Reverses a string.
   *
   * @param env the calling environment
   */
  public static Value strrev(Env env, Value stringV)
    throws Throwable
  {
    StringBuilder sb = new StringBuilder();
    String string = stringV.toString(env);

    for (int i = string.length() - 1; i >= 0; i--) {
      sb.append(string.charAt(i));
    }

    return new StringValue(sb.toString());
  }

  /**
   * Returns the position of a substring.
   *
   * @param env the calling environment
   * @param haystackV the string to search in
   * @param needleV the string to search for
   */
  public static Value strrpos(Env env,
			      Value haystackV,
			      Value needleV,
			      @Optional Value offsetV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue)
      needle = needleV.toString(env);
    else
      needle = String.valueOf((char) needleV.toInt());

    int offset;

    if (offsetV instanceof DefaultValue)
      offset = haystack.length();
    else
      offset = offsetV.toInt();

    int pos = haystack.lastIndexOf(needle, offset);

    if (pos < 0)
      return BooleanValue.FALSE;
    else
      return new LongValue(pos);
  }

  /**
   * Returns the position of a substring, testing case-insensitive.
   *
   * @param env the calling environment
   * @param haystackV the full string to test
   * @param needleV the substring string to test
   * @param offsetV the optional offset to start searching
   */
  public static Value strripos(Env env,
			       Value haystackV,
			       Value needleV,
			       @Optional Value offsetV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue)
      needle = needleV.toString(env);
    else
      needle = String.valueOf((char) needleV.toInt());

    int offset;

    if (offsetV instanceof DefaultValue)
      offset = haystack.length();
    else
      offset = offsetV.toInt();

    haystack = haystack.toLowerCase();
    needle = needle.toLowerCase();

    int pos = haystack.lastIndexOf(needle, offset);

    if (pos < 0)
      return BooleanValue.FALSE;
    else
      return new LongValue(pos);
  }

  /**
   * Finds the length of a matching set.
   *
   * @param string the string to search in
   * @param charset the character set
   * @param offset the starting offset
   * @param length the length
   * @return the trailing match or FALSE
   */
  public static long strspn(String string,
			    String charset,
			    @Optional int offset,
			    @Optional("-1") int length)
    throws Throwable
  {
    if (length < 0)
      length = Integer.MAX_VALUE;

    int strlen = string.length();
    boolean []set = new boolean[256];
    for (int i = charset.length() - 1; i >= 0; i--) {
      set[charset.charAt(i)] = true;
    }

    int end = offset + length;
    if (strlen < end)
      end = strlen;

    int count = 0;
    for (; offset < end; offset++) {
      char ch = string.charAt(offset);

      if (set[ch])
	count++;
      else
	return count;
    }

    return count;
  }

  /**
   * Finds the first instance of a substring
   *
   * @param env the calling environment
   * @param haystackV the string to search in
   * @param needleV the string to search for
   * @return the trailing match or FALSE
   */
  public static Value strstr(Env env,
			     Value haystackV,
			     Value needleV)
    throws Throwable
  {
    String haystack = haystackV.toString(env);
    String needle;

    if (needleV instanceof StringValue) {
      needle = needleV.toString(env);
    }
    else {
      needle = String.valueOf((char) needleV.toLong());
    }

    int i = haystack.indexOf(needle);

    if (i > 0)
      return new StringValue(haystack.substring(i));
    else
      return BooleanValue.FALSE;
  }

  /**
   * Converts to lower case.
   *
   * @param env the calling environment
   * @param stringV the input string
   */
  public static Value strtolower(Env env, Value stringV)
    throws Throwable
  {
    String string = stringV.toString(env);

    return new StringValue(string.toLowerCase());
  }

  /**
   * Converts to upper case.
   *
   * @param env the calling environment
   * @param stringV the input string
   */
  public static Value strtoupper(Env env, Value stringV)
    throws Throwable
  {
    String string = stringV.toString(env);

    return new StringValue(string.toUpperCase());
  }

  /**
   * Translates characters in a string to target values.
   *
   * @param string the source string
   * @param map the character map
   */
  public static String strtr(Env env,
			     String string,
			     Value fromV,
			     @Optional String to)
  {
    if (fromV instanceof ArrayValue)
      return strtr_array(string, (ArrayValue) fromV);

    String from = fromV.toString();

    if (to == null)
      env.error("strtr requires 3 args.");

    int len = from.length();
    if (to.length() < len)
      len = to.length();
    
    char []map = new char[256];
    for (int i = len - 1; i >= 0; i--)
      map[from.charAt(i)] = to.charAt(i);

    StringBuilder sb = new StringBuilder();
    
    len = string.length();
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);
      
      if (map[ch] != 0)
	sb.append(map[ch]);
      else
	sb.append(ch);
    }

    return sb.toString();
  }
    
  /**
   * Translates characters in a string to target values.
   *
   * @param string the source string
   * @param map the character map
   */
  private static String strtr_array(String string, ArrayValue map)
  {
    StringBuilder result = new StringBuilder();
    int len = string.length();

    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      Value value = map.get(StringValue.create(ch));

      if (! value.isNull())
	result.append(value);
      else
	result.append(ch);
    }

    return result.toString();
  }

  /**
   * Returns a substring
   *
   * @param env the calling environment
   * @param stringV the string
   * @param startV the start offset
   * @param lenV the optional length
   */
  public static Value substr(Env env,
			     String string,
			     int start,
			     @Optional Value lenV)
    throws Throwable
  {
    int strLen = string.length();
    if (start < 0)
      start = strLen + start;

    if (start < 0 || strLen < start)
      return BooleanValue.FALSE;

    if (lenV instanceof DefaultValue) {
      return new StringValue(string.substring(start));
    }
    else {
      int len = lenV.toInt();
      int end;

      if (len < 0)
        end = strLen + len;
      else
        end = start + len;

      if (end <= start)
        return StringValue.EMPTY;
      else if (strLen <= end)
        return new StringValue(string.substring(start));
      else
        return new StringValue(string.substring(start, end));
    }
  }

  /**
   * Returns a substring
   *
   * @param env the calling environment
   * @param stringV the string
   * @param startV the start offset
   * @param lenV the optional length
   */
  public static Value substr_replace(String string,
				     String replacement,
				     int start,
				     @Optional("-1") int len)
  {
    int strLen = string.length();

    if (strLen < start)
      start = strLen;

    int end;

    if (len < 0)
      end = strLen;
    else
      end = start + len;

    if (strLen < end)
      end = strLen;

    String result;

    result = string.substring(0, start) + replacement +  string.substring(end);

    return new StringValue(result);
  }

  /**
   * Uppercases the first character
   *
   * @param string the input string
   */
  public static String ucfirst(String string)
    throws Throwable
  {
    if (string.length() == 0)
      return string;

    return Character.toUpperCase(string.charAt(0)) + string.substring(1);
  }

  /**
   * Uppercases the first character of each word
   *
   * @param env the calling environment
   * @param stringV the input string
   */
  public static Value ucwords(Env env, Value stringV)
    throws Throwable
  {
    String string = stringV.toString(env);

    int strLen = string.length();

    boolean isStart = true;
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < strLen; i++) {
      char ch = string.charAt(i);
      
      switch (ch) {
      case ' ': case '\t': case '\r': case '\n':
	isStart = true;
	sb.append(ch);
	break;
      default:
	if (isStart)
	  sb.append(Character.toUpperCase(ch));
	else
	  sb.append(ch);
	isStart = false;
	break;
      }
    }

    return new StringValue(sb.toString());
  }
  /**
   * Wraps a string to the given number of characters.
   *
   * @param string the input string
   * @param width the width
   * @param breakString the break string
   * @param cut if true, break on exact match
   */
  public static String wordwrap(String string,
				@Optional("75") int width,
				@Optional("'\n'") String breakString,
				@Optional boolean cut)
  {
    int len = string.length();
    int head = 0;

    StringBuilder sb = new StringBuilder();
    while (head + width < len) {
      int tail = head + width;

      if (! cut) {
	for (;
	     head < tail && ! Character.isWhitespace(string.charAt(tail));
	     tail--) {
	}

	if (head == tail)
	  tail = head + width;
      }

      if (sb.length() > 0)
	sb.append(breakString);

      sb.append(string.substring(head, tail));

      head = tail;

      if (! cut && head < len && Character.isWhitespace(string.charAt(head)))
	head++;
    }

    if (head < len) {
      if (sb.length() > 0)
	sb.append(breakString);

      sb.append(string.substring(head));
    }

    return sb.toString();
  }

  /**
   * Returns true if the character is a whitespace character.
   */
  private static boolean isWhitespace(char ch)
  {
    return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
  }
}

