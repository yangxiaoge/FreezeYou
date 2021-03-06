package cf.playhi.freezeyou;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;

import net.grandcentrix.tray.AppPreferences;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.AccessibilityUtils;
import cf.playhi.freezeyou.utils.ApplicationLabelUtils;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.ProcessUtils;

import static cf.playhi.freezeyou.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.VersionUtils.checkUpdate;
import static cf.playhi.freezeyou.utils.AccessibilityUtils.isAccessibilitySettingsOn;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public class AutoDiagnosisActivity extends FreezeYouBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.autodiagnosis);
        processActionBar(getActionBar());

        new Thread(new Runnable() {
            @Override
            public void run() {
                go();
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(new Runnable() {
            @Override
            public void run() {
                go();
            }
        }).start();
    }

    private void go() {
        ListView adg_listView = findViewById(R.id.adg_listView);
        ProgressBar adg_progressBar = findViewById(R.id.adg_progressBar);
        final List<Map<String, Object>> problemsList = new ArrayList<>();

        AppPreferences appPreferences = new AppPreferences(this);
        disableIndeterminate(adg_progressBar);
        setProgress(adg_progressBar, 5);

        checkSystemVersion(problemsList);
        setProgress(adg_progressBar, 10);

        checkLongTimeNoUpdate(problemsList);
        setProgress(adg_progressBar, 15);

        checkAccessibilityService(problemsList, appPreferences);
        setProgress(adg_progressBar, 20);

        checkNotificationListenerPermission(problemsList, appPreferences);
        setProgress(adg_progressBar, 25);

        checkNotifyPermission(problemsList);
        setProgress(adg_progressBar, 30);

        checkIsDeviceOwner(problemsList);
        setProgress(adg_progressBar, 35);

        checkRootPermission(problemsList);
        setProgress(adg_progressBar, 40);

        doRegenerateSomeCache(problemsList, adg_progressBar);
        setProgress(adg_progressBar, 90);

        checkIsPowerSaveMode(problemsList);
        setProgress(adg_progressBar, 95);

        checkIsIgnoringBatteryOptimizations(problemsList);
        setProgress(adg_progressBar, 97);

        checkIfNoProblemFound(problemsList);
        setProgress(adg_progressBar, 98);

        Collections.sort(problemsList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> t0, Map<String, Object> t1) {
                int i = ((Integer) t0.get("status")).compareTo((Integer) t1.get("status"));
                return i == 0 ? ((String) t0.get("id")).compareTo((String) t1.get("id")) : i;
            }
        });

        final SimpleAdapter adapter =
                new SimpleAdapter(
                        this,
                        problemsList,
                        R.layout.adg_list_item,
                        new String[]{"title", "sTitle", "status", "id"},
                        new int[]{R.id.adgli_title_textView, R.id.adgli_subTitle_textView, R.id.adgli_status_imageView});

        adg_listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String s = (String) problemsList.get(position).get("id");
                if (s == null)
                    s = "";
                switch (s) {
                    case "-30":
                        checkUpdate(AutoDiagnosisActivity.this);
                        break;
                    case "1":
                        AccessibilityUtils.openAccessibilitySettings(AutoDiagnosisActivity.this);
                        break;
                    case "2":
                        if (Build.VERSION.SDK_INT >= 21) {
                            try {
                                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                            } catch (Exception e) {
                                showToast(AutoDiagnosisActivity.this, R.string.failed);
                            }
                        }
                        break;
                    case "4":
                        if (Build.VERSION.SDK_INT >= 23) {
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            if (intent.resolveActivity(getPackageManager()) != null) {
                                startActivity(intent);
                            }
                        }
                        break;
                    case "6":
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                            intent.putExtra("app_package", "cf.play" + "hi.freezeyou");
                            intent.putExtra("app_uid", getApplicationInfo().uid);
                            intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                        } else {
                            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.parse("package:cf.playhi.freezeyou");
                            intent.setData(uri);
                        }
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                            showToast(getApplicationContext(), e.getLocalizedMessage());
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        setProgress(adg_progressBar, 100);

        done(adg_progressBar, adg_listView, adapter);
    }

    private void disableIndeterminate(final ProgressBar progressBar) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setIndeterminate(false);
            }
        });
    }

    private void done(final ProgressBar progressBar, final ListView listView, final SimpleAdapter adapter) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listView.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void setProgress(final ProgressBar progressBar, final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 24) {
                    progressBar.setProgress(progress, true);
                } else {
                    progressBar.setProgress(progress);
                }
            }
        });
    }

    private HashMap<String, Object> generateHashMap(String title, String sTitle, String id, int statusId) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("title", title);
        hashMap.put("sTitle", sTitle);
        hashMap.put("id", id);
        hashMap.put("status", statusId);
        return hashMap;
    }

    private void checkSystemVersion(List<Map<String, Object>> problemsList) {
        if (Build.VERSION.SDK_INT < 21) {
            problemsList.add(
                    generateHashMap(getString(R.string.sysVerLow), getString(R.string.someFuncUn), "-50", R.drawable.ic_attention)
            );
        } else {
            problemsList.add(
                    generateHashMap(getString(R.string.sysVerLow), getString(R.string.someFuncUn), "-1", R.drawable.ic_done)
            );
        }
    }

    private void checkAccessibilityService(List<Map<String, Object>> problemsList, AppPreferences appPreferences) {
        problemsList.add(
                (getDatabasePath("scheduledTriggerTasks").exists() || appPreferences.getBoolean("freezeOnceQuit", false) || appPreferences.getBoolean("avoidFreezeForegroundApplications", false)) && !isAccessibilitySettingsOn(this) ? generateHashMap(getString(R.string.ACBSNotEnabled), getString(R.string.affect) + " " + getString(R.string.avoidFreezeForegroundApplications) + " " + getString(R.string.scheduledTasks) + " " + getString(R.string.etc), "1", R.drawable.ic_attention) : generateHashMap(getString(R.string.ACBSNotEnabled), getString(R.string.affect) + " " + getString(R.string.avoidFreezeForegroundApplications) + " " + getString(R.string.scheduledTasks) + " " + getString(R.string.etc), "1", R.drawable.ic_done)
        );
    }

    private void checkNotificationListenerPermission(List<Map<String, Object>> problemsList, AppPreferences appPreferences) {
        if (Build.VERSION.SDK_INT >= 21 && appPreferences.getBoolean("avoidFreezeNotifyingApplications", false)) {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            problemsList.add(
                    s == null || !s.contains("cf.playhi.freezeyou/cf.playhi.freezeyou.MyNotificationListenerService")
                            ?
                            generateHashMap(getString(R.string.noNotificationListenerPermission), getString(R.string.affect) + " " + getString(R.string.avoidFreezeNotifyingApplications), "2", R.drawable.ic_attention)
                            :
                            generateHashMap(getString(R.string.noNotificationListenerPermission), getString(R.string.affect) + " " + getString(R.string.avoidFreezeNotifyingApplications), "2", R.drawable.ic_done)
            );
        }
    }

    private void checkNotifyPermission(List<Map<String, Object>> problemsList) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null || (Build.VERSION.SDK_INT >= 24 && !notificationManager.areNotificationsEnabled())) {
            problemsList.add(generateHashMap(getString(R.string.noNotifyPermission), getString(R.string.mayCannotNotify), "6", R.drawable.ic_attention));
        } else {
            problemsList.add(generateHashMap(getString(R.string.noNotifyPermission), getString(R.string.mayCannotNotify), "6", R.drawable.ic_done));
        }
    }

    private void checkIsDeviceOwner(List<Map<String, Object>> problemsList) {
        if (!DevicePolicyManagerUtils.isDeviceOwner(this)) {
            problemsList.add(generateHashMap(getString(R.string.noMRootPermission), getString(R.string.someFuncMayRestrict), "-3", R.drawable.ic_attention));
        } else {
            problemsList.add(generateHashMap(getString(R.string.noMRootPermission), getString(R.string.someFuncMayRestrict), "-3", R.drawable.ic_done));
        }
    }

    private void checkLongTimeNoUpdate(List<Map<String, Object>> problemsList) {
        SharedPreferences sharedPreferences = getSharedPreferences("Ver", MODE_PRIVATE);
        if ((new Date().getTime() - sharedPreferences.getLong("Time", 0)) > 1728000000)
            problemsList.add(generateHashMap(getString(R.string.notUpdatedForALongTime), getString(R.string.someNewFuncMayPub), "-30", R.drawable.ic_attention));
        else
            problemsList.add(generateHashMap(getString(R.string.notUpdatedForALongTime), getString(R.string.someNewFuncMayPub), "-30", R.drawable.ic_done));
    }

    private void checkRootPermission(List<Map<String, Object>> problemsList) {
        boolean hasPermission = true;
        int value = -1;
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            value = process.waitFor();
            ProcessUtils.destroyProcess(outputStream, process);
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("permission denied") || e.getMessage().toLowerCase().contains("not found")) {
                hasPermission = false;
            }
        }
        if (!hasPermission || value != 0) {
            problemsList.add(generateHashMap(getString(R.string.noRootPermission), getString(R.string.someFuncMayRestrict), "-3", R.drawable.ic_attention));
        } else {
            problemsList.add(generateHashMap(getString(R.string.noRootPermission), getString(R.string.someFuncMayRestrict), "-3", R.drawable.ic_done));
        }
    }

    private void checkIsIgnoringBatteryOptimizations(List<Map<String, Object>> problemsList) {
        if (Build.VERSION.SDK_INT >= 23 && !((PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations("cf.playhi.freezeyou")) {
            problemsList.add(generateHashMap(getString(R.string.noIgnoringBO), getString(R.string.someFuncMayBeAff), "4", R.drawable.ic_attention));
        } else {
            problemsList.add(generateHashMap(getString(R.string.noIgnoringBO), getString(R.string.someFuncMayBeAff), "4", R.drawable.ic_done));
        }
    }

    private void checkIsPowerSaveMode(List<Map<String, Object>> problemsList) {
        if (Build.VERSION.SDK_INT >= 21 && ((PowerManager) getSystemService(Context.POWER_SERVICE)).isPowerSaveMode())
            problemsList.add(generateHashMap(getString(R.string.inPowerSaveMode), getString(R.string.someFuncMayBeAff), "5", R.drawable.ic_attention));
        else
            problemsList.add(generateHashMap(getString(R.string.inPowerSaveMode), getString(R.string.someFuncMayBeAff), "5", R.drawable.ic_done));
    }

    private void doRegenerateSomeCache(List<Map<String, Object>> problemsList, ProgressBar progressBar) {
        getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE).edit().clear().apply();
        try {
//            File file = new File(getFilesDir() + "/icon");
//            if (file.exists() && file.isDirectory()) {
//                File[] childFile = file.listFiles();
//                if (childFile == null || childFile.length == 0) {
//                    file.delete();
//                } else {
//                    for (File f : childFile) {
//                        if (f.isFile()) {
//                            f.delete();
//                        }
//                    }
//                }
//            }

            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installedApplications = pm.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
            if (installedApplications != null) {
                int size = installedApplications.size();
                for (int i = 0; i < size; i++) {
                    ApplicationInfo applicationInfo = installedApplications.get(i);
                    ApplicationLabelUtils.getApplicationLabel(this, pm, applicationInfo, applicationInfo.packageName);
//                    ApplicationIconUtils.getApplicationIcon(this, applicationInfo.packageName, applicationInfo, false);
                    setProgress(progressBar, 40 + (int) ((double) i / (double) size * 50));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        problemsList.add(generateHashMap(getString(R.string.regenerateSomeCache), getString(R.string.updateSomeData), "10", R.drawable.ic_done));

    }

    private void checkIfNoProblemFound(List<Map<String, Object>> problemsList) {
        if (problemsList.isEmpty()) {
            problemsList.add(generateHashMap(getString(R.string.noProblemsFound), getString(R.string.everySeemsAllRight), "-99", R.drawable.ic_done));
        }
    }

}
