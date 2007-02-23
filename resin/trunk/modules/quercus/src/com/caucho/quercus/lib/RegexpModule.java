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

package com.caucho.quercus.lib;

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.QuercusModuleException;
import com.caucho.quercus.QuercusRuntimeException;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.Reference;
import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHP regexp routines.
 */
public class RegexpModule
  extends AbstractQuercusModule
{
  private static final L10N L = new L10N(RegexpModule.class);

  private static final int REGEXP_EVAL = 0x01;

  public static final int PREG_PATTERN_ORDER = 0x01;
  public static final int PREG_SET_ORDER = 0x02;
  public static final int PREG_OFFSET_CAPTURE = 0x04;

  public static final int PREG_SPLIT_NO_EMPTY = 0x01;
  public static final int PREG_SPLIT_DELIM_CAPTURE = 0x02;
  public static final int PREG_SPLIT_OFFSET_CAPTURE = 0x04;

  public static final int PREG_GREP_INVERT = 1;

  public static final boolean [] PREG_QUOTE = new boolean[256];

  private static final LruCache<StringValue, Pattern> _patternCache
    = new LruCache<StringValue, Pattern>(1024);

  private static final LruCache<StringValue, ArrayList<Replacement>> _replacementCache
    = new LruCache<StringValue, ArrayList<Replacement>>(1024);

  private static final HashMap<String, Value> _constMap
    = new HashMap<String, Value>();

  public String []getLoadedExtensions()
  {
    return new String[] { "pcre" };
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value ereg(Env env,
                           StringValue pattern,
                           StringValue string,
                           @Optional @Reference Value regsV)
  {
    return ereg(env, pattern, string, regsV, 0);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value eregi(Env env,
                            StringValue pattern,
                            StringValue string,
                            @Optional @Reference Value regsV)
  {
    return ereg(env, pattern, string, regsV, Pattern.CASE_INSENSITIVE);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  private static Value ereg(Env env,
                            StringValue rawPattern,
                            StringValue string,
                            Value regsV,
                            int flags)
  {
    String cleanPattern = cleanRegexp(rawPattern, false);

    Pattern pattern = Pattern.compile(cleanPattern, flags);
    Matcher matcher = pattern.matcher(string);

    if (! (matcher.find())) {
      return BooleanValue.FALSE;
    }

    if (regsV != null && ! (regsV instanceof NullValue)) {
      ArrayValue regs = new ArrayValueImpl();
      regsV.set(regs);

      regs.put(LongValue.ZERO, new StringValueImpl(matcher.group()));
      int count = matcher.groupCount();

      for (int i = 1; i <= count; i++) {
        String group = matcher.group(i);

        Value value;
        if (group == null)
          value = BooleanValue.FALSE;
        else
          value = new StringValueImpl(group);

        regs.put(new LongValue(i), value);
      }

      int len = matcher.end() - matcher.start();

      if (len == 0)
        return LongValue.ONE;
      else
        return new LongValue(len);
    }
    else {
      return LongValue.ONE;
    }
  }

  /**
   * Returns the index of the first match.
   *
   * php/151u
   * The array that preg_match (PHP 5) returns does not have trailing unmatched
   * groups. Therefore, an unmatched group should not be added to the array
   * unless a matched group appears after it.  A couple applications like
   * Gallery2 expect this behavior in order to function correctly.
   * 
   * Only preg_match and preg_match_all(PREG_SET_ORDER) exhibits this odd
   * behavior.
   *
   * @param env the calling environment
   */
  public static int preg_match(Env env,
                               StringValue patternString,
                               StringValue string,
                               @Optional @Reference Value matchRef,
                               @Optional int flags,
                               @Optional int offset)
  {
    if (patternString.length() < 2) {
      env.warning(L.l("Regexp pattern must have opening and closing delimiters"));
      return 0;
    }

    NamedPatterns namedPatterns = new NamedPatterns(patternString);
    patternString = namedPatterns.cleanPattern(patternString);

    Pattern pattern = compileRegexp(patternString);
    Matcher matcher = pattern.matcher(string);

    ArrayValue regs;

    if (matchRef instanceof DefaultValue)
      regs = null;
    else
      regs = new ArrayValueImpl();

    if (! (matcher.find(offset))) {
      matchRef.set(regs);
      return 0;
    }

    boolean isOffsetCapture = (flags & PREG_OFFSET_CAPTURE) != 0;

    if (regs != null) {
      if (isOffsetCapture) {
        ArrayValueImpl part = new ArrayValueImpl();
        part.append(new StringValueImpl(matcher.group()));
        part.append(new LongValue(matcher.start()));

        regs.put(LongValue.ZERO, part);
      }
      else
        regs.put(LongValue.ZERO, new StringValueImpl(matcher.group()));

      int count = matcher.groupCount();

      for (int i = 1; i <= count; i++) {
        String group = matcher.group(i);

        if (group == null)
          continue;

        if (isOffsetCapture) {
          // php/151u
          // add unmatched groups first
          for (int j = regs.getSize(); j < i; j++) {
            ArrayValue part = new ArrayValueImpl();

            part.append(StringValue.EMPTY);
            part.append(LongValue.MINUS_ONE);

            regs.put(new LongValue(j), part);
          }

          ArrayValueImpl part = new ArrayValueImpl();
          part.append(new StringValueImpl(group));
          part.append(new LongValue(matcher.start(i)));

          regs.put(new LongValue(i), part);
          
          Value name = namedPatterns.get(i);
          if (name != null)
            regs.put(name, part);
        }
        else {
          // php/151u
          // add unmatched groups first
          for (int j = regs.getSize(); j < i; j++) {
            regs.put(new LongValue(j), StringValue.EMPTY);
          }

          StringValue match = new StringValueImpl(group);
          
          regs.put(new LongValue(i), match);
          
          Value name = namedPatterns.get(i);
          if (name != null)
            regs.put(name, match);
        }
      }

      matchRef.set(regs);
    }

    return 1;
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static int preg_match_all(Env env,
                                   StringValue patternString,
                                   StringValue subject,
                                   @Reference Value matchRef,
                                   @Optional("PREG_PATTERN_ORDER") int flags,
                                   @Optional int offset)
  {
    if (patternString.length() < 2) {
      env.warning(L.l("Pattern must have at least opening and closing delimiters"));
      return 0;
    }

    if ((flags & PREG_PATTERN_ORDER) == 0) {
      // php/152m
      if ((flags & PREG_SET_ORDER) == 0) {
        flags = flags | PREG_PATTERN_ORDER;
      }
    }
    else {
      if ((flags & PREG_SET_ORDER) != 0) {
        env.warning((L.l("Cannot combine PREG_PATTER_ORDER and PREG_SET_ORDER")));
        return 0;
      }
    }

    NamedPatterns namedPatterns = new NamedPatterns(patternString);
    patternString = namedPatterns.getCleanedPattern();

    Pattern pattern = compileRegexp(patternString);

    ArrayValue matches;

    if (matchRef instanceof ArrayValue)
      matches = (ArrayValue) matchRef;
    else
      matches = new ArrayValueImpl();

    matches.clear();

    matchRef.set(matches);

    if ((flags & PREG_PATTERN_ORDER) != 0) {
      return pregMatchAllPatternOrder(env, pattern, subject,
				      matches, namedPatterns, flags, offset);
    }
    else if ((flags & PREG_SET_ORDER) != 0) {
      return pregMatchAllSetOrder(env, pattern, subject,
				  matches, flags, offset);
    }
    else
      throw new UnsupportedOperationException();
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static int pregMatchAllPatternOrder(Env env,
					     Pattern pattern,
					     StringValue subject,
					     ArrayValue matches,
                         NamedPatterns namedPatterns,
					     int flags,
					     int offset)
  {
    Matcher matcher = pattern.matcher(subject);

    int groupCount = matcher.groupCount();

    ArrayValue []matchList = new ArrayValue[groupCount + 1];

    for (int j = 0; j <= groupCount; j++) {
      ArrayValue values = new ArrayValueImpl();
      matches.put(values);
      matchList[j] = values;
      
      Value patternName = namedPatterns.get(j);
      
      // XXX: named subpatterns causing conflicts with array indexes?
      if (patternName != null)
        matches.put(patternName, values);
    }

    if (! (matcher.find())) {
      return 0;
    }

    int count = 0;

    do {
      count++;

      for (int j = 0; j <= groupCount; j++) {
	ArrayValue values = matchList[j];

	int start = matcher.start(j);
	int end = matcher.end(j);
	  
	StringValue groupValue = subject.substring(start, end);

	Value result = NullValue.NULL;

	if (groupValue != null) {
	  if ((flags & PREG_OFFSET_CAPTURE) != 0) {
	    result = new ArrayValueImpl();
	    result.put(groupValue);
	    result.put(LongValue.create(start));
	  } else {
	    result = groupValue;
	  }
	}

	values.put(result);
      }
    } while (matcher.find());

    return count;
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  private static int pregMatchAllSetOrder(Env env,
					  Pattern pattern,
					  StringValue subject,
					  ArrayValue matches,
					  int flags,
					  int offset)
  {
    Matcher matcher = pattern.matcher(subject);

    if (! (matcher.find())) {
      return 0;
    }

    int count = 0;

    do {
      count++;

      ArrayValue matchResult = new ArrayValueImpl();
      matches.put(matchResult);

      for (int i = 0; i <= matcher.groupCount(); i++) {
        int start = matcher.start(i);
        int end = matcher.end(i);

        // group is unmatched, skip
        if (end - start <= 0)
          continue;
        
        StringValue groupValue = subject.substring(start, end);

        Value result = NullValue.NULL;

        if (groupValue != null) {

          if ((flags & PREG_OFFSET_CAPTURE) != 0) {
            
            // php/152n
            // add unmatched groups first
            for (int j = matchResult.getSize(); j < i; j++) {
              ArrayValue part = new ArrayValueImpl();

              part.append(StringValue.EMPTY);
              part.append(LongValue.MINUS_ONE);

              matchResult.put(LongValue.create(j), part);
            }
            
            
            result = new ArrayValueImpl();
            result.put(groupValue);
            result.put(LongValue.create(start));
          } else {
            
            
            // php/
            // add unmatched groups that was skipped
            for (int j = matchResult.getSize(); j < i; j++) {
              matchResult.put(LongValue.create(j), StringValue.EMPTY);
            }
            
            result = groupValue;
          }
        }
        
        matchResult.put(result);
      }
    } while (matcher.find());

    return count;
  }

  /**
   * Quotes regexp values
   */
  public static String preg_quote(StringValue string,
                                  @Optional String delim)
  {
    StringBuilder sb = new StringBuilder();

    boolean []extra = null;

    if (delim != null && ! delim.equals("")) {
      extra = new boolean[256];

      for (int i = 0; i < delim.length(); i++)
        extra[delim.charAt(i)] = true;
    }

    int length = string.length();
    for (int i = 0; i < length; i++) {
      char ch = string.charAt(i);

      if (ch >= 256)
        sb.append(ch);
      else if (PREG_QUOTE[ch]) {
        sb.append('\\');
        sb.append(ch);
      }
      else if (extra != null && extra[ch]) {
        sb.append('\\');
        sb.append(ch);
      }
      else
        sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Loops through subject if subject is array of strings
   *
   * @param env
   * @param pattern string or array
   * @param replacement string or array
   * @param subject string or array
   * @param limit
   * @param count
   * @return
   */
  public static Value preg_replace(Env env,
                                   Value pattern,
                                   Value replacement,
                                   Value subject,
                                   @Optional("-1") long limit,
                                   @Optional @Reference Value count)
  {
    if (subject instanceof ArrayValue) {
      ArrayValue result = new ArrayValueImpl();

      for (Value value : ((ArrayValue) subject).values()) {
        result.put(pregReplace(env,
                               pattern,
                               replacement,
                               value.toStringValue(),
                               limit,
                               count));
      }

      return result;

    }
    else if (subject.isset()) {
      return pregReplace(env, pattern, replacement, subject.toStringValue(),
			 limit, count);
    } else
      return StringValue.EMPTY;

  }

  /**
   * Replaces values using regexps
   */
  private static Value pregReplace(Env env,
                                   Value patternValue,
                                   Value replacement,
                                   StringValue subject,
                                   @Optional("-1") long limit,
                                   Value countV)
  {
    StringValue string = subject;

    if (limit < 0)
      limit = Long.MAX_VALUE;

    if (patternValue.isArray() && replacement.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;
      ArrayValue replacementArray = (ArrayValue) replacement;

      Iterator<Value> patternIter = patternArray.values().iterator();
      Iterator<Value> replacementIter = replacementArray.values().iterator();

      while (patternIter.hasNext() && replacementIter.hasNext()) {
        string = pregReplaceString(env,
                                   patternIter.next().toStringValue(),
                                   replacementIter.next().toStringValue(),
                                   string,
                                   limit,
                                   countV);
      }
    } else if (patternValue.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;

      for (Value value : patternArray.values()) {
        string = pregReplaceString(env,
                                   value.toStringValue(),
                                   replacement.toStringValue(),
                                   string,
                                   limit,
                                   countV);
      }
    } else {
      return pregReplaceString(env,
			       patternValue.toStringValue(),
			       replacement.toStringValue(),
			       string,
			       limit,
			       countV);
    }

    return string;
  }

  /**
   * replaces values using regexps and callback fun
   * @param env
   * @param patternString
   * @param fun
   * @param subject
   * @param limit
   * @param countV
   * @return subject with everything replaced
   */
  private static StringValue pregReplaceCallbackImpl(Env env,
						     StringValue patternString,
						     Callback fun,
						     StringValue subject,
						     long limit,
						     Value countV)
  {

    long numberOfMatches = 0;

    if (limit < 0)
      limit = Long.MAX_VALUE;

    Pattern pattern = compileRegexp(patternString);

    Matcher matcher = pattern.matcher(subject);

    StringBuilderValue result = new StringBuilderValue();
    int tail = 0;

    while (matcher.find() && numberOfMatches < limit) {
      // Increment countV (note: if countV != null, then it should be a Var)
      if ((countV != null) && (countV instanceof Var)) {
        long count = ((Var) countV).getRawValue().toLong();
        countV.set(LongValue.create(count + 1));
      }

      if (tail < matcher.start())
        result.append(subject.substring(tail, matcher.start()));

      ArrayValue regs = new ArrayValueImpl();

      for (int i = 0; i <= matcher.groupCount(); i++) {
        String group = matcher.group(i);

        if (group != null)
          regs.put(new StringValueImpl(group));
        else
          regs.put(StringValue.EMPTY);
      }

      Value replacement = fun.call(env, regs);

      result.append(replacement);

      tail = matcher.end();

      numberOfMatches++;
    }

    if (tail < subject.length())
      result.append(subject.substring(tail));

    return result;
  }

  /**
   * Replaces values using regexps
   */
  private static StringValue pregReplaceString(Env env,
					       StringValue patternString,
					       StringValue replacement,
					       StringValue subject,
					       long limit,
					       Value countV)
  {
    Pattern pattern = compileRegexp(patternString);

    // check for e modifier in patternString
    int patternFlags = regexpFlags(patternString);
    boolean isEval = (patternFlags & REGEXP_EVAL) != 0;

    ArrayList<Replacement> replacementProgram
      = _replacementCache.get(replacement);

    if (replacementProgram == null) {
      replacementProgram = compileReplacement(env, replacement, isEval);
      _replacementCache.put(replacement, replacementProgram);
    }

    return pregReplaceStringImpl(env,
                                 pattern,
                                 replacementProgram,
                                 subject,
                                 limit,
                                 countV,
				 isEval);
  }

  /**
   * Replaces values using regexps
   */
  public static Value ereg_replace(Env env,
                                   StringValue patternString,
                                   StringValue replacement,
                                   StringValue subject)
  {
    Pattern pattern = Pattern.compile(cleanRegexp(patternString, false));

    ArrayList<Replacement> replacementProgram
      = _replacementCache.get(replacement);

    if (replacementProgram == null) {
      replacementProgram = compileReplacement(env, replacement, false);
      _replacementCache.put(replacement, replacementProgram);
    }

    return pregReplaceStringImpl(env,
				 pattern,
				 replacementProgram,
				 subject,
				 -1,
				 NullValue.NULL,
				 false);
  }

  /**
   * Replaces values using regexps
   */
  public static Value eregi_replace(Env env,
                                    StringValue patternString,
                                    StringValue replacement,
                                    StringValue subject)
  {
    Pattern pattern = Pattern.compile(cleanRegexp(patternString, false),
                                      Pattern.CASE_INSENSITIVE);

    ArrayList<Replacement> replacementProgram
      = _replacementCache.get(replacement);

    if (replacementProgram == null) {
      replacementProgram = compileReplacement(env, replacement, false);
      _replacementCache.put(replacement, replacementProgram);
    }

    return pregReplaceStringImpl(env, pattern, replacementProgram,
				 subject, -1, NullValue.NULL, false);
  }

  /**
   * Replaces values using regexps
   */
  private static StringValue pregReplaceStringImpl(Env env,
						   Pattern pattern,
						   ArrayList<Replacement> replacementList,
						   StringValue subject,
						   long limit,
						   Value countV,
						   boolean isEval)
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    int length = subject.length();

    Matcher matcher = pattern.matcher(subject);

    StringBuilderValue result = null;
    int tail = 0;

    int replacementLen = replacementList.size();

    while (matcher.find() && limit-- > 0) {
      if (result == null)
	result = new StringBuilderValue();
      
      // Increment countV (note: if countV != null, then it should be a Var)
      if ((countV != null) && (countV instanceof Var)) {
        countV.set(LongValue.create(countV.toLong() + 1));
      }

      // append all text up to match
      if (tail < matcher.start())
        result.append(subject, tail, matcher.start());

      // if isEval then append replacement evaluated as PHP code
      // else append replacement string
      if (isEval) {
        StringBuilderValue evalString = new StringBuilderValue();

        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementList.get(i);

          replacement.eval(evalString, subject, matcher);
        }

	try {
	  result.append(env.evalCode(evalString.toString()));
	} catch (IOException e) {
	  throw new QuercusException(e);
	}
      } else {
        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementList.get(i);

          replacement.eval(result, subject, matcher);
        }
      }

      tail = matcher.end();
    }

    if (result == null)
      return subject;
    
    if (tail < length)
      result.append(subject, tail, length);

    return result;
  }

  /**
   * Loops through subject if subject is array of strings
   *
   * @param env
   * @param pattern
   * @param fun
   * @param subject
   * @param limit
   * @param count
   * @return
   */
  public static Value preg_replace_callback(Env env,
                                            Value pattern,
                                            Callback fun,
                                            Value subject,
                                            @Optional("-1") long limit,
                                            @Optional @Reference Value count)
  {
    if (subject instanceof ArrayValue) {
      ArrayValue result = new ArrayValueImpl();

      for (Value value : ((ArrayValue) subject).values()) {
        result.put(pregReplaceCallback(env,
                                       pattern.toStringValue(),
                                       fun,
                                       value.toStringValue(),
                                       limit,
                                       count));
      }

      return result;

    } else if (subject instanceof StringValue) {
      return pregReplaceCallback(env,
                                 pattern.toStringValue(),
                                 fun,
                                 subject.toStringValue(),
                                 limit,
                                 count);
    } else {
      return NullValue.NULL;
    }
  }

  /**
   * Replaces values using regexps
   */
  private static Value pregReplaceCallback(Env env,
                                           Value patternValue,
                                           Callback fun,
                                           StringValue subject,
                                           @Optional("-1") long limit,
                                           @Optional @Reference Value countV)
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    if (patternValue.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;

      for (Value value : patternArray.values()) {
        subject = pregReplaceCallbackImpl(env,
                                          value.toStringValue(),
                                          fun,
                                          subject,
                                          limit,
                                          countV);
      }

      return subject;

    } else if (patternValue instanceof StringValue) {
      return pregReplaceCallbackImpl(env,
				     patternValue.toStringValue(),
				     fun,
				     subject,
				     limit,
				     countV);
    } else {
      return NullValue.NULL;
    }
  }

  /**
   * Returns array of substrings or
   * of arrays ([0] => substring [1] => offset) if
   * PREG_SPLIT_OFFSET_CAPTURE is set
   *
   * @param env the calling environment
   */
  public static Value preg_split(Env env,
                                 StringValue patternString,
                                 StringValue string,
                                 @Optional("-1") long limit,
                                 @Optional int flags)
  {
    if (limit <= 0)
      limit = Long.MAX_VALUE;

    Pattern pattern = compileRegexp(patternString);
    Matcher matcher = pattern.matcher(string);

    ArrayValue result = new ArrayValueImpl();

    int head = 0;
    long count = 0;
    
    boolean allowEmpty = (flags & PREG_SPLIT_NO_EMPTY) == 0;
    boolean isCaptureOffset = (flags & PREG_SPLIT_OFFSET_CAPTURE) != 0; 
    boolean isCaptureDelim = (flags & PREG_SPLIT_DELIM_CAPTURE) != 0;

    while (matcher.find()) {
      int startPosition = head;
      StringValue unmatched;

      // Get non-matching sequence
      if (count == limit - 1) {
        unmatched = string.substring(head);
        head = string.length();
      }
      else {
        unmatched = string.substring(head, matcher.start());
        head = matcher.end();
      }

      // Append non-matching sequence
      if (unmatched.length() != 0 || allowEmpty) {
        if (isCaptureOffset) {
          ArrayValue part = new ArrayValueImpl();

          part.put(unmatched);
          part.put(LongValue.create(startPosition));

          result.put(part);
        }
        else {
          result.put(unmatched);
        }

        count++;
      }
 
      if (count == limit)
        break;

      // Append parameterized delimiters
      if (isCaptureDelim) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
          int start = matcher.start(i);
          int end = matcher.end(i);

          if ((start != -1 && end - start > 0) || allowEmpty) {

            StringValue groupValue;
            if (start < 0)
              groupValue = StringValue.EMPTY;
            else
              groupValue = string.substring(start, end);

            if (isCaptureOffset) {
              ArrayValue part = new ArrayValueImpl();

              part.put(groupValue);
              part.put(LongValue.create(startPosition));

              result.put(part);
            }
            else
              result.put(groupValue);
          }
        }
      }
    }

    // Append non-matching sequence at the end
    if (count < limit && (head < string.length() || allowEmpty)) {
      if (isCaptureOffset) {
        ArrayValue part = new ArrayValueImpl();

        part.put(string.substring(head));
        part.put(LongValue.create(head));

        result.put(part);
      }
      else
        result.put(string.substring(head));
    }

    return result;

/*
    while ((matcher.find()) && (count < limit)) {

      StringValue value;

      int startPosition = head;

      if (head != matcher.start() || isAllowEmpty) {
        // If at limit, then just output the rest of string
        if (count == limit - 1) {

          value = string.substring(head);
          head = string.length();
        } else {
          value = string.substring(head, matcher.start());
          head = matcher.end();
        }

        if ((flags & PREG_SPLIT_OFFSET_CAPTURE) != 0) {
          ArrayValue part = new ArrayValueImpl();
          part.put(value);
          part.put(LongValue.create(startPosition));

          result.put(part);
        } else {
          result.put(value);
        }

        count++;
      } else
        head = matcher.end();

      if ((flags & PREG_SPLIT_DELIM_CAPTURE) != 0) {
	for (int i = 1; i <= matcher.groupCount(); i++) {
	  String group = matcher.group(i);
	  Value groupValue;

	  if (group != null)
	    groupValue = new StringValueImpl(group);
	  else
	    groupValue = StringValue.EMPTY;

          if ((flags & PREG_SPLIT_OFFSET_CAPTURE) != 0) {
            ArrayValue part = new ArrayValueImpl();
            part.put(groupValue);
            part.put(LongValue.create(matcher.start()));

            result.put(part);
          } else {
	    result.put(groupValue);
          }
        }
      }
    }

    if (head == string.length() && ! isAllowEmpty) {
    }
    else if ((head <= string.length()) && (count != limit)) {
      if ((flags & PREG_SPLIT_OFFSET_CAPTURE) != 0) {
        ArrayValue part = new ArrayValueImpl();
        part.put(string.substring(head));
        part.put(LongValue.create(head));

        result.put(part);
      } else {
        result.put(string.substring(head));
      }
    }

    return result;
*/
  }

  /**
   * Makes a regexp for a case-insensitive match.
   */
  public static String sql_regcase(String string)
  {
    StringBuilder sb = new StringBuilder();

    int len = string.length();
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      if (Character.isLowerCase(ch)) {
	sb.append('[');
	sb.append(Character.toUpperCase(ch));
	sb.append(ch);
	sb.append(']');
      }
      else if (Character.isUpperCase(ch)) {
	sb.append('[');
	sb.append(ch);
	sb.append(Character.toLowerCase(ch));
	sb.append(']');
      }
      else
	sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value split(Env env,
                            StringValue patternString,
                            StringValue string,
                            @Optional("-1") long limit)
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    String cleanRegexp = cleanRegexp(patternString, false);

    Pattern pattern = Pattern.compile(cleanRegexp);

    ArrayValue result = new ArrayValueImpl();

    Matcher matcher = pattern.matcher(string);
    long count = 0;
    int head = 0;

    while ((matcher.find()) && (count < limit)) {
      StringValue value;
      if (count == limit - 1) {
        value = string.substring(head);
        head = string.length();
      } else {
        value = string.substring(head, matcher.start());
        head = matcher.end();
      }

      result.put(value);

      count++;
    }

    if ((head <= string.length() && (count != limit))) {
      result.put(string.substring(head));
    }

    return result;
  }

  /**
   * Returns an array of all the values that matched the given pattern if the
   * flag no flag is passed.  Otherwise it will return an array of all the
   * values that did not match.
   *
   * @param patternString the pattern
   * @param input the array to check the pattern against
   * @param flag 0 for matching and 1 for elements that do not match
   * @return an array of either matching elements are non-matching elements
   */
  public static ArrayValue preg_grep(Env env,
                                     StringValue patternString,
                                     ArrayValue input,
                                     @Optional("0") int flag)
  {
    // php/151b

    Pattern pattern = compileRegexp(patternString);

    Matcher matcher = null;

    ArrayValue matchArray = new ArrayValueImpl();

    for (Map.Entry<Value, Value> entry : input.entrySet()) {
      Value entryValue = entry.getValue();
      Value entryKey = entry.getKey();

      matcher = pattern.matcher(entryValue.toString());

      boolean found = matcher.find();

      if (!found && (flag == PREG_GREP_INVERT))
        matchArray.append(entryKey, entryValue);
      else if (found && (flag != PREG_GREP_INVERT))
        matchArray.append(entryKey, entryValue);
    }

    return matchArray;
  }

  /**
   * Returns an array of strings produces from splitting the passed string
   * around the provided pattern.  The pattern is case insensitive.
   *
   * @param patternString the pattern
   * @param string the string to split
   * @param limit if specified, the maximum number of elements in the array
   * @return an array of strings split around the pattern string
   */
  public static ArrayValue spliti(Env env,
                                  StringValue patternString,
                                  StringValue string,
                                  @Optional("-1") long limit)
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    // php/151c

    String cleanRegexp = cleanRegexp(patternString, false);

    Pattern pattern = Pattern.compile(cleanRegexp, Pattern.CASE_INSENSITIVE);

    ArrayValue result = new ArrayValueImpl();

    Matcher matcher = pattern.matcher(string);
    long count = 0;
    int head = 0;

    while ((matcher.find()) && (count < limit)) {
      StringValue value;
      if (count == limit - 1) {
        value = string.substring(head);
        head = string.length();
      } else {
        value = string.substring(head, matcher.start());
        head = matcher.end();
      }

      result.put(value);

      count++;
    }

    if ((head <= string.length()) && (count != limit)) {
      result.put(string.substring(head));
    }

    return result;
  }

  private static Pattern compileRegexp(StringValue rawRegexp)
  {
    Pattern pattern = _patternCache.get(rawRegexp);

    if (pattern != null)
      return pattern;

    if (rawRegexp.length() < 2) {
      throw new IllegalStateException(L.l(
        "Can't find delimiters in regexp '{0}'.",
        rawRegexp));
    }

    char delim = rawRegexp.charAt(0);

    if (delim == '{')
      delim = '}';
    else if (delim == '[')
      delim = ']';
    else if (delim == '(')
      delim = ')';

    int tail = rawRegexp.lastIndexOf(delim);

    if (tail <= 0)
      throw new IllegalStateException(L.l(
        "Can't find second {0} in regexp '{1}'.",
        String.valueOf((char) delim),
        rawRegexp));

    int len = rawRegexp.length();

    int flags = 0;
    boolean isExt = false;
    boolean isGreedy = true;

    for (int i = tail + 1; i < len; i++) {
      char ch = rawRegexp.charAt(i);

      switch (ch) {
      case 'i':
        flags |= Pattern.CASE_INSENSITIVE;
        break;
      case 's':
        flags |= Pattern.DOTALL;
        break;
      case 'x':
        flags |= Pattern.COMMENTS;
        break;
      case 'm':
        flags |= Pattern.MULTILINE;
        break;
      case 'U':
        isGreedy = false;
        break;
      }
    }

    StringValue regexp = rawRegexp.substring(1, tail);

    String cleanRegexp = cleanRegexp(regexp, (flags & Pattern.COMMENTS) != 0);

    if (! isGreedy)
      cleanRegexp = toNonGreedy(cleanRegexp);
    
    pattern = Pattern.compile(cleanRegexp, flags);

    _patternCache.put(rawRegexp, pattern);

    return pattern;
  }

  private static int regexpFlags(StringValue rawRegexp)
  {
    char delim = rawRegexp.charAt(0);
    if (delim == '{')
      delim = '}';
    else if (delim == '[')
      delim = ']';

    int tail = rawRegexp.lastIndexOf(delim);

    if (tail <= 0)
      throw new IllegalStateException(L.l(
        "Can't find second {0} in regexp '{1}'.",
        String.valueOf((char) delim),
        rawRegexp));

    int len = rawRegexp.length();

    int flags = 0;

    for (int i = tail + 1; i < len; i++) {
      char ch = rawRegexp.charAt(i);

      switch (ch) {
      case 'e':
        flags |= REGEXP_EVAL;
        break;
      }
    }

    return flags;
  }

  private static ArrayList<Replacement>
    compileReplacement(Env env, StringValue replacement, boolean isEval)
  {
    ArrayList<Replacement> program = new ArrayList<Replacement>();
    StringBuilder text = new StringBuilder();

    for (int i = 0; i < replacement.length(); i++) {
      char ch = replacement.charAt(i);

      if ((ch == '\\' || ch == '$') && i + 1 < replacement.length()) {
        char digit;

        if ('0' <= (digit = replacement.charAt(i + 1)) && digit <= '9') {
          int group = digit - '0';
          i++;

          if (i + 1 < replacement.length() &&
              '0' <= (digit = replacement.charAt(i + 1)) && digit <= '9') {
            group = 10 * group + digit - '0';
            i++;
          }

          if (text.length() > 0)
            program.add(new TextReplacement(text));

	  if (isEval)
	    program.add(new GroupEscapeReplacement(group));
	  else
	    program.add(new GroupReplacement(group));

          text.setLength(0);
        }
	else if (ch == '\\') {
          i++;

	  if (digit != '\\') {
	    text.append('\\');
	  }
          text.append(digit);
      // took out test for ch == '$' because must be true
      //} else if (ch == '$' && digit == '{') {
        } else if (digit == '{') {
          i += 2;

          int group = 0;

          while (i < replacement.length() &&
                 '0' <= (digit = replacement.charAt(i)) && digit <= '9') {
            group = 10 * group + digit - '0';

            i++;
          }

          if (digit != '}') {
            env.warning(L.l("bad regexp {0}", replacement));
            throw new QuercusException("bad regexp");
          }

          if (text.length() > 0)
            program.add(new TextReplacement(text));

	  if (isEval)
	    program.add(new GroupEscapeReplacement(group));
	  else
	    program.add(new GroupReplacement(group));
	  
          text.setLength(0);
        }
        else
          text.append(ch);
      }
      else
        text.append(ch);
    }

    if (text.length() > 0)
      program.add(new TextReplacement(text));

    return program;
  }

  private static final String [] POSIX_CLASSES = {
    "[:alnum:]", "[:alpha:]", "[:blank:]", "[:cntrl:]",
    "[:digit:]", "[:graph:]", "[:lower:]", "[:print:]",
    "[:punct:]", "[:space:]", "[:upper:]", "[:xdigit:]"
  };

  private static final String [] REGEXP_CLASSES = {
    "\\p{Alnum}", "\\p{Alpha}", "\\p{Blank}", "\\p{Cntrl}",
    "\\p{Digit}", "\\p{Graph}", "\\p{Lower}", "\\p{Print}",
    "\\p{Punct}", "\\p{Space}", "\\p{Upper}", "\\p{XDigit}"
  };

  /**
   * Cleans the regexp from valid values that the Java regexps can't handle.
   * Currently "+?".
   */
  // XXX: not handling '['
  private static String cleanRegexp(StringValue regexp, boolean isComments)
  {
    int len = regexp.length();

    StringBuilder sb = new StringBuilder();
    char quote = 0;

    for (int i = 0; i < len; i++) {
      char ch = regexp.charAt(i);

      switch (ch) {
      case '\\':
        sb.append(ch);

        if (i + 1 < len) {
          i++;

          ch = regexp.charAt(i);

          if (ch == '0' ||
	      '1' <= ch && ch <= '3' && i + 1 < len && '0' <= regexp.charAt(i + 1) && ch <= '7') {
            // Java's regexp requires \0 for octal

            // sb.append('\\');
            sb.append('0');
            sb.append(ch);
          }
          else if (ch == 'x' && i + 1 < len && regexp.charAt(i + 1) == '{') {
            int tail = regexp.indexOf('}', i + 1);

            if (tail > 0) {
              StringValue hex = regexp.substring(i + 2, tail);

              int length = hex.length();

              if (length == 1)
                sb.append("x0" + hex);
              else if (length == 2)
                sb.append("x" + hex);
              else if (length == 3)
                sb.append("u0" + hex);
              else if (length == 4)
                sb.append("u" + hex);
              else
                throw new QuercusRuntimeException(L.l("illegal hex escape"));

              i = tail;
            }
            else {
              sb.append("\\x");
            }
          }
          else
            sb.append(ch);
        }
        break;

      case '[':
        if (quote == '[') {
          if (i + 1 < len && regexp.charAt(i + 1) == ':') {
            String test = regexp.substring(i).toString();
            boolean hasMatch = false;

            for (int j = 0; j < POSIX_CLASSES.length; j++) {
              if (test.startsWith(POSIX_CLASSES[j])) {
                hasMatch = true;

                sb.append(REGEXP_CLASSES[j]);

                i += POSIX_CLASSES[j].length() - 1;
              }
            }

            if (! hasMatch)
              sb.append("\\[");
          }
          else
            sb.append("\\[");
        }
        else if (i + 1 < len && regexp.charAt(i + 1) == '['
		 && ! (i + 2 < len && regexp.charAt(i + 2) == ':')) {
	  // XXX: check regexp grammar
	  // php/151n
          sb.append("[\\[");
	  i += 1;
	}
        else if (i + 2 < len &&
		 regexp.charAt(i + 1) == '^' &&
		 regexp.charAt(i + 2) == ']') {
          sb.append("[^\\]");
	  i += 2;
	}
        else
          sb.append('[');

        if (quote == 0)
          quote = '[';
        break;

      case '#':
        if (quote == '[' && isComments)
          sb.append("\\#");
        else
          sb.append(ch);
        break;

      case ']':
        sb.append(ch);

        if (quote == '[')
          quote = 0;
        break;

      case '{':
        if (i + 1 < len &&
            ('0' <= (ch = regexp.charAt(i + 1)) && ch <= '9' || ch == ',')) {
          sb.append("{");
          for (i++;
               i < len &&
               ('0' <= (ch = regexp.charAt(i)) && ch <= '9' || ch == ',');
               i++) {
            sb.append(ch);
          }

          if (i < len)
            sb.append(regexp.charAt(i));
        }
        else {
          sb.append("\\{");
        }
        break;

      case '}':
        sb.append("\\}");
        break;

      default:
        sb.append(ch);
      }
    }

    return sb.toString();
  }

  /**
   * Converts to non-greedy.
   */
  private static String toNonGreedy(String regexp)
  {
    int len = regexp.length();

    StringBuilder sb = new StringBuilder();
    char quote = 0;

    for (int i = 0; i < len; i++) {
      char ch = regexp.charAt(i);

      switch (ch) {
      case '\\':
        sb.append(ch);

        if (i + 1 < len) {
          sb.append(regexp.charAt(i + 1));
          i++;
        }
        break;

      case '[':
        sb.append(ch);

        if (quote == 0)
          quote = ch;
        break;

      case ']':
        sb.append(ch);

        if (quote == '[')
          quote = 0;
        break;

      // non-capturing special constructs
      case '(':
        sb.append(ch);

        if (i + 1 < len) {
          ch = regexp.charAt(i + 1);

          if (ch == '?') {
            sb.append(ch);
            i++;
          }
        }
        break;

      case '*':
      case '?':
      case '+':
        sb.append(ch);

        if (i + 1 < len && (ch = regexp.charAt(i + 1)) != '?') {
          sb.append('?');
        }
        else {
          // invert non-greedy to greedy
          i++;
        }
        break;

      default:
        sb.append(ch);
      }
    }

    return sb.toString();
  }

  static class Replacement {
    void eval(StringBuilderValue sb, StringValue subject, Matcher matcher)
    {
    }
  }

  static class TextReplacement
    extends Replacement
  {
    private char []_text;

    TextReplacement(StringBuilder text)
    {
      int length = text.length();

      _text = new char[length];

      text.getChars(0, length, _text, 0);
    }

    void eval(StringBuilderValue sb, StringValue subject, Matcher matcher)
    {
      sb.append(_text, 0, _text.length);
    }
  }

  static class GroupReplacement
    extends Replacement
  {
    private int _group;

    GroupReplacement(int group)
    {
      _group = group;
    }

    void eval(StringBuilderValue sb, StringValue subject, Matcher matcher)
    {
      if (_group <= matcher.groupCount())
        sb.append(subject.substring(matcher.start(_group),
				    matcher.end(_group)));
    }
  }

  static class GroupEscapeReplacement
    extends Replacement
  {
    private int _group;

    GroupEscapeReplacement(int group)
    {
      _group = group;
    }

    void eval(StringBuilderValue sb, StringValue subject, Matcher matcher)
    {
      if (_group <= matcher.groupCount()) {
	StringValue group = subject.substring(matcher.start(_group),
					      matcher.end(_group));;
	int len = group.length();

	for (int i = 0; i < len; i++) {
	  char ch = group.charAt(i);

	  if (ch == '\'')
	    sb.append("\\\'");
	  else if (ch == '\"')
	    sb.append("\\\"");
	  else
	    sb.append(ch);
	}
      }
    }
  }
  
  /*
   * Holds PCRE named subpatterns.
   */
  static class NamedPatterns
  {
    private StringValue _pattern;

    private HashMap<Integer,StringValue> _patternMap;

    NamedPatterns(StringValue pattern)
    {
      _pattern = cleanPattern(pattern);
    }
    
    private StringValue cleanPattern(StringValue pattern)
    {
      StringBuilderValue sb = new StringBuilderValue();
      int length = pattern.length();
      
      int groupCount = 1;
      
      for (int i = 0; i < length; i++) {
        char ch = pattern.charAt(i);
        sb.append(ch);
        
        if (ch != '(')
          continue;

        if (++i >= length)
          break;
        
        ch = pattern.charAt(i);
        
        if (ch != '?') {
          sb.append(ch);

          groupCount++;
          continue;
        }

        if (++i >= length)
          break;
        
        ch = pattern.charAt(i);

        if (ch != 'P') {
          sb.append('?');
          sb.append(ch);
          
          continue;
        }

        if (++i >= length)
          break;

        ch = pattern.charAt(i);

        if (ch == '<') {
          if (++i >= length)
            break;
          
          int start = i;
          for (; i < length && pattern.charAt(i) != '>'; i++) {
          }

          StringValue name = pattern.substring(start, i);

          add(groupCount, name);
        }
        else if (ch == '=') {
          //XXX: named back references (?P=name)
          throw new UnsupportedOperationException("back references to named subpatterns");
        }
        else {
          throw new QuercusModuleException("bad PCRE named subpattern");
        }
      }
      
      return sb;
    }
    
    StringValue getCleanedPattern()
    {
      return _pattern;
    }
    
    void add(int group, StringValue name)
    {
      if (_patternMap == null)
        _patternMap = new HashMap<Integer,StringValue>();
      
      _patternMap.put(Integer.valueOf(group), name);
    }
    
    StringValue get(int group)
    { 
      if (_patternMap == null)
        return null;
      else
        return _patternMap.get(Integer.valueOf(group));
    }
  }

  static {
    PREG_QUOTE['\\'] = true;
    PREG_QUOTE['+'] = true;
    PREG_QUOTE['*'] = true;
    PREG_QUOTE['?'] = true;
    PREG_QUOTE['['] = true;
    PREG_QUOTE['^'] = true;
    PREG_QUOTE[']'] = true;
    PREG_QUOTE['$'] = true;
    PREG_QUOTE['('] = true;
    PREG_QUOTE[')'] = true;
    PREG_QUOTE['{'] = true;
    PREG_QUOTE['}'] = true;
    PREG_QUOTE['='] = true;
    PREG_QUOTE['!'] = true;
    PREG_QUOTE['<'] = true;
    PREG_QUOTE['>'] = true;
    PREG_QUOTE['|'] = true;
    PREG_QUOTE[':'] = true;
  }
}
