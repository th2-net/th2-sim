/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/

package com.exactpro.th2.simulator.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

public class StringUtils {

    @NotNull
    public static String findSubstring(CharSequence charSequence, String regex, int fromIndex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(charSequence);
        if (matcher.find(fromIndex)) {
            return matcher.group(0);
        } else {
            return "";
        }
    }

    @NotNull
    public static String findSubstring(CharSequence charSequence, String regex) {
        return findSubstring(charSequence, regex, 0);
    }

}
