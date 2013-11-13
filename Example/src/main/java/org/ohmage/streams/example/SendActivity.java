package org.ohmage.streams.example;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.streams.AsyncBulkInsertHandler;
import org.ohmage.streams.StreamContract;
import org.ohmage.streams.StreamPointBuilder;
import org.ohmage.streams.StreamWriter;

import java.util.concurrent.CountDownLatch;

/**
 * Experiment with different data point sizes and methods of sending points to ohmage. Each of the
 * four write operations can be triggered by one of the buttons. In each case, there are four
 * timing measurements which are taken
 *
 * <ul>
 *     <li>init: the time it takes to create a connection to ohmage</li>
 *     <li>send: the time the call to send data blocks the thread</li>
 *     <li>write: the time it takes to actually write the data to the db</li>
 *     <li>close: the time it takes to close the connection with ohmage</li>
 * </ul>
 */
public class SendActivity extends ActionBarActivity {

    /**
     * A stream ID that will accept any kind of data. Useful for testing.
     */
    public static final String STREAM_ID = "eb5e35ee-6a4d-40ff-b503-e1f5b72e5a1d";

    /**
     * A random timestamp string.. just so I don't have to generate one for each point.
     */
    public static final String TIMESTAMP = "2013-11-12T15:50:02.123-05:00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.send, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static abstract class DurationTestThread extends Thread {

        private final int mSize;

        private final int mCount;

        private PlaceholderFragment mFragment;

        private StreamPointBuilder mStreamPointBuilder;

        public DurationTestThread(PlaceholderFragment fragment, int count, int size) {
            mSize = size;
            mCount = count;
            mFragment = fragment;
        }

        @Override
        public void run() {
            mStreamPointBuilder = new StreamPointBuilder(STREAM_ID, 1);
            mStreamPointBuilder.withTimestamp(TIMESTAMP).setData(createData(mSize));

            long start = System.currentTimeMillis();
            initialize();
            final long initialize = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < mCount; i++) {
                sendData();
            }
            final long sendData = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            finish();
            final long finish = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            cleanUp();
            final long cleanUp = System.currentTimeMillis() - start;

