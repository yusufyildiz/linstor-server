package com.linbit.drbdmanage.propscon;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Common interface for Containers that hold drbdmanage property maps
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public interface Props
{
    public String getProp(String key)
        throws InvalidKeyException;
    public String getProp(String key, String namespace)
        throws InvalidKeyException;

    public String setProp(String key, String value)
        throws InvalidKeyException, InvalidValueException;
    public String setProp(String key, String value, String namespace)
        throws InvalidKeyException, InvalidValueException;

    public String removeProp(String key)
        throws InvalidKeyException;
    public String removeProp(String key, String namespace)
        throws InvalidKeyException;

    public void clear();

    public int size();
    public boolean isEmpty();

    public String getPath();

    public Map<String, String> map();
    public Set<Map.Entry<String, String>> entrySet();
    public Set<String> keySet();
    public Collection<String> values();

    public Iterator<Map.Entry<String, String>> iterator();
    public Iterator<String> keysIterator();
    public Iterator<String> valuesIterator();

    public Props getNamespace(String namespace)
         throws InvalidKeyException;
}
