/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
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
package com.amashchenko.maven.plugin.gitflow;

import org.codehaus.plexus.util.cli.StreamConsumer;

import static java.lang.System.lineSeparator;
import static java.lang.System.out;

public final class StringBuilderStreamConsumer implements StreamConsumer {
    private static final String TAG = "[GITFLOW] ";

    private final StringBuilder builder = new StringBuilder();

    private final boolean printOut;

    public StringBuilderStreamConsumer() {
        this(false);
    }

    public StringBuilderStreamConsumer(boolean printOut) {
        this.printOut = printOut;
    }

    @Override
    public void consumeLine(String line) {
        if (printOut) {
            out.println(TAG + line);
        }

        builder.append(line).append(lineSeparator());
    }

    public String getOutput() {
        return builder.toString();
    }
}
