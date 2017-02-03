/*
 * Copyright 2016 Palantir Technologies
 *
 * Copied under Apache 2.0 from https://github.com/FasterXML/jackson-databind/blob/4883d16b2ba7cb9f2d32208d0c25250776b579db/src/main/java/com/fasterxml/jackson/databind/PropertyNamingStrategy.java#L360
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.dist.tasks;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public final class KebabCaseStrategy extends PropertyNamingStrategy.PropertyNamingStrategyBase {
    @Override
    public String translate(String input) {
        if (input == null) return input; // garbage in, garbage out
        int length = input.length();
        if (length == 0) {
            return input;
        }

        StringBuilder result = new StringBuilder(length + (length >> 1));

        int upperCount = 0;

        for (int i = 0; i < length; ++i) {
            char ch = input.charAt(i);
            char lc = Character.toLowerCase(ch);

            if (lc == ch) { // lower-case letter means we can get new word
                // but need to check for multi-letter upper-case (acronym), where assumption
                // is that the last upper-case char is start of a new word
                if (upperCount > 1) {
                    // so insert hyphen before the last character now
                    result.insert(result.length() - 1, '-');
                }
                upperCount = 0;
            } else {
                // Otherwise starts new word, unless beginning of string
                if ((upperCount == 0) && (i > 0)) {
                    result.append('-');
                }
                ++upperCount;
            }
            result.append(lc);
        }
        return result.toString();
    }
}
