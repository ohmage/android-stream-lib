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

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.location.Location;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;
import java.util.UUID;

/**
 * StreamPointBuilder class which makes it easy to create a point for a stream and send
 * it to ohmage.
 */
public class StreamPointBuilder {

    private static final String TAG = "StreamPointBuilder";

    public StreamPointBuilder() {
    }

    public StreamPointBuilder(String streamId, int streamVersion) {
        mStreamId = streamId;
        mStreamVersion = streamVersion;
    }

    /**
     * Stream name
     */
    private String mStreamId;

    /**
     * Stream version number
     */
    private int mStreamVersion;

    /**
     * Data Json
     */
    private String mData;

    /**
     * Metadata Json
     */
    private String mMetadata;

    /**
     * Point id
     */
    private String mId;

    /**
     * The ISO8601-formatted date-time-timezone string. If this is not present,
     * then the time and timezone fields will be used.
     */
    private String mTimestamp;

    /**
     * The number of milliseconds since the Unix epoch at UTC. This will only be
     * checked if "timestamp" was not present.
     */
    private Long mTime;

    /**
     * The timezone of the device at the time of this recording as a string.
     * This will only be checked if the "timestamp" was not present and the
     * "time" was.
     */
    private String mTimezone;

    /**
     * <ul>
     * <li>time: The number of milliseconds since the Unix epoch at UTC.</li>
     * <li>latitude: The latitude component.</li>
     * <li>longitude: The longitude component.</li>
     * <li>accuracy: The accuracy of the reading.</li>
     * <li>provider: A string representing who provided this information.</li>
     * <ul>
     */
    private Location mLocation;

    /**
     * Location timezone (will most likely be the same as timezone)
     */
    private String mLocationTimezone;

    /**
     * The unique identifier for the stream and version to which this data applies.
     *
     * @param streamId
     * @param streamVersion
     * @return this
     */
    public StreamPointBuilder setStream(String streamId, int streamVersion) {
        mStreamId = streamId;
        mStreamVersion = streamVersion;
        return this;
    }

    /**
     * Set the point data.
     *
     * @param data
     * @return this
     */
    public StreamPointBuilder setData(String data) {
        mData = data;
        return this;
    }

    /**
     * This should be a JSON object containing the metadata for this point. This
     * field is optional. This field will be ignored on write if any other
     * metadata for this point is supplied to the builder.
     *
     * @param metadata
     * @return this
     */
    public StreamPointBuilder setMetadata(String metadata) {
        clearMetadata();
        mMetadata = metadata;
        return this;
    }

    /**
     * Set a UUID for this point.
     *
     * @param id
     * @return this
     */
    public StreamPointBuilder withId(String id) {
        mId = id;
        return this;
    }

    /**
     * Generates a UUID unique to this point.
     *
     * @return
     */
    public StreamPointBuilder withId() {
        mId = UUID.randomUUID().toString();
        return this;
    }

    /**
     * The unique UUID for this point
     *
     * @return the id
     */
    public String id() {
        return mId;
    }

    /**
     * The ISO8601-formatted date-time-timezone string. If this is not present,
     * then the time and timezone fields will be used.
     *
     * @param timestamp
     * @return this
     */
    public StreamPointBuilder withTimestamp(String timestamp) {
        mTimestamp = timestamp;
        return this;
    }

    /**
     * A long specifying the survey completion time by the number of
     * milliseconds since the UNIX epoch and the timezone ID for the timezone of
     * the device when this survey was taken.
     *
     * @param time
     * @param timezone
     * @return this
     */
    public StreamPointBuilder withTime(long time, String timezone) {
        mTime = time;
        mTimezone = timezone;
        return this;
    }

    /**
     * A long specifying the survey completion time by the number of
     * milliseconds since the UNIX epoch. The timezone used is the current
     * timezone.
     *
     * @param time
     * @return this
     */
    public StreamPointBuilder withTime(long time) {
        mTime = time;
        mTimezone = TimeZone.getDefault().getID();
        return this;
    }

    /**
     * Sets the time for this point to now
     *
     * @return this
     */
    public StreamPointBuilder now() {
        mTime = System.currentTimeMillis();
        mTimezone = TimeZone.getDefault().getID();
        return this;
    }

    /**
     * Location that this response was taken
     *
     * @param location
     * @param timezone
     * @return this
     */
    public StreamPointBuilder withLocation(Location location, String timezone) {
        mLocation = location;
        mLocationTimezone = timezone;
        return this;
    }

    /**
     * Location that this response was taken
     *
     * @param time
     * @param timezone
     * @param latitude
     * @param longitude
     * @param accuracy
     * @param provider
     * @return this
     */
    public StreamPointBuilder withLocation(long time, String timezone, double latitude,
                                           double longitude, float accuracy, String provider) {
        mLocation = new Location(provider);
        mLocation.setTime(time);
        mLocationTimezone = timezone;
        mLocation.setLatitude(latitude);
        mLocation.setLongitude(longitude);
        mLocation.setAccuracy(accuracy);
        return this;
    }

    /**
     * Clears all metadata for this point. Any other data will remain.
     *
     * @return this
     */
    public StreamPointBuilder clearMetadata() {
        mId = null;
        mTimestamp = null;
        mTime = null;
        mTimezone = null;
        mLocation = null;
        mMetadata = null;
        return this;
    }

