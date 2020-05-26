package com.android.vrproject;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

public class NodeData {

    private Vector3 position;
    private Vector3 scale;
    private Quaternion rotation;

    public NodeData() {

    }

    public NodeData(Vector3 position, Vector3 scale, Quaternion rotation){
        this.position = position;
        this.scale = scale;
        this.rotation = rotation;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public Vector3 getScale() {
        return scale;
    }

    public void setScale(Vector3 scale) {
        this.scale = scale;
    }

    public Quaternion getRotation() {
        return rotation;
    }

    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
    }
}
