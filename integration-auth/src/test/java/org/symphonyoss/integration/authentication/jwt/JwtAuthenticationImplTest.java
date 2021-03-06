package org.symphonyoss.integration.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.cache.LoadingCache;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.RsaProvider;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonyoss.integration.Integration;
import org.symphonyoss.integration.authentication.AuthenticationProxy;
import org.symphonyoss.integration.authentication.api.AppAuthenticationProxy;
import org.symphonyoss.integration.authentication.api.model.AppToken;
import org.symphonyoss.integration.authentication.api.model.JwtPayload;
import org.symphonyoss.integration.authentication.api.model.PodCertificate;
import org.symphonyoss.integration.exception.authentication.ExpirationException;
import org.symphonyoss.integration.exception.authentication.UnauthorizedUserException;
import org.symphonyoss.integration.exception.bootstrap.UnexpectedBootstrapException;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.model.config.IntegrationSettings;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;
import org.symphonyoss.integration.pod.api.client.IntegrationAuthApiClient;
import org.symphonyoss.integration.pod.api.client.IntegrationHttpApiClient;
import org.symphonyoss.integration.pod.api.client.PodInfoClient;
import org.symphonyoss.integration.pod.api.model.PodInfo;
import org.symphonyoss.integration.service.IntegrationBridge;
import org.symphonyoss.integration.utils.RsaKeyUtils;
import org.symphonyoss.integration.utils.TokenUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link JwtAuthenticationImpl}
 *
 * Created by rsanchez on 31/07/17.
 */
@RunWith(MockitoJUnitRunner.class)
public class JwtAuthenticationImplTest {

  private static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
  private static final String MOCK_SESSION_TOKEN = "mockSessionToken";
  private static final String MOCK_APP_TOKEN = "mockAppToken";
  private static final String MOCK_SYMPHONY_TOKEN = "mockSymphonyToken";
  private static final String MOCK_CONFIG_ID = "mockConfigId";
  private static final String MOCK_APP_ID = "mockAppId";
  private static final Long USER_ID = 12345L;
  private static final Integer PUBLIC_CERT_CACHE_DURATION = 60;
  private static final String MOCK_POD_ID = "111";
  private static final String MOCK_INVALID_POD_ID = "0";

  private static final String POD_ID = "podId";
  private static final String EXTERNAL_POD_ID = "externalPodId";

  @Mock
  private LogMessageSource logMessage;

  @Mock
  private TokenUtils tokenUtils;

  @Mock
  private AppAuthenticationProxy appAuthenticationService;

  @Mock
  private AuthenticationProxy authenticationProxy;

  @Mock
  private IntegrationBridge integrationBridge;

  @Mock
  private IntegrationAuthApiClient apiClient;

  @Mock
  private Integration integration;

  @Mock
  private IntegrationSettings integrationSettings;

  @Mock
  private IntegrationProperties properties;

  @Mock
  private IntegrationHttpApiClient integrationHttpApiClient;

  @Spy
  private RsaKeyUtils rsaKeyUtils = new RsaKeyUtils();

  @Mock
  private PodInfoClient podInfoClient;

  @InjectMocks
  private JwtAuthenticationImpl jwtAuthentication;

  private AppToken mockAppToken;

  private PodCertificate mockValidCertificate = new PodCertificate();

  private PublicKey mockPublicKey;

  private String mockJwt;

  private JwtPayload mockJwtPayload;

  @Before
  public void init() {
    prepareJwtScenario(false);
    mockAppToken = new AppToken(MOCK_CONFIG_ID, MOCK_APP_TOKEN, MOCK_SYMPHONY_TOKEN);
    doReturn(integration).when(integrationBridge).getIntegrationById(MOCK_CONFIG_ID);
    doReturn(integrationSettings).when(integration).getSettings();
    doReturn(MOCK_CONFIG_ID).when(integrationSettings).getType();
    doReturn(MOCK_APP_ID).when(properties).getApplicationId(MOCK_CONFIG_ID);
    doReturn(MOCK_SESSION_TOKEN).when(authenticationProxy).getSessionToken(MOCK_CONFIG_ID);
    doReturn(mockValidCertificate).when(appAuthenticationService)
        .getPodPublicCertificate(MOCK_APP_ID);
    doReturn(PUBLIC_CERT_CACHE_DURATION).when(properties).getPublicPodCertificateCacheDuration();
    ReflectionTestUtils.invokeMethod(jwtAuthentication, "initializeCache",
        PUBLIC_CERT_CACHE_DURATION);
  }

