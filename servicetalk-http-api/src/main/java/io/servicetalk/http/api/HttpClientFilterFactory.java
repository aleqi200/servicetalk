/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Publisher;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * A factory which filters the behavior of {@link StreamingHttpClient} instances.
 */
@FunctionalInterface
public interface HttpClientFilterFactory {
    /**
     * Function that allows to filter an {@link StreamingHttpClient}.
     * @param client the {@link StreamingHttpClient} to filter
     * @param lbEvents the {@link LoadBalancer} events stream
     * @return the filtered {@link StreamingHttpClient}
     */
    StreamingHttpClient apply(StreamingHttpClient client, Publisher<Object> lbEvents);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default HttpClientFilterFactory append(HttpClientFilterFactory before) {
        requireNonNull(before);
        return (client, lbEvents) -> apply(before.apply(client, lbEvents), lbEvents);
    }

    /**
     * Returns a function that always returns its input {@link StreamingHttpClient}.
     *
     * @return a function that always returns its input {@link StreamingHttpClient}.
     */
    static HttpClientFilterFactory identity() {
        return (client, lbEvents) -> client;
    }

    /**
     * Returns a function that adapts from the {@link UnaryOperator}&lt;{@link StreamingHttpClient}&gt; function type to
     * the {@link HttpClientFilterFactory}.
     *
     * @param function the function that is applied to the input {@link StreamingHttpClient}
     * @return the filtered {@link StreamingHttpClient}
     */
    static HttpClientFilterFactory from(UnaryOperator<StreamingHttpClient> function) {
        requireNonNull(function);
        return (client, lbEvents) -> function.apply(client);
    }
}
