/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.binding.bts;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.impl.jam.JClass;

import java.io.Serializable;

public final class BindingTypeName
    implements Serializable
{

  // ========================================================================
  // Variables

  private final JavaTypeName jName;
  private final XmlTypeName xName;

  private static final long serialVersionUID = 1L;

  
  // ========================================================================
  // Factories

  public static BindingTypeName forPair(JavaTypeName jName, XmlTypeName xName) {
    return new BindingTypeName(jName, xName);
  }

  public static BindingTypeName forTypes(JClass jClass, SchemaType sType) {
    return forPair(JavaTypeName.forJClass(jClass), XmlTypeName.forSchemaType(sType));
  }

  // ========================================================================
  // Constructors

  private BindingTypeName(JavaTypeName jName, XmlTypeName xName) {
    if (jName == null) throw new IllegalArgumentException("null jName");
    if (xName == null) throw new IllegalArgumentException("null xName");
    this.jName = jName;
    this.xName = xName;
  }

  // ========================================================================
  // Public methods

  public JavaTypeName getJavaName() {
    return jName;
  }

  public XmlTypeName getXmlName() {
    return xName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BindingTypeName)) return false;

    final BindingTypeName bindingTypeName = (BindingTypeName) o;

    if (!jName.equals(bindingTypeName.jName)) return false;
    if (!xName.equals(bindingTypeName.xName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = jName.hashCode();
    result = 29 * result + xName.hashCode();
    return result;
  }

  public String toString() {
    return "BindingTypeName[" + jName + ";" + xName + "]";
  }
}