            mFragment.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFragment.setDuration(initialize, sendData, finish, cleanUp);
                    mFragment.setFinished();
                    mFragment = null;
                }
            });
        }

        public PlaceholderFragment getFragment() {
            return mFragment;
        }

        protected abstract void initialize();

        protected abstract void sendData();

        protected abstract void finish();

        protected abstract void cleanUp();

        public StreamPointBuilder getDataStreamPoint() {
            return mStreamPointBuilder;
        }

        public int getCount() {
            return mCount;
        }

        public static String createData(int size) {
            JSONObject d = new JSONObject();
            try {
                StringBuilder builder = new StringBuilder(size);
                for (int i = 0; i < size; i++) {
                    builder.append(0);
                }
                d.put("data", builder.toString());
            } catch (JSONException e) {
                // Failed to encode for some reason
            }
            return d.toString();
        }
    }

    public static class AIDLThread extends DurationTestThread {

        private StreamWriter mWriter;

        private StreamPointBuilder point;

        public AIDLThread(PlaceholderFragment fragment, int size, int count) {
            super(fragment, size, count);
        }

        @Override
        protected void initialize() {
            mWriter = new StreamWriter(getFragment().getActivity());
            mWriter.connect();
            final CountDownLatch latch = new CountDownLatch(1);
            mWriter.setServiceConnectionChangeListener(new StreamWriter.ServiceConnectionChange() {
                @Override
                public void onServiceConnected(StreamWriter streamWriter) {
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(StreamWriter streamWriter) {

                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void sendData() {
            try {
                getDataStreamPoint().withId().write(mWriter);
            } catch (RemoteException e) {
                // The write didn't go through
                // Either we are trying to send too many points at once, or the points we are
                // sending are just too large. We should implement some kind of back off.
                // Or write the points by sending them the the ohmage content provider
                e.printStackTrace();
            }
        }

        @Override
        public void finish() {
            //TODO: figure out how to get this...
        }

        @Override
        protected void cleanUp() {
            mWriter.close();
            mWriter = null;
        }
    }

    static public class AsyncBulkThread extends DurationTestThread {

        private boolean mProviderExists;

        private CountDownLatch mLatch;

        private InstrumentedAsyncBulkInsertHandler mHandler;

        public AsyncBulkThread(PlaceholderFragment fragment, InstrumentedAsyncBulkInsertHandler handler, int size, int count) {
            super(fragment, size, count);
            mHandler = handler;
        }

        @Override
        protected void initialize() {
            mLatch = new CountDownLatch(getCount());
            mHandler.setLatch(mLatch);
            mProviderExists = StreamContract.checkContentProviderExists(getFragment().getActivity().getContentResolver());
        }

        @Override
        public void sendData() {
            if(mProviderExists)
                getDataStreamPoint().withId().writeAsync(mHandler);
        }

        @Override
        public void finish() {
            try {
                if(mProviderExists)
                    mLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void cleanUp() {
            mLatch = null;
            mHandler = null;
        }
    }

    static public class AsyncQueryThread extends DurationTestThread {

        private CountDownLatch mLatch;

        private InstrumentedAsyncQueryHandler mHandler;

        private boolean mProviderExists;

        public AsyncQueryThread(PlaceholderFragment fragment, InstrumentedAsyncQueryHandler handler, int size, int count) {
            super(fragment, size, count);
            mHandler = handler;
        }

        @Override
        protected void initialize() {
            mLatch = new CountDownLatch(getCount());
            mHandler.setLatch(mLatch);
            mProviderExists = StreamContract.checkContentProviderExists(getFragment().getActivity().getContentResolver());
        }

        @Override
        public void sendData() {
            if(mProviderExists)
                getDataStreamPoint().withId().writeAsync(mHandler, -1, null);
        }

        @Override
        public void finish() {
            try {
                if(mProviderExists)
                    mLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void cleanUp() {
            mLatch = null;
            mHandler = null;
        }
    }

    static public class ContentResolverThread extends DurationTestThread {

        private ContentResolver mContentResolver;

        public ContentResolverThread(PlaceholderFragment fragment, int size, int count) {
            super(fragment, size, count);
        }

        @Override
        protected void initialize() {
            mContentResolver = getFragment().getActivity().getContentResolver();
        }

        @Override
        public void sendData() {
           getDataStreamPoint().withId().write(mContentResolver);
        }

        @Override
        public void finish() {
            //TODO: how to get this?
        }

        @Override
        protected void cleanUp() {
            mContentResolver = null;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    static public class PlaceholderFragment extends Fragment {

        private EditText count;

        private EditText size;

        private EditText threads;

        private TextView info;

        private TextView init;

        private TextView send;

        private TextView write;

        private TextView close;

        private TextView total;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_send, container, false);

                Button startAIDL = (Button) rootView.findViewById(R.id.start_aidl);
            startAIDL.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    info.setText("Running: write(StreamWriter writer)");
                    for(int i=0;i<getThreads();i++)
                    new Thread() {
                        public void run() {
                            new AIDLThread(PlaceholderFragment.this,getCount(),getSize()).start();
                        }
                    }.start();
                }
            });

            Button startContentProvider = (Button) rootView.findViewById(R.id.start_content_provider1);
            startContentProvider.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    info.setText("Running: write(AsyncBulkInsertHandler handler)");
                    final InstrumentedAsyncBulkInsertHandler handler = getAsyncBulkInsertHandler();
                    for(int i=0;i<getThreads();i++)
                    new Thread() {
                        public void run() {
                            new AsyncBulkThread(PlaceholderFragment.this, handler, getCount(), getSize()).start();
                        }
                    }.start();
                }
            });

            startContentProvider = (Button) rootView.findViewById(R.id.start_content_provider2);
            startContentProvider.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    info.setText("Running: write(AsyncQueryHandler handler)");
                    final InstrumentedAsyncQueryHandler handler = getAsyncQueryHandler();
                    for(int i=0;i<getThreads();i++)
                        new Thread() {
                            public void run() {
                                new AsyncQueryThread(PlaceholderFragment.this, handler, getCount(), getSize()).start();
                            }
                        }.start();
                }
            });

            startContentProvider = (Button) rootView.findViewById(R.id.start_content_provider3);
            startContentProvider.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    info.setText("Running: write(ContentResolver resolver)");
                    final InstrumentedAsyncQueryHandler handler = getAsyncQueryHandler();
                    for(int i=0;i<getThreads();i++)
                        new Thread() {
                            public void run() {
                                new ContentResolverThread(PlaceholderFragment.this, getCount(), getSize()).start();
                            }
                        }.start();
                }
            });

            info = (TextView) rootView.findViewById(R.id.info);
            init = (TextView) rootView.findViewById(R.id.init);
            send = (TextView) rootView.findViewById(R.id.send);
            write = (TextView) rootView.findViewById(R.id.write);
            close = (TextView) rootView.findViewById(R.id.close);
            total = (TextView) rootView.findViewById(R.id.total);


            count = (EditText) rootView.findViewById(R.id.count);
            size = (EditText) rootView.findViewById(R.id.size);
            threads = (EditText) rootView.findViewById(R.id.threads);

            return rootView;
        }

        public int getCount() {
            try {
                return Integer.parseInt(count.getText().toString());
            } catch(NumberFormatException e) {
                count.setText("1");
                return 1;
            }
        }

        public int getSize() {
            try {
                return Integer.parseInt(size.getText().toString());
            } catch(NumberFormatException e) {
                size.setText("128");
                return 128;
            }
        }

        public int getThreads() {
            try {
                return Integer.parseInt(threads.getText().toString());
            } catch(NumberFormatException e) {
                threads.setText("1");
                return 1;
            }
        }

        public InstrumentedAsyncBulkInsertHandler getAsyncBulkInsertHandler() {
            return new InstrumentedAsyncBulkInsertHandler(getActivity().getContentResolver(),
                    StreamContract.Streams.CONTENT_URI);
        }

        public InstrumentedAsyncQueryHandler getAsyncQueryHandler() {
            return new InstrumentedAsyncQueryHandler(getActivity().getContentResolver());
        }

        public void setDuration(long i, long s, long w, long c) {
            init.setText(i+"ms");
            send.setText(s + "ms");
            write.setText(w+"ms");
            close.setText(c+"ms");
            total.setText((i+s+w+c)+"ms");
        }

        public void setFinished() {
            info.setText("Finished:" + info.getText().toString().split(":")[1]);
        }
    }

    public static class InstrumentedAsyncBulkInsertHandler extends AsyncBulkInsertHandler {
        CountDownLatch mLatch;

        public InstrumentedAsyncBulkInsertHandler(ContentResolver cr, Uri uri) {
            super(cr, uri);
        }

        public void setLatch(CountDownLatch latch) {
            mLatch = latch;
        }

        protected void onBulkInsertComplete(int count) {
            for(int i=0;i<count;i++)
                mLatch.countDown();
        }
    }

    public static class InstrumentedAsyncQueryHandler extends AsyncQueryHandler {
        CountDownLatch mLatch;

        public InstrumentedAsyncQueryHandler(ContentResolver cr) {
            super(cr);
        }

        public void setLatch(CountDownLatch latch) {
            mLatch = latch;
        }

        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            mLatch.countDown();
        }
    }

}
