/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.features.topology.plugins.topo.linkd.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opennms.features.topology.api.browsers.ContentType;
import org.opennms.features.topology.api.browsers.SelectionAware;
import org.opennms.features.topology.api.browsers.SelectionChangedListener;
import org.opennms.features.topology.api.topo.AbstractTopologyProvider;
import org.opennms.features.topology.api.topo.Defaults;
import org.opennms.features.topology.api.topo.GraphProvider;
import org.opennms.features.topology.api.topo.Vertex;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.enlinkd.model.CdpLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.IsIsLink;
import org.opennms.netmgt.enlinkd.model.LldpLink;
import org.opennms.netmgt.enlinkd.model.NodeTopologyEntity;
import org.opennms.netmgt.enlinkd.model.OspfLink;
import org.opennms.netmgt.enlinkd.service.api.BridgePort;
import org.opennms.netmgt.enlinkd.service.api.BridgeTopologyException;
import org.opennms.netmgt.enlinkd.service.api.BridgeTopologyService;
import org.opennms.netmgt.enlinkd.service.api.CdpTopologyService;
import org.opennms.netmgt.enlinkd.service.api.IsisTopologyService;
import org.opennms.netmgt.enlinkd.service.api.LldpTopologyService;
import org.opennms.netmgt.enlinkd.service.api.MacPort;
import org.opennms.netmgt.enlinkd.service.api.NodeTopologyService;
import org.opennms.netmgt.enlinkd.service.api.OspfTopologyService;
import org.opennms.netmgt.enlinkd.service.api.ProtocolSupported;
import org.opennms.netmgt.enlinkd.service.api.Topology;
import org.opennms.netmgt.enlinkd.service.api.TopologyConnection;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

public class LinkdTopologyProvider extends AbstractTopologyProvider implements GraphProvider {

    private static Logger LOG = LoggerFactory.getLogger(LinkdTopologyProvider.class);

    private SnmpInterfaceDao m_snmpInterfaceDao;

    private NodeTopologyService m_nodeTopologyService;
    private BridgeTopologyService m_bridgeTopologyService;
    private CdpTopologyService m_cdpTopologyService;
    private LldpTopologyService m_lldpTopologyService;
    private OspfTopologyService m_ospfTopologyService;
    private IsisTopologyService m_isisTopologyService;

    private Table<Integer, Integer,OnmsSnmpInterface> m_nodeToOnmsSnmpTable = HashBasedTable.create();

    private final Timer m_loadFullTimer;
    private final Timer m_loadSnmpInterfacesTimer;
    private final Timer m_loadLldpLinksTimer;
    private final Timer m_loadOspfLinksTimer;
    private final Timer m_loadCdpLinksTimer;
    private final Timer m_loadIsisLinksTimer;
    private final Timer m_loadBridgeLinksTimer;
    private final Timer m_loadVerticesTimer;
    private final Timer m_loadEdgesTimer;

    public static final String TOPOLOGY_NAMESPACE_LINKD = "nodes";
    
    private SelectionAware selectionAwareDelegate = new LinkdSelectionAware();

