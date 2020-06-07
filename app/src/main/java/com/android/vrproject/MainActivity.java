package com.android.vrproject;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import java.util.HashMap;
import java.util.Map;

import helpers.CloudAnchorManager;
import helpers.FirebaseManager;
import helpers.ResolveDialogFragment;

public class MainActivity extends AppCompatActivity {

    /* ARCORE AND HELPERS */
    private CloudAnchorFragment fragment;
    private CloudAnchorManager cloudAnchorManager;
    private FirebaseManager firebaseManager;

    /* RENDERABLES */
    private ModelRenderable modelRenderable;
    private ModelRenderable anchorRedRenderable;
    private ModelRenderable anchorGreenRenderable;

    /* NODES */
    private AnchorNode redAnchorNode;
    private Anchor hostingAnchor;

    private AnchorNode mainAnchorNode;
    private Anchor anchor;

    /* SCENE */
    private Map<Integer, myNode> myNodeMap;
    private Scene arScene;
    private SceneData sceneData;
    private int shortCode = 0;

    /* BUTTONS AND VIEWS */
    private Button clearButton;
    private Button resolveButton;
    private TextView infoText;


    /*
    called initially when application is started
    does all necessary preparation
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialized helpers
        cloudAnchorManager = new CloudAnchorManager();
        firebaseManager = new FirebaseManager(this, this);

        // Get the Fragment from the activity's layout (see res > layout > activity_main.xml
        fragment = (CloudAnchorFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        // handle listeners when Scene is updated
        arScene = fragment.getArSceneView().getScene();
        arScene.addOnUpdateListener(frameTime -> {
            cloudAnchorManager.onUpdate();
        });

        // build all necessary renderables

        // Andy
        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(modelRenderable -> this.modelRenderable = modelRenderable);

        // Red dot
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(
                        material -> {
                            anchorRedRenderable =
                                    ShapeFactory.makeCylinder(0.01f, 0.001f, new Vector3(0.0f, 0.0f, 0.0f), material); });

        // Green dot
        MaterialFactory.makeTransparentWithColor(this, new Color(android.graphics.Color.GREEN))
                .thenAccept(
                        material -> {
                            anchorGreenRenderable =
                                    ShapeFactory.makeCylinder(0.01f, 0.001f, new Vector3(0.0f, 0.0f, 0.0f), material); });



        // listen for tap --> set AnchorNode and put Andy on top of it
        fragment.setOnTapArPlaneListener(
                (HitResult hitresult, Plane plane, MotionEvent motionevent) -> {
                    if (anchor != null) {
                        // anchor was resolved --> add new object
                        addNewObject();
                    }
                    else {
                        // no anchor yet --> create new one and host it.
                        createNewCloudAnchor(hitresult);
                    }
                }
        );

        sceneData = new SceneData();
        sceneData.setNodeDataMap(new HashMap<>());

        myNodeMap = new HashMap<>();

        // initialize buttons
        View buttonPanel = findViewById(R.id.buttonPanel);

        clearButton = buttonPanel.findViewById(R.id.clear_button);
        clearButton.setOnClickListener(v -> onClearButtonPressed());

        resolveButton = buttonPanel.findViewById(R.id.resolve_button);
        resolveButton.setOnClickListener(v -> onResolveButtonPressed());

        infoText = buttonPanel.findViewById(R.id.infotext);
    }



    /*
    OBJECT MANIPULATING FUNCTIONS
     */


