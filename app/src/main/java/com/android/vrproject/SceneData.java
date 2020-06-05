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

    public String cloudAnchorId;
    public int index = 0;
    public Map<String, NodeData> nodeDataMap;

    public SceneData(){
        if (nodeDataMap==null) nodeDataMap = new HashMap<>();
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


    public Map<Integer, myNode> getTransformableNodeMap(TransformationSystem transformationSystem, MainActivity activity){
        Map<Integer, myNode> myNodeMap = new HashMap<>();

        if (nodeDataMap == null) {
            nodeDataMap = new HashMap<>();
            return myNodeMap;
        }

        for (String index : nodeDataMap.keySet()) {
            int i = Integer.parseInt(index.replace(INDEX_PREFIX, ""));

            myNode node = new myNode(transformationSystem, activity, i);

            myNodeMap.put(i, node);
        }
        return myNodeMap;
    }

    public int getIndex(){
        return index;
    }

    public void setIndex(int index){this.index = index;}


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