  private void prepareJwtScenario(boolean expiredJwt) {
    try {
      Calendar calendar = Calendar.getInstance();
      calendar.set(Calendar.MILLISECOND, 0);
      if (!expiredJwt) {
        calendar.add(Calendar.HOUR, 1);
      }
      Long expirationInSeconds = calendar.getTimeInMillis() / 1000;

      mockJwtPayload = new JwtPayload("www.symphony.com", "Symphony Communication Services LLC.",
          USER_ID.toString(), expirationInSeconds, null);

      KeyPair keypair = RsaProvider.generateKeyPair(1024);
      PrivateKey privateKey = keypair.getPrivate();
      mockPublicKey = keypair.getPublic();
      mockJwt = Jwts.builder().
          setSubject(mockJwtPayload.getUserId()).
          setExpiration(mockJwtPayload.getExpirationDate()).
          setAudience(mockJwtPayload.getApplicationId()).
          setIssuer(mockJwtPayload.getCompanyName()).
          signWith(SignatureAlgorithm.RS512, privateKey).compact();

    } catch (Exception e) {
      throw new RuntimeException("Preparation error.", e);
    }
  }

  @Test
  public void testGetJwtTokenEmpty() {
    JwtPayload result = jwtAuthentication.getJwtToken(MOCK_CONFIG_ID, StringUtils.EMPTY);
    assertNull(result);
  }

  @Test
  public void testGetJwtTokenInvalid() {
    JwtPayload result = jwtAuthentication.getJwtToken(MOCK_CONFIG_ID, "?");
    assertNull(result);
  }

  @Test
  public void testGetJwtToken() {
    doReturn(mockPublicKey).when(rsaKeyUtils).getPublicKeyFromCertificate(null);
    String authorizationHeader = AUTHORIZATION_HEADER_PREFIX.concat(mockJwt);
    JwtPayload result = jwtAuthentication.getJwtToken(MOCK_CONFIG_ID, authorizationHeader);

    assertEquals(mockJwtPayload, result);
  }

  @Test(expected = UnauthorizedUserException.class)
  public void testGetUserIdEmptyToken() {
    jwtAuthentication.getUserId(null);
  }

  @Test
  public void testGetUserId() {
    Long userId = jwtAuthentication.getUserId(mockJwtPayload);
    assertEquals(USER_ID, userId);
  }

  @Test
  public void testGetUserIdFromAuthorizationHeader() {
    doReturn(mockPublicKey).when(rsaKeyUtils).getPublicKeyFromCertificate(null);
    String authorizationHeader = AUTHORIZATION_HEADER_PREFIX.concat(mockJwt);
    Long userId = jwtAuthentication.getUserIdFromAuthorizationHeader(MOCK_CONFIG_ID,
        authorizationHeader);
    assertEquals(USER_ID, userId);
  }

  @Test
  public void testAuthenticate() {
    doReturn(MOCK_APP_TOKEN).when(tokenUtils).generateToken();
    doReturn(mockAppToken).when(appAuthenticationService).authenticate(MOCK_APP_ID,
        MOCK_APP_TOKEN);

    String result = jwtAuthentication.authenticate(MOCK_CONFIG_ID);
    assertEquals(MOCK_APP_TOKEN, result);
  }

