package com.android.launcher3.qsb;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.android.launcher3.qsb.QsbContainerView;

public class AssistantIconView extends ImageView {

    public AssistantIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.CENTER);
        setListener(context);
    }

    public AssistantIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setScaleType(ScaleType.CENTER);
        setListener(context);
    }

    public void setListener(Context context) {
        try {
            setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setAction("android.intent.action.VOICE_COMMAND");
                intent.setPackage(QsbContainerView.getSearchWidgetPackageName(context));
                context.startActivity(intent);
            });
        } catch (Exception e) {
            // Do nothing
        }
    }
}
