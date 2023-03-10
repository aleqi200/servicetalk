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

import io.servicetalk.http.api.HttpRequestMetaData;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

final class RequestTagExtractor {

    private RequestTagExtractor() {
        // empty private constructor
    }

    private static String getRequestMethod(HttpRequestMetaData req) {
        return req.method().name();
    }

    private static String getHttpUrl(HttpRequestMetaData req) {
        return req.path()
            + (req.rawQuery() == null ? "" : "?" + req.rawQuery());
    }

    static Span reportTagsAndStart(SpanBuilder span, HttpRequestMetaData httpRequestMetaData) {
        span.setAttribute("http.url", getHttpUrl(httpRequestMetaData));
        span.setAttribute("http.method", getRequestMethod(httpRequestMetaData));
        span.setAttribute("http.target", getHttpUrl(httpRequestMetaData));
        span.setAttribute("http.route", httpRequestMetaData.rawPath());
        span.setAttribute("http.flavor", httpRequestMetaData.version().major() + "."
            + httpRequestMetaData.version().minor());
        String userInfo = httpRequestMetaData.userInfo();
        if (userInfo != null) {
            span.setAttribute("http.user_agent", userInfo);
        }
        String scheme = httpRequestMetaData.scheme();
        if (scheme != null) {
            span.setAttribute("http.scheme", scheme);
        }
        String hostName = httpRequestMetaData.host();
        if (hostName != null) {
            span.setAttribute("net.host.name", hostName);
        }
        span.setAttribute("net.host.port", httpRequestMetaData.port());
        return span.startSpan();
    }
}
