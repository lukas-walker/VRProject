package com.android.vrproject;

import android.util.Log;
import android.view.MotionEvent;

import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SceneData {

    private static final String INDEX_PREFIX= "ID_";

    private String cloudAnchorId;
    private int index = 0;
    private Map<String, NodeData> nodeDataMap;

    public SceneData(){
    }

    public int add(NodeData nodeData){
        nodeDataMap.put(INDEX_PREFIX + ((Integer)index).toString(), nodeData);
        return index++;
    }

    public int addNew(){

        NodeData nodeData = new NodeData();
        nodeData.setPosition(new Vector3(0,0,0));
        nodeData.setScale(new Vector3(1, 1, 1));
        nodeData.setRotation(new Quaternion(0, 0, 0, 0));

        nodeDataMap.put(INDEX_PREFIX + ((Integer)index).toString(), nodeData);
        return index++;
    }


    public Map<Integer, TransformableNode> getTransformableNodeMap(TransformationSystem transformationSystem, AnchorNode anchorNode, ModelRenderable modelRenderable){
        Map<Integer, TransformableNode> transformableNodeMap = new HashMap<>();

        for (String index : nodeDataMap.keySet()) {
            NodeData nodeData = nodeDataMap.get(index);
            int i = Integer.parseInt(index.replace(INDEX_PREFIX, ""));

            TransformableNode node = new TransformableNode(transformationSystem);

            transformableNodeMap.put(i, node);
        }
        return transformableNodeMap;
    }


    public NodeData getNodeData(int index) {
        return nodeDataMap.get(INDEX_PREFIX +  ((Integer)index).toString());
    }

    public void setNodeData(int index, NodeData nodeData) {
        nodeDataMap.replace(INDEX_PREFIX +  ((Integer)index).toString(), nodeData);
    }

    public Map<String, NodeData> getNodeDataMap() {
        return nodeDataMap;
    }

    public void setNodeDataMap(Map<String, NodeData> nodeDataMap) {
        this.nodeDataMap = nodeDataMap;
    }

    public String getCloudAnchorId() {
        return cloudAnchorId;
    }

    public void setCloudAnchorId(String cloudAnchorId) {
        this.cloudAnchorId = cloudAnchorId;
    }
}
