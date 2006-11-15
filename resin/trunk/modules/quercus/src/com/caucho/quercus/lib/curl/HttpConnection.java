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
 * @author Nam Nguyen
 */

package com.caucho.quercus.lib.curl;

import com.caucho.quercus.QuercusModuleException;

import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

import java.util.List;
import java.util.Map;

/**
 * Represents a HttpURLConnection wrapper.
 */
public class HttpConnection
  implements Closeable
{
  private HttpURLConnection _conn;

  private URL _URL;
  private String _username;
  private String _password;

  private URL _proxyURL;
  private String _proxyUsername;
  private String _proxyPassword;
  private String _proxyType;

  private int _responseCode;
  private boolean _hadSentAuthorization = false;
  private boolean _hadSentProxyAuthorization = false;

  private String _authorization;
  private String _proxyAuthorization;

  public HttpConnection(URL url,
                              String username,
                              String password)
    throws IOException
  {
    _URL = url;
    _username = username;
    _password = password;

    init();
  }

  public HttpConnection(URL url,
                              String username,
                              String password,
                              URL proxyURL,
                              String proxyUsername,
                              String proxyPassword,
                              String proxyType)
    throws IOException
  {
    _URL = url;
    _proxyURL = proxyURL;
    _proxyType = proxyType;

    _username = username;
    _password = password;
    _proxyUsername = proxyUsername;
    _proxyPassword = proxyPassword;

    init();
  }

  public void setConnectTimeout(int time)
  {
    _conn.setConnectTimeout(time);
  }

  public void setDoOutput(boolean doOutput)
  {
    _conn.setDoOutput(doOutput);
  }

  public void setInstanceFollowRedirects(boolean isToFollowRedirects)
  {
    _conn.setInstanceFollowRedirects(isToFollowRedirects);
  }

  public void setReadTimeout(int time)
  {
    _conn.setReadTimeout(time);
  }

  public void setRequestMethod(String method)
    throws ProtocolException
  {
    _conn.setRequestMethod(method);
  }

  public void setRequestProperty(String key, String value)
  {
    _conn.setRequestProperty(key, value);
  }

  private void init()
    throws IOException
  {
    Proxy proxy = Proxy.NO_PROXY;

    if (_proxyURL != null) {
      InetSocketAddress address
          = new InetSocketAddress(_proxyURL.getHost(), _proxyURL.getPort());

      proxy = new Proxy(Proxy.Type.valueOf(_proxyType), address);
    }

    _conn = (HttpURLConnection)_URL.openConnection(proxy);
  }

  /**
   * Handles the authentication for this connection.
   */
  public void authenticate()
    throws ConnectException, ProtocolException, SocketTimeoutException,
            IOException
  {
    Proxy proxy = Proxy.NO_PROXY;

    if (_proxyURL != null) {
      InetSocketAddress address
          = new InetSocketAddress(_proxyURL.getHost(), _proxyURL.getPort());

      proxy = new Proxy(Proxy.Type.valueOf(_proxyType), address);
    }

    HttpURLConnection headConn = (HttpURLConnection)_URL.openConnection(proxy);
    headConn.setRequestMethod("HEAD");

    if (_proxyAuthorization != null)
      headConn.setRequestProperty("Proxy-Authorization", _proxyAuthorization);

    if (_authorization != null)
      headConn.setRequestProperty("Authorization", _authorization);

    headConn.connect();

    int responseCode = headConn.getResponseCode();

    if (responseCode == HttpURLConnection.HTTP_PROXY_AUTH
        && _proxyAuthorization == null)
    {
      String header = headConn.getHeaderField("Proxy-Authenticate");

      _proxyAuthorization = getAuthorization(_URL,
                                            _conn.getRequestMethod(),
                                            header,
                                            "Proxy-Authorization",
                                            _proxyUsername,
                                            _proxyPassword);
      authenticate();
    }
    else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
        && _authorization == null)
    {
      String header = headConn.getHeaderField("WWW-Authenticate");

      _authorization = getAuthorization(_URL,
                                       _conn.getRequestMethod(),
                                       header,
                                       "Authorization",
                                       _username,
                                       _password);
      authenticate();
    }

    headConn.disconnect();
  }

  /**
   * Connects to the server.
   */
  public void connect()
    throws ConnectException, ProtocolException, SocketTimeoutException,
            IOException
  {
    if (_username != null || _proxyUsername != null)
      authenticate();

    if (_proxyAuthorization != null)
      _conn.setRequestProperty("Proxy-Authorization", _proxyAuthorization);
    if (_authorization != null)
      _conn.setRequestProperty("Authorization", _authorization);

    _conn.connect();
  }

  /**
   * Returns the authorization response.
   */
  private final String getAuthorization(URL url,
                                        String requestMethod,
                                        String header,
                                        String clientField,
                                        String username,
                                        String password)
    throws ConnectException, SocketTimeoutException, IOException
  {
    if (username == null || password == null)
      return "";

    String uri = url.getFile();
    if (uri.length() == 0)
      uri = "/";

    String auth = Authentication.getAuthorization(username,
                                                  password,
                                                  requestMethod,
                                                  uri,
                                                  header);

    return auth;
  }

  public int getContentLength()
  {
    return _conn.getContentLength();
  }

  public InputStream getErrorStream()
  {
    return _conn.getErrorStream();
  }

  public String getHeaderField(String key)
  {
    return _conn.getHeaderField(key);
  }

  public String getHeaderField(int i)
  {
    return _conn.getHeaderField(i);
  }

  public String getHeaderFieldKey(int i)
  {
    return _conn.getHeaderFieldKey(i);
  }

  public InputStream getInputStream()
    throws IOException
  {
    return _conn.getInputStream();
  }

  public OutputStream getOutputStream()
    throws IOException
  {
    return _conn.getOutputStream();
  }

  public int getResponseCode()
  {
    try {
      return _conn.getResponseCode();
    }
    catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  public void disconnect()
  {
    close();
  }

  public void close()
  {
    if (_conn != null)
      _conn.disconnect();
  }
}
