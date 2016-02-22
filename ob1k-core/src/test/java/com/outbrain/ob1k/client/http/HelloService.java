package com.outbrain.ob1k.client.http;

import com.outbrain.ob1k.Response;
import com.outbrain.ob1k.http.common.ContentType;
import com.outbrain.ob1k.concurrent.ComposableFuture;
import com.outbrain.ob1k.concurrent.ComposableFutures;
import com.outbrain.ob1k.server.netty.ResponseBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.outbrain.ob1k.concurrent.ComposableFutures.fromValue;

/**
 * User: aronen
 * Date: 6/23/13
 * Time: 2:24 PM
 */
public class HelloService implements IHelloService {

  private final Random random = new Random();

  @Override
  public ComposableFuture<String> hello(final String name) {
    if ("moshe".equals(name)) {
      throw new IllegalArgumentException("bad name.");
    }
    return fromValue("hello " + name);
  }

  @Override
  public ComposableFuture<String> helloWorld() {
    return fromValue("hello world");
  }

  @Override
  public ComposableFuture<Integer> getRandomNumber() {
    return fromValue(random.nextInt(9) + 1);
  }

  @Override
  public ComposableFuture<String> helloFilter(final String name) {
    return fromValue("hello " + name);
  }

  @Override
  public ComposableFuture<Response> emptyString() {
    return fromValue(ResponseBuilder.ok().build());
  }

  @Override
  public ComposableFuture<Boolean> sleep(final int milliseconds) {
    return ComposableFutures.schedule(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return true;
      }
    }, milliseconds, TimeUnit.MILLISECONDS);
  }

  public ComposableFuture<List<TestBean>> increaseAge(final List<TestBean> beans, final String newHabit) {
    if (beans == null) {
      return null;
    }

    for(final TestBean bean: beans) {
      bean.setAge(bean.getAge() + 1);
      bean.getHabits().add(newHabit);
    }

    return fromValue(beans);
  }

  @Override
  public Observable<String> getMessages(final String name, final int iterations, final boolean failAtEnd) {
    if (failAtEnd) {
      return Observable.concat(Observable.interval(10, TimeUnit.MILLISECONDS).map(new Func1<Long, String>() {
        @Override
        public String call(final Long num) {
          return "hello " + name + " #" + num;
        }
      }).take(iterations), Observable.<String>error(new RuntimeException("last message is really bad!")));
    } else {
      return Observable.interval(100, TimeUnit.MILLISECONDS).map(new Func1<Long, String>() {
        @Override
        public String call(final Long num) {
          return "hello " + name + " #" + num;
        }
      }).take(iterations);
    }
  }

  public ComposableFuture<Response> noJsonContent() {
    return fromValue(ResponseBuilder.fromStatus(HttpResponseStatus.NO_CONTENT).setContentType(ContentType.JSON.responseEncoding()).build());
  }

  public ComposableFuture<Response> noMsgPackContent() {
    return fromValue(ResponseBuilder.fromStatus(HttpResponseStatus.NO_CONTENT).setContentType(ContentType.MESSAGE_PACK.responseEncoding()).build());
  }
}
