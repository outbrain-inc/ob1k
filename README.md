# Ob1k - A modern RPC Framework

[![Build Status](https://travis-ci.org/outbrain/ob1k.svg?branch=master)](https://travis-ci.org/outbrain/ob1k)
[![Download](https://api.bintray.com/packages/outbrain/OutbrainOSS/OB1K/images/download.svg)](https://bintray.com/outbrain/OutbrainOSS/OB1K/_latestVersion)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/outbrain/ob1k.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/outbrain/ob1k/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/outbrain/ob1k.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/outbrain/ob1k/alerts)

## Overview 
Ob1k is an asynchronous light-weight RPC framework for rapid development of async, high performance micro-services.
Unlike traditional servlet containers, Ob1k is based on [Netty](http://netty.io/) , doing asynchronous event-driven model, and uses a fixed thread-per-core pool for serving.

The coordination of an asynchronous request is performed by using [ComposableFuture](https://github.com/outbrain/ob1k/tree/master/ob1k-concurrent), which enable you to easily create, compose and combine asynchonous computations.

You can start an Ob1k embedded server from your code and once started it will serve HTTP requests based on the endpoints you have configured. Check out our [examples](https://github.com/outbrain/ob1k/tree/master/ob1k-example) for that.

## Anatomy 
Ob1k project consists of the following sub libraries:
 - **ob1k-concurrent**        - Concurrency utils, contains [ComposableFuture](https://github.com/outbrain/ob1k/tree/master/ob1k-concurrent) - an alternative implementation of futures in Java.
 - **ob1k-core**              - RPC framework client and server infrastructure.
 - **ob1k-http**              - Asynchronous HTTP client
 - **ob1k-db**                - Asynchronous MySQL client.
 - **ob1k-cache**             - Asynchronous Memcached client, and guava cache wrapper.
 - **ob1k-cql**               - Asynchronous Cassandra client.
 - **ob1k-security**          - Authentication and authorization for Ob1k.
 - **ob1k-consul**            - Ob1k based [Consul](https://consul.io/) API which simplifies registration and discovery for Ob1k services.
 - **ob1k-swagger**           - Ob1k swagger plugin that will generate the Swagger APi protocol and also provide the Swagger UI.

## Getting started 
Micro-services architecture consists of a group of different services which communicate with each other.
Ob1k supplies the infrastructure to build such microservices and the means for them to communicate.
The communication between services is based on a RPC protocol, (HTTP with JSON or [MessagePack](http://msgpack.org/) payload), using a user provided, strongly typed interface.

*Ob1k* is published to [Bintray](https://bintray.com/outbrain/OutbrainOSS/OB1K).

### Setting up Maven
To start using Ob1k, add the following dependency to your pom:

```
<dependency>
  <groupId>com.outbrain.swinfra</groupId>
  <artifactId>ob1k-core</artifactId>
  <version>0.x</version>
</dependency>
```

### Ob1k Server
Let's start with creating an Ob1k server.

The first step will be creating a new Ob1k service.
A [service](https://github.com/outbrain/ob1k/blob/master/ob1k-core/src/main/java/com/outbrain/ob1k/Service.java) (similar to [controller](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller#Components) in MVC) aggregates set of related endpoints into one serving context.
Each method in the implementation will be mapped to a URL which clients as well as a simple web browsers can invoke.
In the next example we are create a service with an endpoint named `helloWorld` which gets no arguments and returns a string.

```java
public interface IHelloService extends Service {
   ComposableFuture<String> helloWorld();
}
```

The method implementation just returns a "Hello world" string which will be returned by the Ob1k framework to the client:

```java 
public class HelloService implements IHelloService {
   @Override
   public ComposableFuture<String> helloWorld() {
     return fromValue("Hello World!");
   }
}
```
 
Now that you have the service endpoint we can build the Ob1k server. For that, we will need to set the port to use and the base URL which is called context.
In addition we need to bind our services to a URL under the context. After setting some more properties (e.g. requestTimeout) we call the build method and this creates a server.

```java 
final Server server = ServerBuilder.newBuilder().
  contextPath("/services").
  configure(builder -> builder.usePort(8080).requestTimeout(50, TimeUnit.MILLISECONDS)).
  service(builder -> builder.register(new HelloService(), "/hello")).
  build();
```

To start the server:
```java
server.start(); 
```
Now you can access the service endpoint just go to 
    http://localhost:8080/services/hello/helloWorld


### Ob1k Client
Now we are going to create an Ob1k client. Most of the times Ob1k clients are going to be executed inside an Ob1k service, but for simplicity we will show just the client code for now.
We use the `ClientBuilder` to build the client by specifying a target URL, the interface of the service we're invoking, content type (which is controlled by the client) and can be either JSON or MessagePack, timeouts, etc.
```java
final String target = "http://localhost:8080/services/hello";
final IHelloService helloService = new ClientBuilder<>(IHelloService.class).
            setProtocol(ContentType.JSON).
            setRequestTimeout(-1).
            setTargetProvider(new SimpleTargetProvider(target)).
            build();
```
Now that we have `helloService` we can invoke methods on it which will be directed automatically to the server.
```java
final ComposableFuture<String> helloWorld = helloService.helloWorld();
System.out.println(helloWorld.get());
```

And that's it :)


## Examples
More examples can be found here 
[ob1k-example](https://github.com/outbrain/ob1k/tree/master/ob1k-example/src/main/java/com/outbrain/ob1k/example/)

## Links
[Ob1k Presentation Slides](http://www.slideshare.net/eranharel/ob1k-presentation-at-javail)

## License
Ob1k is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
