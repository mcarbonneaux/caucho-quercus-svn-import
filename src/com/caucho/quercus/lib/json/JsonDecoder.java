/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.quercus.lib.json;

import com.caucho.quercus.env.*;
import com.caucho.util.L10N;

class JsonDecoder {
  private static final L10N L = new L10N(JsonDecoder.class);

  private StringValue _str;
  private int _len;
  private int _offset;

  private boolean _isAssociative;

  public Value jsonDecode(Env env,
                          StringValue s,
                          boolean assoc)
  {
    _str = s;
    _len = _str.length();
    _offset = 0;

    _isAssociative = assoc;

    Value val = jsonDecodeImpl(env);

    // Should now be at end of string or have only white spaces left.
    skipWhitespace();
    
    if (_offset < _len)
      return errorReturn(env, "expected no more input");

    return val;
  }

  /**
   * Entry point to decode a JSON value.
   *
   * @return decoded PHP value 
   */
  private Value jsonDecodeImpl(Env env)
  {
    skipWhitespace();
    
    if (_offset >= _len)
      return errorReturn(env);
    
    char ch = _str.charAt(_offset);

    if (ch == '"') {
      return decodeString(env);
    }
    else if (ch == 't') {
      if (_offset + 3 < _len
          && _str.charAt(_offset + 1) == 'r'
          && _str.charAt(_offset + 2) == 'u'
          && _str.charAt(_offset + 3) == 'e') {
        _offset += 4;
        return BooleanValue.TRUE;
      }
      else
        return errorReturn(env, "expected 'true'");
    }
    else if (ch == 'f') {
      if (_offset + 4 < _len
          && _str.charAt(_offset + 1) == 'a'
          && _str.charAt(_offset + 2) == 'l'
          && _str.charAt(_offset + 3) == 's'
          && _str.charAt(_offset + 4) == 'e') {
        _offset += 5;
        return BooleanValue.FALSE;
      }
      else
        return errorReturn(env, "expected 'false'");
    }
    else if (ch == 'n') {
      if (_offset + 3 < _len
          && _str.charAt(_offset + 1) == 'u'
          && _str.charAt(_offset + 2) == 'l'
          && _str.charAt(_offset + 3) == 'l') {
        _offset += 4;
        return NullValue.NULL;
      }
      else
        return errorReturn(env, "expected 'null'");
    }
    else if (ch == '[') {
      return decodeArray(env);
    }
    else if (ch == '{') {
      return decodeObject(env);
    }
    else {
      if (ch == '-' || ('0' <= ch && ch <= '9'))
        return decodeNumber(env);
      else
        return errorReturn(env);
    }
  }

  /**
   * Checks to see if there is a valid number per JSON Internet Draft.
   */
  private Value decodeNumber(Env env)
  {
    StringBuilder sb = new StringBuilder();

    char ch;
    
    // (-)?
    if ((ch = _str.charAt(_offset)) == '-') {
      sb.append(ch);
      
      _offset++;
    }

    if (_offset >= _len)
      return errorReturn(env, "expected 1-9");

    ch = _str.charAt(_offset++);
    
    // (0) | ([1-9] [0-9]*)
    if (ch == '0') {
      sb.append(ch);
    }
    else if ('1' <= ch && ch <= '9') {
      sb.append(ch);

      while (_offset < _len
             && '0' <= (ch = _str.charAt(_offset)) && ch <= '9') {
        _offset++;
          
        sb.append(ch);
      }
    }

    int integerEnd = sb.length();

    // ((decimalPoint) [0-9]+)?
    if (_offset < _len && (ch = _str.charAt(_offset)) == '.') {
      _offset++;
      
      sb.append(ch);
      
      while (_offset < _len
             && '0' <= (ch = _str.charAt(_offset)) && ch <= '9') {
        _offset++;
        sb.append(ch);
      }
    }

    // ((e | E) (+ | -)? [0-9]+)
    if (_offset < _len && (ch = _str.charAt(_offset)) == 'e' || ch == 'E') {
      _offset++;
      
      sb.append(ch);

      if (_offset < _len && (ch = _str.charAt(_offset)) == '+' || ch == '-') {
        _offset++;
        
        sb.append(ch);
      }

      if (_offset < _len && '0' <= (ch = _str.charAt(_offset)) && ch <= '9') {
        _offset++;
        
        sb.append(ch);
        
        while (_offset < _len) {
          if ('0' <= (ch = _str.charAt(_offset)) && ch <= '9') {
            _offset++;
            
            sb.append(ch);
          }
          else
            break;
        }
      }
      else
        return errorReturn(env, "expected 0-9 exponent");
    }

    if (integerEnd != sb.length())
      return DoubleValue.create(Double.parseDouble(sb.toString()));
    else
      return LongValue.create(Long.parseLong(sb.toString()));
  }

