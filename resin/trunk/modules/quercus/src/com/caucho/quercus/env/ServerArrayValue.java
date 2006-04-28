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

import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.LinkedHashMap;

import javax.servlet.http.HttpServletRequest;

import com.caucho.vfs.WriteStream;

/**
 * Represents the server
 */
public class ServerArrayValue extends ArrayValueImpl {
  private static final StringValue SERVER_NAME_V
    = new StringValueImpl("SERVER_NAME");
  private static final StringValue SERVER_PORT_V
    = new StringValueImpl("SERVER_PORT");
  private static final StringValue REMOTE_HOST_V
    = new StringValueImpl("REMOTE_HOST");
  private static final StringValue REMOTE_ADDR_V
    = new StringValueImpl("REMOTE_ADDR");
  private static final StringValue REMOTE_PORT_V
    = new StringValueImpl("REMOTE_PORT");
  
  private static final StringValue DOCUMENT_ROOT_V
    = new StringValueImpl("DOCUMENT_ROOT");
  
  private static final StringValue SERVER_PROTOCOL_V
    = new StringValueImpl("SERVER_PROTOCOL");
  private static final StringValue REQUEST_METHOD_V
    = new StringValueImpl("REQUEST_METHOD");
  private static final StringValue QUERY_STRING_V
    = new StringValueImpl("QUERY_STRING");
  
  private static final StringValue REQUEST_URI_V
    = new StringValueImpl("REQUEST_URI");
  private static final StringValue SCRIPT_NAME_V
    = new StringValueImpl("SCRIPT_NAME");
  private static final StringValue SCRIPT_FILENAME_V
    = new StringValueImpl("SCRIPT_FILENAME");
  private static final StringValue PATH_INFO_V
    = new StringValueImpl("PATH_INFO");
  private static final StringValue PATH_TRANSLATED_V
    = new StringValueImpl("PATH_TRANSLATED");
  
  private static final StringValue PHP_SELF_V
    = new StringValueImpl("PHP_SELF");
  
  private static final StringValue HTTPS_V
    = new StringValueImpl("HTTPS");
  
  private final Env _env;
  
  private boolean _isFilled;

  public ServerArrayValue(Env env)
  {
    _env = env;
  }
  
  /**
   * Converts to a string.
   * @param env
   */
  public String toString(Env env)
  {
    return "Array";
  }
  
  /**
   * Converts to an object.
   */
  public Object toObject()
  {
    return null;
  }

  /**
   * Adds a new value.
   */
  public Value put(Value key, Value value)
  {
    if (! _isFilled)
      fillMap();

    return super.put(key, value);
  }

  /**
   * Adds a new value.
   */
  public Value put(Value value)
  {
    if (! _isFilled)
      fillMap();

    return super.put(value);
  }

  /**
   * Gets a new value.
   */
  public Value get(Value key)
  {
    if (! _isFilled)
      fillMap();

    return super.get(key);
  }
  
  /**
   * Returns the array ref.
   */
  public Var getRef(Value key)
  {
    if (! _isFilled)
      fillMap();
    
    return super.getRef(key);
  }
  
  /**
   * Copy for assignment.
   */
  public Value copy()
  {
    if (! _isFilled)
      fillMap();
    
    return new ArrayValueImpl(this);
  }

  /**
   * Returns an iterator of the entries.
   */
  public Set<Map.Entry<Value,Value>> entrySet()
  {
    if (! _isFilled)
      fillMap();
    
    return super.entrySet();
  }

  /**
   * Convenience for lib.
   */
  public void put(String key, String value)
  {
    if (! _isFilled)
      fillMap();

    super.put(key, value);
  }

  /**
   * Prints the value.
   * @param env
   */
  public void print(Env env)
    throws IOException
  {
    env.getOut().print("Array");
  }

  /**
   * Fills the map.
   */
  private void fillMap()
  {
    HttpServletRequest request = _env.getRequest();

    super.put(SERVER_NAME_V,
	      new StringValueImpl(request.getServerName()));
    super.put(SERVER_PORT_V,
	      new LongValue(request.getServerPort()));
    super.put(REMOTE_HOST_V,
	      new StringValueImpl(request.getRemoteHost()));
    super.put(REMOTE_ADDR_V,
	      new StringValueImpl(request.getRemoteAddr()));
    super.put(REMOTE_PORT_V,
	      new LongValue(request.getRemotePort()));
    
    super.put(SERVER_PROTOCOL_V,
	      new StringValueImpl(request.getProtocol()));
    super.put(REQUEST_METHOD_V,
	      new StringValueImpl(request.getMethod()));
    
    if (request.getQueryString() != null) {
      super.put(QUERY_STRING_V,
		new StringValueImpl(request.getQueryString()));
    }
    
    super.put(DOCUMENT_ROOT_V,
	      new StringValueImpl(request.getRealPath("/")));
    
    super.put(SCRIPT_NAME_V,
	      new StringValueImpl(request.getContextPath() +
			      request.getServletPath()));

    String requestURI = request.getRequestURI();
    String queryString = request.getQueryString();

    if (queryString != null)
      requestURI = requestURI + '?' + queryString;
    
    super.put(REQUEST_URI_V,
	      new StringValueImpl(requestURI));
    super.put(SCRIPT_FILENAME_V,
	      new StringValueImpl(request.getRealPath(request.getServletPath())));

    if (request.getPathInfo() != null) {
      super.put(PATH_INFO_V,
		new StringValueImpl(request.getPathInfo()));
      super.put(PATH_TRANSLATED_V,
		new StringValueImpl(request.getRealPath(request.getPathInfo())));
    }

    if (request.isSecure())
      super.put(HTTPS_V, new StringValueImpl("on"));

    String contextPath = request.getContextPath();
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();

    if (pathInfo == null)
      super.put(PHP_SELF_V, new StringValueImpl(contextPath + servletPath));
    else
      super.put(PHP_SELF_V, new StringValueImpl(contextPath + servletPath + pathInfo));

    Enumeration e = request.getHeaderNames();
    while (e.hasMoreElements()) {
      String key = (String) e.nextElement();

      String value = request.getHeader(key);

      super.put(convertHttpKey(key), new StringValueImpl(value));
    }
  }

  /**
   * Converts a header key to HTTP_
   */
  private StringValue convertHttpKey(String key)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("HTTP_");

    int len = key.length();
    for (int i = 0; i < len; i++) {
      char ch = key.charAt(i);

      if (Character.isLowerCase(ch))
	sb.append(Character.toUpperCase(ch));
      else if (ch == '-')
	sb.append('_');
      else
	sb.append(ch);
    }

    return new StringValueImpl(sb.toString());
  }
}

