package com.android.vrproject;

import android.util.Log;
import android.view.ScaleGestureDetector;

class ScaleListener
        extends ScaleGestureDetector.SimpleOnScaleGestureListener {

    private myNode node;

    public ScaleListener(myNode node){
        this.node = node;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
        node.update();
        Log.e("SCALE","scaling ended");
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        Log.e("SCALE","scaling");
        return true;
    }
}