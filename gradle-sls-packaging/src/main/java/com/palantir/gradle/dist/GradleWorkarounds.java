/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.dist;

import java.lang.reflect.Proxy;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;

@SuppressWarnings("UnstableApiUsage")
final class GradleWorkarounds {

    /**
     * Allow a {@link ListProperty} to be used with {@link DomainObjectCollection#addAllLater}.
     *
     * <p>Fixed in gradle 6: https://github.com/gradle/gradle/pull/10288
     */
    @SuppressWarnings("unchecked")
    static <T> ListProperty<T> fixListProperty(ListProperty<T> property) {
        Class<?> propertyInternalClass = org.gradle.api.internal.provider.CollectionPropertyInternal.class;
        return (ListProperty<T>) Proxy.newProxyInstance(
                GradleWorkarounds.class.getClassLoader(),
                new Class<?>[] {org.gradle.api.internal.provider.CollectionProviderInternal.class, ListProperty.class},
                (_proxy, method, args) -> {
                    // Find matching method on CollectionPropertyInternal
                    // org.gradle.api.internal.provider.CollectionProviderInternal
                    if (method.getDeclaringClass()
                            == org.gradle.api.internal.provider.CollectionProviderInternal.class) {
                        if (method.getName().equals("getElementType")) {
                            // Proxy to `propertyInternalClass` which we know DefaultListProperty implements.
                            return propertyInternalClass
                                    .getMethod(method.getName(), method.getParameterTypes())
                                    .invoke(property, args);
                        } else if (method.getName().equals("size")) {
                            return property.get().size();
                        }
                        throw new GradleException(
                                String.format("Could not proxy method '%s' to object %s", method, property.get()));
                    } else {
                        return method.invoke(property, args);
                    }
                });
    }

    private GradleWorkarounds() {}
}
