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

package org.apache.xmlbeans.impl.marshal;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.common.XmlWhitespace;


final class PreserveStringTypeConverter
    extends StringTypeConverter
{
    private static final TypeConverter INSTANCE
        = new PreserveStringTypeConverter();

    static TypeConverter getInstance()
    {
        return INSTANCE;
    }

    private PreserveStringTypeConverter()
    {
    }

    public Object unmarshalAttribute(UnmarshalResult context)
        throws XmlException
    {
        return context.getAttributeStringValue(XmlWhitespace.WS_PRESERVE);
    }

    protected Object getObject(UnmarshalResult context) throws XmlException
    {
        return context.getStringValue(XmlWhitespace.WS_PRESERVE);
    }

}