/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package helpers;

import android.content.Context;
import android.util.Log;

import com.android.vrproject.NodeData;
import com.android.vrproject.MainActivity;
import com.android.vrproject.SceneData;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/** Helper class for Firebase storage of cloud anchor IDs. */
public class FirebaseManager {

  /** Listener for a new Cloud Anchor ID from the Firebase Database. */
  public interface CloudAnchorIdListener {
    void onCloudAnchorIdAvailable(String cloudAnchorId);
  }

  /** Listener for a new short code from the Firebase Database. */
  public interface ShortCodeListener {
    void onShortCodeAvailable(Integer shortCode);
  }

  private static final String TAG = FirebaseManager.class.getName();
  private static final String KEY_ROOT_DIR = "shared_anchor_codelab_root";
  private static final String KEY_NEXT_SHORT_CODE = "next_short_code";
  private static final String KEY_PREFIX = "anchor;";
  private static final int INITIAL_SHORT_CODE = 142;
  private final DatabaseReference rootRef;
  private DatabaseReference anchorRef;
  private MainActivity activity;

  /** Constructor that initializes the Firebase connection. */
  public FirebaseManager(Context context, MainActivity activity) {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(context);
    rootRef = FirebaseDatabase.getInstance(firebaseApp).getReference().child(KEY_ROOT_DIR);
    DatabaseReference.goOnline();

    this.activity = activity;
  }

  /** Gets a new short code that can be used to store the anchor ID. */
  public void nextShortCode(ShortCodeListener listener) {
    // Run a transaction on the node containing the next short code available. This increments the
    // value in the database and retrieves it in one atomic all-or-nothing operation.
    rootRef
        .child(KEY_NEXT_SHORT_CODE)
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                Integer shortCode = currentData.getValue(Integer.class);
                if (shortCode == null) {
                  // Set the initial short code if one did not exist before.
                  shortCode = INITIAL_SHORT_CODE - 1;
                }
                currentData.setValue(shortCode + 1);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!committed) {
                  Log.e(TAG, "Firebase Error", error.toException());
                  listener.onShortCodeAvailable(null);
                } else {
                  listener.onShortCodeAvailable(currentData.getValue(Integer.class));
                }
              }
            });
  }

  /** Stores the cloud anchor ID in the configured Firebase Database. */
  public void storeUsingShortCode(int shortCode, SceneData sceneData) {
    rootRef.child(KEY_PREFIX + shortCode).setValue(sceneData);
  }

  /**
   * Retrieves the cloud anchor ID using a short code. Returns an empty string if a cloud anchor ID
   * was not stored for this short code.
   */
  public void getCloudAnchorId(int shortCode, CloudAnchorIdListener listener) {
        anchorRef = rootRef.child(KEY_PREFIX + shortCode);
        anchorRef.addListenerForSingleValueEvent(
              new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot dataSnapshot) {
                      // Listener invoked when the data is successfully read from Firebase.
                      SceneData sceneData = dataSnapshot.getValue(SceneData.class);
                      listener.onCloudAnchorIdAvailable(sceneData.getCloudAnchorId());

                      Log.e("CLOUDID_NULL", "getCloudAnchorId(): "+sceneData.getCloudAnchorId());
                      activity.setSceneData(sceneData);
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                      Log.e(
                              TAG,
                              "The Firebase operation for getCloudAnchorId was cancelled.",
                              error.toException());
                      listener.onCloudAnchorIdAvailable(null);
                  }
              });
        anchorRef.addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
              SceneData sceneData = dataSnapshot.getValue(SceneData.class);
              Log.e("CLOUDID_NULL", "anchorRef.addValueEventListener: "+sceneData.getCloudAnchorId());
              activity.setSceneData(sceneData);
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
              System.out.println("The read failed: " + databaseError.getCode());
          }
      });

  }

  public void updateSceneData(int shortCode, SceneData sceneData){
      Log.e("UPDATE", "called"+shortCode);
      Log.e("CLOUDID_NULL", "updateSceneData: "+sceneData.getCloudAnchorId());

      Map<String, Object> updates = new HashMap<>();
      updates.put(KEY_PREFIX + shortCode, sceneData);
      rootRef.updateChildren(updates);
  }
}
