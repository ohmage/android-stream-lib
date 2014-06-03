android-stream-lib
==================

The android-stream-lib is an Android Library (aar) which makes it easier to write an apk that sends
stream data to the android ohmage app on the phone. The ohmage app can then handle uploading that
data to the ohmage server.

This is a SNAPSHOT release of the proposed 2.0 version of this library for ohmage 3.0. The older
version of this library for ohmage 2.16 is available here: [ohmage-probe-library]

HOW TO ADD THE LIBRARY
----------------------

The library is available with maven. Since this is only a snapshot release for now, you will have to
add the snapshot url of the maven repo to the repositories section of your build.gradle file like
this:

    repositories {
        mavenCentral()
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
        }
    }

Then you can add the compile statement to tell gradle to get this library

    dependencies {
        compile 'org.ohmage:android-stream-lib:2.0-SNAPSHOT'
    }

EXAMPLE
-------

The simplest way to send points to ohmage is to use the [StreamPointBuilder]. This builder allows
you to specify the stream, version, metadata, and data for a point and then easily send a point to
the ohmage apk.

    JSONObject data = new JSONObject();
    mBuilder = new StreamPointBuilder("streamId", streamVersion)
        .withId()
        .now()
        .setData(data.toString());
    mBuilder.write(resolver);

This example creates a point for a stream "streamId". It sets a unique UUID on the point, sets the
timestamp of the point to the current time and sets the data to the value of the `data` JSONObject.
Finally it writes the data to ohmage via the ContentResolver.

If you are running on your main thread and want to easily offload the write to a different thread,
you could call [StreamPointBuilder#writeAsync(AsyncQueryHandler)]. This writes to an
[AsyncQueryHandler] which will return immediately.

The last examples showed how to send a single point to ohmage. If you have many points to send
within a few milliseconds, you can use the [AsyncBulkInsertHandler] to asynchronously batch points.
Instead of writing data to the ContentResolver, you just need to create an [AsyncBulkInsertHandler]
and call the method [StreamPointBuilder#writeAsync(AsyncBulkInsertHandler)] on the
[StreamPointBuilder].

Look at the [javadocs] for more information about the [StreamPointBuilder] and other classes.

INTEGRATE WITH OHMAGE ANDROID APP
---------------------------------

In your manifest, if you have a specific activity (that isn't the main activity) which is meant to
configure the stream, you should add an `intent-filter` to handle the
[StreamContract.ACTION_CONFIGURE] action. It will look something like this:

    <activity android:name=".ConfigureActivity" android:label="@string/app_name">
        <intent-filter>
            <action android:name="org.ohmage.streams.ACTION_CONFIGURE" />
            <category android:name="android.intent.category.DEFAULT" />
        </intent-filter>
    </activity>

If the main activity (`android.intent.action.MAIN`) is the activity which can be used to configure
the stream you don't need to add an extra `intent-filter`. Just make sure to include the
`android.intent.category.DEFAULT` category which lets ohmage launch it.

CREATE STREAM ON OHMAGE SERVER
------------------------------

Until the [front end] is finished, [EasyPost] can be used to create the stream. You should first
Authorize yourself using the `Authorization Token` api. Then you can use the `Stream Creation` api
with json like the following:

    {
       "name":"Mobility Stream (Google)",
       "description":"The mobility stream that uses the google classifier.",
       "definition":{
          "type":"array",
          "doc":"An array of probable activities and confidence levels",
          "optional":false,
          "name":"accel_data",
          "constType":{
             "type":"object",
             "doc":"A single activity and confidence level.",
             "optional":false,
             "fields":[
                {
                   "type":"string",
                   "doc":"The activity that was detected.",
                   "optional":false,
                   "name":"activity"
                },
                {
                   "type":"number",
                   "doc":"A value from 0 to 100 indicating the likelihood that the user is performing this activity.",
                   "optional":false,
                   "name":"confidence"
                }
             ]
          }
       },
       "apps":{
          "ios":null,
          "android":{
             "app_uri":"https://play.google.com/store/apps/details?id=org.ohmage.mobility",
             "authorization_uri":null,
             "package":"org.ohmage.mobility",
             "version":1
          }
       }
    }

`definition` is a [concordia] schema. More information on how to define your stream can be found
here: https://github.com/jojenki/Concordia/wiki/Examples

CONTRIBUTE
----------

If you would like to contribute code to the android stream lib, you can do so through
GitHub by forking the repository and sending a pull request.

In general if you are contributing we ask you to follow the [AOSP coding style guidelines]. If you
are using an IDE such as Eclipse, it is easiest to use the [AOSP style formatters]. If you are using
Android Studio the default formatting should conform to the AOSP guidelines.

You may [file an issue] if you find bugs or would like to add a new feature.

LICENSE
-------

    Copyright (C) 2013 ohmage

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[ohmage-probe-library]: https://github.com/ohmage/ohmage-probe-library
[StreamPointBuilder]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/StreamPointBuilder.html
[StreamPointBuilder#writeAsync(AsyncQueryHandler)]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/StreamPointBuilder.html#writeAsync(android.content.AsyncQueryHandler,%20int,%20java.lang.Object)
[AsyncQueryHandler]: http://developer.android.com/reference/android/content/AsyncQueryHandler.html
[AsyncBulkInsertHandler]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/AsyncBulkInsertHandler.html
[StreamPointBuilder#writeAsync(AsyncBulkInsertHandler)]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/StreamPointBuilder.html#writeAsync(org.ohmage.streams.AsyncBulkInsertHandler)
[javadocs]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/package-summary.html
[StreamContract.ACTION_CONFIGURE]: http://ohmage.org/android-stream-lib/reference/org/ohmage/streams/StreamContract.html#ACTION_CONFIGURE
[front end]: https://github.com/ohmage/front-end
[EasyPost]: https://dev.ohmage.org/ohmage/EasyPost.html
[concordia]: https://github.com/jojenki/Concordia
[AOSP coding style guidelines]: http://source.android.com/source/code-style.html
[AOSP style formatters]: http://source.android.com/source/using-eclipse.html#eclipse-formatting
[file an issue]: https://github.com/ohmage/android-stream-lib/issues/new
