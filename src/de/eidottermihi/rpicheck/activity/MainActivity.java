package de.eidottermihi.rpicheck.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sheetrock.panda.changelog.ChangeLog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.eidottermihi.rpicheck.R;
import de.eidottermihi.rpicheck.activity.helper.LoggingHelper;
import de.eidottermihi.rpicheck.db.DeviceDbHelper;

public class MainActivity extends SherlockFragmentActivity {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(MainActivity.class);

	protected static final String EXTRA_DEVICE_ID = "device_id";

	private SharedPreferences sharedPrefs;

	private DeviceDbHelper deviceDb;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// assigning Shared Preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// init device database
		deviceDb = new DeviceDbHelper(this);
		// init spinner

		boolean debugLogging = sharedPrefs.getBoolean(
				SettingsActivity.KEY_PREF_DEBUG_LOGGING, false);
		if (debugLogging) {
			LoggingHelper.changeLogger(true);
		}

		// changelog
		final ChangeLog cl = new ChangeLog(this);
		if (cl.firstRun())
			cl.getLogDialog().show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

}
