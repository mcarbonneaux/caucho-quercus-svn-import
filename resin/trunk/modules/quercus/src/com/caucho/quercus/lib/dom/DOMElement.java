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
 * @author Sam
 */

package com.caucho.quercus.lib.dom;

import com.caucho.quercus.module.Optional;
import com.caucho.xml.QElement;
import com.caucho.xml.QName;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Node;

public class DOMElement
  extends QElement
{
  public DOMElement(String name, @Optional String textContent, @Optional String namespace)
  {
    super(new QName(name, namespace));

    if (textContent != null && textContent.length() > 0)
      setTextContent(textContent);
  }

  DOMElement(DOMDocument owner, QName name, String textContent)
  {
    super(owner, name);

    if (textContent != null && textContent.length() > 0)
      setTextContent(textContent);
  }

  public String getNodeValue()
  {
    StringBuilder value = new StringBuilder();

    Node node = getFirstChild();

    while (node != null) {
      if (node instanceof CharacterData) {
        String nodeValue = node.getNodeValue();

        if (nodeValue != null)
          value.append(nodeValue);
      }
      else if (node instanceof DOMElement) {
        value.append(node.getNodeValue());
      }

      node = node.getNextSibling();
    }

    return value.toString();
  }

  public String toString()
  {
    return "DOMElement[" + getTagName() + "]";
  }
}
