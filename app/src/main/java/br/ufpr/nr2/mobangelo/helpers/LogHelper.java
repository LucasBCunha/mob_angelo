package br.ufpr.nr2.mobangelo.helpers;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import br.ufpr.nr2.mobangelo.MainActivity;

public abstract class LogHelper {

    public static void saveLogInSDCard(Context context){
        String filename = context.getExternalFilesDir(null) + File.separator + "mob_angelo.log";
        Log.i(MainActivity.TAG, filename);
        String[] cmd = new String[] { "logcat", "-f", filename,  "ActivityManager:W", "MOBANGELO:V", "mob_angelo:V", "METRICS:V","*:W", "\n" };
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
