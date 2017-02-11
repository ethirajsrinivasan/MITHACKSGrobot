package com.segway.robot.lococonn;

import android.text.TextUtils;
import android.widget.EditText;

/**
 * Created by sunguangshan on 2016/7/26.
 */

public class Util {
    private static final String TAG = "Util";

    public static boolean isEditTextEmpty(EditText editText) {
        if (editText == null) {
            throw new NullPointerException("editText is null");
        }
        String text = editText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return true;
        } else {
            return false;
        }
    }

    public static String floatToString(float f) {
        return String.valueOf(f);
    }

    public static float getEditTextFloatValue(EditText editText) {
        String text = editText.getText().toString().trim();
        return Float.parseFloat(text);
    }

}
