/**
 * Copyright 2016-2017 Symphony Integrations - Symphony LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.symphonyoss.integration.healthcheck.services.indicators;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonyoss.integration.authentication.api.enums.ServiceName;
import org.symphonyoss.integration.event.MessageMLVersionUpdatedEventData;
import org.symphonyoss.integration.healthcheck.services.MockApplicationPublisher;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;

/**
 * Test class to validate {@link AgentHealthIndicator}
 * Created by rsanchez on 23/11/16.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@ContextConfiguration(classes = {IntegrationProperties.class, AgentHealthIndicator.class})
public class AgentHealthIndicatorTest {

  private static final ServiceName SERVICE_NAME = ServiceName.AGENT;

  @MockBean
  private LogMessageSource logMessageSource;

  @Autowired
  private AgentHealthIndicator indicator;

  private MockApplicationPublisher<MessageMLVersionUpdatedEventData> publisher =
      new MockApplicationPublisher<>();

  @Before
  public void init() {
    ReflectionTestUtils.setField(indicator, "publisher", publisher);
  }

  @Test
  public void testServiceName() {
    assertEquals(SERVICE_NAME, indicator.getServiceName());
  }

}
