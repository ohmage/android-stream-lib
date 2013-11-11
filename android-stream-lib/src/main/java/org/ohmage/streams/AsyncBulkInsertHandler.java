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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * A helper class to help make handling asynchronous {@link android.content.ContentResolver#bulkInsert}
 * operations easier.
 */
public class AsyncBulkInsertHandler extends Handler {
    /**
     * Maximum number of points which should be in the buffer at any give time
     */
    public static final int MAX_BUFFER = 600;

    /**
     * Maximum number of milliseconds to wait before flushing data to db
     */
    public static final long FLUSH_DELAY = 500;

    private static final int EVENT_ARG_INSERT = 0;

    private static final int EVENT_ARG_BULK_INSERT = 1;

    final WeakReference<ContentResolver> mResolver;

    /**
     * The time to wait for more points for this batch in ms
     */
    private final long mDelay;

    /**
     * The maximum number of points to save in the buffer before starting to write this batch
     */
    private final int mMaxBufferSize;

    private final Uri mUri;

    private ArrayList<ContentValues> values;

    private static Looper sLooper = null;

    private Handler mWorkerThreadHandler;

    protected static final class WorkerArgs {
        public Handler handler;

        public Object result;

        public ContentValues values;
    }

    protected class WorkerHandler extends Handler {
        public WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            final ContentResolver resolver = mResolver.get();
            if (resolver == null) return;

            WorkerArgs args = (WorkerArgs) msg.obj;

            switch (msg.what) {
                case EVENT_ARG_INSERT:
                    values.add(args.values);
                    queueBulkInsert(values.size() > mMaxBufferSize);
                    break;

                case EVENT_ARG_BULK_INSERT:
                    args.result = resolver.bulkInsert(mUri, values.toArray(new ContentValues[]{}));
                    values.clear();

                    Message reply = args.handler.obtainMessage(msg.what);
                    reply.obj = args;
                    reply.sendToTarget();
                    break;
            }
        }
    }

    /**
     * Create an {@link AsyncBulkInsertHandler} which delays writing to the db for
     * {@link #FLUSH_DELAY} ms to wait for more points for the batch up to {@link #MAX_BUFFER}
     * points
     * @param cr The {@link ContentResolver} to insert data with
     * @param uri The uri to insert bulk data on
     */
    public AsyncBulkInsertHandler(ContentResolver cr, Uri uri) {
        this(cr, uri, FLUSH_DELAY, MAX_BUFFER);
    }

    /**
     * Create an {@link AsyncBulkInsertHandler} which batches points according to the delay and max
     * buffer size specified in the parameters.
     * @param cr The {@link ContentResolver} to insert data with
     * @param uri The uri to insert bulk data on
     * @param delay the time in ms to wait before flushing the buffer to the db
     * @param maxBufferSize the maximum number of points to keep in the buffer before flushing
     */
    public AsyncBulkInsertHandler(ContentResolver cr, Uri uri, long delay, int maxBufferSize) {
        super();
        mResolver = new WeakReference<ContentResolver>(cr);
        synchronized (org.ohmage.streams.AsyncBulkInsertHandler.class) {
            if (sLooper == null) {
                HandlerThread thread = new HandlerThread("AsyncBulkInsertHandler");
                thread.start();

                sLooper = thread.getLooper();
            }
        }
        mWorkerThreadHandler = createHandler(sLooper);

        mUri = uri;
        mMaxBufferSize = maxBufferSize;
        mDelay = delay;
        values = new ArrayList<ContentValues>(mMaxBufferSize);
    }

    protected Handler createHandler(Looper looper) {
        return new WorkerHandler(looper);
    }

    /**
     * This method begins an asynchronous insert that will be inserted into the db as a batch
     *
     * @param values the ContentValues parameter passed to the insert operation.
     */
    public final void startInsert(ContentValues values) {
        Message msg = mWorkerThreadHandler.obtainMessage(EVENT_ARG_INSERT);

        WorkerArgs args = new WorkerArgs();
        args.values = values;
        msg.obj = args;

        mWorkerThreadHandler.sendMessage(msg);
    }

    /**
     * This method queues an asynchronous bulk insert. When the bulk insert operation is
     * done {@link #onBulkInsertComplete} is called.
     *
     * @param immediate if true the bulk insert will be performed immediately
     */
    private final void queueBulkInsert(boolean immediate) {
        mWorkerThreadHandler.removeMessages(EVENT_ARG_BULK_INSERT);

        Message msg = mWorkerThreadHandler.obtainMessage(EVENT_ARG_BULK_INSERT);

        WorkerArgs args = new WorkerArgs();
        args.handler = this;
        msg.obj = args;

        if (immediate) {
            mWorkerThreadHandler.sendMessageAtFrontOfQueue(msg);
        } else {
            mWorkerThreadHandler.sendMessageDelayed(msg, mDelay);
        }
    }

    /**
     * Called when an asynchronous bulk insert is completed.
     *
     * @param count the number of rows inserted in a batch
     */
    protected void onBulkInsertComplete(int count) {
        // Empty
    }

    @Override
    public void handleMessage(Message msg) {
        WorkerArgs args = (WorkerArgs) msg.obj;

        switch (msg.what) {
            case EVENT_ARG_BULK_INSERT:
                onBulkInsertComplete((Integer) args.result);
                break;
        }
    }
}
