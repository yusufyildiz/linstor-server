package com.linbit.linstor.storage.layer.adapter.drbd.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.linbit.ImplementationError;
import com.linbit.InvalidNameException;
import com.linbit.linstor.LinStorException;
import com.linbit.linstor.LinStorRuntimeException;
import com.linbit.linstor.LsIpAddress;
import com.linbit.linstor.NetInterface;
import com.linbit.linstor.NetInterfaceName;
import com.linbit.linstor.Node;
import com.linbit.linstor.NodeConnection;
import com.linbit.linstor.PriorityProps;
import com.linbit.linstor.Resource;
import com.linbit.linstor.ResourceConnection;
import com.linbit.linstor.ResourceDefinition;
import com.linbit.linstor.TcpPortNumber;
import com.linbit.linstor.Volume;
import com.linbit.linstor.VolumeDefinition;
import com.linbit.linstor.Resource.RscFlags;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.prop.LinStorObject;
import com.linbit.linstor.api.prop.WhitelistProps;
import com.linbit.linstor.core.LinStor;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.StorageException;
import com.linbit.linstor.storage.data.adapter.drbd.DrbdRscData;
import com.linbit.linstor.storage.data.adapter.drbd.DrbdRscDfnData;
import com.linbit.linstor.storage.data.adapter.drbd.DrbdVlmData;
import com.linbit.utils.Pair;

import org.slf4j.event.Level;

public class ConfFileBuilder
{
    private static final ResourceNameComparator RESOURCE_NAME_COMPARATOR = new ResourceNameComparator();

    private final ErrorReporter errorReporter;
    private final AccessContext accCtx;
    private final DrbdRscData localRscData;
    private final Collection<DrbdRscData> remoteResourceData;
    private final WhitelistProps whitelistProps;

    private StringBuilder stringBuilder;
    private int indentDepth;

    public ConfFileBuilder(
        final ErrorReporter errorReporterRef,
        final AccessContext accCtxRef,
        final DrbdRscData localRscRef,
        final Collection<DrbdRscData> remoteResourcesRef,
        final WhitelistProps whitelistPropsRef
    )
    {
        errorReporter = errorReporterRef;
        accCtx = accCtxRef;
        localRscData = localRscRef;
        remoteResourceData = remoteResourcesRef;
        whitelistProps = whitelistPropsRef;

        stringBuilder = new StringBuilder();
        indentDepth = 0;
    }

    // Constructor used for the common linstor conf
    public ConfFileBuilder(
        final ErrorReporter errorReporterRef,
        final WhitelistProps whitelistPropsRef
    )
    {
        errorReporter = errorReporterRef;
        accCtx = null;
        localRscData = null;
        remoteResourceData = null;
        whitelistProps = whitelistPropsRef;

        stringBuilder = new StringBuilder();
        indentDepth = 0;
    }

    private String header()
    {
        return String.format("# This file was generated by linstor(%s), do not edit manually.",
            LinStor.VERSION_INFO_PROVIDER.getVersion());
    }

    public String build()
        throws AccessDeniedException, StorageException
    {
        Set<DrbdRscData> peerRscSet = new TreeSet<>(RESOURCE_NAME_COMPARATOR);
        DrbdRscDfnData rscDfnData = localRscData.getRscDfnLayerObject();
        if (remoteResourceData == null)
        {
            throw new ImplementationError("No remote resources found for " + localRscData.getResource() + "!");
        }
        peerRscSet.addAll(remoteResourceData); // node-alphabetically sorted

        Resource localRsc = localRscData.getResource();
        final ResourceDefinition rscDfn = localRsc.getDefinition();
        if (rscDfn == null)
        {
            throw new ImplementationError("No resource definition found for " + localRsc + "!");
        }

        appendLine(header());
        appendLine("");
        appendLine("resource \"%s\"", localRscData.getSuffixedResourceName());
        try (Section resourceSection = new Section())
        {
            // include linstor common
            appendLine("template-file \"linstor_common.conf\";");

            if (rscDfn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_DRBD_HANDLER_OPTIONS).isPresent())
            {
                appendLine("");
                appendLine("handlers");
                try (Section optionsSection = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.RESOURCE_DEFINITION,
                        rscDfn.getProps(accCtx),
                        ApiConsts.NAMESPC_DRBD_HANDLER_OPTIONS,
                        true
                    );
                }
            }

