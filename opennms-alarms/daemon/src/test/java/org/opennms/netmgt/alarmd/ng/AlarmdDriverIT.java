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

package org.opennms.netmgt.alarmd.ng;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.MockDatabase;
import org.opennms.core.test.db.TemporaryDatabaseAware;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.netmgt.alarmd.AlarmManager;
import org.opennms.netmgt.alarmd.Alarmd;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-alarmd.xml"
})
@JUnitConfigurationEnvironment(systemProperties = {"alarmd.pseudoclock=true"})
@JUnitTemporaryDatabase(dirtiesContext=false,tempDbClass=MockDatabase.class)
public class AlarmdDriverIT implements TemporaryDatabaseAware<MockDatabase>, ActionVisitor {

    static Scenario SCENARIO;
    static ScenarioResults RESULTS;

    @Autowired
    private Alarmd m_alarmd;

    @Autowired
    private MonitoringLocationDao m_locationDao;

    @Autowired
    private NodeDao m_nodeDao;

    @Autowired
    private AlarmDao m_alarmDao;

    @Autowired
    private MockEventIpcManager m_eventMgr;

    @Autowired
    private TransactionTemplate m_transactionTemplate;

    private MockDatabase m_database;

    @Autowired
    private AlarmManager m_alarmManager;

    @Override
    public void setTemporaryDatabase(final MockDatabase database) {
        m_database = database;
    }

    private final long tickLength = 1;

    private ScenarioResults results = new ScenarioResults();

    @Before
    public void setUp() {
        // Async.
        m_eventMgr.setSynchronous(false);

        // Events need database IDs to make alarmd happy
        m_eventMgr.setEventWriter(m_database);

        // Events need to real nodes too
        final OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "node1");
        node.setId(1);
        m_nodeDao.save(node);
    }

    @After
    public void tearDown() {
        m_alarmd.destroy();
    }


    @Test
    public void canDriveScenario() {
        if (SCENARIO.getActions().size() == 0) {
            RESULTS = results;
            return;
        }

        final Map<Long,List<Action>> actionsByTick = SCENARIO.getActions().stream()
                .collect(Collectors.groupingBy(a -> roundToTick(a.getTime())));

        final long start = Math.max(SCENARIO.getActions().stream()
                .min(Comparator.comparing(Action::getTime))
                .map(e -> roundToTick(e.getTime()))
                .get() - tickLength, 0);
        final long end = SCENARIO.getActions().stream()
                .max(Comparator.comparing(Action::getTime))
                .map(e -> roundToTick(e.getTime()))
                .get() + tickLength;

        if (start > 0) {
            // Tick
            m_alarmManager.getClock().advanceTime(tickLength, TimeUnit.MILLISECONDS);
            m_alarmManager.tick();
        }

        for (long now = start; now <= end; now += tickLength) {
            // Perform the actions
            final List<Action> actions = actionsByTick.get(now);
            if (actions != null) {
                for (Action  a : actions) {
                    a.visit(this);
                }
            }

            // Tick
            m_alarmManager.getClock().advanceTime(tickLength, TimeUnit.MILLISECONDS);
            m_alarmManager.tick();

            results.addAlarms(now, m_alarmDao.findAll());
        }

        // Tick every 5 minutes for the next 24 hours
        tickAtRateUntil(TimeUnit.MINUTES.toMillis(5),
                end,
                end + TimeUnit.DAYS.toMillis(1));

        // Tick every hour for the next week
        tickAtRateUntil(TimeUnit.HOURS.toMillis(1),
                end + TimeUnit.DAYS.toMillis(1),
                end + TimeUnit.DAYS.toMillis(8));

        RESULTS = results;
    }

    private void tickAtRateUntil(long tickLength, long start, long end) {
        // Now keep tick'ing at an accelerated rate for another week
        for (long now = start; now <= end; now += tickLength) {
            // Tick
            m_alarmManager.getClock().advanceTime(tickLength, TimeUnit.MILLISECONDS);
            m_alarmManager.tick();
            results.addAlarms(now, m_alarmDao.findAll());
        }
    }

    private long roundToTick(Date date) {
        return Math.floorDiv(date.getTime(),tickLength) * tickLength;
    }

    @Override
    public void sendEvent(Event e) {
        m_eventMgr.sendNow(e, true);
    }

    @Override
    public void acknowledgeAlarm(String ackUser, Date ackTime, Function<OnmsAlarm, Boolean> filter) {
        m_transactionTemplate.execute((t) -> {
            m_alarmDao.findAll().stream()
                    .filter(filter::apply)
                    .forEach(a -> {
                        a.setAlarmAckUser(ackUser);
                        a.setAlarmAckTime(ackTime);
                    });
            return null;
        });
    }
}