    /*
    creates new anchor and starts the hosting process
     */
    private void createNewCloudAnchor(HitResult hitResult) {
        // set new AnchorNode into the scene
        hostingAnchor = hitResult.createAnchor();

        // set red dot
        redAnchorNode = new AnchorNode(hostingAnchor);
        redAnchorNode.setParent(fragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(redAnchorNode);
        node.setRenderable(anchorRedRenderable);

        // host it
        cloudAnchorManager.hostCloudAnchor(
                fragment.getArSceneView().getSession(), hostingAnchor, this::onHostedAnchorAvailable);
    }

    /*
    Adds a new object to the scene. If no anchor has been resolved, it does nothing
     */
    public void addNewObject() {
        if (shortCode == 0) {
            // not hosted yet --> do nothing
            return;
        }

        // add new object to this anchor
        int index = sceneData.addNew();
        myNode node = new myNode(fragment.getTransformationSystem(), this, index);
        myNodeMap.put(index, node);

        initObject(index);

        // update the cloud state of the scene
        updateFirebase(index);
    }

    /*
    This is called whenever the cloud state is changed. Updates the object in the scene
     */
    public void setSceneData(SceneData sceneData){
        this.sceneData = sceneData;

        updateObjects();
    }

    /*
    initializes one object. The corresponding myNode object hast have already been initialized.
     */
    public void initObject(int index){
        // put Andy on the anchor
        myNode node = myNodeMap.get(index);
        NodeData nodeData = sceneData.getNodeData(index);

        float[] anchorPosition = anchor.getPose().getTranslation();

        //add new anchorNode right on top of original anchor --> can then be moved
        AnchorNode anchorNode = new AnchorNode();
        anchorNode.setParent(mainAnchorNode);
        anchorNode.setLocalPosition(new Vector3(0,0,0));

        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();

        node.getScaleController().setMinScale(0.2f);
        node.getScaleController().setMaxScale(5f);
    }


    /*
    iterates through all objects and updates their location, scale and rotation according to the cloud state
     */
    public void updateObjects() {
        for (int index : myNodeMap.keySet()) {
            NodeData nodeData = sceneData.getNodeData(index);
            myNode node = myNodeMap.get(index);

            // manipulate object
            node.getParent().setLocalPosition(nodeData.getPosition());
            node.setLocalScale(nodeData.getScale());
            node.getScaleController().onActivated(node); // necessary since otherwise original scale remains cached
            node.setLocalRotation(nodeData.getRotation());
        }

    }



    /*
    LISTENER FUNCTIONS
     */


    private void onClearButtonPressed() {
        clear();
    }

    /*
     shows the resolve dialog, asks for short code, then call onShortCodeEntered
     */
    private void onResolveButtonPressed() {
        ResolveDialogFragment dialog = ResolveDialogFragment.createWithOkListener(
                this::onShortCodeEntered);;
        dialog.show(fragment.getFragmentManager(), "Resolve");
    }


    /*
    called when the CloudAnchor has been resolved.
    It stores the current scene and the newly obtained CloudAnchorID in the cloud state
     */
    private synchronized void onHostedAnchorAvailable(Anchor anchor) {
        Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            String cloudAnchorId = anchor.getCloudAnchorId();

            // store sceneData including cloudAnchorID in database with next available short code
            firebaseManager.nextShortCode(shortCode -> {
                if (shortCode != null) {
                    sceneData.setCloudAnchorId(cloudAnchorId);
                    firebaseManager.storeUsingShortCode(shortCode, sceneData);
                    setMessage("Saving with " + shortCode);


                    firebaseManager.getCloudAnchorId(shortCode, cloudAnchorIdLambda -> {
                        // if anchor ID not found...
                        if (cloudAnchorIdLambda == null || cloudAnchorIdLambda.isEmpty()) {
                            setMessage("A Cloud Anchor ID for the short code " + shortCode + " was not found.");
                            return;
                        }

                        // resolve cloud anchor
                        cloudAnchorManager.resolveCloudAnchor(
                                fragment.getArSceneView().getSession(),
                                cloudAnchorIdLambda,
                                anchorLambda -> onResolvedAnchorAvailable(anchorLambda, shortCode));
                    });

                } else {
                    // Firebase could not provide a short code.
                    setMessage("Cloud Anchor Hosted, but could not get a short code from Firebase.");
                }
            });
        } else {
            setMessage("Error while hosting: " + cloudState.toString());
        }
    }

    /*
    called when short code entered in resolve dialog.
    clears scene and resolves corresponding cloud anchor and its scene
     */
    private synchronized void onShortCodeEntered(int shortCode) {
        clear();

        this.shortCode = shortCode;

        firebaseManager.getCloudAnchorId(shortCode, cloudAnchorId -> {
            // if anchor ID not found...
            if (cloudAnchorId == null || cloudAnchorId.isEmpty()) {
                setMessage("A Cloud Anchor ID for the short code " + shortCode + " was not found.");
                return;
            }

            // resolve cloud anchor
            cloudAnchorManager.resolveCloudAnchor(
                    fragment.getArSceneView().getSession(),
                    cloudAnchorId,
                    anchorLambda -> onResolvedAnchorAvailable(anchorLambda, shortCode));
        });
    }

    /*
    called when given short code could be resolved.
    changes red dot to green.

     */
    private synchronized void onResolvedAnchorAvailable(Anchor anchor, int shortCode) {
        clear();

        this.shortCode = shortCode;

        this.anchor = anchor;

        if (redAnchorNode != null) redAnchorNode.setParent(null);

        //set green ball on top of anchor
        mainAnchorNode = new AnchorNode(this.anchor);
        mainAnchorNode.setParent(fragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(mainAnchorNode);
        node.setRenderable(anchorGreenRenderable);

        Anchor.CloudAnchorState cloudState = this.anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            setMessage("Resolved. Short code: " + shortCode);

            // sceneData has been set according to newly resolved anchor
            myNodeMap = sceneData.getTransformableNodeMap(fragment.getTransformationSystem(), this);

            // initialize local objects according to newly obtained scene information and update them.
            for (int index : myNodeMap.keySet()) {
                initObject(index);
            }
            updateObjects();
        } else {
            setMessage("Error while resolving anchor with short code "+shortCode+". Error: " + cloudState.toString());
        }
    }



    /*
    HELPER FUNCTIONS
     */



    /*
    clear entire scene
     */
    public void clear(){
        setMessage("cleared");

        // clear pending listeners
        cloudAnchorManager.clearListeners();

        shortCode = 0;

        if (myNodeMap != null){
            for( TransformableNode node : myNodeMap.values()){
                node.getParent().setParent(null); // remove all nodes from the scene
            }
        }

        // throw away the old map
        myNodeMap = new HashMap<>();

        if (mainAnchorNode != null) mainAnchorNode.setParent(null);

        anchor = null;
    }

    /*
    sets a massage to the infoText View.
     */
    public void setMessage(String message) {
        infoText.setText(message);
    }

    /*
    getter function for short code
     */
    public int getShortCode() {
        return shortCode;
    }

    /*
    setter function for short code
     */
    public void setShortCode(int shortCode) {
        this.shortCode = shortCode;
    }

    /*
    Updates one specific object in the firebase cloud state.
     */
    public void updateFirebase(int index){
        if (anchor != null) {
            NodeData nodeData = sceneData.getNodeData(index);
            myNode node = myNodeMap.get(index);

            // update values
            nodeData.setPosition(node.getParent().getLocalPosition());
            nodeData.setScale(node.getLocalScale());
            nodeData.setRotation(node.getLocalRotation());
            sceneData.setNodeData(index, nodeData);

            firebaseManager.updateSceneData(getShortCode(), sceneData);
        }
    }
}

