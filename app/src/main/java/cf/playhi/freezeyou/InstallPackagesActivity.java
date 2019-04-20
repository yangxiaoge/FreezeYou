package cf.playhi.freezeyou;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;

import net.grandcentrix.tray.AppPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import static cf.playhi.freezeyou.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.ToastUtils.showToast;

/**
 * Install and uninstall
 */
public class InstallPackagesActivity extends Activity {
    private static final String ILLEGALPKGNAME = "Fy^&IllegalPN*@!128`+=：:,.[";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this, true);
        super.onCreate(savedInstanceState);

        init();
//        try {
//            getDevicePolicyManager(this).clearPackagePersistentPreferredActivities(
//                    DeviceAdminReceiver.getComponentName(this), getPackageName()
//            );
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
    }

    private void clearTempFile(String filePath) {
        InstallPackagesUtils.deleteTempFile(this, filePath, false);
    }

    private void init() {

        final Intent intent = getIntent();
        final Uri packageUri = intent.getData();

        if (packageUri == null) {
            showToast(this, String.format(getString(R.string.invalidUriToast), "null"));
            finish();
            return;
        }

        String scheme = packageUri.getScheme();

        if ((!ContentResolver.SCHEME_FILE.equals(scheme)
                && !ContentResolver.SCHEME_CONTENT.equals(scheme)) && !"package".equals(scheme)) {
            showToast(this, String.format(getString(R.string.invalidUriToast), packageUri));
            finish();
            return;
        }

        final boolean install =
                !(Intent.ACTION_DELETE.equals(intent.getAction()) ||
                        Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction()));

        final String apkFilePath;

        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            // Check Storage Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    AlertDialogUtils
                            .buildAlertDialog(
                                    this,
                                    android.R.drawable.ic_dialog_alert,
                                    R.string.needStoragePermission,
                                    R.string.notice)
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    finish();
                                }
                            })
                            .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermissions(
                                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                            301
                                    );
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .create().show();
                } else {
                    apkFilePath = packageUri.getPath();
                    checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
                }
            } else {
                apkFilePath = packageUri.getEncodedPath();
                checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
            }
        } else {
            String apkFileName = "package" + new Date().getTime() + "F.apk";
            apkFilePath = getExternalCacheDir() + File.separator + "ZDF-" + apkFileName;

            checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
        }

    }

    private void checkAutoAndPrepareInstallDialog(boolean install, Uri packageUri, String apkFilePath) {

        final String fromPkgLabel;
        final String fromPkgName;
        if (Build.VERSION.SDK_INT >= 22) {
            Uri referrerUri = getReferrer();
            if (referrerUri == null || !"android-app".equals(referrerUri.getScheme())) {
                fromPkgLabel = ILLEGALPKGNAME;
                fromPkgName = ILLEGALPKGNAME;
            } else {
                fromPkgName = referrerUri.getEncodedSchemeSpecificPart().substring(2);
                String refererPackageLabel =
                        getApplicationLabel(
                                InstallPackagesActivity.this,
                                null, null,
                                fromPkgName
                        );
                if (refererPackageLabel.equals(getString(R.string.uninstalled))) {
                    fromPkgLabel = ILLEGALPKGNAME;
                } else {
                    fromPkgLabel = refererPackageLabel;
                }
            }
        } else {
            fromPkgLabel = ILLEGALPKGNAME;
            fromPkgName = ILLEGALPKGNAME;
        }

        if (install) {
            //Check AutoAllow
            AppPreferences sp = new AppPreferences(this);
            String originData = sp.getString("installPkgs_autoAllowPkgs_allows", "");
            if (originData != null
                    && !ILLEGALPKGNAME.equals(fromPkgLabel)
                    && MoreUtils.convertToList(originData, ",").contains(
                    Base64.encodeToString(fromPkgName.getBytes(), Base64.DEFAULT))) {
                //Allow
                ServiceUtils.startService(
                        InstallPackagesActivity.this,
                        new Intent(InstallPackagesActivity.this, InstallPackagesService.class)
                                .putExtra("install", true)
                                .putExtra("packageUri", packageUri)
                                .putExtra("apkFilePath", apkFilePath));
                finish();
                return;
            }
        }

        prepareInstallDialog(install, packageUri, apkFilePath, fromPkgLabel, fromPkgName);
    }

    private void prepareInstallDialog(final boolean install, final Uri packageUri, final String apkFilePath, final String fromPkgLabel, final String fromPkgName) {
        final StringBuilder alertDialogMessage = new StringBuilder();
        final ProgressDialog progressDialog =
                ProgressDialog.show(this, getString(R.string.plsWait), getString(R.string.loading___));
        final String nl = System.getProperty("line.separator");
        if (install) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (apkFilePath.startsWith(getExternalCacheDir() + File.separator + "ZDF-")) {
                            InputStream in = getContentResolver().openInputStream(packageUri);
                            if (in == null) {
                                return;
                            }
                            OutputStream out = new FileOutputStream(apkFilePath);
                            byte[] buffer = new byte[1024 * 1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) >= 0) {
                                out.write(buffer, 0, bytesRead);
                            }
                            out.close();
                            in.close();
                        }
                        PackageManager pm = getPackageManager();
                        PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFilePath, 0);
                        packageInfo.applicationInfo.sourceDir = apkFilePath;
                        packageInfo.applicationInfo.publicSourceDir = apkFilePath;
                        alertDialogMessage.append(getString(R.string.requestFromPackage_colon));
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(
                                ILLEGALPKGNAME.equals(fromPkgLabel) ?
                                        getString(R.string.unknown) : fromPkgLabel);
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(getString(R.string.installPackage_colon));
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(getString(R.string.application_colon));
                        alertDialogMessage.append(pm.getApplicationLabel(packageInfo.applicationInfo));
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(getString(R.string.pkgName_colon));
                        alertDialogMessage.append(packageInfo.packageName);
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(getString(R.string.version_colon));
                        alertDialogMessage.append(packageInfo.versionName);
                        try {
                            String installedVerName = getPackageManager().getPackageInfo(
                                    packageInfo.packageName,
                                    PackageManager.GET_UNINSTALLED_PACKAGES
                            ).versionName;
                            alertDialogMessage.append(nl);
                            alertDialogMessage.append(String.format(getString(R.string.existed_colon), installedVerName));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(getString(R.string.whetherAllow));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showInstallDialog(
                                        progressDialog, 1,
                                        alertDialogMessage, apkFilePath,
                                        packageUri, fromPkgLabel, fromPkgName
                                );
                            }
                        });
                    } catch (Exception e) {
                        alertDialogMessage.append(getString(R.string.cannotInstall_colon)).append(e.getLocalizedMessage());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showInstallDialog(
                                        progressDialog, 2,
                                        alertDialogMessage, apkFilePath,
                                        packageUri, fromPkgLabel, fromPkgName
                                );
                            }
                        });
                    }
                }
            }).start();
        } else {
            String packageName = packageUri.getEncodedSchemeSpecificPart();
            if (packageName == null) {
                showToast(this, String.format(getString(R.string.invalidUriToast), packageUri));
                finish();
                return;
            }
            alertDialogMessage.append(getString(R.string.requestFromPackage_colon));
            alertDialogMessage.append(nl);
            alertDialogMessage.append(
                    ILLEGALPKGNAME.equals(fromPkgLabel) ?
                            getString(R.string.unknown) : fromPkgLabel);
            alertDialogMessage.append(nl);
            alertDialogMessage.append(nl);
            alertDialogMessage.append(getString(R.string.uninstallPackage_colon));
            alertDialogMessage.append(nl);
            alertDialogMessage.append(getString(R.string.application_colon));
            alertDialogMessage.append(getApplicationLabel(this, null, null, packageName));
            alertDialogMessage.append(nl);
            alertDialogMessage.append(getString(R.string.pkgName_colon));
            alertDialogMessage.append(packageName);
            alertDialogMessage.append(nl);
            alertDialogMessage.append(getString(R.string.whetherAllow));
            showInstallDialog(
                    progressDialog, 0,
                    alertDialogMessage, apkFilePath,
                    packageUri, fromPkgLabel, fromPkgName
            );
        }
    }

    //install: 0-uninstall, 1-install, 2-failed.
    private void showInstallDialog(final ProgressDialog progressDialog, final int install, final CharSequence alertDialogMessage, final String apkFilePath, final Uri packageUri, final String fromPkgLabel, final String fromPkgName) {
        final ObsdAlertDialog installPackagesAlertDialog = new ObsdAlertDialog(this);
        if (install == 1) {
            //Init CheckBox
            View checkBoxView = View.inflate(this, R.layout.ipa_dialog_checkbox, null);
            CheckBox checkBox = checkBoxView.findViewById(R.id.ipa_dialog_checkBox);
            if (fromPkgLabel.equals(ILLEGALPKGNAME)) {
                checkBox.setVisibility(View.GONE);
            } else {
                checkBox.setText(String.format(getString(R.string.alwaysAllow_name), fromPkgLabel));
            }
            installPackagesAlertDialog.setView(checkBoxView);
        }

        switch (install) {
            case 0:
                installPackagesAlertDialog.setTitle(R.string.uninstall);
                break;
            case 1:
                installPackagesAlertDialog.setTitle(R.string.install);
                break;
            case 2:
                installPackagesAlertDialog.setTitle(R.string.failed);
                break;
            default:
                break;
        }

        installPackagesAlertDialog.setMessage(alertDialogMessage);
        installPackagesAlertDialog.setButton(
                DialogInterface.BUTTON_POSITIVE,
                getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (installPackagesAlertDialog.isObsd()) {
                            AlertDialogUtils.buildAlertDialog(
                                    InstallPackagesActivity.this,
                                    android.R.drawable.ic_dialog_alert,
                                    R.string.alert_isObsd,
                                    R.string.dangerous)
                                    .setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            showInstallDialog(progressDialog, install, alertDialogMessage, apkFilePath, packageUri, fromPkgLabel, fromPkgName);
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (install != 0) clearTempFile(apkFilePath);
                                            finish();
                                        }
                                    })
                                    .create().show();
                        } else {
                            if (install == 1) {
                                CheckBox checkBox = ((ObsdAlertDialog) dialog).findViewById(R.id.ipa_dialog_checkBox);
                                if (checkBox != null && checkBox.isChecked()) {
                                    AppPreferences sp = new AppPreferences(InstallPackagesActivity.this);
                                    String originData = sp.getString("installPkgs_autoAllowPkgs_allows", "");
                                    List<String> originData_list = MoreUtils.convertToList(originData, ",");
                                    if (!ILLEGALPKGNAME.equals(fromPkgLabel)
                                            &&
                                            (originData == null ||
                                                    !MoreUtils.convertToList(originData, ",").contains(
                                                            Base64.encodeToString(
                                                                    fromPkgName.getBytes(), Base64.DEFAULT)))) {
                                        originData_list.add(
                                                Base64.encodeToString(fromPkgName.getBytes(), Base64.DEFAULT));
                                        sp.put(
                                                "installPkgs_autoAllowPkgs_allows",
                                                MoreUtils.listToString(originData_list, ",")
                                        );
                                    }
                                }
                            }
                            if (install == 2) {
                                clearTempFile(apkFilePath);
                            } else {
                                ServiceUtils.startService(
                                        InstallPackagesActivity.this,
                                        new Intent(InstallPackagesActivity.this, InstallPackagesService.class)
                                                .putExtra("install", install == 1)
                                                .putExtra("packageUri", packageUri)
                                                .putExtra("apkFilePath", apkFilePath));
                            }
                            finish();
                        }
                    }
                });
        installPackagesAlertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (install != 0) clearTempFile(apkFilePath);
                finish();
            }
        });
        installPackagesAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (install != 0) clearTempFile(apkFilePath);
                finish();
            }
        });

        if (progressDialog.isShowing()) {
            progressDialog.cancel();
        }

        installPackagesAlertDialog.show();
        Window w = installPackagesAlertDialog.getWindow();
        if (w != null) {
            View v = (View) w.findViewById(android.R.id.custom).getParent();
            if (v != null) {
                v.setMinimumHeight(0);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 301) {
            if (resultCode == RESULT_OK) {
                init();
            } else {
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}