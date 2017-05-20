package com.outbrain.ob1k.client;

import com.outbrain.ob1k.HttpRequestMethodType;
import com.outbrain.ob1k.Service;
import com.outbrain.ob1k.client.dispatch.DefaultDispatchStrategy;
import com.outbrain.ob1k.client.dispatch.DispatchStrategy;
import com.outbrain.ob1k.client.endpoints.AbstractClientEndpoint;
import com.outbrain.ob1k.client.endpoints.AsyncClientEndpoint;
import com.outbrain.ob1k.client.endpoints.EndpointDescription;
import com.outbrain.ob1k.client.endpoints.StreamClientEndpoint;
import com.outbrain.ob1k.client.targets.EmptyTargetProvider;
import com.outbrain.ob1k.client.targets.TargetProvider;
import com.outbrain.ob1k.common.filters.AsyncFilter;
import com.outbrain.ob1k.common.filters.ServiceFilter;
import com.outbrain.ob1k.common.filters.StreamFilter;
import com.outbrain.ob1k.common.marshalling.RequestMarshallerRegistry;
import com.outbrain.ob1k.http.HttpClient;
import com.outbrain.ob1k.http.common.ContentType;
import com.outbrain.swinfra.metrics.api.MetricFactory;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.outbrain.ob1k.common.endpoints.ServiceEndpointContract.*;

/**
 * @author aronen
 */
public class ClientBuilder<T extends Service> {

  private final Class<T> type;
  private final List<AsyncFilter> asyncFilters;
  private final List<StreamFilter> streamFilters;
  private final Map<String, EndpointDescriptor> endpointDescriptors;
  private final HttpClient.Builder httpClientBuilder;

  private TargetProvider targetProvider = new EmptyTargetProvider();
  private ContentType clientType = ContentType.JSON;
  private DispatchStrategy dispatchStrategy = DefaultDispatchStrategy.INSTANCE;

  public ClientBuilder(final Class<T> type) {
    this.type = type;
    this.httpClientBuilder = new HttpClient.Builder();
    this.asyncFilters = new ArrayList<>();
    this.streamFilters = new ArrayList<>();
    this.endpointDescriptors = new HashMap<>();
  }

  public ClientBuilder<T> addFilter(final ServiceFilter filter) {
    if (filter instanceof AsyncFilter) {
      asyncFilters.add((AsyncFilter) filter);
    }

    if (filter instanceof StreamFilter) {
      streamFilters.add((StreamFilter) filter);
    }

    return this;
  }

  public ClientBuilder<T> setProtocol(final ContentType clientType) {
    this.clientType = clientType;
    return this;
  }

  public ClientBuilder<T> followRedirect(final boolean followRedirect) {
    httpClientBuilder.setFollowRedirect(followRedirect);
    return this;
  }

  public ClientBuilder<T> setMetricFactory(final MetricFactory metricFactory) {
    httpClientBuilder.setMetricFactory(metricFactory);
    return this;
  }

  public ClientBuilder<T> setCompression(final boolean compression) {
    httpClientBuilder.setCompressionEnforced(compression);
    return this;
  }

  public ClientBuilder<T> setMaxConnectionsPerHost(final int maxConnectionsPerHost) {
    httpClientBuilder.setMaxConnectionsPerHost(maxConnectionsPerHost);
    setTotalMaxConnections(maxConnectionsPerHost * 2);
    return this;
  }

  public ClientBuilder<T> setTotalMaxConnections(final int maxConnections) {
    httpClientBuilder.setMaxTotalConnections(maxConnections);
    return this;
  }

  public ClientBuilder<T> setResponseMaxSize(final long responseMaxSize) {
    httpClientBuilder.setResponseMaxSize(responseMaxSize);
    return this;
  }

  public ClientBuilder<T> setRetries(final int retries) {
    httpClientBuilder.setRetries(retries);
    return this;
  }

  public ClientBuilder<T> setConnectionTimeout(final int timeout) {
    httpClientBuilder.setConnectionTimeout(timeout);
    return this;
  }

  public ClientBuilder<T> setRequestTimeout(final int timeout) {
    httpClientBuilder.setRequestTimeout(timeout);
    return this;
  }

  public ClientBuilder<T> setReadTimeout(final int timeout) {
    httpClientBuilder.setReadTimeout(timeout);
    return this;
  }

  public ClientBuilder<T> setTargetProvider(final TargetProvider targetProvider) {
    this.targetProvider = targetProvider == null ? new EmptyTargetProvider() : targetProvider;
    return this;
  }

  public ClientBuilder<T> bindEndpoint(final String methodName, final String path, final ServiceFilter... filters) {
    bindEndpoint(methodName, HttpRequestMethodType.ANY, path, filters);
    return this;
  }

  public ClientBuilder<T> bindEndpoint(final String methodName, final HttpRequestMethodType requestMethodType) {
    bindEndpoint(methodName, requestMethodType, methodName);
    return this;
  }

