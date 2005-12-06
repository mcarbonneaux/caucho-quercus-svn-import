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

package com.caucho.php.lib;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

import java.util.logging.Logger;

import javax.servlet.http.Cookie;

import com.caucho.util.L10N;
import com.caucho.util.RandomUtil;
import com.caucho.util.Alarm;

import com.caucho.php.Php;

import com.caucho.php.module.PhpModule;
import com.caucho.php.module.AbstractPhpModule;
import com.caucho.php.module.Optional;

import com.caucho.php.env.Value;
import com.caucho.php.env.Env;
import com.caucho.php.env.NullValue;
import com.caucho.php.env.BooleanValue;
import com.caucho.php.env.ArrayValue;
import com.caucho.php.env.LongValue;
import com.caucho.php.env.DoubleValue;
import com.caucho.php.env.StringValue;
import com.caucho.php.env.VarMap;
import com.caucho.php.env.ObjectValue;
import com.caucho.php.env.ChainedMap;
import com.caucho.php.env.ResourceValue;

import com.caucho.php.program.PhpProgram;

import com.caucho.vfs.WriteStream;

/**
 * PHP class information
 */
public class PhpSessionModule extends AbstractPhpModule {
  private static final L10N L = new L10N(PhpSessionModule.class);
  private static final Logger log
    = Logger.getLogger(PhpSessionModule.class.getName());

  private static final HashMap<String,StringValue> _iniMap
    = new HashMap<String,StringValue>();

  /**
   * Returns the default php.ini values.
   */
  public Map<String,StringValue> getDefaultIni()
  {
    return _iniMap;
  }

  /**
   * Start the session
   */
  public String session_id(Env env)
  {
    Value sessionIdValue = env.getSpecialValue("caucho.session_id");

    if (sessionIdValue != null)
      return sessionIdValue.toString();
    else
      return "";
  }
  
  /**
   * Returns the object's class name
   */
  public Value session_name(Env env, @Optional String newValue)
  {
    Value value = env.getIni("session.name");

    if (newValue != null && ! newValue.equals(""))
      env.setIni("session.name", newValue);
      
    return value;
  }
  
  /**
   * Returns the session cache limiter value
   */
  public Value session_cache_limiter(Env env, @Optional String newValue)
  {
    Value value = env.getIni("session.cache_limiter");

    if (newValue != null && ! newValue.equals(""))
      env.setIni("session.cache_limiter", newValue);
      
    return value;
  }

  /**
   * Sets the session cookie parameters
   */
  public Value session_set_cookie_params(Env env,
					 long lifetime,
					 @Optional String path,
					 @Optional String domain,
					 @Optional boolean secure)
    throws Throwable
  {
    env.setIni("session.cookie_lifetime", String.valueOf(lifetime));

    if (path != null)
      env.setIni("session.cookie_path", path);

    if (domain != null)
      env.setIni("session.cookie_domain", domain);

    env.setIni("session.cookie_secure", secure ? "1" : "0");

    return NullValue.NULL;
  }

  /**
   * Start the session
   */
  public boolean session_start(Env env)
    throws Throwable
  {
    if (env.getSession() != null)
      return true;
    
    Value sessionIdValue = env.getSpecialValue("caucho.session_id");
    String sessionId = null;

    if (sessionIdValue != null && ! sessionIdValue.isNull()) {
      sessionId = sessionIdValue.toString();
    }
    else {
      String cookieName = env.getIni("session.name").toString();

      Cookie []cookies = env.getRequest().getCookies();

      for (int i = 0; cookies != null && i < cookies.length; i++) {
	if (cookies[i].getName().equals(cookieName)) {
	  sessionId = cookies[i].getValue();
	}
      }

      if (sessionId == null) {
	sessionId = generateSessionId(env);

	Cookie cookie = new Cookie(cookieName, sessionId);
	cookie.setVersion(1);

	Value path = env.getIni("session.cookie_path");
	cookie.setPath(path.toString());

	Value maxAge = env.getIni("session.cookie_lifetime");
	if (maxAge.toInt() != 0)
	  cookie.setMaxAge(maxAge.toInt());

	Value domain = env.getIni("session.cookie_domain");
	cookie.setDomain(domain.toString());

	Value secure = env.getIni("session.cookie_secure");
	cookie.setSecure(secure.toBoolean());
	
	env.getResponse().addCookie(cookie);

	String cacheLimiter = env.getIni("session.cache_limiter").toString();
	
	if ("private".equals(cacheLimiter))
	  env.getResponse().addHeader("Cache-Control", "private");
	else if ("nocache".equals(cacheLimiter))
	  env.getResponse().addHeader("Cache-Control", "nocache");
      }
    }
    
    env.setSpecialValue("caucho.session_id", new StringValue(sessionId));
    env.createSession(sessionId);

    return true;
  }

  private static String generateSessionId(Env env)
  {
    StringBuilder sb = new StringBuilder();

    long random = RandomUtil.getRandomLong();
    long date = Alarm.getCurrentTime();

    for (int i = 0; i < (64 + 5) / 6; i++) {
      sb.append(encode(random >> (i * 6)));
    }

    for (int i = 0; i < 8; i++) {
      sb.append(encode(date >> (i * 6)));
    }

    return sb.toString();
  }

  /**
   * Converts an integer to a printable character
   */
  private static char encode(long code)
  {
    code = code & 0x3f;
    
    if (code < 26)
      return (char) ('a' + code);
    else if (code < 52)
      return (char) ('A' + code - 26);
    else if (code < 62)
      return (char) ('0' + code - 52);
    else if (code == 62)
      return '_';
    else
      return '-';
  }

  static {
    addIni(_iniMap, "session.save_path", "", PHP_INI_ALL);
    addIni(_iniMap, "session.name", "PHPSESSID", PHP_INI_ALL);
    addIni(_iniMap, "session.save_handler", "files", PHP_INI_ALL);
    addIni(_iniMap, "session.auto_start", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.gc_probability_start", "1", PHP_INI_ALL);
    addIni(_iniMap, "session.gc_divisor", "100", PHP_INI_ALL);
    addIni(_iniMap, "session.gc_maxlifetime", "1440", PHP_INI_ALL);
    addIni(_iniMap, "session.serialize_handler", "php", PHP_INI_ALL);
    addIni(_iniMap, "session.cookie_lifetime", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.cookie_path", "/", PHP_INI_ALL);
    addIni(_iniMap, "session.cookie_domain", "", PHP_INI_ALL);
    addIni(_iniMap, "session.cookie_secure", "", PHP_INI_ALL);
    addIni(_iniMap, "session.use_cookies", "1", PHP_INI_ALL);
    addIni(_iniMap, "session.use_only_cookies", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.referer_check", "", PHP_INI_ALL);
    addIni(_iniMap, "session.entropy_file", "", PHP_INI_ALL);
    addIni(_iniMap, "session.entropy_length", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.cache_limiter", "nocache", PHP_INI_ALL);
    addIni(_iniMap, "session.cache_exire", "180", PHP_INI_ALL);
    addIni(_iniMap, "session.use_trans_sid", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.bug_compat_42", "1", PHP_INI_ALL);
    addIni(_iniMap, "session.bug_compat_warn", "1", PHP_INI_ALL);
    addIni(_iniMap, "session.hash_function", "0", PHP_INI_ALL);
    addIni(_iniMap, "session.hash_bits_per_character", "4", PHP_INI_ALL);
    addIni(_iniMap, "user_rewriter.tags", "a=href,area=href,frame=src,form=,fieldset=", PHP_INI_ALL);
  }
}
