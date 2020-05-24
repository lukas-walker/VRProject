package com.android.vrproject;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import helpers.CloudAnchorManager;
import helpers.FirebaseManager;
import helpers.ResolveDialogFragment;

public class MainActivity extends AppCompatActivity {

    private CloudAnchorFragment fragment;
    private CloudAnchorManager cloudAnchorManager;
    private FirebaseManager firebaseManager;

    private ModelRenderable modelRenderable;
    private AnchorNode anchorNode;
    private TransformableNode lamp;
    private Scene arScene;
    private AnchorData anchorData;
    private int shortCode;

    private Button clearButton;
    private Button resolveButton;
    private TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cloudAnchorManager = new CloudAnchorManager();
        firebaseManager = new FirebaseManager(this, this);

        fragment = (CloudAnchorFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        arScene = fragment.getArSceneView().getScene();
        arScene.addOnUpdateListener(frameTime -> cloudAnchorManager.onUpdate());

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(modelRenderable -> this.modelRenderable = modelRenderable);


        // listen for tap --> set AnchorNode and put Andy on top of it
        fragment.setOnTapArPlaneListener(
                (HitResult hitresult, Plane plane, MotionEvent motionevent) -> {
                    createNewCloudAnchor(hitresult);
                }
        );

        // initialize buttons
        View buttonPanel = findViewById(R.id.buttonPanel);

        clearButton = buttonPanel.findViewById(R.id.clear_button);
        clearButton.setOnClickListener(v -> onClearButtonPressed());

        resolveButton = buttonPanel.findViewById(R.id.resolve_button);
        resolveButton.setOnClickListener(v -> onResolveButtonPressed());

        infoText = buttonPanel.findViewById(R.id.infotext);

    }

    private void createNewCloudAnchor(HitResult hitResult) {

        infoText.setText("Plane tapped");
        // renderable not yet loaded
        if (modelRenderable == null){
            return;
        }

        if (anchorNode != null) {
            // Do nothing if there was already an anchor in the Scene.
            return;
        }

        anchorData = new AnchorData();
        anchorData.setPosition(new Vector3(0,0,0));
        anchorData.setScale(new Vector3(1, 1, 1));

        // set new AnchorNode into the scene
        Anchor anchor = hitResult.createAnchor();
        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(fragment.getArSceneView().getScene());

        initAndy();

        cloudAnchorManager.hostCloudAnchor(
                fragment.getArSceneView().getSession(), anchor, this::onHostedAnchorAvailable);
    }


    private void onClearButtonPressed() {
        infoText.setText("cleared");

        // clear pending listeners
        cloudAnchorManager.clearListeners();

        // Clear the anchor from the scene.
        updateAnchor(null);
    }

    // show resolve dialog, ask for CloudAnchor ID, then call onShortCodeEntered
    private void onResolveButtonPressed() {


        ResolveDialogFragment dialog = ResolveDialogFragment.createWithOkListener(
                this::onShortCodeEntered);;
        dialog.show(fragment.getFragmentManager(), "Resolve");
    }

    private void updateAnchor(Anchor anchor)
    {
        if (anchor == null) {
            // reset pressed
            if (anchorNode != null) {
                arScene.removeChild(anchorNode);
                //anchorNode = null;
            }
            return;
        }

        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(fragment.getArSceneView().getScene());

        initAndy();
        updateAndy();

    }


    private synchronized void onHostedAnchorAvailable(Anchor anchor) {
        infoText.setText("onHostedAnchorAvailable was called");

        // called when hosted anchor and respective shortcode is available

        Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            String cloudAnchorId = anchor.getCloudAnchorId();
            firebaseManager.nextShortCode(shortCode -> {
                if (shortCode != null) {
                    anchorData.setCloudAnchorId(cloudAnchorId);
                    firebaseManager.storeUsingShortCode(shortCode, anchorData);
                    infoText.setText("Cloud Anchor Hosted. Short code: " + shortCode);
                } else {
                    // Firebase could not provide a short code.
                    infoText.setText("Cloud Anchor Hosted, but could not get a short code from Firebase.");
                }
            });
        } else {
            infoText.setText("Error while hosting: " + cloudState.toString());
        }
    }

    private synchronized void onShortCodeEntered(int shortCode) {

        // clear pending listeners
        cloudAnchorManager.clearListeners();

        // Clear the anchor from the scene.
        updateAnchor(null);

        this.shortCode = shortCode;

        infoText.setText("onShortCodeEntered was called");

        firebaseManager.getCloudAnchorId(shortCode, cloudAnchorId -> {
            // if anchor ID not found...
            if (cloudAnchorId == null || cloudAnchorId.isEmpty()) {
                infoText.setText("A Cloud Anchor ID for the short code " + shortCode + " was not found.");
                return;
            }

            // resolve cloud anchor
            cloudAnchorManager.resolveCloudAnchor(
                    fragment.getArSceneView().getSession(),
                    cloudAnchorId,
                    anchor -> onResolvedAnchorAvailable(anchor, shortCode));
        });
    }

    private synchronized void onResolvedAnchorAvailable(Anchor anchor, int shortCode) {
        this.shortCode = shortCode;

        Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            infoText.setText("Cloud Anchor Resolved. Short code: " + shortCode);
            updateAnchor(anchor);
        } else {
            infoText.setText("Error while resolving anchor with short code "+shortCode+". Error: " + cloudState.toString());
        }
    }

    public void setAnchorData(AnchorData anchorData){
        this.anchorData = anchorData;
        updateAndy();
    }

    public void initAndy(){
        // put Andy on the anchor
        lamp = new TransformableNode(fragment.getTransformationSystem());

        lamp.getScaleController().setMinScale(0.2f);
        lamp.getScaleController().setMaxScale(5f);

        lamp.setParent(anchorNode);
        lamp.setRenderable(modelRenderable);
        lamp.select();
        lamp.setOnTapListener((hitTestResult, motionEvent) -> {
            anchorData.setPosition(lamp.getWorldPosition());
            anchorData.setScale(lamp.getWorldScale());
            firebaseManager.updateAnchorData(shortCode, anchorData);
        });
    }

    public void updateAndy() {
        lamp.setWorldPosition(anchorData.getPosition());
        lamp.setWorldScale(anchorData.getScale());
    }
}

