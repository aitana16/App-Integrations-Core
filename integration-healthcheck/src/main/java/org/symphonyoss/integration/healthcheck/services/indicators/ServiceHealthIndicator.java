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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.symphonyoss.integration.authentication.api.enums.ServiceName;
import org.symphonyoss.integration.healthcheck.config.CachingConfiguration;
import org.symphonyoss.integration.healthcheck.services.IntegrationBridgeServiceInfo;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;

/**
 * Abstract class that holds common methods to all service health indicators.
 *
 * Created by rsanchez on 27/01/17.
 */
public abstract class ServiceHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceHealthIndicator.class);

  private IntegrationBridgeServiceInfo serviceInfo;


  @Autowired
  protected IntegrationProperties properties;

  @Autowired
  protected ApplicationEventPublisher publisher;

  @Autowired
  protected LogMessageSource logMessageSource;

  @Override
  public Health health() {
    String serviceName = mountUserFriendlyServiceName();

    // Get the concurrent map values and put it in the service info
    return reportServiceHealth(serviceName, getServiceInfo());
  }

  /**
   * Returns the service health
   * @param serviceName Service name
   * @param service Service response
   * @return Service health
   */
  private Health reportServiceHealth(String serviceName, IntegrationBridgeServiceInfo service) {
    Health health;

    if (service == null) {
      health = Health.down().build();
    } else {

      health = Health.status(service.getConnectivity())
          .withDetail(serviceName, service)
          .build();
    }
    return health;
  }

  @Cacheable(cacheResolver = CachingConfiguration.CACHE_RESOLVER_NAME)
  public IntegrationBridgeServiceInfo getServiceInfo() {
    return serviceInfo;
  }

  @CacheEvict(cacheResolver = CachingConfiguration.CACHE_RESOLVER_NAME)
  public void setServiceInfo(IntegrationBridgeServiceInfo serviceInfo) {
    this.serviceInfo = serviceInfo;
  }

  /**
   * Returns the friendly name when it exists, or else, return the toString method for the
   * Service Name
   * @return
   */
  public String mountUserFriendlyServiceName() {
    String friendlyServiceName = getFriendlyServiceName();
    if (StringUtils.isEmpty(friendlyServiceName)) {
      friendlyServiceName = getServiceName().toString();
    }
    return friendlyServiceName;
  }

  /**
   * Returns the service name.
   * @return Service name
   */
  protected abstract ServiceName getServiceName();

  /**
   * Returns the service name to be displayed to a user.
   * @return Friendly service name
   */
  protected String getFriendlyServiceName() {
    return getServiceName() != null ? getServiceName().toString() : "No name";
  }

}
