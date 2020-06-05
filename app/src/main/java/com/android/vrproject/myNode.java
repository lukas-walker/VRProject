package com.android.vrproject;

import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

public class myNode extends TransformableNode {
    private MainActivity activity;
    private ScaleGestureDetector scaleDetector;
    int index;

    public myNode(TransformationSystem transformationSystem, MainActivity activity, int index) {
        super(transformationSystem);

        this.activity = activity;
        this.index = index;

        scaleDetector = new ScaleGestureDetector(activity, new ScaleListener(this));
    }

    @Override
    public boolean onTouchEvent(HitTestResult hitTestResult, MotionEvent motionEvent) {
        Log.e("ACTVATED","touchevent");
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) update();

        scaleDetector.onTouchEvent(motionEvent);

        return super.onTouchEvent(hitTestResult, motionEvent);
    }


    public void update(){
        activity.updateFirebase(index);
    }

    public int getIndex(){return index;}
}
