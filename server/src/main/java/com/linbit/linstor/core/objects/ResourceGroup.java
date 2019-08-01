package com.linbit.linstor.core.objects;

import com.linbit.linstor.DbgInstanceUuid;
import com.linbit.linstor.api.interfaces.AutoSelectFilterApi;
import com.linbit.linstor.core.identifier.ResourceGroupName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.VolumeGroup.VlmGrpApi;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.security.ProtectedObject;
import com.linbit.linstor.transaction.TransactionObject;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public interface ResourceGroup extends TransactionObject, DbgInstanceUuid,
    Comparable<ResourceGroup>, ProtectedObject
{
    UUID getUuid();

    ResourceGroupName getName();

    String getDescription(AccessContext accCtxRef)
        throws AccessDeniedException;

    String setDescription(AccessContext accCtxRef, String descriptionRef)
        throws AccessDeniedException, DatabaseException;

    void addResourceDefinition(AccessContext accCtxRef, ResourceDefinitionData rscDfnRef)
        throws AccessDeniedException;

    void removeResourceDefinition(AccessContext accCtxRef, ResourceDefinitionData rscDfnRef)
        throws AccessDeniedException;

    boolean hasResourceDefinitions(AccessContext accCtxRef) throws AccessDeniedException;

    Props getProps(AccessContext accCtxRef)
        throws AccessDeniedException;

    AutoSelectorConfig getAutoPlaceConfig();

    VolumeGroup getVolumeGroup(AccessContext accCtxRef, VolumeNumber vlmNrRef)
        throws AccessDeniedException;

    Stream<VolumeGroup> streamVolumeGroups(AccessContext accCtxRef)
        throws AccessDeniedException;

    /**
     * Calls {@link #getVolumeGroup(AccessContext, VolumeNumber).getProps(AccessContext)} but instead of
     * a possible {@link NullPointerException} (in case the {@link VolumeGroup} does not exist) this method
     * returns an empty {@link ReadOnlyProps} instance.
     *
     * @param accCtxRef
     * @param vlmNrRef
     * @throws AccessDeniedException
     */
    Props getVolumeGroupProps(AccessContext accCtxRef, VolumeNumber vlmNrRef)
        throws AccessDeniedException;

    void deleteVolumeGroup(AccessContext accCtxRef, VolumeNumber vlmNrRef)
        throws AccessDeniedException;

    Collection<ResourceDefinition> getRscDfns(AccessContext accCtxRef)
        throws AccessDeniedException;

    void delete(AccessContext accCtxRef)
        throws AccessDeniedException, DatabaseException;

    RscGrpApi getApiData(AccessContext accCtxRef)
        throws AccessDeniedException;

    interface RscGrpApi
    {
        @Nullable UUID getUuid();
        String getName();
        String getDescription();
        Map<String, String> getProps();
        @Nullable AutoSelectFilterApi getAutoSelectFilter();
        List<VlmGrpApi> getVlmGrpList();
    }

    interface InitMaps
    {
        Map<VolumeNumber, VolumeGroup> getVlmGrpMap();
        Map<ResourceName, ResourceDefinition> getRscDfnMap();
    }

}