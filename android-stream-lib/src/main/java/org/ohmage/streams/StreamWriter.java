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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Easily connect and write streams to ohmage to be uploaded.
 *
 * @author cketcham
 */
public class StreamWriter implements ServiceConnection {

    /**
     * Intent action which initializes the connection to the remote service
     */
    private static final String ACTION_WRITE = "org.ohmage.streams.ACTION_WRITE";

    /**
     * Holds a list of streams which were collected before the service connected
     */
    private final ArrayList<StreamPointBuilder> mBuffer;

    private IStreamReceiver dataService;

    protected final Context mContext;

    private ServiceConnectionChange mListener;

    private boolean mShouldClose = false;

    public static interface ServiceConnectionChange {
        public void onServiceConnected(StreamWriter writer);

        public void onServiceDisconnected(StreamWriter writer);
    }

    public StreamWriter(Context context) {
        mContext = context;
        mBuffer = new ArrayList<StreamPointBuilder>();
    }

    /**
     * is called once the bind succeeds
     */
    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder service) {
        dataService = IStreamReceiver.Stub.asInterface(service);

        if (mListener != null)
            mListener.onServiceConnected(this);

        // Write any streams which came before we were connected
        for (StreamPointBuilder stream : mBuffer) {
            try {
                stream.write(this);
            } catch (RemoteException e) {
                // Remote connection was lost
                e.printStackTrace();
            }
        }
        mBuffer.clear();

        if(mShouldClose) {
            close();
        }
    }

    /**
     * is called once the remote service is no longer available
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        dataService = null;

        if (mListener != null)
            mListener.onServiceDisconnected(this);
    }

    public boolean connect() {
        Intent intent = new Intent(ACTION_WRITE);
        return mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    public void setServiceConnectionChangeListener(ServiceConnectionChange listener) {
        mListener = listener;
    }

    public void close() {
        if(!mBuffer.isEmpty()) {
            mShouldClose = true;
        } else {
            mContext.unbindService(this);
            dataService = null;
        }
    }

    public synchronized void write(String streamId, int streamVersion, String metadata, String data)
            throws RemoteException {

        if (TextUtils.isEmpty(data))
            throw new RuntimeException("Must specify data");

        // Check that the data is valid json
        try {
            new JSONObject(data);
        } catch (JSONException e) {
            throw new RuntimeException("data not valid json");
        }

        // Check that the metadata is valid json
        if (!TextUtils.isEmpty(metadata)) {
            try {
                new JSONObject(metadata);
            } catch (JSONException e) {
                throw new RuntimeException("metadata not valid json");
            }
        }

        if (dataService != null) {
            dataService.sendStream(streamId, streamVersion, metadata, data);
        } else {
            mBuffer.add(new StreamPointBuilder(streamId, streamVersion)
                    .setData(data)
                    .setMetadata(metadata));
            if (!connect())
                mBuffer.clear(); // No point in buffering data if we can't connect to the service
        }
    }
}