  /**
   * Returns a non-associative PHP array.
   */
  private Value decodeArray(Env env)
  {
    ArrayValueImpl array = new ArrayValueImpl();

    _offset++;
    
    while (true) {
      skipWhitespace();
      
      if (_offset >= _len)
        return errorReturn(env, "expected either ',' or ']'");
      
      if (_str.charAt(_offset) == ']') {
        _offset++;
        break;
      }

      array.append(jsonDecodeImpl(env));

      skipWhitespace();

      if (_offset >= _len)
        return errorReturn(env, "expected either ',' or ']'");
      
      char ch = _str.charAt(_offset++);
      
      if (ch == ',') {
      }
      else if (ch == ']')
        break;
      else
        return errorReturn(env, "expected either ',' or ']'");
    }

    return array;
  }

  private Value decodeObject(Env env)
  {
    if (_isAssociative)
      return decodeObjectToArray(env);
    else
      return decodeObjectToObject(env);
  }

  /**
   * Returns a PHP associative array of JSON object.
   */
  private Value decodeObjectToArray(Env env)
  {
    ArrayValue array = new ArrayValueImpl();

    _offset++;
    
    while (true) {
      skipWhitespace();

      if (_offset >= _len || _str.charAt(_offset) == '}') {
        break;
      }

      Value name = jsonDecodeImpl(env);

      skipWhitespace();

      if (_offset >= _len || _str.charAt(_offset++) != ':')
        return errorReturn(env, "expected ':'");

      array.append(name, jsonDecodeImpl(env));

      skipWhitespace();

      char ch;
      
      if (_offset >= _len)
        return errorReturn(env, "expected either ',' or '}'");
      else if ((ch = _str.charAt(_offset++)) == ',') {
      }
      else if (ch == '}')
        break;
      else
        return errorReturn(env, "expected either ',' or '}'");
    }

    return array;
  }

  /**
   * Returns a PHP stdObject of JSON object.
   */
  private Value decodeObjectToObject(Env env)
  {
    ObjectValue object = env.createObject();

    _offset++;
    
    while (true) {
      skipWhitespace();

      if (_offset >= _len || _str.charAt(_offset) == '}')
        break;
      
      Value name = jsonDecodeImpl(env);

      skipWhitespace();

      if (_offset >= _len || _str.charAt(_offset++) != ':')
        return errorReturn(env, "expected ':'");

      object.putField(env, name.toString(), jsonDecodeImpl(env));

      skipWhitespace();

      char ch;
      
      if (_offset >= _len)
        return errorReturn(env, "expected either ',' or '}'");
      else if ((ch = _str.charAt(_offset++)) == ',') {
      }
      else if (ch == '}')
        break;
      else
        return errorReturn(env, "expected either ',' or '}'");
    }

    return object;
  }

  /**
   * Returns a PHP string.
   */
  private Value decodeString(Env env)
  {
    StringValue sb = env.createUnicodeBuilder();

    _offset++;
    
    while (_offset < _len) {
      char ch = _str.charAt(_offset++);
      
      switch (ch) {

        // Escaped Characters
      case '\\':
        if (_offset >= _len)
          return errorReturn(env, "invalid escape character");
        
        ch = _str.charAt(_offset++);
          
        switch (ch) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
          case 'U':
            int hex = 0;

            for (int i = 0; _offset < _len && i < 4; i++) {
              hex = hex << 4;
              ch = _str.charAt(_offset++);

              if ('0' <= ch && ch <= '9')
                hex += ch - '0';
              else if (ch >= 'a' && ch <= 'f')
                hex += ch - 'a' + 10;
              else if (ch >= 'A' && ch <= 'F')
                hex += ch - 'A' + 10;
              else
                return errorReturn(env, "invalid escaped hex character");
            }

            if (hex < 0x80)
              sb.append((char)hex);
            else if (hex < 0x800) {
              sb.append((char) (0xc0 + (hex >> 6)));
              sb.append((char) (0x80 + (hex & 0x3f)));
            }
            else {
              sb.append((char) (0xe0 + (hex >> 12)));
              sb.append((char) (0x80 + ((hex >> 6) & 0x3f)));
              sb.append((char) (0x80 + (hex & 0x3f)));
            }
          }
        
        break;

        case '"':
          return sb;

        default:
          sb.append(ch);
      }
    }

    return errorReturn(env, "error decoding string");
  }

  private Value errorReturn(Env env)
  {
    return errorReturn(env, null);
  }

  private Value errorReturn(Env env, String message)
  {
    int start;
    int end;

    if (_offset < _len) {
      start = _offset - 1;
      end = _offset;
    }
    else {
      start = _len - 1;
      end = _len;
    }

    String token = _str.substring(start, end).toString();

    if (message != null)
      env.warning(L.l("error parsing '{0}': {1}", token, message));
    else
      env.warning(L.l("error parsing '{0}'", token));

    return NullValue.NULL;
  }

  private void skipWhitespace()
  {
    while (_offset < _len) {
      char ch = _str.charAt(_offset);
      
      if (ch == ' ' ||
          ch == '\n' ||
          ch == '\r' ||
          ch == '\t')
        _offset++;
      else
        break;
    }
  }
}
