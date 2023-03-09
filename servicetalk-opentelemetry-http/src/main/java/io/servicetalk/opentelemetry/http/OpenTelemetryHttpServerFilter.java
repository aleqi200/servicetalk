/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.function.UnaryOperator;

/**
 * A {@link StreamingHttpService} that supports
 * <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link StreamingHttpService#handle(HttpServiceContext, StreamingHttpRequest, StreamingHttpResponseFactory)
 * response meta data} or the {@link StreamingHttpResponse#transformMessageBody(UnaryOperator) response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public final class OpenTelemetryHttpServerFilter extends AbstractOpenTelemetryFilter
    implements StreamingHttpServiceFilterFactory {

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     */
    public OpenTelemetryHttpServerFilter(final OpenTelemetry openTelemetry) {
        super(openTelemetry);
    }

    /**
     * Create a new Instance, searching for any instance of an opentelemetry available.
     */
    public OpenTelemetryHttpServerFilter() {
        this(GlobalOpenTelemetry.get());
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return trackRequest(delegate(), ctx, request, responseFactory);
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpService delegate,
                                                       final HttpServiceContext ctx,
                                                       final StreamingHttpRequest request,
                                                       final StreamingHttpResponseFactory responseFactory) {
        final Context context = Context.root();
        io.opentelemetry.context.Context tracingContext =
            propagators.getTextMapPropagator().extract(context, request.headers(), HeadersPropagatorGetter.INSTANCE);

        final Span span = RequestTagExtractor.reportTagsAndStart(tracer
            .spanBuilder(getOperationName(request))
            .setParent(tracingContext)
            .setSpanKind(SpanKind.SERVER), request);

        final Scope scope = span.makeCurrent();
        final ScopeTracker tracker = new ScopeTracker(scope, span) {
            @Override
            protected void tagStatusCode() {
                super.tagStatusCode();
                if (metaData != null) {
                    propagators.getTextMapPropagator().inject(Context.current(), metaData.headers(),
                            HeadersPropagatorSetter.INSTANCE);
                }
            }
        };
        Single<StreamingHttpResponse> response;
        try {
            response = delegate.handle(ctx, request, responseFactory);
        } catch (Throwable t) {
            tracker.onError(t);
            return Single.failed(t);
        }
        return tracker.track(response);
    }

    /**
     * Get the operation name to build the span with.
     *
     * @param metaData The {@link HttpRequestMetaData}.
     * @return the operation name to build the span with.
     */
    private static String getOperationName(HttpRequestMetaData metaData) {
        return metaData.method().name() + ' ' + metaData.path();
    }
}
