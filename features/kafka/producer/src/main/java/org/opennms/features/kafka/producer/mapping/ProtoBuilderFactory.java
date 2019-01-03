/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.features.kafka.producer.mapping;

import org.opennms.features.kafka.producer.model.OpennmsModelProtos;

/**
 * Factory that supplies builder objects to generated MapStruct mapping code.
 */
class ProtoBuilderFactory {
    
    // Note: Any mapping classes that target a builder should include
    // "nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS" in their @Mapper annotation since the protobuf builders
    // are picky about allowing nulls in their setters
    
    OpennmsModelProtos.Node.Builder createNodeBuilder() {
        return OpennmsModelProtos.Node.newBuilder();
    }

    OpennmsModelProtos.HwEntity.Builder createHwEntityBuilder() {
        return OpennmsModelProtos.HwEntity.newBuilder();
    }

    OpennmsModelProtos.HwAlias.Builder createHwAliasBuilder() {
        return OpennmsModelProtos.HwAlias.newBuilder();
    }

    OpennmsModelProtos.IpInterface.Builder createIpInterfaceBuilder() {
        return OpennmsModelProtos.IpInterface.newBuilder();
    }

    OpennmsModelProtos.SnmpInterface.Builder createSnmpInterfaceBuilder() {
        return OpennmsModelProtos.SnmpInterface.newBuilder();
    }

    OpennmsModelProtos.NodeCriteria.Builder createNodeCriteriaBuilder() {
        return OpennmsModelProtos.NodeCriteria.newBuilder();
    }

    OpennmsModelProtos.AlarmFeedback.Builder createAlarmFeedbackBuilder() {
        return OpennmsModelProtos.AlarmFeedback.newBuilder();
    }

    OpennmsModelProtos.Alarm.Builder createAlarmBuilder() {
        return OpennmsModelProtos.Alarm.newBuilder();
    }

    OpennmsModelProtos.Event.Builder createEventBuilder() {
        return OpennmsModelProtos.Event.newBuilder();
    }
}