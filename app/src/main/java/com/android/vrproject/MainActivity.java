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

        cloudAnchorManager = new CloudAnchorManager();
        firebaseManager = new FirebaseManager(this, this);

        fragment = (CloudAnchorFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        arScene = fragment.getArSceneView().getScene();
        arScene.addOnUpdateListener(frameTime -> {
            cloudAnchorManager.onUpdate();
            if (anchor != null) Log.e("ANCHOR POSITION", anchor.getPose().toString());
        });

        // build all necessary renderables
        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(modelRenderable -> this.modelRenderable = modelRenderable);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(
                        material -> {
                            anchorRedRenderable =
                                    ShapeFactory.makeCylinder(0.01f, 0.001f, new Vector3(0.0f, 0.0f, 0.0f), material); });


        MaterialFactory.makeTransparentWithColor(this, new Color(android.graphics.Color.GREEN))
                .thenAccept(
                        material -> {
                            anchorGreenRenderable =
                                    ShapeFactory.makeCylinder(0.01f, 0.001f, new Vector3(0.0f, 0.0f, 0.0f), material); });



        // listen for tap --> set AnchorNode and put Andy on top of it
        fragment.setOnTapArPlaneListener(
                (HitResult hitresult, Plane plane, MotionEvent motionevent) -> {
                    if (anchor != null) {
                        // either new anchor already created or old one already resolved
                        addNewObject();
                    }
                    else {
                        createNewCloudAnchor(hitresult);
                    }

                    //if (fragment.getTransformationSystem().getSelectedNode() instanceof myNode)
                    //    updateFirebase(((myNode)fragment.getTransformationSystem().getSelectedNode()).getIndex());
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
    OBJECT FUNCTIONS
     */





    private void createNewCloudAnchor(HitResult hitResult) {
        // set new AnchorNode into the scene
        hostingAnchor = hitResult.createAnchor();
        redAnchorNode = new AnchorNode(hostingAnchor);
        redAnchorNode.setParent(fragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(redAnchorNode);
        node.setRenderable(anchorRedRenderable);

        cloudAnchorManager.hostCloudAnchor(
                fragment.getArSceneView().getSession(), hostingAnchor, this::onHostedAnchorAvailable);
    }

    public void addNewObject() {
        if (shortCode == 0) {
            // not hosted yet --> wait
            return;
        }

        // add new object to this anchor
        int index = sceneData.addNew();
        myNode node = new myNode(fragment.getTransformationSystem(), this, index);
        myNodeMap.put(index, node);

        initObject(index);

        updateFirebase(index);
    }


    public void setSceneData(SceneData sceneData){
        this.sceneData = sceneData;

        updateObjects();
    }

    public void initObject(int index){
        // put Andy on the anchor
        myNode node = myNodeMap.get(index);
        NodeData nodeData = sceneData.getNodeData(index);

        float[] anchorPosition = anchor.getPose().getTranslation();

        //add new anchorNode right on top of original anchor --> can then be moved
        AnchorNode anchorNode = new AnchorNode();
        //anchorNode.setWorldPosition(new Vector3(anchorPosition[0], anchorPosition[1], anchorPosition[2]));
        anchorNode.setParent(mainAnchorNode);//fragment.getArSceneView().getScene());
        anchorNode.setLocalPosition(new Vector3(0,0,0));

        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();

        node.getScaleController().setMinScale(0.2f);
        node.getScaleController().setMaxScale(5f);

        /*
        node.setOnTouchListener((hitTestResult, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ) {
                updateFirebase(index);
            }
            return true;
        });

         */
    }


    public void updateObjects() {
        for (int index : myNodeMap.keySet()) {
            Log.e("INDEX", ((Integer)index).toString());
            NodeData nodeData = sceneData.getNodeData(index);
            myNode node = myNodeMap.get(index);

            node.getParent().setLocalPosition(nodeData.getPosition());
            //Node parent = node.getParent();
            //node.setParent(null);
            node.setLocalScale(nodeData.getScale());
            node.getScaleController().onActivated(node); //solves problem?
            //node.setParent(parent);
            Log.e("SCALE",nodeData.getScale().toString());
            node.setLocalRotation(nodeData.getRotation());
        }


/*
        greenAnchorNode = new AnchorNode(anchor);
        greenAnchorNode.setParent(fragment.getArSceneView().getScene());


        Node node = new Node();
        node.setParent(greenAnchorNode);
        node.setRenderable(anchorGreenRenderable);
 */

    }





    /*
    LISTENER FUNCTIONS
     */


    private void onClearButtonPressed() {
        clear();
    }

    // show resolve dialog, ask for CloudAnchor ID, then call onShortCodeEntered
    private void onResolveButtonPressed() {
        ResolveDialogFragment dialog = ResolveDialogFragment.createWithOkListener(
                this::onShortCodeEntered);;
        dialog.show(fragment.getFragmentManager(), "Resolve");
    }


    private synchronized void onHostedAnchorAvailable(Anchor anchor) {
        Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            String cloudAnchorId = anchor.getCloudAnchorId();
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






    public void clear(){
        setMessage("cleared");

        // clear pending listeners
        cloudAnchorManager.clearListeners();
        // THROWS CONCURRENT EXCEPTION?!?!

        shortCode = 0;

        if (myNodeMap != null){
            for( TransformableNode node : myNodeMap.values()){
                node.getParent().setParent(null); // remove all nodes from the scene
            }
        }

        // throw away the map
        myNodeMap = new HashMap<>();

        if (mainAnchorNode != null) mainAnchorNode.setParent(null);

        anchor = null;
    }

    public void setMessage(String message) {
        infoText.setText(message);
    }

    public void updateMessages(TransformableNode node) {
        setMessage("node loc pos: "+node.getLocalPosition().x+" "+node.getLocalPosition().y+" "+node.getLocalPosition().z);
        setMessage("anchor node pos: "+node.getParent().getLocalPosition().x+" "+node.getParent().getLocalPosition().y+" "+node.getParent().getLocalPosition().z);
    }

    public int getShortCode() {
        return shortCode;
    }

    public void setShortCode(int shortCode) {
        this.shortCode = shortCode;
    }

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

