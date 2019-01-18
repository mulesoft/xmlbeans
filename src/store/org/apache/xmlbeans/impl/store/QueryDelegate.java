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
package org.apache.xmlbeans.impl.store;

import org.apache.xmlbeans.XmlOptions;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Constructor;

public final class QueryDelegate {
    private static final Map<String, Constructor<? extends QueryInterface>> _constructors =
        new HashMap<String, Constructor<? extends QueryInterface>>();

    private QueryDelegate() {
    }

    private static synchronized void init(String implClassName) {
        // default to Saxon
        if (implClassName == null)
            implClassName = "org.apache.xmlbeans.impl.xquery.saxon.XBeansXQuery";
        Class<? extends QueryInterface> queryInterfaceImpl = null;
        boolean engineAvailable = true;
        try {
            //noinspection unchecked
            queryInterfaceImpl = (Class<? extends QueryInterface>) Class.forName(implClassName);
        } catch (ClassNotFoundException e) {
            engineAvailable = false;
        } catch (NoClassDefFoundError e) {
            engineAvailable = false;
        }

        if (engineAvailable) {
            try {
                Constructor<? extends QueryInterface> constructor = queryInterfaceImpl.getConstructor(
                    String.class, String.class, Integer.class, XmlOptions.class);
                _constructors.put(implClassName, constructor);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static synchronized QueryInterface createInstance(String implClassName,
         String query, String contextVar, int boundary, XmlOptions xmlOptions) {
        if (_constructors.get(implClassName) == null)
            init(implClassName);

        if (_constructors.get(implClassName) == null)
            return null;

        Constructor<? extends QueryInterface> constructor = _constructors.get(implClassName);
        try {
            return constructor.newInstance(query, contextVar, boundary, xmlOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface QueryInterface {
        List execQuery(Object node, Map variableBindings);
    }
}
