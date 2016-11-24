package com.firebase.androidchat;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides utility methods for loading more {@link DataSnapshot}s from a given Firebase
 * location.
 */
public class FirebaseListUtil {

    public interface Listener {
        void onOlderEntriesLoaded(int originalEntryNum, int loadedEntryNum);
        void onNewerEntriesLoaded(int originalEntryNum, int loadedEntryNum);
    }

    private Query mRef;
    private Query mMonitorQuery;
    private List<DataSnapshot> mSnapshotList;
    private Listener mListener;

    /**
     * @param ref          The Firebase location to watch for data changes. Can also be a slice of
     *                     a location, using some combination of <code>startAt()</code>,
     *                     and <code>endAt()</code>. DO NOT USE <code>limit()</code>.
     */
    public FirebaseListUtil(Query ref) {
        mRef = ref;
        mSnapshotList = new ArrayList<>();
    }

    public void cleanup() {
        // We're being destroyed, clean all entries in memory
        mSnapshotList.clear();
    }

    public int getCount() {
        return mSnapshotList.size();
    }

    public DataSnapshot getSnapshot(int i) {
        return mSnapshotList.get(i);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void loadOlderEntries(final int num) {
        Query query;
        if (mSnapshotList.size() == 0) {
            query = mRef.orderByKey().limitToLast(num);
        } else {
            query = mRef.orderByKey()
                    .endAt(mSnapshotList.get(0).getKey())
                    .limitToLast(num + 1);
        }

        final DataSnapshot entryToIgnore = mSnapshotList.size() == 0 ? null : mSnapshotList.get(0);
        final int originalEntryNum = mSnapshotList.size();

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<DataSnapshot> newEntryList = new ArrayList<>(num);
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    if (entryToIgnore != null
                            && entryToIgnore.getKey().equals(child.getKey())) {
                        continue;
                    }

                    newEntryList.add(child);
                }
                mSnapshotList.addAll(0, newEntryList);

                int loadedEntryNum = mSnapshotList.size() - originalEntryNum;
                if (mListener != null) {
                    mListener.onOlderEntriesLoaded(originalEntryNum, loadedEntryNum);
                }

                updateMonitorQuery();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                // Do nothing.
            }
        });
    }

    private void updateMonitorQuery() {
        if (mMonitorQuery != null) {
            mMonitorQuery.removeEventListener(mChildEventListener);
        }

        if (mSnapshotList.size() == 0) {
            mMonitorQuery = mRef.orderByKey();
        } else {
            mMonitorQuery = mRef.orderByKey()
                    .startAt(mSnapshotList.get(mSnapshotList.size() - 1).getKey());
        }

        mMonitorQuery.addChildEventListener(mChildEventListener);

    }

    // Used to monitor latest changes.
    private ChildEventListener mChildEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String previousChildKey) {
            if (mSnapshotList.size() != 0) {
                DataSnapshot entryToIgnore = mSnapshotList.get(mSnapshotList.size() - 1);
                if (entryToIgnore.getKey().equals(dataSnapshot.getKey())) {
                    return;
                }
            }

            int oldSize = mSnapshotList.size();
            mSnapshotList.add(dataSnapshot);
            mListener.onNewerEntriesLoaded(oldSize, 1);
            updateMonitorQuery();
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {

        }
    };
}
