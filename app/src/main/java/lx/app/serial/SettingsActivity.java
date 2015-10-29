package lx.app.serial;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {
    private static final String SETTINGS_VERSION = "pref_version";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.about_title);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_about);

            Preference version = findPreference(SETTINGS_VERSION);
            if (version != null)
                version.setSummary(getVersion());
        }

        private String getVersion() {
            Activity activity = getActivity();
            if (activity != null) {
                try {
                    PackageInfo p = activity.getPackageManager()
                            .getPackageInfo(activity.getPackageName(), 0);
                    return p.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    // ignore
                }
            }
            return "1.0";
        }
    }
}