    public LinkdTopologyProvider(MetricRegistry registry) {
        super(TOPOLOGY_NAMESPACE_LINKD);
        Objects.requireNonNull(registry);
        m_loadFullTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "full"));
        m_loadSnmpInterfacesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "snmpinterfaces"));
        m_loadLldpLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "lldp"));
        m_loadOspfLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "ospf"));
        m_loadCdpLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "cdp"));
        m_loadIsisLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "isis"));
        m_loadBridgeLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "bridge"));
        m_loadVerticesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "vertices", "none"));
        m_loadEdgesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "edges", "none"));
    }

    private OnmsSnmpInterface getSnmpInterface(Integer nodeid, Integer ifindex) {
        if(m_nodeToOnmsSnmpTable.contains(nodeid,ifindex) ) {
                return m_nodeToOnmsSnmpTable.get(nodeid,ifindex);
        }
        return null;
    }
    
    @Override
    public SelectionChangedListener.Selection getSelection(List<VertexRef> selectedVertices, ContentType type) {
       return selectionAwareDelegate.getSelection(selectedVertices, type);
    }

    @Override
    public boolean contributesTo(ContentType type) {
        return selectionAwareDelegate.contributesTo(type);
    }
    
    private void loadEdges() {

        Timer.Context context = m_loadLldpLinksTimer.time();
        try{
            getLldpLinks();
            LOG.info("loadEdges: LldpLink loaded");
        } catch (Exception e){
            LOG.error("Loading LldpLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadOspfLinksTimer.time();
        try{
            getOspfLinks();
            LOG.info("loadEdges: OspfLink loaded");
        } catch (Exception e){
            LOG.error("Loading OspfLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadCdpLinksTimer.time();
        try{
            getCdpLinks();
            LOG.info("loadEdges: CdpLink loaded");
        } catch (Exception e){
            LOG.error("Loading CdpLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadIsisLinksTimer.time();
        try{
            getIsIsLinks();
            LOG.info("loadEdges: IsIsLink loaded");
        } catch (Exception e){
            LOG.error("Exception getting IsIs link: "+e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadBridgeLinksTimer.time();
        try{
            getBridgeLinks();
            LOG.info("loadEdges: BridgeLink loaded");
        } catch (Exception e){
            LOG.error("Loading BridgeLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }
    }


    private void getLldpLinks() {
        for (TopologyConnection<LldpLink, LldpLink> pair : m_lldpTopologyService.match()) {
            LinkdVertex source = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getLeft().getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.LLDP);
            LinkdVertex target = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getRight().getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.LLDP);
            addEdges(
                     LinkdEdge.create(
                                      Topology.getDefaultEdgeId(pair.getLeft().getId(), pair.getRight().getId()),
                                      LinkdPort.create(
                                                       source,
                                                       getSnmpInterface(pair.getLeft().getNode().getId(), pair.getLeft().getLldpPortIfindex()), 
                                                       Topology.getRemoteAddress(pair.getRight())
                                                       ),
                                      LinkdPort.create(
                                                       target, 
                                                       getSnmpInterface(pair.getRight().getNode().getId(), pair.getRight().getLldpPortIfindex()), 
                                                       Topology.getRemoteAddress(pair.getLeft())
                                                       ),
                                      ProtocolSupported.LLDP)
                     );
        }
    }

    private void getOspfLinks() {
        for (TopologyConnection<OspfLink, OspfLink> pair : m_ospfTopologyService.match()) {
            LinkdVertex source = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getLeft().getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.OSPF);
            LinkdVertex target = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getRight().getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.OSPF);
            addEdges(
                     LinkdEdge.create(
                                      Topology.getDefaultEdgeId(pair.getLeft().getId(), pair.getRight().getId()),
                                      LinkdPort.create(
                                                       source,
                                                       getSnmpInterface(pair.getLeft().getNode().getId(), pair.getLeft().getOspfIfIndex()), 
                                                       Topology.getRemoteAddress(pair.getRight())
                                                       ),
                                      LinkdPort.create(
                                                       target, 
                                                       getSnmpInterface(pair.getRight().getNode().getId(), pair.getRight().getOspfIfIndex()), 
                                                       Topology.getRemoteAddress(pair.getLeft())
                                                       ),
                                      ProtocolSupported.OSPF)
                     );
        }
    }

    private void getCdpLinks() {
        for(TopologyConnection<CdpLinkTopologyEntity, CdpLinkTopologyEntity> pair : m_cdpTopologyService.match()) {
            LinkdVertex source = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getLeft().getNodeIdAsString());
            source.getProtocolSupported().add(ProtocolSupported.CDP);
            LinkdVertex target = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getRight().getNodeIdAsString());
            target.getProtocolSupported().add(ProtocolSupported.CDP);
            addEdges(
                     LinkdEdge.create(
                                      Topology.getDefaultEdgeId(pair.getLeft().getId(), pair.getRight().getId()), 
                                      LinkdPort.create(
                                                       source,
                                                       getSnmpInterface(pair.getLeft().getNodeId(), pair.getLeft().getCdpCacheIfIndex()), 
                                                       Topology.getAddress(pair.getLeft())
                                                       ),
                                      LinkdPort.create(
                                                       target, 
                                                       getSnmpInterface(pair.getRight().getNodeId(), pair.getRight().getCdpCacheIfIndex()), 
                                                       Topology.getAddress(pair.getRight())
                                                       ),
                                      ProtocolSupported.CDP
                              )
                     );
        }
    }

    private void getIsIsLinks() {

        for(TopologyConnection<IsIsLink, IsIsLink> pair : m_isisTopologyService.match()) {
            LinkdVertex source = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getLeft().getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.ISIS);
            LinkdVertex target = (LinkdVertex) getVertex(TOPOLOGY_NAMESPACE_LINKD, pair.getRight().getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.ISIS);
            addEdges(
                     LinkdEdge.create(
                                      Topology.getDefaultEdgeId(pair.getLeft().getId(), pair.getRight().getId()), 
                                      LinkdPort.create(
                                                       source,
                                                       getSnmpInterface(pair.getLeft().getNode().getId(), pair.getLeft().getIsisCircIfIndex()), 
                                                       Topology.getRemoteAddress(pair.getRight())
                                                       ),
                                      LinkdPort.create(
                                                       target, 
                                                       getSnmpInterface(pair.getRight().getNode().getId(), pair.getRight().getIsisCircIfIndex()), 
                                                       Topology.getRemoteAddress(pair.getLeft())
                                                       ),
                                      ProtocolSupported.ISIS)
                    );
        }
    }

    
    private void getBridgeLinks() throws BridgeTopologyException {
        m_bridgeTopologyService.match().stream().forEach(topologylink ->
        {
            Map<BridgePort,LinkdVertex> portToNodeVertexMap =  topologylink.getBridgePorts().
                    stream().
                    collect(Collectors.toMap(bp -> bp, bp -> (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, bp.getNodeId().toString())));

            portToNodeVertexMap.values().stream().forEach(vertex ->        
                vertex.getProtocolSupported().add(ProtocolSupported.BRIDGE));
            
            Map<MacPort,LinkdVertex> macPortToNodeVertexMap = topologylink.getMacPorts().
                    stream().
                    filter( port -> port.getNodeId() != null).
                    collect(Collectors.toMap(mp -> mp, mp -> (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, mp.getNodeId().toString())));
            
            macPortToNodeVertexMap.values().stream().forEach(vertex ->        
                vertex.getProtocolSupported().add(ProtocolSupported.BRIDGE));

            List<MacPort> macportswithoutnodeid = topologylink.getMacPorts().
                    stream().
                    filter( port -> port.getNodeId() == null).collect(Collectors.toList());

            LinkdVertex macsVertex = null;
            if (topologylink.getCloud() != null || macportswithoutnodeid.size() > 0) {
                macsVertex = LinkdVertex.create(topologylink.getCloud(),macportswithoutnodeid,topologylink.getUpPort());
                addVertices(macsVertex);
            } 
            
            if (portToNodeVertexMap.size() == 2 && 
                    macPortToNodeVertexMap.size() == 0 && macsVertex == null ) {
                LinkdVertex source = null;
                LinkdVertex target = null;
                BridgePort sourcebp = null;
                BridgePort targetbp = null;
                for (BridgePort bp: portToNodeVertexMap.keySet()) {
                    if (bp.getNodeId() == topologylink.getUpPort().getNodeId()) {
                        source = portToNodeVertexMap.get(bp);
                        sourcebp = bp;
                        continue;
                    } 
                    target = portToNodeVertexMap.get(bp);
                    targetbp=bp;
                }
                addEdges(
                         LinkdEdge.create(
                                          Topology.getEdgeId(sourcebp,targetbp),
                                          LinkdPort.create(
                                                           source,
                                                           getSnmpInterface(sourcebp.getNodeId(), sourcebp.getBridgePortIfIndex()),
                                                           Topology.getAddress(sourcebp)
                                                           ),
                                          LinkdPort.create(
                                                           target,
                                                           getSnmpInterface(targetbp.getNodeId(), targetbp.getBridgePortIfIndex()),
                                                           Topology.getAddress(targetbp)
                                                           ),
                                          ProtocolSupported.BRIDGE
                                          )
                         );
            } else if (portToNodeVertexMap.size() == 1 && 
                macPortToNodeVertexMap.size() == 1 && macsVertex == null ) {
                BridgePort sourcebp = portToNodeVertexMap.keySet().iterator().next();
                MacPort targetmp = macPortToNodeVertexMap.keySet().iterator().next();
                addEdges(
                         LinkdEdge.create(
                                          Topology.getEdgeId(sourcebp,targetmp),
                                          LinkdPort.create(
                                                           portToNodeVertexMap.values().iterator().next(),
                                                           getSnmpInterface(sourcebp.getNodeId(), sourcebp.getBridgePortIfIndex()),
                                                           Topology.getAddress(sourcebp)
                                                           ),
                                          LinkdPort.create(
                                                           macPortToNodeVertexMap.values().iterator().next(),
                                                           getSnmpInterface(targetmp.getNodeId(),targetmp.getMacPortIfIndex()),
                                                           Topology.getAddress(targetmp)
                                                           ),
                                          ProtocolSupported.BRIDGE
                                          )
                         );
            } else  if (portToNodeVertexMap.size() == 1 && 
                    macPortToNodeVertexMap.size() == 0 && macsVertex != null ) {
                    BridgePort sourcebp = portToNodeVertexMap.keySet().iterator().next();
                    addEdges(
                             LinkdEdge.create(
                                             Topology.getEdgeId(macsVertex.getId(),sourcebp), 
                                             LinkdPort.create(
                                                              portToNodeVertexMap.values().iterator().next(),
                                                              getSnmpInterface(sourcebp.getNodeId(), sourcebp.getBridgePortIfIndex()),
                                                              Topology.getAddress(sourcebp)
                                                              ),
                                             LinkdPort.create(
                                                              macsVertex,
                                                              macsVertex.getIpAddress()
                                                              ),
                                             ProtocolSupported.BRIDGE
                                             )
                             );
            } else {
                LinkdVertex cloud = LinkdVertex.create(topologylink.getUpPort());
                addVertices(cloud);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("parseSegment: adding cloud: id: '{}', {}", cloud.getId(), topologylink.getUpPort().printTopology());
                }
                for (BridgePort bp: portToNodeVertexMap.keySet()) {
                    addEdges(
                             LinkdEdge.create(
                                              Topology.getEdgeId(cloud.getId(), bp), 
                                              LinkdPort.create(
                                                               cloud,
                                                               Topology.getAddress(topologylink.getUpPort())
                                                               ),
                                              LinkdPort.create(
                                                               portToNodeVertexMap.get(bp),
                                                               getSnmpInterface(bp.getNodeId(), bp.getBridgePortIfIndex()), 
                                                               Topology.getAddress(bp)),
                                              ProtocolSupported.BRIDGE
                                              )
                             );
                    
                }
                for (MacPort mp: macPortToNodeVertexMap.keySet()) {
                    addEdges(
                             LinkdEdge.create(
                                              Topology.getEdgeId(cloud.getId(), mp), 
                                              LinkdPort.create(
                                                               cloud,
                                                               Topology.getAddress(topologylink.getUpPort())
                                                               ),
                                              LinkdPort.create(
                                                               macPortToNodeVertexMap.get(mp),
                                                               getSnmpInterface(mp.getNodeId(), mp.getMacPortIfIndex()),
                                                               Topology.getAddress(mp)
                                                               ),
                                              ProtocolSupported.BRIDGE
                                              )
                              );
                }
                
                if (macsVertex != null) {
                    addEdges(
                             LinkdEdge.create(
                                              Topology.getDefaultEdgeId(cloud.getId(), macsVertex.getId()), 
                                              LinkdPort.create(
                                                               cloud,
                                                               Topology.getAddress(topologylink.getUpPort())
                                                               ),
                                              LinkdPort.create(
                                                               macsVertex,
                                                               macsVertex.getIpAddress()
                                                               ),
                                              ProtocolSupported.BRIDGE
                                              )
                             );
                    
                }
            }
        });
    }
        
    @Override
    public Defaults getDefaults() {
        return new Defaults()
                .withSemanticZoomLevel(Defaults.DEFAULT_SEMANTIC_ZOOM_LEVEL)
                .withPreferredLayout("D3 Layout") // D3 Layout
                .withCriteria(() -> {
                    final NodeTopologyEntity node = m_nodeTopologyService.getDefaultFocusPoint();

                    if (node != null) {
                        final Vertex defaultVertex = getVertex(TOPOLOGY_NAMESPACE_LINKD, node.getId());
                        if (defaultVertex != null) {
                            return Lists.newArrayList(LinkdHopCriteria.createCriteria(node.getId(), node.getLabel()));
                        }
                    }
                    return Lists.newArrayList();
                });
    }
    
    private void doRefresh() {        
        Timer.Context vcontext = m_loadSnmpInterfacesTimer.time();
        try {
            for (OnmsSnmpInterface snmp: m_snmpInterfaceDao.findAll()) {
                if (!m_nodeToOnmsSnmpTable.contains(snmp.getNode().getId(),snmp.getIfIndex())) {
                    m_nodeToOnmsSnmpTable.put(snmp.getNode().getId(),snmp.getIfIndex(),snmp);
                }
            }
            LOG.info("refresh: Snmp Interface loaded");
        } catch (Exception e){
            LOG.error("Loading Snmp Interface failed: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }


        vcontext = m_loadVerticesTimer.time();
        try {
            for (NodeTopologyEntity node : m_nodeTopologyService.findAll()) {
                addVertices(LinkdVertex.create(node));
            }
            LOG.info("refresh: Loaded Vertices");
        } catch (Exception e){
            LOG.error("Exception Loading Vertices: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }
        
        vcontext = m_loadEdgesTimer.time();
        try {
            loadEdges();
            LOG.info("refresh: Loaded Edges");
        } catch (Exception e){
            LOG.error("Exception Loading Edges: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }
    }

    @Override
    public void refresh() {
        final Timer.Context context = m_loadFullTimer.time();
        try {
            resetContainer();
            m_nodeToOnmsSnmpTable.clear();
            doRefresh();
        } finally {
            context.stop();
        }
        
        LOG.info("refresh: Found {} groups", getGroups().size());
        LOG.info("refresh: Found {} vertices", getVerticesWithoutGroups().size());
        LOG.info("refresh: Found {} edges", getEdges().size());
    }
    public CdpTopologyService getCdpTopologyService() {
        return m_cdpTopologyService;
    }
    public void setCdpTopologyService(CdpTopologyService cdpTopologyService) {
        m_cdpTopologyService = cdpTopologyService;
    }
    public LldpTopologyService getLldpTopologyService() {
        return m_lldpTopologyService;
    }
    public void setLldpTopologyService(LldpTopologyService lldpTopologyService) {
        m_lldpTopologyService = lldpTopologyService;
    }
    public OspfTopologyService getOspfTopologyService() {
        return m_ospfTopologyService;
    }
    public void setOspfTopologyService(OspfTopologyService ospfTopologyService) {
        m_ospfTopologyService = ospfTopologyService;
    }
    public IsisTopologyService getIsisTopologyService() {
        return m_isisTopologyService;
    }
    public void setIsisTopologyService(IsisTopologyService isisTopologyService) {
        m_isisTopologyService = isisTopologyService;
    }
    public NodeTopologyService getNodeTopologyService() {
        return m_nodeTopologyService;
    }
    public void setNodeTopologyService(NodeTopologyService nodeTopologyService) {
        m_nodeTopologyService = nodeTopologyService;
    }
    public BridgeTopologyService getBridgeTopologyService() {
        return m_bridgeTopologyService;
    }
    public void setBridgeTopologyService(BridgeTopologyService bridgeTopologyService) {
        m_bridgeTopologyService = bridgeTopologyService;
    }
    public SnmpInterfaceDao getSnmpInterfaceDao() {
        return m_snmpInterfaceDao;
    }
    public void setSnmpInterfaceDao(SnmpInterfaceDao snmpInterfaceDao) {
        m_snmpInterfaceDao = snmpInterfaceDao;
    }
}