    /**
     * Clears everything associated with this point.
     *
     * @return this
     */
    public StreamPointBuilder clear() {
        clearMetadata();
        mStreamId = null;
        mStreamVersion = 0;
        mData = null;
        return this;
    }

    /**
     * Returns the metadata for this point
     *
     * @return the metadata as a JSON string
     */
    public String getMetadata() {
        if (mMetadata == null)
            buildMetaData();
        return mMetadata;
    }

    /**
     * Creates the {@link ContentValues} for this point
     *
     * @return the content values
     */
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(StreamContract.Streams.STREAM_ID, mStreamId);
        values.put(StreamContract.Streams.STREAM_VERSION, mStreamVersion);
        values.put(StreamContract.Streams.STREAM_METADATA, mMetadata);
        values.put(StreamContract.Streams.STREAM_DATA, mData);
        return values;
    }

    /**
     * Send this point to ohmage.
     * <p/>
     * If you have many points to send within a few milliseconds of each
     * other, you will get better performance by calling {@link #write(StreamWriter)} or
     * {@link #writeAsync(AsyncBulkInsertHandler)}.
     * <p/>
     * This call will block until the point has been saved in ohmage.
     *
     * @param resolver a {@link ContentResolver} that is used to save the point
     */
    public void write(ContentResolver resolver) {
        buildMetaData();
        if (StreamContract.checkContentProviderExists(resolver))
            resolver.insert(StreamContract.Streams.CONTENT_URI, toContentValues());
    }

    /**
     * Asynchronously send this point to ohmage.
     * <p/>
     * If you have many points to send within a few milliseconds of each
     * other, you will get better performance by calling {@link #write(StreamWriter)} or
     * {@link #writeAsync(AsyncBulkInsertHandler)}.
     * <p/>
     * This call returns without waiting for ohmage to save the point. Implement
     * {@link AsyncQueryHandler#onInsertComplete} for the handler provided to get callbacks whenever
     * a point is saved to ohmage.
     * <p/>
     * Before sending data to ohmage, the stream should check that the content provider exists by
     * calling {@link StreamContract#checkContentProviderExists(ContentResolver)}
     *
     * @param handler an {@link AsyncQueryHandler} to send the point
     * @param token   A token passed into {@link AsyncQueryHandler#onInsertComplete} to identify
     *                this write operation.
     * @param cookie  An object that gets passed into {@link AsyncQueryHandler#onInsertComplete}
     */
    public void writeAsync(AsyncQueryHandler handler, int token, Object cookie) {
        buildMetaData();
        handler.startInsert(token, cookie, StreamContract.Streams.CONTENT_URI, toContentValues());
    }

    /**
     * Asynchronously queue this point to be sent to ohmage in a batch.
     * <p/>
     * If there is more time than the flush delay of the {@link AsyncBulkInsertHandler} between
     * points you will get better performance by calling {@link #write(ContentResolver resolver)} or
     * {@link #writeAsync(AsyncQueryHandler handler, int token, Object cookie)}.
     * <p/>
     * This call returns without waiting for ohmage to save points. Implement
     * {@link AsyncBulkInsertHandler#onBulkInsertComplete(int)} for the handler provided to get
     * callbacks whenever batches of points are saved to ohmage.
     * <p/>
     * Before sending data to ohmage, the stream should check that the content provider exists by
     * calling {@link StreamContract#checkContentProviderExists(ContentResolver)}
     *
     * @param handler an {@link AsyncBulkInsertHandler} to send batches of points
     */
    public void writeAsync(AsyncBulkInsertHandler handler) {
        buildMetaData();
        handler.startInsert(toContentValues());
    }

    /**
     * Uses a {@link StreamWriter} to send data to ohmage. The {@link StreamWriter} connects to a
     * remote service with a oneway interface.
     * <p/>
     * This method is useful when you have a lot of points to send and you have a clear start and
     * end. If the {@link StreamWriter} is not connected yet, this method will automatically buffer
     * the point and try to connect. After the data is sent, the writer should be closed to
     * disconnect from the remote service.
     * <p/>
     * This method returns slower than the async calls, but generally the data is saved to ohmage
     * faster overall.
     *
     * @param writer a {@link StreamWriter} to send points.
     * @throws RemoteException can be thrown if there was a problem sending the data to ohmage.
     */
    public void write(StreamWriter writer) throws RemoteException {
        buildMetaData();
        writer.write(mStreamId, mStreamVersion, mMetadata, mData);
    }

    /**
     * Builds the metadata string. Only sets the string if not null.
     */
    private void buildMetaData() {
        try {
            JSONObject metadata = new JSONObject();
            if (mId != null)
                metadata.put("id", mId);
            if (mTimestamp != null)
                metadata.put("timestamp", mTimestamp);
            if (mTime != null)
                metadata.put("time", mTime);
            if (mTimezone != null)
                metadata.put("timezone", mTimezone);
            if (mLocation != null) {
                JSONObject location = new JSONObject();
                location.put("time", mLocation.getTime());
                location.put("timezone", mLocationTimezone);
                location.put("latitude", mLocation.getLatitude());
                location.put("longitude", mLocation.getLongitude());
                location.put("accuracy", mLocation.getAccuracy());
                location.put("provider", mLocation.getProvider());
                metadata.put("location", location);
            }
            if (metadata.length() > 0)
                mMetadata = metadata.toString();
        } catch (JSONException e) {
            Log.e(TAG, "JSON format exception");
        }
    }
}
