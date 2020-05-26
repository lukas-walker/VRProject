package com.android.vrproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.HashMap;
import java.util.Map;

import helpers.CloudAnchorManager;
import helpers.FirebaseManager;
import helpers.ResolveDialogFragment;

public class MainActivity extends AppCompatActivity {

    private CloudAnchorFragment fragment;
    private CloudAnchorManager cloudAnchorManager;
    private FirebaseManager firebaseManager;

    private ModelRenderable modelRenderable;
    private AnchorNode anchorNode;
    private Map<Integer, TransformableNode> transformableNodeMap;
    private Scene arScene;
    private SceneData sceneData;
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

        transformableNodeMap = new HashMap<>();

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

            int index = sceneData.addNew();
            TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
            transformableNodeMap.put(index, node);

            initObject(index);
            updateObjects();

            return;
        }

        sceneData = new SceneData();
        sceneData.setNodeDataMap(new HashMap<>());

        // set new AnchorNode into the scene
        Anchor anchor = hitResult.createAnchor();
        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(fragment.getArSceneView().getScene());

        // add new node and nodeData to Scene
        int index = sceneData.addNew();
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        transformableNodeMap.put(index, node);

        initObject(index);
        updateObjects();

        cloudAnchorManager.hostCloudAnchor(
                fragment.getArSceneView().getSession(), anchor, this::onHostedAnchorAvailable);
    }

    public int getShortCode(){
        return  shortCode;
    }

    public void setShortCode(int shortCode){
        this.shortCode = shortCode;
    }

    private void onClearButtonPressed() {
        infoText.setText("cleared");

        // clear pending listeners
        cloudAnchorManager.clearListeners();

        shortCode = 0;

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
                anchorNode = null;
            }

            return;
        }

        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(fragment.getArSceneView().getScene());

        // sceneData is up todate, need to update TransformationNodeMap
        transformableNodeMap = sceneData.getTransformableNodeMap(fragment.getTransformationSystem(), anchorNode, modelRenderable);

        for (int index : transformableNodeMap.keySet()) {
            initObject(index);
        }

        updateObjects();
    }


    private synchronized void onHostedAnchorAvailable(Anchor anchor) {
        infoText.setText("onHostedAnchorAvailable was called");

        // called when hosted anchor and respective shortcode is available

        Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            String cloudAnchorId = anchor.getCloudAnchorId();
            firebaseManager.nextShortCode(shortCode -> {
                if (shortCode != null) {
                    setShortCode(shortCode);
                    sceneData.setCloudAnchorId(cloudAnchorId);
                    firebaseManager.storeUsingShortCode(shortCode, sceneData);
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

    public void setSceneData(SceneData sceneData){
        this.sceneData = sceneData;
        updateObjects();
    }

    public void initObject(int index){
        // put Andy on the anchor
        TransformableNode node = transformableNodeMap.get(index);
        NodeData nodeData = sceneData.getNodeData(index);

        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();

        node.getScaleController().setMinScale(0.2f);
        node.getScaleController().setMaxScale(5f);

        /*node.setOnTapListener((hitTestResult, motionEvent) -> {
            nodeData.setPosition(node.getWorldPosition());
            nodeData.setScale(node.getWorldScale());
            nodeData.setRotation(node.getWorldRotation());
            sceneData.setNodeData(index, nodeData);
        });

         */

        Log.e("UPDATE", "in initObject: "+shortCode);

        node.setOnTouchListener((hitTestResult, event) -> {
            Log.e("UPDATE", "in listener: "+shortCode);

            nodeData.setPosition(node.getLocalPosition());
            nodeData.setScale(node.getLocalScale());
            nodeData.setRotation(node.getLocalRotation());
            sceneData.setNodeData(index, nodeData);

            if (event.getAction() == MotionEvent.ACTION_UP ) {
                if (anchorNode != null) {
                    firebaseManager.updateSceneData(getShortCode(), sceneData);
                }
            }
            return true;
        });
    }

    public void updateObjects() {

        for (int index : transformableNodeMap.keySet()) {
            Log.e("INDEX", ((Integer)index).toString());
            NodeData nodeData = sceneData.getNodeData(index);
            TransformableNode node = transformableNodeMap.get(index);

            node.setLocalPosition(nodeData.getPosition());
            node.setLocalScale(nodeData.getScale());
            node.setLocalRotation(nodeData.getRotation());
        }
    }

/*
    protected static ObjectAnimator createAnimator(boolean clockwise, float axisTiltDeg) {
        // Node's setLocalRotation method accepts Quaternions as parameters.
        // First, set up orientations that will animate a circle.
        Quaternion[] orientations = new Quaternion[4];
        // Rotation to apply first, to tilt its axis.
        Quaternion baseOrientation = Quaternion.axisAngle(new Vector3(1.0f, 0f, 0.0f), axisTiltDeg);
        for (int i = 0; i < orientations.length; i++) {
            float angle = i * 360 / (float)(orientations.length - 1);
            if (clockwise) {
                angle = 360 - angle;
            }
            Quaternion orientation = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), angle);
            orientations[i] = Quaternion.multiply(baseOrientation, orientation);
        }

        ObjectAnimator orbitAnimation = new ObjectAnimator();
        // Cast to Object[] to make sure the varargs overload is called.
        orbitAnimation.setObjectValues((Object[]) orientations);

        // Next, give it the localRotation property.
        orbitAnimation.setPropertyName("localRotation");

        // Use Sceneform's QuaternionEvaluator.
        orbitAnimation.setEvaluator(new QuaternionEvaluator());

        //  Allow orbitAnimation to repeat forever
        orbitAnimation.setRepeatCount(ObjectAnimator.INFINITE);
        orbitAnimation.setRepeatMode(ObjectAnimator.RESTART);
        orbitAnimation.setInterpolator(new LinearInterpolator());
        orbitAnimation.setAutoCancel(true);

        return orbitAnimation;
    }

 */
}

