package com.firebase.androidchat;

import android.os.Handler;
import android.util.Log;

import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides utility methods for loading more entries from a given Firebase location.
 *
 * @param <T> The class type to use as a model for the data contained in the children of the given Firebase location
 */
public class FirebaseListUtil<T> {

    public interface Listener {
        void onOlderEntriesLoaded(int originalEntryNum, int loadedEntryNum);
    }

    private static class Entry<T> {
        String key;
        T val;

        public Entry(String key, T val) {
            this.key = key;
            this.val = val;
        }
    }

    private Query mRef;
    private Class<T> mModelClass;
    private Handler mMainHandler;
    private Handler mAsyncHandler;
    private List<Entry<T>> mEntryList;
    private Listener mListener;

    /**
     * @param ref          The Firebase location to watch for data changes. Can also be a slice of a location, using some
     *                     combination of <code>startAt()</code>, and <code>endAt()</code>. Do NOT use <code>limit()</code>.
     * @param modelClass   Firebase will marshall the data at a location into an instance of a class that you provide.
     * @param mainHandler  All callbacks will be called on this <code>Handler</code>.
     * @param asyncHandler All async interactions with Firebase locations will happen on this <code>Handler</code>.
     */
    public FirebaseListUtil(Query ref, Class<T> modelClass, Handler mainHandler, Handler asyncHandler) {
        mRef = ref;
        mModelClass = modelClass;
        mMainHandler = mainHandler;
        mAsyncHandler = asyncHandler;
        mEntryList = new ArrayList<>();
    }

    public void cleanup() {
        // We're being destroyed, clean all entries in memory
        mEntryList.clear();
    }

    public int getCount() {
        return mEntryList.size();
    }

    public T getItem(int i) {
        return mEntryList.get(i).val;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void loadOlderEntries(final int num) {
        mAsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                loadOlderEntriesInternal(num);
            }
        });
    }

    private void loadOlderEntriesInternal(final int num) {
        Query query;
        if (mEntryList.size() == 0) {
            query = mRef.limitToLast(num);
            Log.d("XXX", "nothing in list, limitToLast " + num);

        } else {
            query = mRef.orderByKey().endAt(mEntryList.get(0).key).limitToLast(num + 1);
            Log.d("XXX", "load older entries called, endAt = " + mEntryList.get(mEntryList.size() - 1).val.toString() + ", limitToLast = " + (num + 1));
        }

        final Entry<T> entryToIgnore = mEntryList.size() == 0 ? null : mEntryList.get(0);
        final int originalEntryNum = mEntryList.size();

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Entry<T>> newEntryList = new ArrayList<>(num);
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    Entry<T> newModel = new Entry<>(child.getKey(),
                            child.getValue(FirebaseListUtil.this.mModelClass));
                    if (entryToIgnore != null && entryToIgnore.key.equals(newModel.key)) {
                        continue;
                    }

                    newEntryList.add(newModel);
                }
                mEntryList.addAll(0, newEntryList);

                final int loadedEntryNum = mEntryList.size() - originalEntryNum;

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) {
                            mListener.onOlderEntriesLoaded(originalEntryNum, loadedEntryNum);
                        }
                    }
                });
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                // Do nothing.
            }
        });
    }
}
