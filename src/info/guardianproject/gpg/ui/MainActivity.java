package info.guardianproject.gpg.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.actionbarsherlock.app.SherlockFragmentActivity;

import info.guardianproject.gpg.GnuPG;
import info.guardianproject.gpg.GnuPrivacyGuard.ApgId;
import info.guardianproject.gpg.GpgAgentService;
import info.guardianproject.gpg.NativeHelper;
import info.guardianproject.gpg.R;
import info.guardianproject.gpg.SharedDaemonsService;
import info.guardianproject.gpg.apg_compat.Apg;

import java.io.File;

public class MainActivity extends SherlockFragmentActivity
                          implements TabListener,
                          OnPageChangeListener,
                          KeyListFragment.OnKeysSelectedListener {

    private final static String TAG = MainActivity.class.getSimpleName();

    private static final String TAB_POSITION="position";
    private ViewPager pager = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      NativeHelper.setup(getApplicationContext());

      // this also sets up GnuPG.context in onPostExecute()
      new InstallAndSetupTask(this).execute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGpgAgent();
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
      super.onRestoreInstanceState(state);

      pager.setCurrentItem(state.getInt(TAB_POSITION));
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
      super.onSaveInstanceState(state);

      state.putInt(TAB_POSITION, pager.getCurrentItem());
    }


    @Override
    public void onPageSelected(int position) {
        getSupportActionBar().setSelectedNavigationItem(position);
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        Integer position=(Integer) tab.getTag();
        pager.setCurrentItem(position);
    }


    public class MainPagerAdapter extends FragmentPagerAdapter {
        public MainPagerAdapter(FragmentManager mgr) {
          super(mgr);
        }

        @Override
        public int getCount() {
          return(2);
        }

        @Override
        public Fragment getItem(int position) {
            KeyListFragment frag = new KeyListFragment();
            Bundle args = new Bundle();
            Bundle extras = new Bundle();
            switch (position) {
                case 0: // public keys
                {
                    args.putString("action", Apg.Intent.SELECT_PUBLIC_KEYS);
                    extras.putString(ApgId.EXTRA_INTENT_VERSION, ApgId.VERSION);
                    break;
                }
                case 1: //private keys
                {
                    args.putString("action", Apg.Intent.SELECT_SECRET_KEY);
                    extras.putString(ApgId.EXTRA_INTENT_VERSION, ApgId.VERSION);
                    break;
                }
                default:
                    return null;
            }
            args.putBundle("extras", extras);
            frag.setArguments(args);
            return frag;
        }
      }


    @Override
    public void onKeySelectionCanceled() {
    }

    private void startGpgAgent() {
        File gpgAgentSocket = new File(NativeHelper.app_home, "S.gpg-agent");
        // gpg-agent is not running, start it
        if (!gpgAgentSocket.exists()) {
            Intent service = new Intent(this, GpgAgentService.class);
            startService(service);
        }
    }

    private void setupView() {
        pager = (ViewPager)findViewById(R.id.main_pager);
        FragmentManager mgr = getSupportFragmentManager();
        if( mgr == null ) Log.e("GNUPG", "getSupportFragmentManager returned null wtf!");
        pager.setAdapter(new MainPagerAdapter(mgr));
        pager.setOnPageChangeListener(this);

        ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.addTab(bar.newTab()
                        .setText("Public Keys")
                        .setTabListener(this).setTag(0));
        bar.addTab(bar.newTab()
                .setText("Private Keys")
                .setTabListener(this).setTag(1));
    }

    public class InstallAndSetupTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        private boolean doInstall;

        private final Context context = getApplicationContext();
        private final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                dialog.setMessage(msg.getData().getString("message"));
            }
        };

        private void showProgressMessage(int resId) {
            String messageText = getString(resId);
            if (messageText == null) messageText = "(null)";
            if (dialog == null) {
                Log.e(TAG, "installDialog is null!");
                return;
            }
            dialog.setMessage(messageText);
            if (!dialog.isShowing())
                dialog.show();
        }

        private void hideProgressDialog() {
            dialog.dismiss();
        }

        public InstallAndSetupTask(Context c) {
            dialog = new ProgressDialog(c);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setTitle(R.string.dialog_installing_title);
            dialog.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            doInstall = NativeHelper.installOrUpgradeAppOpt(context);
            if (doInstall)
                showProgressMessage(R.string.dialog_installing_msg);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (doInstall)
                NativeHelper.unpackAssets(context, handler);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            hideProgressDialog();

            // these need to be loaded before System.load("gnupg-for-java"); and in
            // the right order, since they have interdependencies.
            System.load(NativeHelper.app_opt + "/lib/libgpg-error.so.0");
            System.load(NativeHelper.app_opt + "/lib/libassuan.so.0");
            System.load(NativeHelper.app_opt + "/lib/libgpgme.so.11");

            Intent intent = new Intent(MainActivity.this, GpgAgentService.class);
            startService(intent);
            intent = new Intent(MainActivity.this, SharedDaemonsService.class);
            startService(intent);
            GnuPG.createContext();

            MainActivity.this.setupView();
        }
    }

/* IGNORED EVENTS */

    @Override
    public void onPageScrollStateChanged(int arg0) {
        // no op
    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {
        // no op
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        // no op
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
        // no op
    }

    @Override
    public void onKeySelected(long id, String userId) {
        // no op
    }


    @Override
    public void onKeysSelected(long[] selectedKeyIds, String[] selectedUserIds) {
        // no op
    }
}