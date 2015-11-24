package com.outbrain.ob1k.server.registry.endpoints;

import com.google.common.base.Joiner;
import com.outbrain.ob1k.HttpRequestMethodType;
import com.outbrain.ob1k.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by aronen on 4/24/14.
*/
abstract class AbstractServerEndpoint implements ServerEndpoint {

  private static final String TARGET_FORMAT = "%s.%s(%s)";

  private final Service service;
  private final Method method;
  private final HttpRequestMethodType requestMethodType;
  private final String[] paramNames;

  public AbstractServerEndpoint(final Service service, final Method method, final HttpRequestMethodType requestMethodType, final String[] paramNames) {
    this.service = service;
    this.method = method;
    this.requestMethodType = requestMethodType;
    this.paramNames = paramNames;
  }

  @Override
  public String getTargetAsString() {
    return String.format(TARGET_FORMAT, service.getClass().getName(), method.getName(), Joiner.on(", ").join(paramNames));
  }

  @Override
  public HttpRequestMethodType getRequestMethodType() {
    return requestMethodType;
  }

  @Override
  public Method getMethod() {
    return method;
  }

  @Override
  public String[] getParamNames() {
    return paramNames;
  }

  protected Object invokeMethodOnService(Object[] params) throws InvocationTargetException, IllegalAccessException {
    return method.invoke(service, params);
  }
}
