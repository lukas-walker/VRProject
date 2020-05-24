package com.android.vrproject;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.math.Vector3;

public class AnchorData {

    private String cloudAnchorId;
    private Vector3 position;

    public AnchorData() {
        
    }

    public AnchorData(String cloudAnchorId, Vector3 position){
        this.cloudAnchorId = cloudAnchorId;
        this.position = position;
    }


    public String getCloudAnchorId() {
        return cloudAnchorId;
    }

    public void setCloudAnchorId(String cloudAnchorId) {
        this.cloudAnchorId = cloudAnchorId;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }
}