            if (rscDfn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_DRBD_RESOURCE_OPTIONS).isPresent())
            {
                appendLine("");
                appendLine("options");
                try (Section optionsSection = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        rscDfn.getProps(accCtx),
                        ApiConsts.NAMESPC_DRBD_RESOURCE_OPTIONS
                    );
                }
            }

            appendLine("");
            appendLine("net");
            try (Section netSection = new Section())
            {
                // TODO: make configurable
                appendLine("cram-hmac-alg     %s;", "sha1");
                // TODO: make configurable
                appendLine("shared-secret     \"%s\";", rscDfnData.getSecret());

                appendDrbdOptions(
                    LinStorObject.CONTROLLER,
                    rscDfn.getProps(accCtx),
                    ApiConsts.NAMESPC_DRBD_NET_OPTIONS
                );
            }

            if (rscDfn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_DRBD_DISK_OPTIONS).isPresent())
            {
                appendLine("");
                appendLine("disk");
                try (Section ignore = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        rscDfn.getProps(accCtx),
                        ApiConsts.NAMESPC_DRBD_DISK_OPTIONS
                    );
                }
            }

            int port = rscDfnData.getTcpPort().value;
            // Create local network configuration
            {
                appendLine("");
                appendLine("on %s", localRsc.getAssignedNode().getName().displayValue);
                try (Section onSection = new Section())
                {
                    Collection<DrbdVlmData> vlmDataList = localRscData.getVlmLayerObjects().values();
                    for (DrbdVlmData vlmData : vlmDataList)
                    {
                        appendVlmIfPresent(vlmData, accCtx, false);
                    }
                    appendLine("node-id    %d;", localRscData.getNodeId().value);
                }
            }

            for (final DrbdRscData peerRscData : peerRscSet)
            {
                Resource peerRsc = peerRscData.getResource();
                if (peerRsc.getStateFlags().isUnset(accCtx, RscFlags.DELETE))
                {
                    appendLine("");
                    appendLine("on %s", peerRsc.getAssignedNode().getName().displayValue);
                    try (Section onSection = new Section())
                    {
                        Collection<DrbdVlmData> peerVlmDataList = peerRscData.getVlmLayerObjects().values();
                        for (DrbdVlmData peerVlmData : peerVlmDataList)
                        {
                            appendVlmIfPresent(peerVlmData, accCtx, true);
                        }

                        appendLine("node-id    %d;", peerRscData.getNodeId().value);

                        // TODO: implement "multi-connection / path magic" (nodeMeshes + singleConnections vars)
                        // sb.append(peerResource.co)
                    }
                }
            }

            // first generate all with local first
            for (final DrbdRscData peerRscData : peerRscSet)
            {
                Resource peerRsc = peerRscData.getResource();
                // don't create a connection entry if the resource has the deleted flag
                // or if it is a connection between two diskless nodes
                if (peerRsc.getStateFlags().isUnset(accCtx, RscFlags.DELETE) &&
                        !(peerRsc.disklessForPeers(accCtx) &&
                            localRsc.getStateFlags().isSet(accCtx, RscFlags.DISKLESS)))
                {
                    appendLine("");
                    appendLine("connection");
                    try (Section connectionSection = new Section())
                    {
                        List<Pair<NetInterface, NetInterface>> pathsList = new ArrayList<>();
                        ResourceConnection rscConn = localRsc.getResourceConnection(accCtx, peerRsc);
                        NodeConnection nodeConn;
                        Optional<Props> paths = Optional.empty();

                        if (rscConn != null)
                        {
                            // get paths from resource connection...
                            paths = rscConn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_CONNECTION_PATHS);

                            Props rscConnProps = rscConn.getProps(accCtx);
                            if (rscConnProps.getNamespace(ApiConsts.NAMESPC_DRBD_NET_OPTIONS).isPresent())
                            {
                                appendLine("");
                                appendLine("net");
                                try (Section ignore = new Section())
                                {
                                    appendDrbdOptions(
                                        LinStorObject.CONTROLLER,
                                        rscConnProps,
                                        ApiConsts.NAMESPC_DRBD_NET_OPTIONS
                                    );
                                }
                            }

                            if (rscConnProps.getNamespace(ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS).isPresent())
                            {
                                appendLine("");
                                appendLine("disk");
                                try (Section ignore = new Section())
                                {
                                    appendConflictingDrbdOptions(
                                        LinStorObject.CONTROLLER,
                                        "resource-definition",
                                        rscDfn.getProps(accCtx),
                                        rscConnProps,
                                        ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS
                                    );
                                }
                            }
                        }
                        else
                        {
                            if (rscDfn.getProps(accCtx)
                                    .getNamespace(ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS).isPresent()
                            )
                            {
                                appendLine("");
                                appendLine("disk");
                                try (Section ignore = new Section())
                                {
                                    appendDrbdOptions(
                                            LinStorObject.CONTROLLER,
                                            rscDfn.getProps(accCtx),
                                            ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS
                                    );
                                }
                            }
                        }

                        // ...or fall back to node connection
                        if (!paths.isPresent())
                        {
                            nodeConn = localRsc.getAssignedNode().getNodeConnection(accCtx, peerRsc.getAssignedNode());

                            if (nodeConn != null)
                            {
                                paths = nodeConn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_CONNECTION_PATHS);
                            }
                        }

                        if (paths.isPresent())
                        {
                            // iterate through network connection paths
                            Iterator<String> pathsIterator = paths.get().iterateNamespaces();
                            while (pathsIterator.hasNext())
                            {
                                String path = pathsIterator.next();
                                Optional<Props> nodes = paths.get().getNamespace(path);

                                if (nodes.isPresent() && nodes.get().map().size() == 2)
                                {
                                    Node firstNode = peerRsc.getAssignedNode();
                                    Node secondNode = localRsc.getAssignedNode();
                                    try
                                    {
                                        // iterate through nodes (should be exactly 2)
                                        Iterator<String> nodesIterator = nodes.get().keysIterator();
                                        String firstNodeName = nodesIterator.next().split("/")[2];
                                        String secondNodeName = nodesIterator.next().split("/")[2];

                                        // keep order of nodes correct
                                        if (firstNode.getName().value.equalsIgnoreCase(secondNodeName) &&
                                            secondNode.getName().value.equalsIgnoreCase(firstNodeName))
                                        {
                                            Node temp = firstNode;
                                            firstNode = secondNode;
                                            secondNode = temp;
                                        }
                                        else if (!(firstNode.getName().value.equalsIgnoreCase(firstNodeName) &&
                                                secondNode.getName().value.equalsIgnoreCase(secondNodeName)))
                                        {
                                            throw new ImplementationError(
                                                    "Configured node names " + firstNodeName + " and " +
                                                    secondNodeName + " do not match the actual node names."
                                            );
                                        }

                                        // get corresponding network interfaces
                                        String nicName = nodes.get().getProp(firstNodeName);
                                        NetInterface firstNic = firstNode.getNetInterface(
                                                accCtx, new NetInterfaceName(nicName));

                                        if (firstNic == null)
                                        {
                                            throw new StorageException("Network interface '" + nicName +
                                                    "' of node '" + firstNode + "' does not exist!");
                                        }

                                        nicName = nodes.get().getProp(secondNodeName);
                                        NetInterface secondNic = secondNode.getNetInterface(
                                                accCtx, new NetInterfaceName(nicName));

                                        if (secondNic == null)
                                        {
                                            throw new StorageException("Network interface '" + nicName +
                                                    "' of node '" + secondNode + "' does not exist!");
                                        }

                                        pathsList.add(new Pair<>(firstNic, secondNic));
                                    }
                                    catch (InvalidKeyException exc)
                                    {
                                        throw new ImplementationError(
                                                "No network interface configured!", exc);
                                    }
                                    catch (InvalidNameException exc)
                                    {
                                        throw new StorageException(
                                                "Name format of for network interface is not valid!", exc);
                                    }
                                }
                                else
                                {
                                    throw new ImplementationError(
                                            "When configuring a path it must contain exactly two nodes!");
                                }
                            }

                            // add network connection paths...
                            for (Pair<NetInterface, NetInterface> path : pathsList)
                            {
                                if (path != pathsList.get(0))
                                {
                                    appendLine("");
                                }
                                appendLine("path");
                                try (Section pathSection = new Section())
                                {
                                    appendConnectionHost(port, rscConn, path.objA);
                                    appendConnectionHost(port, rscConn, path.objB);
                                }
                            }
                        }
                        else
                        {
                            // ...or fall back to previous implementation
                            appendConnectionHost(port, rscConn, getPreferredNetIf(localRsc));
                            appendConnectionHost(port, rscConn, getPreferredNetIf(peerRsc));
                        }
                    }
                }
            }

            Optional<String> compressionTypeProp = rscDfn.getProps(accCtx)
                .getNamespace(ApiConsts.NAMESPC_DRBD_PROXY)
                .map(Props::map)
                .map(map -> map.get(ApiConsts.KEY_DRBD_PROXY_COMPRESSION_TYPE));

            if (rscDfn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_DRBD_PROXY_OPTIONS).isPresent() ||
                compressionTypeProp.isPresent())
            {
                appendLine("");
                appendLine("proxy");
                try (Section ignore = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.DRBD_PROXY,
                        rscDfn.getProps(accCtx),
                        ApiConsts.NAMESPC_DRBD_PROXY_OPTIONS
                    );

                    if (compressionTypeProp.isPresent())
                    {
                        appendCompressionPlugin(rscDfn, compressionTypeProp.get());
                    }
                }
            }
        }

        return stringBuilder.toString();
    }

    private void appendConnectionHost(int rscDfnPort, ResourceConnection rscConn, NetInterface netIf)
        throws AccessDeniedException
    {
        TcpPortNumber rscConnPort = rscConn == null ? null : rscConn.getPort(accCtx);
        int port = rscConnPort == null ? rscDfnPort : rscConnPort.value;

        LsIpAddress addr = netIf.getAddress(accCtx);
        String addrText = addr.getAddress();

        String outsideAddress;
        if (addr.getAddressType() == LsIpAddress.AddrType.IPv6)
        {
            outsideAddress = String.format("ipv6 [%s]:%d", addrText, port);
        }
        else
        {
            outsideAddress = String.format("ipv4 %s:%d", addrText, port);
        }

        String hostName = netIf.getNode().getName().displayValue;

        if (rscConn != null && rscConn.getStateFlags().isSet(accCtx, ResourceConnection.RscConnFlags.LOCAL_DRBD_PROXY))
        {
            appendLine("host %s address 127.0.0.1:%d via proxy on %s", hostName, port, hostName);
            try (Section ignore = new Section())
            {
                appendLine("inside 127.0.0.2:%d;", port);
                appendLine("outside %s;", outsideAddress);
            }
        }
        else
        {
            appendLine("host %s address %s;", hostName, outsideAddress);
        }
    }

    public String buildCommonConf(final Props satelliteProps)
    {
        appendLine(header());
        appendLine("");
        appendLine("common");
        try (Section commonSection = new Section())
        {
            if (satelliteProps.getNamespace(ApiConsts.NAMESPC_DRBD_DISK_OPTIONS).isPresent() ||
                satelliteProps.getNamespace(ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS).isPresent())
            {
                appendLine("disk");
                try (Section ignore = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        satelliteProps,
                        ApiConsts.NAMESPC_DRBD_DISK_OPTIONS
                    );

                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        satelliteProps,
                        ApiConsts.NAMESPC_DRBD_PEER_DEVICE_OPTIONS
                    );
                }
            }

            if (satelliteProps.getNamespace(ApiConsts.NAMESPC_DRBD_HANDLER_OPTIONS).isPresent())
            {
                appendLine("");
                appendLine("handlers");
                try (Section optionsSection = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        satelliteProps,
                        ApiConsts.NAMESPC_DRBD_HANDLER_OPTIONS,
                        true
                    );
                }
            }

            if (satelliteProps.getNamespace(ApiConsts.NAMESPC_DRBD_NET_OPTIONS).isPresent())
            {
                appendLine("net");
                try (Section ignore = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        satelliteProps,
                        ApiConsts.NAMESPC_DRBD_NET_OPTIONS
                    );
                }
            }

            if (satelliteProps.getNamespace(ApiConsts.NAMESPC_DRBD_RESOURCE_OPTIONS).isPresent())
            {
                appendLine("options");
                try (Section ignore = new Section())
                {
                    appendDrbdOptions(
                        LinStorObject.CONTROLLER,
                        satelliteProps,
                        ApiConsts.NAMESPC_DRBD_RESOURCE_OPTIONS
                    );
                }
            }
        }

        return stringBuilder.toString();
    }

    private void appendCompressionPlugin(ResourceDefinition rscDfn, String compressionType)
        throws AccessDeniedException
    {
        appendLine("plugin");
        try (Section pluginSection = new Section())
        {
            String namespace = ApiConsts.NAMESPC_DRBD_PROXY_COMPRESSION_OPTIONS;

            List<String> compressionPluginTerms = new ArrayList<>();
            compressionPluginTerms.add(compressionType);

            Map<String, String> drbdProps = rscDfn.getProps(accCtx)
                .getNamespace(namespace)
                .map(Props::map).orElse(new HashMap<>());

            for (Map.Entry<String, String> entry : drbdProps.entrySet())
            {
                String key = entry.getKey();
                String value = entry.getValue();
                if (checkValidDrbdOption(LinStorObject.drbdProxyCompressionObject(compressionType), key, value))
                {
                    compressionPluginTerms.add(key.substring(namespace.length() + 1));
                    compressionPluginTerms.add(value);
                }
            }

            appendLine("%s;", String.join(" ", compressionPluginTerms));
        }
    }

    private boolean checkValidDrbdOption(
        final LinStorObject lsObj,
        final String key,
        final String value
    )
    {
        boolean ret = true;
        if (!whitelistProps.isAllowed(lsObj, new ArrayList<>(), key, value, true))
        {
            ret = false;
            errorReporter.reportProblem(
                Level.WARN,
                new LinStorException(
                    "Ignoring property '" + key + "' with value '" + value + "' as it is not whitelisted."
                ),
                null,
                null,
                "The whitelist was generated from 'drbdsetup xml-help {resource,peer-device,net,disk}-options'" +
                    " when the satellite started."
            );
        }

        return ret;
    }

    private void appendConflictingDrbdOptions(
        final LinStorObject lsObj,
        final String parentName,
        final Props propsParent,
        final Props props,
        final String namespace
    )
    {
        Map<String, String> mapParent = propsParent.getNamespace(namespace)
            .map(Props::map).orElse(new HashMap<>());

        Map<String, String> mapProps = props.getNamespace(namespace)
            .map(Props::map).orElse(new HashMap<>());

        Set<String> writtenProps = new TreeSet<>();

        for (Map.Entry<String, String> entry : mapParent.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            final String configKey = key.substring(namespace.length() + 1);
            if (checkValidDrbdOption(lsObj, key, value))
            {
                final String absKey = Props.PATH_SEPARATOR + key; // key needs to be absolute
                if (mapProps.containsKey(absKey))
                {
                    appendCommentLine("%s %s; # set on %s",
                        configKey,
                        value,
                        parentName
                    );
                    appendLine("%s %s;",
                        configKey,
                        mapProps.get(absKey)
                    );
                }
                else
                {
                    appendLine("%s %s;",
                        configKey,
                        value
                    );
                }
                writtenProps.add(key);
            }
        }

        for (Map.Entry<String, String> entry : mapProps.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            final String configKey = key.substring(namespace.length() + 1);
            if (!writtenProps.contains(key) && checkValidDrbdOption(lsObj, key, value))
            {
                appendLine("%s %s;",
                    configKey,
                    value
                );
            }
        }
    }

    private void appendDrbdOptions(
        final LinStorObject lsObj,
        final Props props,
        final String namespace
    )
    {
        appendDrbdOptions(lsObj, props, namespace, false);
    }

    private void appendDrbdOptions(
        final LinStorObject lsObj,
        final Props props,
        final String namespace,
        boolean quote
    )
    {
        Map<String, String> drbdProps = props.getNamespace(namespace)
            .map(Props::map).orElse(new HashMap<>());

        for (Map.Entry<String, String> entry : drbdProps.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            if (checkValidDrbdOption(lsObj, key, value))
            {
                String sFormat = quote ? "%s \"%s\";" : "%s %s;";
                appendLine(
                    sFormat,
                    key.substring(namespace.length() + 1),
                    value
                );
            }
        }
    }

    private NetInterface getPreferredNetIf(Resource rsc)
    {
        NetInterface preferredNetIf = null;
        try
        {
            Iterator<Volume> iterateVolumes = rsc.iterateVolumes();
            PriorityProps prioProps = new PriorityProps(
                rsc.getProps(accCtx),
                iterateVolumes.hasNext() ?
                    iterateVolumes.next().getStorPool(accCtx).getProps(accCtx) :
                    null, // prioProps will skip null props-instances
                rsc.getAssignedNode().getProps(accCtx)
            );
            String prefNic = prioProps.getProp(ApiConsts.KEY_STOR_POOL_PREF_NIC);

            if (prefNic != null)
            {
                preferredNetIf = rsc.getAssignedNode().getNetInterface(
                    accCtx,
                    new NetInterfaceName(prefNic)
                );

                if (preferredNetIf == null)
                {
                    errorReporter.logWarning(
                        String.format("Preferred network interface '%s' not found, fallback to default", prefNic)
                    );
                }
            }

            // fallback if preferred couldn't be found
            if (preferredNetIf == null)
            {
                Node assgNode = rsc.getAssignedNode();
                // Try to find the 'default' network interface
                preferredNetIf = assgNode.getNetInterface(accCtx, NetInterfaceName.DEFAULT_NET_INTERFACE_NAME);
                // If there is not even a 'default', use the first one that is found in the node's
                // list of network interfaces
                if (preferredNetIf == null)
                {
                    preferredNetIf = assgNode.streamNetInterfaces(accCtx).findFirst().orElse(null);
                }
            }
        }
        catch (AccessDeniedException | InvalidKeyException | InvalidNameException implError)
        {
            throw new ImplementationError(implError);
        }

        return preferredNetIf;
    }

    private void appendVlmIfPresent(DrbdVlmData vlmData, AccessContext localAccCtx, boolean isPeerRsc)
        throws AccessDeniedException
    {
        if (vlmData.getVolume().getFlags().isUnset(localAccCtx, Volume.VlmFlags.DELETE))
        {
            final String disk;
            if ((!isPeerRsc && vlmData.getBackingDevice() == null) ||
                (isPeerRsc &&
                // FIXME: vlmData.getRscLayerObject().getFlags should be used here
                     vlmData.getVolume().getResource().disklessForPeers(accCtx)
                ) ||
                (!isPeerRsc &&
                // FIXME: vlmData.getRscLayerObject().getFlags should be used here
                     vlmData.getVolume().getResource().isDiskless(accCtx)
                )
            )
            {
                disk = "none";
            }
            else
            {
                if (!isPeerRsc)
                {
                    String backingDiskPath = vlmData.getBackingDevice();
                    if (backingDiskPath.trim().equals(""))
                    {
                        throw new LinStorRuntimeException(
                            "Local volume does an empty block device. This might be result of an other error.",
                            "The storage driver returned an empty string instead of the path of the backing device",
                            "This is either an implementation error or just a side effect of an other " +
                                "recently occured error. Please check the error logs and try to solve the other " +
                                "other errors first",
                            null,
                            vlmData.toString()
                        );
                    }
                    disk = backingDiskPath;
                }
                else
                {
                    // Do not use the backing disk path from the peer resource because it may be 'none' when
                    // the peer resource is converting from diskless, but the path here should not be 'none'
                    disk = "/dev/drbd/this/is/not/used";
                }
            }
            final String metaDisk;
            if (vlmData.getMetaDiskPath() == null)
            {
                metaDisk = "internal";
            }
            else
            {
                String tmpMeta = vlmData.getMetaDiskPath();
                if (tmpMeta.trim().equals(""))
                {
                    metaDisk = "internal";
                }
                else
                {
                    metaDisk = vlmData.getMetaDiskPath();
                }
            }

            final VolumeDefinition vlmDfn = vlmData.getVolume().getVolumeDefinition();
            appendLine("volume %s", vlmDfn.getVolumeNumber().value);
            try (Section volumeSection = new Section())
            {
                appendLine("disk        %s;", disk);

                if (vlmDfn.getProps(accCtx).getNamespace(ApiConsts.NAMESPC_DRBD_DISK_OPTIONS).isPresent())
                {
                    appendLine("disk");
                    try (Section ignore = new Section())
                    {
                        appendDrbdOptions(
                            LinStorObject.CONTROLLER,
                            vlmDfn.getProps(accCtx),
                            ApiConsts.NAMESPC_DRBD_DISK_OPTIONS
                        );
                    }
                }

                appendLine("meta-disk   %s;", metaDisk);
                appendLine("device      minor %d;",
                    vlmData.getVlmDfnLayerObject().getMinorNr().value
                // TODO: impl and ask storPool for device
                );
                // TODO: add "disk { ... }" section
            }
        }
    }

    private void appendIndent()
    {
        for (int idx = 0; idx < indentDepth; idx++)
        {
            stringBuilder.append("    ");
        }
    }

    private void append(String format, Object... args)
    {
        stringBuilder.append(String.format(format, args));
    }

    private void appendLine(String format, Object... args)
    {
        appendIndent();
        append(format, args);
        stringBuilder.append("\n");
    }

    private void appendCommentLine(String format, Object... args)
    {
        stringBuilder.append("#");
        appendLine(format, args);
    }

    private static class ResourceNameComparator implements Comparator<DrbdRscData>
    {
        @Override
        public int compare(DrbdRscData o1, DrbdRscData o2)
        {
            return o1.getResource().getAssignedNode().getName().compareTo(
                   o2.getResource().getAssignedNode().getName()
            );
        }
    }

    /**
     * Allows a section to be expressed using try-with-resources so that it is automatically closed.
     * <p>
     * Non-static to allow access to the indentDepth.
     */
    private class Section implements AutoCloseable
    {
        Section()
        {
            appendLine("{");
            indentDepth++;
        }

        @Override
        public void close()
        {
            indentDepth--;
            appendLine("}");
        }
    }
}
