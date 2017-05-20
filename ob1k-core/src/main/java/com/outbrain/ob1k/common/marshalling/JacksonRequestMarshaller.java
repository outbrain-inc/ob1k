package com.outbrain.ob1k.common.marshalling;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.outbrain.ob1k.HttpRequestMethodType;
import com.outbrain.ob1k.Request;
import com.outbrain.ob1k.http.Response;
import com.outbrain.ob1k.http.common.ContentType;
import com.outbrain.ob1k.http.marshalling.JacksonMarshallingStrategy;
import com.outbrain.ob1k.http.marshalling.MarshallingStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * @author aronen
 */
public abstract class JacksonRequestMarshaller implements RequestMarshaller {

  private static final byte[] NEW_LINE = "\n".getBytes(CharsetUtil.UTF_8);
  private static final byte[] HEADER = ChunkHeader.ELEMENT_HEADER.getBytes(CharsetUtil.UTF_8);

  protected final ObjectMapper mapper;
  protected final JsonFactory factory;
  protected final MarshallingStrategy marshallingStrategy;
  protected final ContentType contentType;

  public JacksonRequestMarshaller(final ObjectMapper objectMapper, final ContentType contentType) {
    this.mapper = checkNotNull(objectMapper, "objectMapper may not be null");
    this.contentType = checkNotNull(contentType, "contentType may not be null");
    this.marshallingStrategy = new JacksonMarshallingStrategy(mapper);
    this.factory = objectMapper.getFactory();
  }

  @Override
  public Object[] unmarshallRequestParams(final Request request,
                                          final Method method,
                                          final String[] paramNames) throws IOException {
    // if the method is not expecting anything, no reason trying unmarshalling
    if (paramNames.length == 0) {
      return new Object[0];
    }

    final HttpRequestMethodType httpMethod = request.getMethod();
    if (HttpRequestMethodType.GET == httpMethod || HttpRequestMethodType.DELETE == httpMethod) {
      // if we're having query params, we'll try to unmarshall by them
      // else, trying to read the values from the body
      if (!request.getQueryParams().isEmpty()) {
        return parseURLRequestParams(request, method, paramNames);
      }
    }

    final Map<String, String> pathParams = request.getPathParams();
    if (isBodyEmpty(request) && pathParams.isEmpty()) {
      return new Object[paramNames.length];
    }

    return parseBodyRequestParams(request.getRequestInputStream(), paramNames, pathParams, method);
  }

  @Override
  public HttpContent marshallResponsePart(final Object res,
                                          final HttpResponseStatus status,
                                          final boolean rawStream) throws IOException {
    final byte[] content = mapper.writeValueAsBytes(res);
    final ByteBuf buf = rawStream ?
      Unpooled.copiedBuffer(content, NEW_LINE) :
      Unpooled.copiedBuffer(HEADER, content, NEW_LINE);

    return new DefaultHttpContent(buf);
  }

  @Override
  public FullHttpResponse marshallResponse(final Object res,
                                           final HttpResponseStatus status) throws JsonProcessingException {
    final byte[] content = mapper.writeValueAsBytes(res);
    final ByteBuf buf = Unpooled.copiedBuffer(content);
    final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, buf);

