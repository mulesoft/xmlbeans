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

package org.apache.xmlbeans.impl.binding.tylar;

import org.apache.xmlbeans.impl.binding.bts.BindingLoader;
import org.apache.xmlbeans.impl.binding.bts.BuiltinBindingLoader;
import org.apache.xmlbeans.impl.binding.bts.CompositeBindingLoader;
import org.apache.xmlbeans.impl.binding.bts.BindingFile;
import org.apache.xmlbeans.impl.jam.JamClassLoader;
import org.apache.xmlbeans.impl.jam.JamServiceFactory;
import org.apache.xmlbeans.*;
import org.w3.x2001.xmlSchema.SchemaDocument;
import java.net.URI;

/**
 * <p>Base class for simplifying implementation of the Tylar interface.</p>
 *
 * @author Patrick Calahan <pcal@bea.com>
 */
public abstract class BaseTylarImpl implements Tylar {

  // ========================================================================
  // Partial default Tylar implementation

  public String getDescription() {
    URI uri = getLocation();
    if (uri != null) return uri.toString();
    return "["+this.getClass().getName()+"]";
  }

  public URI getLocation() {
    return null;
  }

  public BindingLoader getBindingLoader() {
    //REVIEW should consider caching this result
    BindingFile[] bfs = getBindingFiles();
    BindingLoader[] loaders = new BindingLoader[bfs.length+1];
    System.arraycopy(bfs,0,loaders,0,bfs.length);
    loaders[loaders.length-1] = BuiltinBindingLoader.getBuiltinBindingLoader(false);
    return CompositeBindingLoader.forPath(loaders);
  }

  public JamClassLoader getJamClassLoader()
  {
    // REVIEW should consider caching this result
    // create a classloader chain that runs throw all of the base tylars
    ClassLoader cl = createClassLoader(ClassLoader.getSystemClassLoader());
    return JamServiceFactory.getInstance().createJamClassLoader(cl);
  }

  // ========================================================================
  // Protected methods

  /**
   * <p>Creates a schema type system by compiling all of the schema documents
   * returned by getSchemas().  This can be used as a fallback for 
   * implementing getSchemaTypeSystem().
   *
   * @return
   */
  protected SchemaTypeSystem getDefaultSchemaTypeSystem()
  {
    SchemaDocument[] xsds = getSchemas();
    XmlObject[] xxds = new XmlObject[xsds.length];
    for(int i=0; i<xsds.length; i++) xxds[i] = xsds[i].getSchema();
    try {
      return XmlBeans.compileXsd(xxds,XmlBeans.getBuiltinTypeSystem(),null);
    } catch(XmlException xe) {
      // REVIEW we need to enforce an invariant that a tylar with invalid
      // schemas can never be instantiated.
      xe.printStackTrace();
      throw new IllegalStateException(xe.getMessage());
    }
  }


}