/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.cluster.codec.response;

import com.alibaba.csp.sentinel.cluster.codec.EntityWriter;
import com.alibaba.csp.sentinel.cluster.response.Response;

/**
 * A universal {@link EntityWriter} interface for publishing {@link Response} to a target stream.
 *
 * @author Eric Zhao
 * @since 1.4.0
 * 响应体写入接口
 */
public interface ResponseEntityWriter<E extends Response, T> extends EntityWriter<E, T> {

}