  @Test
  public void testIsValidTokenPair() {
    doReturn(MOCK_APP_TOKEN).when(tokenUtils).generateToken();
    doReturn(mockAppToken).when(appAuthenticationService).authenticate(MOCK_APP_ID,
        MOCK_APP_TOKEN);
    doReturn(mockAppToken).when(apiClient).getAppAuthenticationToken(
        MOCK_SESSION_TOKEN, MOCK_CONFIG_ID, MOCK_APP_TOKEN);

    boolean result = jwtAuthentication.isValidTokenPair(
        MOCK_CONFIG_ID, MOCK_APP_TOKEN, MOCK_SYMPHONY_TOKEN);
    assertTrue(result);
  }

  @Test
  public void testIsInvalidTokenPair() {
    doReturn(MOCK_APP_TOKEN).when(tokenUtils).generateToken();
    doReturn(mockAppToken).when(appAuthenticationService).authenticate(MOCK_APP_ID,
        MOCK_APP_TOKEN);

    doReturn(null).when(apiClient).getAppAuthenticationToken(
        MOCK_SESSION_TOKEN, MOCK_CONFIG_ID, MOCK_APP_TOKEN);

    boolean result = jwtAuthentication.isValidTokenPair(
        MOCK_CONFIG_ID, MOCK_APP_TOKEN, MOCK_SYMPHONY_TOKEN);
    assertFalse(result);
  }

  @Test
  public void testParseJwtPayload() {
    doReturn(mockPublicKey).when(rsaKeyUtils).getPublicKeyFromCertificate(null);
    JwtPayload jwtPayload = jwtAuthentication.parseJwtPayload(MOCK_CONFIG_ID, mockJwt);
    assertEquals(mockJwtPayload, jwtPayload);
  }

  @Test(expected = ExpirationException.class)
  public void testParseJwtPayloadExpired() {
    prepareJwtScenario(true);
    doReturn(mockPublicKey).when(rsaKeyUtils).getPublicKeyFromCertificate(null);

    JwtPayload jwtPayload = jwtAuthentication.parseJwtPayload(MOCK_CONFIG_ID, mockJwt);
    assertEquals(mockJwtPayload, jwtPayload);
  }

  @Test(expected = UnexpectedBootstrapException.class)
  public void testGetIntegrationAndCheckAvailabilityNull() {
    doReturn(null).when(integrationBridge).getIntegrationById(MOCK_CONFIG_ID);
    jwtAuthentication.isValidTokenPair(MOCK_CONFIG_ID, MOCK_APP_TOKEN, MOCK_SYMPHONY_TOKEN);
  }

  @Test
  public void testInit() {
    jwtAuthentication.init();

    Object apiClientObj = ReflectionTestUtils.getField(jwtAuthentication, "apiClient");
    assertTrue(apiClientObj instanceof IntegrationAuthApiClient);

    Object cacheObj = ReflectionTestUtils.getField(jwtAuthentication,
        "podPublicSignatureVerifierCache");
    assertTrue(cacheObj instanceof LoadingCache);
    LoadingCache cache = (LoadingCache) cacheObj;
    assertEquals(0, cache.size());
  }

  @Test
  public void testPodInfoNullPodId() {
    assertFalse(jwtAuthentication.checkPodInfo(MOCK_CONFIG_ID, null));
  }

  @Test
  public void testPodInfo() {
    ReflectionTestUtils.setField(jwtAuthentication, "podInfo", null);

    Map<String, Object> data = new HashMap<>();
    data.put(POD_ID, MOCK_POD_ID);
    data.put(EXTERNAL_POD_ID, MOCK_POD_ID);

    PodInfo podInfo = new PodInfo(data);

    doReturn(podInfo).when(podInfoClient).getPodInfo(MOCK_SESSION_TOKEN);

    assertTrue(jwtAuthentication.checkPodInfo(MOCK_CONFIG_ID, MOCK_POD_ID));
    assertFalse(jwtAuthentication.checkPodInfo(MOCK_CONFIG_ID, MOCK_INVALID_POD_ID));

    verify(podInfoClient, times(1)).getPodInfo(MOCK_SESSION_TOKEN);
  }

}
