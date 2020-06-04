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
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
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
    private ModelRenderable anchorRedRenderable;
    private ModelRenderable anchorGreenRenderable;

    private AnchorNode redAnchorNode;
    private AnchorNode greenAnchorNode;
    private Anchor anchor;
    private Anchor hostingAnchor;
    private Map<Integer, TransformableNode> transformableNodeMap;
    private Scene arScene;
    private SceneData sceneData;
    private int shortCode = 0;

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
        arScene.addOnUpdateListener(frameTime -> {
            cloudAnchorManager.onUpdate();
            if (anchor != null) Log.e("ANCHOR POSITION", anchor.getPose().toString());
        });

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(modelRenderable -> this.modelRenderable = modelRenderable);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(
                        material -> {
                            anchorRedRenderable =
                                    ShapeFactory.makeSphere(0.1f, new Vector3(0.0f, 0.0f, 0.0f), material); });


        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.GREEN))
                .thenAccept(
                        material -> {
                            anchorGreenRenderable =
                                    ShapeFactory.makeSphere(0.1f, new Vector3(0.0f, 0.0f, 0.0f), material); });



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
                }
        );

        sceneData = new SceneData();
        sceneData.setNodeDataMap(new HashMap<>());

        transformableNodeMap = new HashMap<>();

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
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        transformableNodeMap.put(index, node);

        initObject(index);

        if (anchor != null) {
            firebaseManager.updateSceneData(getShortCode(), sceneData);
            //updateMessages(node);
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

        float[] anchorPosition = anchor.getPose().getTranslation();

        //add new anchorNode right on top of original anchor --> can then be moved
        AnchorNode anchorNode = new AnchorNode();
        //anchorNode.setWorldPosition(new Vector3(anchorPosition[0], anchorPosition[1], anchorPosition[2]));
        anchorNode.setParent(greenAnchorNode);//fragment.getArSceneView().getScene());
        anchorNode.setLocalPosition(new Vector3(0,0,0));

        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();

        node.getScaleController().setMinScale(0.2f);
        node.getScaleController().setMaxScale(5f);

        node.setOnTouchListener((hitTestResult, event) -> {
            nodeData.setPosition(node.getParent().getLocalPosition());
            //nodeData.setScale(node.getParent().getWorldScale());
            //nodeData.setRotation(node.getParent().getWorldRotation());
            sceneData.setNodeData(index, nodeData);

            if (event.getAction() == MotionEvent.ACTION_UP ) {
                if (anchor != null) {
                    firebaseManager.updateSceneData(getShortCode(), sceneData);
                    //updateMessages(node);
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

            node.getParent().setLocalPosition(nodeData.getPosition());
            //node.getParent().setWorldScale(nodeData.getScale());
            //node.getParent().setWorldRotation(nodeData.getRotation());
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
        greenAnchorNode = new AnchorNode(this.anchor);
        greenAnchorNode.setParent(fragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(greenAnchorNode);
        node.setRenderable(anchorGreenRenderable);

        Anchor.CloudAnchorState cloudState = this.anchor.getCloudAnchorState();
        if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
            setMessage("Resolved. Short code: " + shortCode);

            // sceneData has been set according to newly resolved anchor

            transformableNodeMap = sceneData.getTransformableNodeMap(fragment.getTransformationSystem());

            for (int index : transformableNodeMap.keySet()) {
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

        if (transformableNodeMap != null){
            for( TransformableNode node : transformableNodeMap.values()){
                node.getParent().setParent(null); // remove all nodes from the scene
            }
        }

        // throw away the map
        transformableNodeMap = new HashMap<>();

        if (greenAnchorNode != null) greenAnchorNode.setParent(null);

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
}

