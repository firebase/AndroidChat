package com.firebase.androidchat;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: greg
 * Date: 6/21/13
 * Time: 1:47 PM
 */

/**
 * This class is a generic way of backing an Android ListView with a Firebase location.
 * It handles all of the child events at the given Firebase location. It marshals received data into the given
 * class type. Extend this class and provide an implementation of <code>populateView</code>, which will be given an
 * instance of your list item layout and an instance your class that holds your data. Simply populate the view however
 * you like and this class will handle updating the list as the data changes.
 * @param <T> The class type to use as a model for the data contained in the children of the given Firebase location
 */
public abstract class FirebaseListAdapter<T> extends BaseAdapter {

    private Query ref;
    private Class<T> modelClass;
    private int layout;
    private LayoutInflater inflater;
    private List<T> models;
    private Map<String, T> modelNames;
    private ChildEventListener listener;


    /**
     * @param ref The Firebase location to watch for data changes. Can also be a slice of a location, using some
     *            combination of <code>limit()</code>, <code>startAt()</code>, and <code>endAt()</code>,
     * @param modelClass Firebase will marshall the data at a location into an instance of a class that you provide
     * @param layout This is the layout used to represent a single list item. You will be responsible for populating an
     *               instance of the corresponding view with the data from an instance of modelClass.
     * @param activity The activity containing the ListView
     */
    public FirebaseListAdapter(Query ref, Class<T> modelClass, int layout, Activity activity) {
        this.ref = ref;
        this.modelClass = modelClass;
        this.layout = layout;
        inflater = activity.getLayoutInflater();
        models = new ArrayList<T>();
        modelNames = new HashMap<String, T>();
        // Look for all child events. We will then map them to our own internal ArrayList, which backs ListView
        listener = this.ref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {

                T model = dataSnapshot.getValue(FirebaseListAdapter.this.modelClass);
                modelNames.put(dataSnapshot.getName(), model);

                // Insert into the correct location, based on previousChildName
                if (previousChildName == null) {
                    models.add(0, model);
                } else {
                    T previousModel = modelNames.get(previousChildName);
                    int previousIndex = models.indexOf(previousModel);
                    int nextIndex = previousIndex + 1;
                    if (nextIndex == models.size()) {
                        models.add(model);
                    } else {
                        models.add(nextIndex, model);
                    }
                }

                notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                // One of the models changed. Replace it in our list and name mapping
                String modelName = dataSnapshot.getName();
                T oldModel = modelNames.get(modelName);
                T newModel = dataSnapshot.getValue(FirebaseListAdapter.this.modelClass);
                int index = models.indexOf(oldModel);

                models.set(index, newModel);
                modelNames.put(modelName, newModel);

                notifyDataSetChanged();
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

                // A model was removed from the list. Remove it from our list and the name mapping
                String modelName = dataSnapshot.getName();
                T oldModel = modelNames.get(modelName);
                models.remove(oldModel);
                modelNames.remove(modelName);
                notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {

                // A model changed position in the list. Update our list accordingly
                String modelName = dataSnapshot.getName();
                T oldModel = modelNames.get(modelName);
                T newModel = dataSnapshot.getValue(FirebaseListAdapter.this.modelClass);
                int index = models.indexOf(oldModel);
                models.remove(index);
                if (previousChildName == null) {
                    models.add(0, newModel);
                } else {
                    T previousModel = modelNames.get(previousChildName);
                    int previousIndex = models.indexOf(previousModel);
                    int nextIndex = previousIndex + 1;
                    if (nextIndex == models.size()) {
                        models.add(newModel);
                    } else {
                        models.add(nextIndex, newModel);
                    }
                }
                notifyDataSetChanged();
            }

            @Override
            public void onCancelled() {
                Log.e("FirebaseListAdapter", "Listen was cancelled, no more updates will occur");
            }
        });
    }

    public void cleanup() {
        // We're being destroyed, let go of our listener and forget about all of the models
        ref.removeEventListener(listener);
        models.clear();
        modelNames.clear();
    }

    @Override
    public int getCount() {
        return models.size();
    }

    @Override
    public Object getItem(int i) {
        return models.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = inflater.inflate(layout, viewGroup, false);
        }

        T model = models.get(i);
        // Call out to subclass to marshall this model into the provided view
        populateView(view, model);
        return view;
    }

    /**
     * Each time the data at the given Firebase location changes, this method will be called for each item that needs
     * to be displayed. The arguments correspond to the layout and modelClass given to the constructor of this class.
     *
     * Your implementation should populate the view using the data contained in the model.
     * @param v The view to populate
     * @param model The object containing the data used to populate the view
     */
    protected abstract void populateView(View v, T model);
}
