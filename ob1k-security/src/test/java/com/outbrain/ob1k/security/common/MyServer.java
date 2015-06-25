package com.outbrain.ob1k.security.common;

import com.google.common.collect.Lists;
import com.outbrain.ob1k.common.filters.ServiceFilter;
import com.outbrain.ob1k.security.server.AuthenticationCookieAesEncryptor;
import com.outbrain.ob1k.security.server.CredentialsAuthenticator;
import com.outbrain.ob1k.security.server.HttpBasicAuthenticationFilter;
import com.outbrain.ob1k.security.server.UserPasswordToken;
import com.outbrain.ob1k.server.Server;
import com.outbrain.ob1k.server.build.AddRawServicePhase;
import com.outbrain.ob1k.server.build.ChoosePortPhase;
import com.outbrain.ob1k.server.build.PortsProvider;
import com.outbrain.ob1k.server.build.RawServiceProvider;
import com.outbrain.ob1k.server.build.ServerBuilder;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;

/**
 * Created by gmarom on 6/24/15
 */
public class MyServer {

  public final static String CONTEXT_PATH = "/app/";

  public static Server newServer() {
    return ServerBuilder.newBuilder()
      .configurePorts(createPortsProvider())
      .setContextPath(CONTEXT_PATH)
      .withServices(createServices())
      .build();
  }

  private static RawServiceProvider createServices() {
    final ServiceFilter securityFilter = createAuthFilter();

    return new RawServiceProvider() {
      @Override
      public void addServices(final AddRawServicePhase builder) {
        builder
          .addService(new UnsecureServiceImpl(), UnsecureService.class.getSimpleName())
          .addService(new SecureServiceImpl(), SecureService.class.getSimpleName(), securityFilter);
      }
    };
  }

  private static HttpBasicAuthenticationFilter createAuthFilter() {
    return new HttpBasicAuthenticationFilter(
      new AuthenticationCookieAesEncryptor(createKey()),
      Lists.<CredentialsAuthenticator<UserPasswordToken>>newArrayList(new UserPassEqualAuthenticator()),
      "myAppId",
      3600
    );
  }

  private static byte[] createKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      SecretKey key = generator.generateKey();
      return key.getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Error creating key", e);
    }
  }

  private static PortsProvider createPortsProvider() {
    return new PortsProvider() {
      @Override
      public void configure(final ChoosePortPhase builder) {
        builder.useRandomPort();
      }
    };
  }

}
