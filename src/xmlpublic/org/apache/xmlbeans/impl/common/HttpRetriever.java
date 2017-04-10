package org.apache.xmlbeans.impl.common;

import java.io.InputStream;

/**
 * Interface to offer custom functionality to retrieve a resource through
 * HTTP.
 */
public interface HttpRetriever
{
    /**
     * Retrieves the data through HTTP.
     * 
     * @param url URL to retrieve the stream from
     * @return an input stream containing the data.
     * @throws Exception 
     */
    InputStream getStreamFrom(String url) throws Exception;
}
