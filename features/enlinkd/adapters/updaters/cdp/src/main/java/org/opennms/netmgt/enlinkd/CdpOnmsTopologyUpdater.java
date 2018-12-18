/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.enlinkd;

import java.util.Map;

import org.opennms.netmgt.enlinkd.model.CdpElementTopologyEntity;
import org.opennms.netmgt.enlinkd.model.CdpLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.IpInterfaceTopologyEntity;
import org.opennms.netmgt.enlinkd.model.NodeTopologyEntity;
import org.opennms.netmgt.enlinkd.service.api.CdpTopologyService;
import org.opennms.netmgt.enlinkd.service.api.NodeTopologyService;
import org.opennms.netmgt.enlinkd.service.api.ProtocolSupported;
import org.opennms.netmgt.enlinkd.service.api.Topology;
import org.opennms.netmgt.enlinkd.service.api.TopologyConnection;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.topologies.service.api.OnmsTopology;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyDao;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyEdge;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyException;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyPort;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyVertex;

public class CdpOnmsTopologyUpdater extends EnlinkdOnmsTopologyUpdater {

    public static OnmsTopologyPort create(OnmsTopologyVertex vertex,CdpLinkTopologyEntity cdpLink ) throws OnmsTopologyException {
        OnmsTopologyPort port = OnmsTopologyPort.create(cdpLink.getId().toString(),vertex, cdpLink.getCdpCacheIfIndex());
        port.setPort(cdpLink.getCdpInterfaceName());
        port.setAddr(Topology.getAddress(cdpLink));
        port.setToolTipText(Topology.getToolTipText(vertex.getLabel(), port.getIndex(), port.getPort(), port.getAddr(), null));
        return port;
    }
    
    private final CdpTopologyService m_cdpTopologyService;

    public CdpOnmsTopologyUpdater(EventForwarder eventforwarder,
            OnmsTopologyDao topologyDao, CdpTopologyService cdpTopologyService, NodeTopologyService nodeTopologyService,
            long interval, long initialsleeptime) {
        super(eventforwarder, cdpTopologyService,topologyDao,nodeTopologyService,interval, initialsleeptime);
        m_cdpTopologyService = cdpTopologyService;
    }            
    
    @Override
    public String getName() {
        return "CdpTopologyUpdater";
    }

    @Override
    public OnmsTopology buildTopology() throws OnmsTopologyException {
        Map<Integer, NodeTopologyEntity> nodeMap= getNodeMap();
        Map<Integer, IpInterfaceTopologyEntity> ipMap= getIpPrimaryMap();
        OnmsTopology topology = new OnmsTopology();
        for (CdpElementTopologyEntity element: m_cdpTopologyService.findAllCdpElements()) {
            topology.getVertices().add(create(nodeMap.get(element.getNodeId()),ipMap.get(element.getNodeId()).getIpAddress()));
        }
        
        for(TopologyConnection<CdpLinkTopologyEntity, CdpLinkTopologyEntity> pair : m_cdpTopologyService.match()) {
            topology.getEdges().add(
                OnmsTopologyEdge.create(
                        Topology.getDefaultEdgeId(pair.getLeft().getId().intValue(), pair.getRight().getId().intValue()),
                        create(topology.getVertex(pair.getLeft().getNodeIdAsString()),pair.getLeft()),
                        create(topology.getVertex(pair.getRight().getNodeIdAsString()),pair.getRight())
                        )
                );
       }
        
        return topology;
    }

    @Override
    public String getProtocol() {
        return ProtocolSupported.CDP.name();
    }
            
}
