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

package org.symphonyoss.integration.healthcheck.connectivity;

import static javax.ws.rs.core.Response.Status.Family.REDIRECTION;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.symphonyoss.integration.authentication.AuthenticationProxy;
import org.symphonyoss.integration.authentication.exception.UnregisteredUserAuthException;
import org.symphonyoss.integration.model.yaml.Application;
import org.symphonyoss.integration.model.yaml.ApplicationState;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Holds common methods to all integration bridge connectivity indicators.
 *
 * Created by Milton Quilzini on 11/11/16.
 */
public abstract class AbstractConnectivityHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractConnectivityHealthIndicator.class);

  /**
   * Cache period (in seconds) for the connectivity status of the Integration Bridge.
   */
  private static final int CONNECTIVITY_CACHE_PERIOD_SECS = 20;

  /**
   * HTTP Connection timeout (in miliseconds)
   */
  private static final int CONNECT_TIMEOUT_MILLIS = 1000;

  /**
   * HTTP Read timeout (in miliseconds)
   */
  private static final int READ_TIMEOUT_MILLIS = 5000;

  @Autowired
  protected IntegrationProperties properties;

  @Autowired
  private AuthenticationProxy authenticationProxy;

  /**
   * Cache for the connectivity statuses.
   */
  private LoadingCache<String, Status> connectivityStatusCache;

  @PostConstruct
  public void init() {
    connectivityStatusCache = initConnectivityStatusCache();
  }

  private LoadingCache<String, Status> initConnectivityStatusCache() {
    return CacheBuilder.newBuilder()
        .expireAfterWrite(CONNECTIVITY_CACHE_PERIOD_SECS, TimeUnit.SECONDS)
        .build(new CacheLoader<String, Status>() {
          @Override
          public Status load(String key) throws Exception {
            if (key.equals(getHealthName())) {
              return currentConnectivityStatus();
            }

            return Status.UNKNOWN;
          }
        });
  }

  /**
   * Determine the user to be used on connectivity checks, looking on the base YAML file for a
   * provisioned one.
   * @return the user name.
   */
  protected String availableIntegrationUser() {
    for (Application app : this.properties.getApplications().values()) {
      if (app.getState().equals(ApplicationState.PROVISIONED)) {
        return app.getComponent();
      }
    }

    return StringUtils.EMPTY;
  }

  /**
   * Build the specific health check URL for the component which connectivity will be checked for.
   * @return the built service URL.
   */
  protected abstract String getHealthCheckUrl();

  /**
   * Returns the health name.
   * @return Health name
   */
  protected abstract String getHealthName();

  @Override
  public Health health() {
    String healthName = getHealthName();

    Status status;
    try {
      status = connectivityStatusCache.get(healthName);
    } catch (UncheckedExecutionException | ExecutionException e) {
      LOG.error(String.format("Unable to retrieve %s connectivity status", healthName), e);
      status = Status.UNKNOWN;
    }

    return Health.status(status).build();
  }

  /**
   * Hits the built URL to the corresponding service, checks its response
   * {@link Response.Status.Family}, and returns the corresponding connectivity status.
   * @return Connectivity status: "UP" if the check is successful, "DOWN" otherwise.
   */
  public Status currentConnectivityStatus() {
    try {
      Client client = authenticationProxy.httpClientForUser(availableIntegrationUser());

      Invocation.Builder invocationBuilder = client.target(getHealthCheckUrl())
          .property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MILLIS)
          .property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT_MILLIS)
          .request()
          .accept(MediaType.APPLICATION_JSON_TYPE);

      Response response = invocationBuilder.get();
      Response.Status.Family statusFamily = response.getStatusInfo().getFamily();

      return statusFamily.equals(SUCCESSFUL) || statusFamily.equals(REDIRECTION) ? Status.UP : Status.DOWN;
    } catch (ProcessingException | UnregisteredUserAuthException e) {
      LOG.error("Trying to reach {} but getting exception: {}", getHealthCheckUrl(), e.getMessage(), e);
      return Status.DOWN;
    }
  }
}
