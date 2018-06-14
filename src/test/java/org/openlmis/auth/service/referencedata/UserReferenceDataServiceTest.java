/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.auth.service.referencedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openlmis.auth.DummyUserMainDetailsDto;
import org.openlmis.auth.dto.LocalizedMessageDto;
import org.openlmis.auth.dto.PageDto;
import org.openlmis.auth.dto.ResultDto;
import org.openlmis.auth.dto.referencedata.UserMainDetailsDto;
import org.openlmis.auth.exception.ExternalApiException;
import org.openlmis.auth.service.BaseCommunicationService;
import org.openlmis.auth.service.BaseCommunicationServiceTest;
import org.openlmis.auth.service.DataRetrievalException;
import org.openlmis.auth.util.DynamicResultDtoTypeReference;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;

public class UserReferenceDataServiceTest extends BaseCommunicationServiceTest {

  UserReferenceDataService service;

  @Override
  protected BaseCommunicationService getService() {
    return new UserReferenceDataService();
  }

  @Override
  protected UserReferenceDataService prepareService() {
    BaseCommunicationService service = super.prepareService();

    ReflectionTestUtils.setField(service, "serviceUrl", "http://localhost/referencedata");

    return (UserReferenceDataService) service;
  }

  @Before
  public void before() {
    service = prepareService();
  }

  @Test
  public void shouldFindUserByEmail() {
    // given
    UserMainDetailsDto userMainDetailsDto = new DummyUserMainDetailsDto();

    Map<String, Object> payload = new HashMap<>();
    payload.put("email", userMainDetailsDto.getEmail());

    ResponseEntity response = mock(ResponseEntity.class);

    // when
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
        any(ParameterizedTypeReference.class)))
        .thenReturn(response);

    PageDto<UserMainDetailsDto> page = new PageDto<>(new PageImpl<>(ImmutableList.of(
        userMainDetailsDto)));

    when(response.getBody()).thenReturn(page);

    UserMainDetailsDto user = service.findUserByEmail(userMainDetailsDto.getEmail());

    // then
    verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
        entityCaptor.capture(), any(ParameterizedTypeReference.class));

    URI uri = uriCaptor.getValue();
    String url = service.getServiceUrl() + service.getUrl() + "search";

    assertThat(uri.toString()).isEqualTo(url);
    assertThat(user.getEmail()).isEqualTo(userMainDetailsDto.getEmail());

    assertAuthHeader(entityCaptor.getValue());
    assertThat(entityCaptor.getValue().getBody()).isEqualTo(payload);
  }

  @Test
  public void shouldReturnNullIfUserCannotBeFoundByEmail() {
    // given
    String email = "example@test.org";

    ResponseEntity response = mock(ResponseEntity.class);

    Map<String, Object> payload = new HashMap<>();
    payload.put("email", email);

    // when
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
        any(ParameterizedTypeReference.class)))
        .thenReturn(response);

    PageDto<UserMainDetailsDto> page = new PageDto<>(new PageImpl<>(Collections.emptyList()));
    when(response.getBody()).thenReturn(page);

    UserMainDetailsDto user = service.findUserByEmail(email);

    // then
    verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
        entityCaptor.capture(), any(ParameterizedTypeReference.class));

    URI uri = uriCaptor.getValue();
    String url = service.getServiceUrl() + service.getUrl() + "search";

    assertThat(uri.toString()).isEqualTo(url);
    assertThat(user).isNull();

    assertAuthHeader(entityCaptor.getValue());
    assertThat(entityCaptor.getValue().getBody()).isEqualTo(payload);
  }

  @Test
  public void shouldSaveUser() {
    // given
    UserMainDetailsDto request = new DummyUserMainDetailsDto();
    ResponseEntity response = mock(ResponseEntity.class);

    // when
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class),
        eq(service.getResultClass())))
        .thenReturn(response);

    when(response.getBody()).thenReturn(request);

    UserMainDetailsDto user = service.putUser(request);

    // then
    verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.PUT),
        entityCaptor.capture(), eq(service.getResultClass()));

    URI uri = uriCaptor.getValue();
    String url = service.getServiceUrl() + service.getUrl();

    assertThat(uri.toString()).isEqualTo(url);
    assertThat(user).isEqualTo(request);
    assertAuthHeader(entityCaptor.getValue());
  }

  @Test(expected = ExternalApiException.class)
  public void shouldPassErrorMessageFromExternalService() throws IOException {
    HttpStatusCodeException exp = mock(HttpStatusCodeException.class);
    when(exp.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
    when(exp.getResponseBodyAsString()).thenReturn("body");

    when(objectMapper.readValue("body", LocalizedMessageDto.class))
        .thenReturn(new LocalizedMessageDto("key", "message"));

    doThrow(exp)
        .when(restTemplate)
        .exchange(
            any(URI.class), eq(HttpMethod.PUT),
            any(HttpEntity.class), eq(service.getResultClass()));

    service.putUser(new DummyUserMainDetailsDto());
  }

  @Test(expected = DataRetrievalException.class)
  public void shouldThrowExceptionIfThereWereProblemWithRequest() {
    HttpStatusCodeException exp = mock(HttpStatusCodeException.class);
    when(exp.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
    when(exp.getResponseBodyAsString()).thenReturn("body");

    doThrow(exp)
        .when(restTemplate)
        .exchange(
            any(URI.class), eq(HttpMethod.PUT),
            any(HttpEntity.class), eq(service.getResultClass()));

    service.putUser(new DummyUserMainDetailsDto());
  }

  @Test
  public void shouldReturnTrueIfUserHasRight() {
    executeHasRightEndpoint(true);
  }

  @Test
  public void shouldReturnFalseIfUserHasNoRight() {
    executeHasRightEndpoint(false);
  }

  private void executeHasRightEndpoint(boolean expectedValue) {
    // given
    UUID userId = UUID.randomUUID();
    UUID rightId = UUID.randomUUID();
    ResponseEntity response = mock(ResponseEntity.class);

    // when
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
        any(HttpEntity.class), any(DynamicResultDtoTypeReference.class)))
        .thenReturn(response);
    when(response.getBody()).thenReturn(new ResultDto<>(expectedValue));

    ResultDto result = service.hasRight(userId, rightId);

    // then
    Assert.assertThat(result.getResult(), is(expectedValue));

    verify(restTemplate, atLeastOnce()).exchange(
        uriCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(),
        any(DynamicResultDtoTypeReference.class)
    );

    URI uri = uriCaptor.getValue();
    String url = service.getServiceUrl() + service.getUrl()
        + userId + "/hasRight?rightId=" + rightId;

    assertThat(uri.toString()).isEqualTo(url);

    assertAuthHeader(entityCaptor.getValue());
    Assert.assertThat(entityCaptor.getValue().getBody(), is(nullValue()));
  }
}
