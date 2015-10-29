package lx.app.serial;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import java.util.List;

public class ConsoleSettingsActivity extends PreferenceActivity {
    static final Class<?>[] INNER_CLASSES = ConsoleSettingsActivity.class.getDeclaredClasses();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.setting_title);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_console_header, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        for (Class<?> cls : INNER_CLASSES) {
            if (cls.getName().equals(fragmentName))
                return true;
        }
        return false;
    }

    abstract static class SummaryPreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(findPreference(key));
        }

        @Override
        public void onStart() {
            super.onStart();
            updateSummaries();
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        protected void updateSummaries() {
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
                updateSummary(getPreferenceScreen().getPreference(i));
            }
        }

        protected void updateSummary(Preference pref) {
            if (pref instanceof ListPreference) {
                ListPreference lp = (ListPreference) pref;
                lp.setSummary(lp.getEntry());
            }
        }
    }

    public static class SerialPreferenceFragment extends SummaryPreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_serial);
        }
    }

    public static class DisplayPreferenceFragment extends SummaryPreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_display);
        }
    }
}
