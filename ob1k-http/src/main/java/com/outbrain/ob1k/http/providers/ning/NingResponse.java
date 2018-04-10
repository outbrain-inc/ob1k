package com.outbrain.ob1k.http.providers.ning;

import com.outbrain.ob1k.concurrent.Try;
import com.outbrain.ob1k.http.TypedResponse;
import com.outbrain.ob1k.http.common.Cookie;
import com.outbrain.ob1k.http.marshalling.MarshallingStrategy;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author marenzon
 */
class NingResponse<T> implements TypedResponse<T> {

  private final MarshallingStrategy marshallingStrategy;
  private final Type type;
  private final Response ningResponse;
  private volatile T typedBody;

  NingResponse(final Response ningResponse, final Type type,
                      final MarshallingStrategy marshallingStrategy) {

    this.ningResponse = checkNotNull(ningResponse, "ningResponse may not be null");
    this.marshallingStrategy = marshallingStrategy;
    this.type = type;
  }

  @Override
  public int getStatusCode() {

    return ningResponse.getStatusCode();
  }

  @Override
  public String getStatusText() {

    return ningResponse.getStatusText();
  }

  @Override
  public URI getUri() throws URISyntaxException {

    return ningResponse.getUri().toJavaNetURI();
  }

  @Override
  public String getUrl() {

    return ningResponse.getUri().toUrl();
  }

  @Override
  public String getContentType() {

    return ningResponse.getContentType();
  }

  @Override
  public T getTypedBody() throws IOException {

    if (typedBody == null) {

      checkNotNull(marshallingStrategy, "marshallingStrategy may not be null");
      checkNotNull(type, "class type may not be null");

      typedBody = marshallingStrategy.unmarshall(type, this);
    }

    return typedBody;
  }

  @Override
  public String getResponseBody() throws IOException {

    return ningResponse.getResponseBody();
  }

  @Override
  public byte[] getResponseBodyAsBytes() throws IOException {

    return ningResponse.getResponseBodyAsBytes();
  }

  @Override
  public InputStream getResponseBodyAsStream() throws IOException {

    return ningResponse.getResponseBodyAsStream();
  }

  @Override
  public ByteBuffer getResponseBodyAsByteBuffer() throws IOException {

    return ningResponse.getResponseBodyAsByteBuffer();
  }

  @Override
  public List<Cookie> getCookies() {

    return transformNingResponseCookies(ningResponse.getCookies());
  }

  @Override
  public String getHeader(final String name) {

    return ningResponse.getHeader(name);
  }

  @Override
  public List<String> getHeaders(final String name) {

    return ningResponse.getHeaders(name);
  }

  @Override
  public Iterable<Map.Entry<String, String>> getHeaders() {

    return ningResponse.getHeaders();
  }

  @Override
  public boolean isRedirected() {

    return ningResponse.isRedirected();
  }

  @Override
  public boolean hasResponseBody() {

    return ningResponse.hasResponseBody();
  }

  @Override
  public boolean hasResponseStatus() {

    return ningResponse.hasResponseStatus();
  }

  @Override
  public boolean hasResponseHeaders() {

    return ningResponse.hasResponseHeaders();
  }

  private List<Cookie> transformNingResponseCookies(final List<org.asynchttpclient.cookie.Cookie> cookies) {

    final Function<org.asynchttpclient.cookie.Cookie, Cookie> transformer = ningCookie ->
      new Cookie(ningCookie.getName(), ningCookie.getValue(), ningCookie.getDomain(),
        ningCookie.getPath(), ningCookie.getMaxAge(),
        ningCookie.isSecure(), ningCookie.isHttpOnly());

    return cookies.stream().map(transformer).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    final StringBuilder response = new StringBuilder("Response(statusCode=[");

    response.append(getStatusCode());
    response.append("],");
    response.append("headers=[");
    response.append(getHeaders());
    response.append("],responseBody=[");
    response.append(Try.apply(this::getResponseBody));
    response.append("]");

    return response.toString();
  }
}