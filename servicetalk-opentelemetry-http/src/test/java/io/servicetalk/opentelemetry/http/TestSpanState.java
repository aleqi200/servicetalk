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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentelemetry.api.trace.SpanContext;

public class TestSpanState {
    private final String traceId;
    private final String spanId;

    @JsonCreator
    public TestSpanState(@JsonProperty("traceId") String traceId, @JsonProperty("spanId") String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
    }

    public TestSpanState(SpanContext spanContext) {
        this.spanId = spanContext.getSpanId();
        this.traceId = spanContext.getTraceId();
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceId() {
        return traceId;
    }
}
