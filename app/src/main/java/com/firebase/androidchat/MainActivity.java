package com.firebase.androidchat;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import java.util.Random;

public class MainActivity extends Activity {

    // TODO: change this to your own Firebase URL
    private static final String FIREBASE_URL = "https://android-chat.firebaseio-demo.com";

    private String mUsername;
    private Firebase mFirebaseRef;
    private ValueEventListener mConnectedListener;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private FirebaseAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Make sure we have a mUsername
        setupUsername();

        setTitle("Chatting as " + mUsername);

        // Setup our Firebase mFirebaseRef
        mFirebaseRef = new Firebase(FIREBASE_URL).child("chat");

        // Setup our input methods. Enter key on the keyboard or pushing the send button
        EditText inputText = (EditText) findViewById(R.id.messageInput);
        inputText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                }
                return true;
            }
        });

        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.chatList);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mAdapter = new FirebaseAdapter(30, 10, 5);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0) {
                    mAdapter.onScrollUp();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        mConnectedListener = mFirebaseRef.getRoot().child(".info/connected").addValueEventListener(
                new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean connected = (Boolean) dataSnapshot.getValue();
                if (connected) {
                    Toast.makeText(MainActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Disconnected from Firebase", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                // No-op
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mFirebaseRef.getRoot().child(".info/connected").removeEventListener(mConnectedListener);
    }

    @Override
    public void onDestroy() {
        mAdapter.clean();
        super.onDestroy();
    }

    private void setupUsername() {
        SharedPreferences prefs = getApplication().getSharedPreferences("ChatPrefs", 0);
        mUsername = prefs.getString("username", null);
        if (mUsername == null) {
            Random r = new Random();
            // Assign a random user name if we don't have one saved.
            mUsername = "JavaUser" + r.nextInt(100000);
            prefs.edit().putString("username", mUsername).commit();
        }
    }

    private void sendMessage() {
        EditText inputText = (EditText) findViewById(R.id.messageInput);
        String input = inputText.getText().toString();
        if (!input.equals("")) {
            // Create our 'model', a Chat object
            Chat chat = new Chat(input, mUsername);
            // Create a new, auto-generated child of that chat location, and save our chat data there
            mFirebaseRef.push().setValue(chat);
            inputText.setText("");
        }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        private TextView mAuthorView;
        private TextView mMessageView;

        public ChatViewHolder(View itemView) {
            super(itemView);
            mAuthorView = (TextView) itemView.findViewById(R.id.author);
            mMessageView = (TextView) itemView.findViewById(R.id.message);
        }

        public void setChat(Chat chat, int color) {
            mAuthorView.setText(chat.getAuthor()  + ": ");
            mAuthorView.setTextColor(color);
            mMessageView.setText(chat.getMessage());
        }
    }

    private class FirebaseAdapter extends RecyclerView.Adapter<ChatViewHolder>
            implements FirebaseListUtil.Listener {

        private FirebaseListUtil<Chat> mFirebaseListUtil;
        private boolean mLoading;
        private int mInitialLoadCount;
        private int mIncrementalLoadCount;
        private int mPrefetchThreshold;

        FirebaseAdapter(int initialLoadCount, int incrementalLoadCount, int prefetchThreshold) {
            mInitialLoadCount = initialLoadCount;
            mIncrementalLoadCount = incrementalLoadCount;
            mPrefetchThreshold = prefetchThreshold;
            HandlerThread asyncThread = new HandlerThread("AsyncThread");
            asyncThread.start();
            mFirebaseListUtil = new FirebaseListUtil<>(mFirebaseRef, Chat.class, new Handler(),
                    new Handler(asyncThread.getLooper()));
            mFirebaseListUtil.setListener(this);
        }

        @Override
        public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.chat_message, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ChatViewHolder holder, int position) {
            Chat chat = mFirebaseListUtil.getItem(position);
            String author = chat.getAuthor();
            int color = (author != null && author.equals(mUsername)) ? Color.RED : Color.BLUE;
            holder.setChat(chat, color);
        }

        @Override
        public int getItemCount() {
            if (mFirebaseListUtil.getCount() == 0 && !mLoading){
                mLoading = true;
                mFirebaseListUtil.loadOlderEntries(mInitialLoadCount);
            }

            return mFirebaseListUtil.getCount();
        }

        @Override
        public void onOlderEntriesLoaded(int originalEntryNum, int loadedEntryNum) {
            mLoading = false;
            // Old entries are always inserted in the beginning.
            notifyItemRangeInserted(0, loadedEntryNum);

            // Scroll to the the last (newest item) if this is the first load.
            if (originalEntryNum == 0) {
                notifyItemRangeInserted(originalEntryNum, loadedEntryNum);
                mRecyclerView.scrollToPosition(getItemCount() - 1);
            }
        }

        public void onScrollUp() {
            if (!mLoading && shouldPrefetch()) {
                mLoading = true;
                mFirebaseListUtil.loadOlderEntries(mIncrementalLoadCount);
            }
        }

        public void clean() {
            mFirebaseListUtil.cleanup();
        }

        private boolean shouldPrefetch() {
            return mLinearLayoutManager.findFirstVisibleItemPosition() < mPrefetchThreshold;
        }
    }
}
