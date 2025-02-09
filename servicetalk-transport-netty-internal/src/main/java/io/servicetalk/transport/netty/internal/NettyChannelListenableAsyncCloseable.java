/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;

import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.transport.netty.internal.CloseStates.CLOSING;
import static io.servicetalk.transport.netty.internal.CloseStates.GRACEFULLY_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseStates.OPEN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Implements {@link ListenableAsyncCloseable} using a netty {@link Channel}.
 */
public class NettyChannelListenableAsyncCloseable implements PrivilegedListenableAsyncCloseable {
    private static final AtomicIntegerFieldUpdater<NettyChannelListenableAsyncCloseable> stateUpdater =
            newUpdater(NettyChannelListenableAsyncCloseable.class, "state");
    private final Channel channel;
    @SuppressWarnings("unused")
    private volatile int state;
    private final CompletableSource.Processor onClosing;
    private final SubscribableCompletable onCloseNoOffload;
    private final Completable onClose;

    /**
     * New instance.
     *
     * @param channel to use.
     * @param offloadingExecutor {@link Executor} used to offload any signals to any asynchronous created by this
     * {@link NettyChannelListenableAsyncCloseable} which could interact with the EventLoop. Providing
     * {@link Executors#immediate()} will result in no offloading.
     */
    public NettyChannelListenableAsyncCloseable(Channel channel, Executor offloadingExecutor) {
        this.channel = requireNonNull(channel);
        onClosing = newCompletableProcessor();
        onCloseNoOffload = new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }
                NettyFutureCompletable.connectToSubscriber(subscriber, channel.closeFuture());
            }
        };
        // Since onClose termination will come from EventLoop, offload those signals to avoid blocking EventLoop
        onClose = onCloseNoOffload.publishOn(offloadingExecutor);
        // Users may depend on onClosing to be notified for all kinds of closures and not just manual close.
        // So, we should make sure that onClosing at least terminates with the channel.
        // Since, onClose is guaranteed to be notified for any kind of closures, we cascade it to onClosing.
        // An alternative would be to intercept channelInactive() or close() in the pipeline but adding a pipeline
        // handler in the pipeline may race with closure as we have already created the channel. If that happens,
        // we may miss a pipeline event.
        // "onClosing" is an internal API. Therefore, it does not require offloading.
        onCloseNoOffload.subscribe(onClosing);
    }

    /**
     * Used to notify onClosing ASAP to notify the LoadBalancer to stop using the connection.
     */
    protected final void notifyOnClosing() {
        onClosing.onComplete();
    }

    /**
     * Returns a {@link Completable} that notifies when the connection has begun its closing sequence.
     * <p>
     * <b>Note:</b>The {@code Completable} is not required to be blocking-safe and should be offloaded if the
     * {@link CompletableSource.Subscriber} may block.
     *
     * @return a {@link Completable} that notifies when the connection has begun its closing sequence.
     */
    public final Completable onClosing() {
        return fromSource(onClosing);
    }

    @Override
    public final Completable closeAsync() {
        return closeAsync(onClose());
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return closeAsyncGracefully(onClose());
    }

    @Override
    public final Completable closeAsyncNoOffload() {
        return closeAsync(onCloseNoOffload());
    }

    @Override
    public final Completable closeAsyncGracefullyNoOffload() {
        return closeAsyncGracefully(onCloseNoOffload());
    }

    private Completable closeAsync(Completable source) {
        return new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                toSource(source).subscribe(subscriber);
                if (stateUpdater.getAndSet(NettyChannelListenableAsyncCloseable.this, CLOSING) != CLOSING) {
                    notifyOnClosing();
                    channel.close();
                }
            }
        };
    }

    private Completable closeAsyncGracefully(Completable source) {
        return new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                if (stateUpdater.compareAndSet(NettyChannelListenableAsyncCloseable.this,
                        OPEN, GRACEFULLY_CLOSING)) {
                    notifyOnClosing();
                    try {
                        doCloseAsyncGracefully();
                    } catch (Throwable t) {
                        deliverErrorFromSource(subscriber, t);
                        return;
                    }
                }
                toSource(source).subscribe(subscriber);
            }
        };
    }

    @Override
    public final Completable onClose() {
        return onClose;
    }

    final Completable onCloseNoOffload() {
        return onCloseNoOffload;
    }

    /**
     * Get access to the underlying {@link Channel}.
     *
     * @return the underlying {@link Channel}.
     */
    protected final Channel channel() {
        return channel;
    }

    /**
     * Initiate graceful closure.
     */
    protected void doCloseAsyncGracefully() {
        channel.close();
    }
}
