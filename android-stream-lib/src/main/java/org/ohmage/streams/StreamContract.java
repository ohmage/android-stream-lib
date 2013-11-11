/*
 * Copyright (C) 2013 ohmage
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

package org.ohmage.streams;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Contract class for interacting with the Stream ContentProvider in ohmage.
 *
 * @author cketcham
 */
public class StreamContract {
    public static final String CONTENT_AUTHORITY = "org.ohmage.streams";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    /**
     * Intent action for configuring a stream apk via ohmage. Add this intent filter to your
     * manifest if you want your apk to handle the configure operation.
     */
    public static final String ACTION_CONFIGURE = "org.ohmage.streams.ACTION_CONFIGURE";

    /**
     * Intent action for viewing data in a stream apk via ohmage. Add this intent filter to your
     * manifest if you want your apk to handle the view operation.
     */
    public static final String ACTION_VIEW = "org.ohmage.streams.ACTION_VIEW";

    interface StreamColumns {
        /** Unique string identifying the stream */
        String STREAM_ID = "stream_id";
        /** Version to identify stream */
        String STREAM_VERSION = "stream_version";
    }

    interface StreamDataColumns extends StreamColumns {
        /** Username */
        String USERNAME = "username";
        /** Stream metadata */
        String STREAM_METADATA = "stream_metadata";
        /** Stream Data */
        String STREAM_DATA = "stream_data";
    }

    private static final String PATH_STREAMS = "streams";

    /**
     * Represents a stream.
     */
    public static final class Streams implements BaseColumns, StreamDataColumns {

        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_STREAMS)
                .build();
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.ohmage.streams.stream";
    }

    private static final String PATH_COUNTS = "counts";

    /**
     * Returns a list of the counts for each stream currently in the system
     */
    public static final class StreamCounts implements StreamColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_COUNTS)
                .build();

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.ohmage.streams.count";
    }

    /**
     * Check that the content provider exists for the streams uri. This prevents errors when streams
     * try to send points when ohmage doesn't exist.
     * @param resolver
     * @return true if the content provider exists
     */
    public static boolean checkContentProviderExists(ContentResolver resolver) {
        ContentProviderClient client = resolver.acquireContentProviderClient(CONTENT_AUTHORITY);
        if (client != null) {
            client.release();
            return true;
        }

        return false;
    }
}