  public ClientBuilder<T> bindEndpoint(final String methodName, final HttpRequestMethodType requestMethodType,
                                       final ServiceFilter... filters) {
    bindEndpoint(methodName, requestMethodType, methodName, filters);
    return this;
  }

  public ClientBuilder<T> bindEndpoint(final String methodName, final ServiceFilter... filters) {
    bindEndpoint(methodName, HttpRequestMethodType.ANY, methodName, filters);
    return this;
  }

  public ClientBuilder<T> setDispatchStrategy(final DispatchStrategy dispatchStrategy) {
    this.dispatchStrategy = dispatchStrategy;
    return this;
  }

  public ClientBuilder<T> bindEndpoint(final String methodName, final HttpRequestMethodType requestMethodType,
                                       final String path, final ServiceFilter... filters) {
    final List<? extends ServiceFilter> serviceFilters;
    if (filters == null) {
      serviceFilters = new ArrayList<>();
    } else {
      serviceFilters = Arrays.asList(filters);
    }
    endpointDescriptors.put(methodName, new EndpointDescriptor(methodName, path, serviceFilters, requestMethodType));
    return this;
  }

  public T build() {
    if (!type.isInterface()) {
      throw new IllegalArgumentException("Type " + type.getCanonicalName() + " must be an interface as client uses JDK proxy");
    }

    final ClassLoader loader = ClientBuilder.class.getClassLoader();
    final HttpClient httpClient = httpClientBuilder.build();
    final Map<Method, AbstractClientEndpoint> endpoints = extractEndpointsFromType(httpClient);
    final HttpInvocationHandler handler = new HttpInvocationHandler(targetProvider, httpClient, endpoints, dispatchStrategy);

    @SuppressWarnings("unchecked")
    final T proxy = (T) Proxy.newProxyInstance(loader, new Class[]{type, Closeable.class}, handler);
    return proxy;
  }

  private Map<Method, AbstractClientEndpoint> extractEndpointsFromType(final HttpClient httpClient) {
    final Map<Method, AbstractClientEndpoint> endpoints = new HashMap<>();
    final Method[] methods = type.getDeclaredMethods();

    for (final Method method : methods) {
      if (isEndpoint(method)) {
        final String methodName = method.getName();
        final EndpointDescriptor endpointDescriptor = getEndpointDescriptor(methodName);
        final EndpointDescription endpoint = new EndpointDescription(method, type, clientType,
          endpointDescriptor.path, endpointDescriptor.requestMethodType);
        final AbstractClientEndpoint clientEndpoint;

        if (isAsyncMethod(method)) {
          final List<AsyncFilter> filters = mergeFilters(AsyncFilter.class, asyncFilters, endpointDescriptor.filters);
          clientEndpoint = new AsyncClientEndpoint(httpClient, RequestMarshallerRegistry.INSTANCE, endpoint,
            filters.toArray(new AsyncFilter[filters.size()]));
        } else if (isStreamingMethod(method)) {
          final List<StreamFilter> filters = mergeFilters(StreamFilter.class, streamFilters, endpointDescriptor.filters);
          clientEndpoint = new StreamClientEndpoint(httpClient, RequestMarshallerRegistry.INSTANCE, endpoint,
            filters.toArray(new StreamFilter[filters.size()]));
        } else {
          throw new IllegalArgumentException("Interface method " + type.getSimpleName() + "::" + methodName +
            " has illegal signature. All public methods must return ComposableFuture or Observable.");
        }

        endpoints.put(method, clientEndpoint);
      }
    }

    return endpoints;
  }

  private EndpointDescriptor getEndpointDescriptor(final String methodName) {
    return endpointDescriptors.containsKey(methodName) ?
      endpointDescriptors.get(methodName) :
      new EndpointDescriptor(methodName, methodName, null, HttpRequestMethodType.ANY);
  }

  private static <T extends ServiceFilter> List<T> mergeFilters(final Class<T> filterType, final List<T> baseFilters,
                                                                final List<? extends ServiceFilter> specificFilters) {
    final List<T> filters = new ArrayList<>();
    filters.addAll(baseFilters);
    if (specificFilters != null) {
      for (final ServiceFilter filter : specificFilters) {
        if (filter instanceof AsyncFilter) {
          filters.add(filterType.cast(filter));
        }
      }
    }

    return filters;
  }

  /**
   * Describes how endpoint of service looks for the client builder
   *
   * @author marenzon
   */
  private class EndpointDescriptor {

    public final String method;
    public final String path;
    public final List<? extends ServiceFilter> filters;
    public final HttpRequestMethodType requestMethodType;

    public EndpointDescriptor(final String method, final String path, final List<? extends ServiceFilter> filters,
                              final HttpRequestMethodType requestMethodType) {
      this.method = method;
      this.path = path;
      this.filters = filters;
      this.requestMethodType = requestMethodType;
    }
  }
}
