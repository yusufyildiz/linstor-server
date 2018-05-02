package com.linbit.linstor.event;

import com.linbit.linstor.LinStorDataAlreadyExistsException;

import java.util.Collection;

public interface EventStreamStore
{
    void addEventStream(EventIdentifier eventIdentifier)
        throws LinStorDataAlreadyExistsException;

    /**
     * @return True if the event stream did not previously exist.
     */
    boolean addEventStreamIfNew(EventIdentifier eventIdentifier);

    void removeEventStream(EventIdentifier eventIdentifier);

    Collection<EventIdentifier> getDescendantEventStreams(EventIdentifier eventIdentifier);
}
