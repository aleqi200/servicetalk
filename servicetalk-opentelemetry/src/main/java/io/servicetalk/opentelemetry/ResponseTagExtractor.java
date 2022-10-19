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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.HttpResponseMetaData;

final class ResponseTagExtractor implements TagExtractor<HttpResponseMetaData> {

    public static final TagExtractor<HttpResponseMetaData> INSTANCE = new ResponseTagExtractor();

    private ResponseTagExtractor() {
        // private constructor
    }

    public int len(HttpResponseMetaData resp) {
        return 1;
    }

    public String name(HttpResponseMetaData resp, int index) {
        if (index == 0) {
            return "http.status_code";
        } else {
            throw new IndexOutOfBoundsException("Invalid tag index " + index);
        }
    }

    public String value(HttpResponseMetaData resp, int index) {
        if (index == 0) {
            return "" + resp.status().code();
        } else {
            throw new IndexOutOfBoundsException("Invalid tag index " + index);
        }
    }
}
