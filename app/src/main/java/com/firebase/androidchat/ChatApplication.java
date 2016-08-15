package com.firebase.androidchat;

import com.google.firebase.FirebaseApp;

/**
 * @author Jenny Tong (mimming)
 * @since 12/5/14
 *
 * Initialize Firebase with the application context. This must happen before the client is used.
 */
public class ChatApplication extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //FirebaseApp.setAndroidContext(this);
    }
}
