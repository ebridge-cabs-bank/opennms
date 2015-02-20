/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2014 The OpenNMS Group, Inc.
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

package org.opennms.smoketest;

import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

public class EventsPageTest extends OpenNMSSeleniumTestCase {
    @Before
    public void setUp() throws Exception {
        m_driver.get(BASE_URL + "opennms/event/list");
    }

    @Test
    public void testLinksAndForms() throws Exception {
        findElementByName("event_search");
        findElementByName("acknowledge_form");
        findElementByLink("ID");
        findElementByLink("Severity");
        findElementByLink("Time");
        findElementByLink("Node");
        findElementByLink("Interface");
        findElementByLink("Service");
    }

    @Test 
    public void testAdvancedSearch() throws InterruptedException {
        findElementByXpath("//button[@type='button' and text() = 'Search']").click();
        findElementByName("msgsub");
        findElementByName("iplike");
        findElementByName("nodenamelike");
        findElementByName("severity");
        findElementByName("exactuei");
        findElementByName("service");
        findElementByName("usebeforetime");
    }

    @Test
    public void testNodeIdNotFoundPage() throws InterruptedException {
        m_driver.get(BASE_URL + "opennms/event/detail.jsp?id=999999999");
        m_driver.findElement(By.xpath("//p[text()='Event not found in database.']"));
    }

}
