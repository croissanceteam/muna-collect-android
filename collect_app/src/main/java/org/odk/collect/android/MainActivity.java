package org.odk.collect.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.odk.collect.android.activities.FileManagerTabs;
import org.odk.collect.android.preferences.AdminPreferencesActivity;

import org.odk.collect.android.activities.AboutActivity;
import org.odk.collect.android.activities.FormChooserList;
import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.activities.GoogleDriveActivity;
import org.odk.collect.android.activities.InstanceChooserList;
import org.odk.collect.android.activities.InstanceUploaderList;
import org.odk.collect.android.activities.MainMenuActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.AdminSharedPreferences;
import org.odk.collect.android.preferences.AutoSendPreferenceMigrator;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.PlayServicesUtil;
import org.odk.collect.android.utilities.ToastUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.util.Map;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final MainActivity.IncomingHandler handler = new MainActivity.IncomingHandler(MainActivity.this);
    private SharedPreferences adminPreferences;
    private static final int PASSWORD_DIALOG = 1;
    private int completedCount;
    private int savedCount;
    private int viewSentCount;
    private Cursor finalizedCursor;
    private Cursor savedCursor;
    private Cursor viewSentCursor;
    private Button manageFilesButton;
    private Button sendDataButton;
    private Button viewSentFormsButton;
    private Button reviewDataButton;
    private Button getFormsButton;
    private View reviewSpacer;
    private View getFormsSpacer;
    private AlertDialog alertDialog;
    private ViewFlipper viewFlipper;
    private static final boolean EXIT = true;
    // buttons
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PASSWORD_DIALOG:

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final AlertDialog passwordDialog = builder.create();
                passwordDialog.setTitle(getString(R.string.enter_admin_password));
                LayoutInflater inflater = this.getLayoutInflater();
                View dialogView = inflater.inflate(R.layout.dialogbox_layout, null);
                passwordDialog.setView(dialogView, 20, 10, 20, 10);
                final CheckBox checkBox = dialogView.findViewById(R.id.checkBox);
                final EditText input = dialogView.findViewById(R.id.editText);
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if (!checkBox.isChecked()) {
                            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        } else {
                            input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        }
                    }
                });
                passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                String value = input.getText().toString();
                                String pw = adminPreferences.getString(
                                        AdminKeys.KEY_ADMIN_PW, "");
                                if (pw.compareTo(value) == 0) {
                                    Intent i = new Intent(getApplicationContext(),
                                            AdminPreferencesActivity.class);
                                    startActivity(i);
                                    input.setText("");
                                    passwordDialog.dismiss();
                                } else {
                                    ToastUtils.showShortToast(R.string.admin_password_incorrect);
                                }
                            }
                        });

                passwordDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                input.setText("");
                            }
                        });

                passwordDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return passwordDialog;

        }
        return null;
    }
    private void updateButtons() {
        if (finalizedCursor != null && !finalizedCursor.isClosed()) {
            finalizedCursor.requery();
            completedCount = finalizedCursor.getCount();
            if (completedCount > 0) {
                sendDataButton.setText(
                        getString(R.string.send_data_button, String.valueOf(completedCount)));
            } else {
                sendDataButton.setText(getString(R.string.send_data));
            }
        } else {
            sendDataButton.setText(getString(R.string.send_data));
            Timber.w("Cannot update \"Send Finalized\" button label since the database is closed. "
                    + "Perhaps the app is running in the background?");
        }

        if (savedCursor != null && !savedCursor.isClosed()) {
            savedCursor.requery();
            savedCount = savedCursor.getCount();
            if (savedCount > 0) {
                reviewDataButton.setText(getString(R.string.review_data_button,
                        String.valueOf(savedCount)));
            } else {
                reviewDataButton.setText(getString(R.string.review_data));
            }
        } else {
            reviewDataButton.setText(getString(R.string.review_data));
            Timber.w("Cannot update \"Edit Form\" button label since the database is closed. "
                    + "Perhaps the app is running in the background?");
        }

        if (viewSentCursor != null && !viewSentCursor.isClosed()) {
            viewSentCursor.requery();
            viewSentCount = viewSentCursor.getCount();
            if (viewSentCount > 0) {
                viewSentFormsButton.setText(
                        getString(R.string.view_sent_forms_button, String.valueOf(viewSentCount)));
            } else {
                viewSentFormsButton.setText(getString(R.string.view_sent_forms));
            }
        } else {
            viewSentFormsButton.setText(getString(R.string.view_sent_forms));
            Timber.w("Cannot update \"View Sent\" button label since the database is closed. "
                    + "Perhaps the app is running in the background?");
        }
    }

    private boolean loadSharedPreferencesFromFile(File src) {
        // this should probably be in a thread if it ever gets big
        boolean res = false;
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            GeneralSharedPreferences.getInstance().clear();

            // first object is preferences
            Map<String, ?> entries = (Map<String, ?>) input.readObject();

            AutoSendPreferenceMigrator.migrate(entries);

            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                GeneralSharedPreferences.getInstance().save(entry.getKey(), entry.getValue());
            }

            AdminSharedPreferences.getInstance().clear();

            // second object is admin options
            Map<String, ?> adminEntries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : adminEntries.entrySet()) {
                AdminSharedPreferences.getInstance().save(entry.getKey(), entry.getValue());
            }
            Collect.getInstance().initProperties();
            res = true;
        } catch (IOException | ClassNotFoundException e) {
            Timber.e(e, "Exception while loading preferences from file due to : %s ", e.getMessage());
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                Timber.e(ex, "Exception thrown while closing an input stream due to: %s ", ex.getMessage());
            }
        }
        return res;
    }

    /*
     * Used to prevent memory leaks
     */
    static class IncomingHandler extends Handler {
        private final WeakReference<MainActivity> target;

        IncomingHandler(MainActivity target) {
            this.target = new WeakReference<MainActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity target = this.target.get();
            if (target != null) {
                target.updateButtons();
            }
        }
    }

    /**
     * notifies us that something changed
     */
    private class MyContentObserver extends ContentObserver {

        MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            handler.sendEmptyMessage(0);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "MUNA Collect Version 1.2", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {
            case R.id.menu_about2:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.menu_general_preferences:
                startActivity(new Intent(this, PreferencesActivity.class));
                return true;
            case R.id.menu_admin_preferences2:

                try{
                    adminPreferences = this.getSharedPreferences(
                            AdminPreferencesActivity.ADMIN_PREFERENCES, 0);
                    String pw = adminPreferences.getString(AdminKeys.KEY_ADMIN_PW, "");
                    if ("".equalsIgnoreCase(pw)) {
                        startActivity(new Intent(this, AdminPreferencesActivity.class));
                    } else {
                        showDialog(PASSWORD_DIALOG);
                    }

                }catch(Exception e){
                    Toast.makeText(this, "Exception :"+e.getMessage(), Toast.LENGTH_SHORT).show();
                }


                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            if (Collect.allowClick(getClass().getName())) {
                Intent i = new Intent(getApplicationContext(),
                        FormChooserList.class);
                startActivity(i);
            }
        } else if (id == R.id.nav_gallery) {
            if (Collect.allowClick(getClass().getName())) {
                Intent i = new Intent(getApplicationContext(), InstanceChooserList.class);
                i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE,
                        ApplicationConstants.FormModes.EDIT_SAVED);
                startActivity(i);
            }
        } else if (id == R.id.nav_slideshow) {
            if (Collect.allowClick(getClass().getName())) {
                Intent i = new Intent(getApplicationContext(),
                        InstanceUploaderList.class);
                startActivity(i);
            }
        } else if (id == R.id.nav_manage) {
            if (Collect.allowClick(getClass().getName())) {
                Intent i = new Intent(getApplicationContext(), InstanceChooserList.class);
                i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE,
                        ApplicationConstants.FormModes.VIEW_SENT);
                startActivity(i);
            }
        } else if (id == R.id.nav_share) {
            if (Collect.allowClick(getClass().getName())) {
                SharedPreferences sharedPreferences = PreferenceManager
                        .getDefaultSharedPreferences(MainActivity.this);
                String protocol = sharedPreferences.getString(
                        GeneralKeys.KEY_PROTOCOL, getString(R.string.protocol_odk_default));
                Intent i = null;
                if (protocol.equalsIgnoreCase(getString(R.string.protocol_google_sheets))) {
                    if (PlayServicesUtil.isGooglePlayServicesAvailable(MainActivity.this)) {
                        i = new Intent(getApplicationContext(),
                                GoogleDriveActivity.class);
                    } else {
                        PlayServicesUtil.showGooglePlayServicesAvailabilityErrorDialog(MainActivity.this);

                    }
                } else {
                    i = new Intent(getApplicationContext(),
                            FormDownloadList.class);
                }
                startActivity(i);
            }
        } else if (id == R.id.nav_send) {
            if (Collect.allowClick(getClass().getName())) {
                Intent i = new Intent(getApplicationContext(),
                        FileManagerTabs.class);
                startActivity(i);

            }

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
