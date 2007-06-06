/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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

package com.caucho.quercus.lib.simplexml.node;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;
import com.caucho.vfs.WriteStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * Represents a SimpleXML nodelist (though only for non-unique elements).
 * There is no parent node.
 */
class SimpleElementList extends SimpleElement
{
  ArrayList<SimpleElement> _sameNameSiblings
    = new ArrayList<SimpleElement>();

  SimpleElementList()
  {
    super(null);
  }

  ArrayList<SimpleElement> getSameNameSiblings()
  {
    return _sameNameSiblings;
  }
  
  void addSameNameSibling(SimpleElement node)
  {
    _sameNameSiblings.add(node);
  }
  
  @Override
  public
  SimpleElement get(int index)
  {
    return _sameNameSiblings.get(index);
  }
  
  int size()
  {
    return _sameNameSiblings.size();
  }
  
  @Override
  public String getName()
  {
    return _sameNameSiblings.get(0).getName();
  }
  
  @Override
  public ArrayList<SimpleNode> getChildren()
  {
    return _sameNameSiblings.get(0).getChildren();
  }
  
  @Override
  public SimpleElement getElement(String name)
  {
    return _sameNameSiblings.get(0).getElement(name);
  }
  
  @Override
  public void removeChildren()
  {
    _sameNameSiblings.get(0).removeChildren();
  }
  
  @Override
  HashMap<String,SimpleElement> getElementMap()
  {
    return _sameNameSiblings.get(0).getElementMap();  
  }
  
  @Override
  public Iterator<SimpleElement> iterator()
  {
    return _sameNameSiblings.iterator();
  }
  
  @Override
  public void addAttribute(SimpleAttribute attr)
  {
    _sameNameSiblings.get(0).addAttribute(attr);
  }
  
  @Override
  public SimpleAttribute getAttribute(String name)
  {
    return _sameNameSiblings.get(0).getAttribute(name);
  }
  
  @Override
  public ArrayList<SimpleAttribute> getAttributes()
  {
    return _sameNameSiblings.get(0).getAttributes();
  }
  
  @Override
  public boolean isElementList()
  {
    return true;
  }
  
  @Override
  public boolean isSameNamespace(String namespace)
  {
    return _sameNameSiblings.get(0).isSameNamespace(namespace);
  }
  
  @Override
  public String toXML()
  {
    StringBuilder sb = new StringBuilder();
    
    toXMLImpl(sb);
    
    return sb.toString();
  }
  
  @Override
  protected void toXMLImpl(StringBuilder sb)
  {
    for (SimpleElement node : _sameNameSiblings) {
      node.toXMLImpl(sb);
    }
  }
  
  @Override
  public void varDumpImpl(Env env,
                          WriteStream out,
                          int depth,
                          IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    printDepth(out, 2 * depth);
    out.println("[\"" + getName() + "\"]=>");
    
    varDumpNested(env, out, depth, valueSet);
  }
  
  @Override
  void varDumpNested(Env env,
                     WriteStream out,
                     int depth,
                     IdentityHashMap<Value, String> valueSet)
    throws IOException
  {  
    printDepth(out, 2 * depth);
    out.println("array(" + _sameNameSiblings.size() + ") {");
    
    int size = _sameNameSiblings.size();
    for (int i = 0; i < size; i++) {
      printDepth(out, 2 * (depth + 1));
      out.println("[" + i + "]=>");
      
      _sameNameSiblings.get(i).varDumpNested(env, out, depth + 1, valueSet);
    }
    
    printDepth(out, 2 * depth);
    out.println('}');
  }
  
  @Override
  void printRNested(Env env,
                    WriteStream out,
                    int depth,
                    IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    out.println("Array");
    printDepth(out, 4 * depth);
    out.println('(');
    
    int size = _sameNameSiblings.size();
    for (int i = 0; i < size; i++) {
      printDepth(out, 4 * (depth + 1));
      out.print("[" + i + "] => ");
      
      _sameNameSiblings.get(i).printRNested(env, out, depth + 2, valueSet);
    }
    
    printDepth(out, 4 * depth);
    out.println(')');
    out.println();
  }
  
  public String toString()
  {
    return _sameNameSiblings.get(0).toString();
  }
}