    response.headers().set(CONTENT_TYPE, contentType.responseEncoding());
    return response;
  }

  @Override
  public HttpResponse marshallResponseHeaders(final boolean rawStream) {
    final String contentType = rawStream ? ContentType.TEXT_HTML.responseEncoding() : this.contentType.responseEncoding();
    final HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    res.headers().add(TRANSFER_ENCODING, CHUNKED);
    res.headers().add(CONNECTION, KEEP_ALIVE);
    res.headers().add(CONTENT_TYPE, contentType);
    return res;
  }

  @Override
  public <T> T unmarshallResponse(final Response response, final Type type) throws IOException {
    return marshallingStrategy.unmarshall(type, response);
  }

  @Override
  public <T> T unmarshallStreamResponse(final Response response, final Type type) throws IOException {

    final ByteBuffer byteBufferBody = response.getResponseBodyAsByteBuffer();
    final int chunkHeaderSize = ChunkHeader.ELEMENT_HEADER.length();

    if (byteBufferBody.remaining() < chunkHeaderSize) {
      throw new IOException("bad stream response - no chunk header");
    }

    final byte[] header = new byte[chunkHeaderSize];
    byteBufferBody.get(header);

    final int remaining = byteBufferBody.remaining();
    final byte[] body = new byte[remaining];
    byteBufferBody.get(body);

    if (Arrays.equals(ChunkHeader.ELEMENT_HEADER.getBytes(CharsetUtil.UTF_8), header)) {

      if (remaining == 0) {
        // on empty body the object mapper throws "JsonMappingException: No content to map due to end-of-input"
        return null;
      }

      return mapper.readValue(body, getJacksonType(type));

    } else if (Arrays.equals(ChunkHeader.ERROR_HEADER.getBytes(CharsetUtil.UTF_8), header)) {

      throw new RuntimeException(new String(body, CharsetUtil.UTF_8));
    }

    throw new IOException("invalid chunk header - unsupported " + new String(header, CharsetUtil.UTF_8));
  }

  private Object[] parseURLRequestParams(final Request request,
                                         final Method method,
                                         final String[] paramNames) throws IOException {
    final Object[] result = new Object[paramNames.length];
    final Type[] types = method.getGenericParameterTypes();
    int index = 0;
    for (final String paramName : paramNames) {
      String param = request.getQueryParam(paramName);
      if (param == null) {
        param = request.getPathParam(paramName);
      }
      final Type currentType = types[index];
      if (param == null) {
        if (currentType instanceof Class && ((Class) currentType).isPrimitive()) {
          throw new IOException("Parameter " + paramName + " is primitive and cannot be null");
        }
        result[index] = null;
      } else if (currentType == String.class && !param.startsWith("'") && !param.endsWith("'")) {
        // parsing is unneeded.
        result[index] = param;
      } else {
        final Object value = mapper.readValue(param, getJacksonType(types[index]));
        result[index] = value;
      }
      index++;
    }

    return result;
  }

  private Object[] parseBodyRequestParams(final InputStream requestBodyJson,
                                          final String[] paramNames,
                                          final Map<String, String> pathParams,
                                          final Method method) throws IOException {
    final Type[] types = method.getGenericParameterTypes();
    final List<Object> results = extractPathParams(paramNames, pathParams, types);

    if (results.size() == types.length) {
      return results.toArray();
    }

    final int numOfBodyParams = types.length - results.size();
    int index = results.size();

    final JsonParser jp = factory.createParser(requestBodyJson);
    JsonToken token = jp.nextToken();
    if (token == JsonToken.START_ARRAY) {
      token = jp.nextToken();
      while (true) {
        if (token == JsonToken.END_ARRAY) {
          break;
        }

        final Object res = mapper.readValue(jp, getJacksonType(types[index]));
        results.add(res);
        index++;

        token = jp.nextToken();
      }
    } else {
      if (numOfBodyParams == 1) {
        // in case of single body param we assume a single object with no wrapping array.
        // we read it completely and finish.
        requestBodyJson.reset();
        final Object param = mapper.readValue(requestBodyJson, getJacksonType(types[index]));
        results.add(param);
      } else {
        // we have multiple objects to unmarshall and no array of objects.
        throw new IOException("can't unmarshall request. got a single object in the body but expected multiple objects in an array");
      }
    }

    return results.toArray();
  }

  private List<Object> extractPathParams(final String[] paramNames, final Map<String, String> pathParams, final Type[] types) throws IOException {
    final List<Object> results = new ArrayList<>(types.length);
    int index = 0;

    for (final String paramName : paramNames) {
      if (pathParams.containsKey(paramName)) {
        results.add(ParamMarshaller.unmarshall(pathParams.get(paramName), (Class) types[index]));
        index++;
      } else {
        break;
      }
    }

    if (results.size() < pathParams.size()) {
      throw new IOException("path params should be bounded to be a prefix of the method parameters list.");
    }

    return results;
  }

  private JavaType getJacksonType(final Type type) {
    final TypeFactory typeFactory = TypeFactory.defaultInstance();
    return typeFactory.constructType(type);
  }

  private boolean isBodyEmpty(final Request request) {
    return request.getContentLength() == 0;
  }
}