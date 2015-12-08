package com.outbrain.ob1k.server.services;

import com.outbrain.ob1k.server.build.ServerBuilderState;
import com.outbrain.ob1k.server.registry.ServiceRegistryView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class EndpointMappingServiceProviderTest {

  @Mock
  private ServerBuilderState state;
  @Mock
  private ServiceRegistryView registryView;

  @Test
  public void shouldAddServiceDescriptorForEndpointMappingService() {
    // given
    Mockito.when(state.getRegistry()).thenReturn(registryView);

    // when
    EndpointMappingServiceProvider.registerMappingService("path").provide(state);

    // then
    Mockito.verify(state).addServiceDescriptor(any(EndpointMappingService.class), eq("path"));
  }
}