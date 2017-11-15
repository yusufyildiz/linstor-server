package com.linbit.drbdmanage.propscon;

import com.linbit.TransactionMgr;
import com.linbit.drbdmanage.security.AccessDeniedException;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Read-only access to a properties container
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public class ReadOnlyProps implements Props
{
    private Props propsMap;

    public ReadOnlyProps(Props propsRef)
    {
        propsMap = propsRef;
    }

    @Override
    public String getProp(String key) throws InvalidKeyException
    {
        return propsMap.getProp(key);
    }

    @Override
    public String getProp(String key, String namespace) throws InvalidKeyException
    {
        return propsMap.getProp(key, namespace);
    }

    @Override
    public String setProp(String key, String value)
        throws InvalidKeyException, InvalidValueException, AccessDeniedException
    {
        // throws UnsupportedOperationException
        denyAccess();

        // Never reached
        return null;
    }

    @Override
    public String setProp(String key, String value, String namespace)
        throws InvalidKeyException, InvalidValueException, AccessDeniedException
    {
        // throws UnsupportedOperationException
        denyAccess();

        // Never reached
        return null;
    }

    @Override
    public String removeProp(String key) throws InvalidKeyException, AccessDeniedException
    {
        // throws UnsupportedOperationException
        denyAccess();

        // Never reached
        return null;
    }

    @Override
    public String removeProp(String key, String namespace)
        throws InvalidKeyException, AccessDeniedException
    {
        // throws UnsupportedOperationException
        denyAccess();

        // Never reached
        return null;
    }

    @Override
    public void clear() throws AccessDeniedException
    {
        // throws UnsupportedOperationException
        denyAccess();
    }

    @Override
    public int size()
    {
        return propsMap.size();
    }

    @Override
    public boolean isEmpty()
    {
        return propsMap.isEmpty();
    }

    @Override
    public String getPath()
    {
        return propsMap.getPath();
    }

    @Override
    public Map<String, String> map()
    {
        return Collections.unmodifiableMap(propsMap.map());
    }

    @Override
    public Set<Map.Entry<String, String>> entrySet()
    {
        return Collections.unmodifiableSet(propsMap.entrySet());
    }

    @Override
    public Set<String> keySet()
    {
        return Collections.unmodifiableSet(propsMap.keySet());
    }

    @Override
    public Collection<String> values()
    {
        return Collections.unmodifiableCollection(propsMap.values());
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator()
    {
        return new ReadOnlyIterator<>(propsMap.iterator());
    }

    @Override
    public Iterator<String> keysIterator()
    {
        return new ReadOnlyIterator<>(propsMap.keysIterator());
    }

    @Override
    public Iterator<String> valuesIterator()
    {
        return new ReadOnlyIterator<>(propsMap.valuesIterator());
    }

    @Override
    public Props getNamespace(String namespace) throws InvalidKeyException
    {
        Props ret = propsMap.getNamespace(namespace);
        if (ret != null)
        {
            ret = new ReadOnlyProps(ret);
        }
        return ret;
    }


    @Override
    public Iterator<String> iterateNamespaces()
    {
        return propsMap.iterateNamespaces();
    }

    @Override
    public void initialized()
    {
        propsMap.initialized();
    }

    @Override
    public boolean isInitialized()
    {
        return propsMap.isInitialized();
    }

    @Override
    public void setConnection(TransactionMgr transMgr)
    {
        // ignore - ReadOnlyProps cannot be changed
    }

    @Override
    public boolean isDirty()
    {
        return propsMap.isDirty();
    }

    @Override
    public boolean isDbCacheDirty()
    {
        return propsMap.isDbCacheDirty();
    }

    @Override
    public boolean hasTransMgr()
    {
        return propsMap.hasTransMgr();
    }

    @Override
    public void commit()
    {
        // ignore - ReadOnlyProps cannot be changed. If the changes should be persisted,
        // the modifiable propsCon.commit() should be called, not this
    }

    @Override
    public void rollback()
    {
        // ignore - ReadOnlyProps cannot be changed. If the changes should be rolled back,
        // the modifiable propsCon.rollback() should be called, not this
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((propsMap == null) ? 0 : propsMap.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = false;
        if (this == obj)
        {
            result = true;
        }
        else
        if (obj != null && getClass() == obj.getClass())
        {
            ReadOnlyProps other = (ReadOnlyProps) obj;
            if (propsMap == null)
            {
                if (other.propsMap == null)
                {
                    result = true;
                }
            }
            else
            if (propsMap.equals(other.propsMap))
            {
                result = true;
            }
        }
        return result;
    }

    private void denyAccess()
        throws AccessDeniedException
    {
        throw new AccessDeniedException(
            "Permission to modify a read-only properties container was denied",
            // Description
            "Permission to modify the properties of an object was denied",
            // Cause
            "A read-only view of the object properties was provided. The common reason is " +
            "that the role that was used to fetch the object properties does not have " +
            "write access to the protected object that the properties belong to.",
            // Correction
            "Write access must be granted to the protected object to fetch a modifiable " +
            "view of the object properties",
            // No error detail
            null
        );
    }

    private void unsupported()
    {
        throw new UnsupportedOperationException(
            "Attempt to perform an unsupported operation on a read-only view " +
            "of a properties container"
        );
    }

    static class ReadOnlyIterator<T> implements Iterator<T>
    {
        private Iterator<T> iter;

        ReadOnlyIterator(Iterator<T> iterRef)
        {
            iter = iterRef;
        }

        @Override
        public boolean hasNext()
        {
            return iter.hasNext();
        }

        @Override
        public T next()
        {
            return iter.next();
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException(
                "Attempt to modify an object through read-only iterator"
            );
        }
    }
}
