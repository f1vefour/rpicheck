package de.eidottermihi.rpicheck.activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sheetrock.panda.changelog.ChangeLog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshScrollView;

import de.eidottermihi.raspitools.RaspiQuery;
import de.eidottermihi.raspitools.RaspiQueryException;
import de.eidottermihi.raspitools.beans.DiskUsageBean;
import de.eidottermihi.raspitools.beans.NetworkInterfaceInformation;
import de.eidottermihi.raspitools.beans.ProcessBean;
import de.eidottermihi.raspitools.beans.RaspiMemoryBean;
import de.eidottermihi.raspitools.beans.UptimeBean;
import de.eidottermihi.raspitools.beans.VcgencmdBean;
import de.eidottermihi.rpicheck.R;
import de.eidottermihi.rpicheck.activity.helper.Helper;
import de.eidottermihi.rpicheck.activity.helper.LoggingHelper;
import de.eidottermihi.rpicheck.bean.QueryBean;
import de.eidottermihi.rpicheck.bean.ShutdownResult;
import de.eidottermihi.rpicheck.db.DeviceDbHelper;
import de.eidottermihi.rpicheck.db.RaspberryDeviceBean;
import de.eidottermihi.rpicheck.fragment.dialog.QueryErrorMessagesDialog;
import de.eidottermihi.rpicheck.fragment.dialog.QueryExceptionDialog;
import de.eidottermihi.rpicheck.fragment.dialog.RebootDialogFragment;
import de.eidottermihi.rpicheck.fragment.dialog.RebootDialogFragment.ShutdownDialogListener;

public class MainActivity extends SherlockFragmentActivity implements
		ActionBar.OnNavigationListener, OnRefreshListener<ScrollView>,
		ShutdownDialogListener {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(MainActivity.class);

	protected static final String EXTRA_DEVICE_ID = "device_id";

	public static final String KEY_PREFERENCES_SHOWN = "key_prefs_preferences_shown";

	private static final String QUERY_DATA = "queryData";
	private static final String CURRENT_DEVICE = "currentDevice";

	private static final String TYPE_REBOOT = "reboot";
	private static final String TYPE_HALT = "halt";

	private final Helper helper = new Helper();

	private Intent settingsIntent;
	private Intent newRaspiIntent;
	private Intent editRaspiIntent;
	private RaspiQuery raspiQuery;

	private TextView coreTempText;
	private TextView armFreqText;
	private TextView coreFreqText;
	private TextView coreVoltText;
	private TextView lastUpdateText;
	private TextView uptimeText;
	private TextView averageLoadText;
	private TextView totalMemoryText;
	private TextView freeMemoryText;
	private TextView serialNoText;
	private TextView distriText;
	private TextView firmwareText;
	private TableLayout diskTable;
	private TableLayout processTable;
	private TableLayout networkTable;
	private ProgressBar progressBar;
	private PullToRefreshScrollView refreshableScrollView;

	private SharedPreferences sharedPrefs;

	private QueryBean queryData;

	private ShutdownResult shutdownResult;

	private DeviceDbHelper deviceDb;

	private RaspberryDeviceBean currentDevice;

	private Cursor deviceCursor;

	// Need handler for callbacks to the UI thread
	final Handler mHandler = new Handler();

	// Create runnable for posting
	final Runnable mUpdateResults = new Runnable() {
		public void run() {
			// gets called from AsyncTask when data was queried
			updateResultsInUi();
		}
	};

	// Runnable for reboot result
	final Runnable mRebootResult = new Runnable() {
		public void run() {
			// gets called from AsyncTask when reboot command was sent
			afterShutdown();
		}

	};

	private MenuItem refreshItem;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// assigning Shared Preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		// init intents
		settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
		newRaspiIntent = new Intent(MainActivity.this, NewRaspiActivity.class);
		editRaspiIntent = new Intent(MainActivity.this, EditRaspiActivity.class);

		// assigning progressbar
		progressBar = (ProgressBar) findViewById(R.id.progressBar1);

		// assigning textviews to fields
		armFreqText = (TextView) findViewById(R.id.armFreqText);
		coreFreqText = (TextView) findViewById(R.id.coreFreqText);
		coreVoltText = (TextView) findViewById(R.id.coreVoltText);
		coreTempText = (TextView) findViewById(R.id.coreTempText);
		firmwareText = (TextView) findViewById(R.id.firmwareText);
		lastUpdateText = (TextView) findViewById(R.id.lastUpdateText);
		uptimeText = (TextView) findViewById(R.id.startedText);
		averageLoadText = (TextView) findViewById(R.id.loadText);
		totalMemoryText = (TextView) findViewById(R.id.totalMemText);
		freeMemoryText = (TextView) findViewById(R.id.freeMemText);
		serialNoText = (TextView) findViewById(R.id.cpuSerialText);
		distriText = (TextView) findViewById(R.id.distriText);
		diskTable = (TableLayout) findViewById(R.id.diskTable);
		processTable = (TableLayout) findViewById(R.id.processTable);
		networkTable = (TableLayout) findViewById(R.id.networkTable);

		// assigning refreshable root scrollview
		refreshableScrollView = (PullToRefreshScrollView) findViewById(R.id.scrollView1);
		refreshableScrollView.setOnRefreshListener(this);

		// init device database
		deviceDb = new DeviceDbHelper(this);
		// init spinner
		initSpinner();

		boolean debugLogging = sharedPrefs.getBoolean(
				SettingsActivity.KEY_PREF_DEBUG_LOGGING, false);
		if (debugLogging) {
			LoggingHelper.changeLogger(true);
		}

		// restoring tables
		if (savedInstanceState != null) {
			if (savedInstanceState.getSerializable(QUERY_DATA) != null) {
				LOGGER.debug("Restoring dynamic tables.");
				queryData = (QueryBean) savedInstanceState
						.getSerializable(QUERY_DATA);
				if (queryData.getDisks() != null) {
					this.updateDiskTable(queryData.getDisks());
				}
				if (queryData.getProcesses() != null) {
					this.updateProcessTable(queryData.getProcesses());
				}
				if (queryData.getNetworkInfo() != null) {
					this.updateNetworkTable(queryData.getNetworkInfo());
				}
			}
			if (savedInstanceState.getSerializable(CURRENT_DEVICE) != null) {
				LOGGER.debug("Restoring current device.");
				currentDevice = (RaspberryDeviceBean) savedInstanceState
						.getSerializable(CURRENT_DEVICE);
				this.getSupportActionBar().setSelectedNavigationItem(
						currentDevice.getSpinnerPosition());
			}
		}

		// changelog
		final ChangeLog cl = new ChangeLog(this);
		if (cl.firstRun())
			cl.getLogDialog().show();
	}

	private void updateQueryDataInView() {
		final List<String> errorMessages = queryData.getErrorMessages();
		final String tempScale = sharedPrefs.getString(
				SettingsActivity.KEY_PREF_TEMPERATURE_SCALE,
				getString(R.string.pref_temperature_scala_default));
		coreTempText.setText(helper.formatTemperature(queryData
				.getVcgencmdInfo().getCpuTemperature(), tempScale));
		final String freqScale = sharedPrefs.getString(
				SettingsActivity.KEY_PREF_FREQUENCY_UNIT,
				getString(R.string.pref_frequency_unit_default));
		armFreqText.setText(helper.formatFrequency(queryData.getVcgencmdInfo()
				.getArmFrequency(), freqScale));
		coreFreqText.setText(helper.formatFrequency(queryData.getVcgencmdInfo()
				.getCoreFrequency(), freqScale));
		coreVoltText.setText(helper.formatDecimal(queryData.getVcgencmdInfo()
				.getCoreVolts()));
		firmwareText.setText(queryData.getVcgencmdInfo().getVersion());
		lastUpdateText.setText(SimpleDateFormat.getDateTimeInstance().format(
				queryData.getLastUpdate()));
		// uptime and average load may contain errors
		if (queryData.getAvgLoad() != null) {
			averageLoadText.setText(queryData.getAvgLoad().toString());
		}
		if (queryData.getStartup() != null) {
			uptimeText.setText(queryData.getStartup());
		}
		if (queryData.getFreeMem() != null) {
			freeMemoryText.setText(queryData.getFreeMem()
					.humanReadableByteCount(false));
		}
		if (queryData.getTotalMem() != null) {
			totalMemoryText.setText(queryData.getTotalMem()
					.humanReadableByteCount(false));
		}
		serialNoText.setText(queryData.getSerialNo());
		distriText.setText(queryData.getDistri());
		// update tables
		updateNetworkTable(queryData.getNetworkInfo());
		updateDiskTable(queryData.getDisks());
		updateProcessTable(queryData.getProcesses());
		this.handleQueryError(errorMessages);
	}

	/**
	 * Shows a dialog containing the ErrorMessages.
	 * 
	 * @param errorMessages
	 *            the messages
	 */
	private void handleQueryError(List<String> errorMessages) {
		final ArrayList<String> messages = new ArrayList<String>(errorMessages);
		if (errorMessages.size() > 0) {
			LOGGER.trace("Showing query error messages.");
			Bundle args = new Bundle();
			args.putStringArrayList(
					QueryErrorMessagesDialog.KEY_ERROR_MESSAGES, messages);
			final QueryErrorMessagesDialog messageDialog = new QueryErrorMessagesDialog();
			messageDialog.setArguments(args);
			messageDialog.show(getSupportFragmentManager(),
					"QueryErrorMessagesDialog");
		}
	}

	private void updateNetworkTable(
			List<NetworkInterfaceInformation> networkInfo) {
		// remove rows except header
		networkTable.removeViews(1, networkTable.getChildCount() - 1);
		for (NetworkInterfaceInformation interfaceInformation : networkInfo) {
			networkTable.addView(createNetworkRow(interfaceInformation));
		}
	}

	private View createNetworkRow(
			NetworkInterfaceInformation interfaceInformation) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(interfaceInformation.getName()));
		CharSequence statusText;
		if (interfaceInformation.isHasCarrier()) {
			statusText = getText(R.string.network_status_up);
		} else {
			statusText = getText(R.string.network_status_down);
		}
		tempRow.addView(createTextView(statusText.toString()));
		if (interfaceInformation.getIpAdress() != null) {
			tempRow.addView(createTextView(interfaceInformation.getIpAdress()));
		}
		if (interfaceInformation.getWlanInfo() != null) {
			tempRow.addView(createTextView(interfaceInformation.getWlanInfo()
					.getSignalLevel() + ""));
			tempRow.addView(createTextView(interfaceInformation.getWlanInfo()
					.getLinkQuality() + ""));
		}
		return tempRow;
	}

	private void updateProcessTable(List<ProcessBean> processes) {
		// remove current rows except header row
		processTable.removeViews(1, processTable.getChildCount() - 1);
		for (ProcessBean processBean : processes) {
			processTable.addView(createProcessRow(processBean));
		}
	}

	private View createProcessRow(ProcessBean processBean) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(processBean.getpId() + ""));
		tempRow.addView(createTextView(processBean.getTty()));
		tempRow.addView(createTextView(processBean.getCpuTime()));
		tempRow.addView(createTextView(processBean.getCommand()));
		return tempRow;
	}

	private View createDiskRow(DiskUsageBean disk) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(disk.getFileSystem()));
		tempRow.addView(createTextView(disk.getSize()));
		tempRow.addView(createTextView(disk.getAvailable()));
		tempRow.addView(createTextView(disk.getUsedPercent()));
		tempRow.addView(createTextView(disk.getMountedOn()));
		return tempRow;
	}

	private View createTextView(String text) {
		final TextView tempText = new TextView(this);
		tempText.setText(text);
		return tempText;
	}

	private void updateDiskTable(List<DiskUsageBean> disks) {
		// remove current rows except header row
		diskTable.removeViews(1, diskTable.getChildCount() - 1);
		for (DiskUsageBean diskUsageBean : disks) {
			// add row to table
			diskTable.addView(createDiskRow(diskUsageBean));
		}
	}

	private void initSpinner() {
		deviceCursor = deviceDb.getFullDeviceCursor();
		LOGGER.trace("Cursor rows: " + deviceCursor.getCount());
		// only show spinner if theres already a device to show
		if (deviceCursor.getCount() > 0) {
			// make adapter
			@SuppressWarnings("deprecation")
			SimpleCursorAdapter spinadapter = new SimpleCursorAdapter(this,
					R.layout.sherlock_spinner_dropdown_item, deviceCursor,
					new String[] { "name", "_id" },
					new int[] { android.R.id.text1 });
			spinadapter
					.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
			this.getSupportActionBar().setNavigationMode(
					ActionBar.NAVIGATION_MODE_LIST);
			this.getSupportActionBar().setListNavigationCallbacks(spinadapter,
					this);
			this.getSupportActionBar().setDisplayShowTitleEnabled(false);
		} else {
			// no device, start activity to create a new
			Toast.makeText(this, R.string.please_add_a_raspberry_pi,
					Toast.LENGTH_LONG).show();
			this.startActivity(newRaspiIntent);
		}
	}

	private void updateResultsInUi() {
		// hide and reset progress bar
		progressBar.setVisibility(View.GONE);
		progressBar.setProgress(0);
		// update and reset pullToRefresh
		refreshableScrollView.onRefreshComplete();
		refreshableScrollView.setMode(Mode.PULL_FROM_START);
		// update refresh indicator
		refreshItem.setActionView(null);
		if (queryData.getException() == null) {
			// update view data
			this.updateQueryDataInView();
		} else {
			this.handleQueryException(queryData.getException());
		}
	}

	/**
	 * Shows a dialog with a detailed error message.
	 * 
	 * @param exception
	 */
	private void handleQueryException(RaspiQueryException exception) {
		LOGGER.trace("Query caused exception. Showing dialog.");
		// build dialog
		final String errorMessage = this.mapExceptionToErrorMessage(exception);
		Bundle dialogArgs = new Bundle();
		dialogArgs.putString(QueryExceptionDialog.MESSAGE_KEY, errorMessage);
		QueryExceptionDialog dialogFragment = new QueryExceptionDialog();
		dialogFragment.setArguments(dialogArgs);
		dialogFragment
				.show(getSupportFragmentManager(), "QueryExceptionDialog");
	}

	private String mapExceptionToErrorMessage(RaspiQueryException exception) {
		String message = null;
		switch (exception.getReasonCode()) {
		case RaspiQueryException.REASON_CONNECTION_FAILED:
			message = getString(R.string.connection_failed);
			break;
		case RaspiQueryException.REASON_AUTHENTIFICATION_FAILED:
			message = getString(R.string.authentication_failed);
			break;
		case RaspiQueryException.REASON_TRANSPORT_EXCEPTION:
			message = getString(R.string.transport_exception);
			break;
		case RaspiQueryException.REASON_IO_EXCEPTION:
			message = getString(R.string.unexpected_exception);
			break;
		case RaspiQueryException.REASON_VCGENCMD_NOT_FOUND:
			message = getString(R.string.exception_vcgencmd);
			break;
		default:
			message = getString(R.string.weird_exception);
			break;
		}
		return message;
	}

	/**
	 * Just show Toast.
	 */
	private void afterShutdown() {
		if (shutdownResult.getType().equals(TYPE_REBOOT)) {
			if (shutdownResult.getExcpetion() == null) {
				Toast.makeText(this, R.string.reboot_success, Toast.LENGTH_LONG)
						.show();
			} else {
				Toast.makeText(this, R.string.reboot_fail, Toast.LENGTH_LONG)
						.show();
			}
		} else if (shutdownResult.getType().equals(TYPE_HALT)) {
			if (shutdownResult.getExcpetion() == null) {
				Toast.makeText(this, R.string.halt_success, Toast.LENGTH_LONG)
						.show();
			} else {
				Toast.makeText(this, R.string.halt_fail, Toast.LENGTH_LONG)
						.show();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		// assign refresh button to field
		refreshItem = menu.findItem(R.id.menu_refresh);
		// set delete, edit and reboot visible if there is a current device
		if (currentDevice != null) {
			LOGGER.trace("Enabling menu buttons.");
			menu.findItem(R.id.menu_delete).setVisible(true);
			menu.findItem(R.id.menu_edit_raspi).setVisible(true);
			menu.findItem(R.id.menu_reboot).setVisible(true);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			this.startActivity(settingsIntent);
			break;
		case R.id.menu_refresh:
			// do query
			refreshItem = item;
			this.doQuery(false);
			break;
		case R.id.menu_new_raspi:
			this.startActivity(newRaspiIntent);
			break;
		case R.id.menu_delete:
			this.deleteCurrentDevice();
			this.initSpinner();
			break;
		case R.id.menu_edit_raspi:
			final Bundle extras = new Bundle();
			extras.putInt(EXTRA_DEVICE_ID, currentDevice.getId());
			editRaspiIntent.putExtras(extras);
			this.startActivity(editRaspiIntent);
			break;
		case R.id.menu_reboot:
			this.showRebootDialog();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showRebootDialog() {
		LOGGER.trace("Showing reboot dialog.");
		DialogFragment rebootDialog = new RebootDialogFragment();
		rebootDialog.show(getSupportFragmentManager(), "reboot");
	}

	private void doReboot() {
		LOGGER.info("Doing reboot on {}...", currentDevice.getName());
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			// get connection settings from shared preferences
			final String host = currentDevice.getHost();
			final String user = currentDevice.getUser();
			final String pass = currentDevice.getPass();
			final String port = currentDevice.getPort() + "";
			final String sudoPass = currentDevice.getSudoPass();
			final String type = TYPE_REBOOT;
			new SSHShutdownTask().execute(host, user, pass, port, sudoPass,
					type);
		} else {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
		}
	}

	private void doHalt() {
		LOGGER.debug("Doing halt on {}...", currentDevice.getName());
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			// get connection settings from shared preferences
			final String host = currentDevice.getHost();
			final String user = currentDevice.getUser();
			final String pass = currentDevice.getPass();
			final String port = currentDevice.getPort() + "";
			final String sudoPass = currentDevice.getSudoPass();
			final String type = TYPE_HALT;
			new SSHShutdownTask().execute(host, user, pass, port, sudoPass,
					type);
		} else {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
		}
	}

	private void deleteCurrentDevice() {
		LOGGER.info("Deleting pi {}.", currentDevice.getName());
		deviceDb.delete(currentDevice.getId());
	}

	private void doQuery(boolean initByPullToRefresh) {
		if (currentDevice == null) {
			// no device available, show hint for user
			Toast.makeText(this, R.string.no_device_available,
					Toast.LENGTH_LONG).show();
			// stop refresh animation from pull-to-refresh
			refreshableScrollView.onRefreshComplete();
			return;
		}
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			// show progressbar
			progressBar.setVisibility(View.VISIBLE);
			// animate refresh button
			refreshItem.setActionView(R.layout.action_button_refresh);
			// get connection settings from shared preferences
			String host = currentDevice.getHost();
			String user = currentDevice.getUser();
			String pass = currentDevice.getPass();
			String port = currentDevice.getPort() + "";
			// reading process preference
			final Boolean hideRoot = Boolean.valueOf(sharedPrefs.getBoolean(
					SettingsActivity.KEY_PREF_QUERY_HIDE_ROOT_PROCESSES, true));
			// get connection from current device in dropdown
			if (host == null) {
				Toast.makeText(this, R.string.no_hostname_specified,
						Toast.LENGTH_LONG);
			} else if (user == null) {
				Toast.makeText(this, R.string.no_username_specified,
						Toast.LENGTH_LONG);
			} else if (pass == null) {
				Toast.makeText(this, R.string.no_password_specified,
						Toast.LENGTH_LONG);
			} else {
				// disable pullToRefresh (if refresh initiated by action bar)
				if (!initByPullToRefresh) {
					refreshableScrollView.setMode(Mode.DISABLED);
				}
				// execute query
				new SSHQueryTask().execute(host, user, pass, port,
						hideRoot.toString());
			}
		} else {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
			// stop refresh animation from pull-to-refresh
			refreshableScrollView.onRefreshComplete();
		}
	}

	private class SSHShutdownTask extends
			AsyncTask<String, Integer, ShutdownResult> {

		/**
		 * Send the reboot command. Returns true, when no Exception was raised.
		 */
		@Override
		protected ShutdownResult doInBackground(String... params) {
			raspiQuery = new RaspiQuery((String) params[0], (String) params[1],
					(String) params[2], Integer.parseInt(params[3]));
			final String sudoPass = params[4];
			final String type = params[5];
			ShutdownResult result = new ShutdownResult();
			result.setType(type);
			try {
				raspiQuery.connect();
				if (type.equals(TYPE_REBOOT)) {
					raspiQuery.sendRebootSignal(sudoPass);
				} else if (type.equals(TYPE_HALT)) {
					raspiQuery.sendHaltSignal(sudoPass);
				}
				raspiQuery.disconnect();
				return result;
			} catch (RaspiQueryException e) {
				LOGGER.error(e.getMessage());
				result.setExcpetion(e);
				return result;
			}
		}

		@Override
		protected void onPostExecute(ShutdownResult result) {
			shutdownResult = result;
			// inform handler
			mHandler.post(mRebootResult);
		}

	}

	private class SSHQueryTask extends AsyncTask<String, Integer, QueryBean> {

		@Override
		protected QueryBean doInBackground(String... params) {
			// create and do query
			raspiQuery = new RaspiQuery((String) params[0], (String) params[1],
					(String) params[2], Integer.parseInt(params[3]));
			boolean hideRootProcesses = Boolean.parseBoolean(params[4]);
			QueryBean bean = new QueryBean();
			bean.setErrorMessages(new ArrayList<String>());
			try {
				publishProgress(5);
				raspiQuery.connect();
				publishProgress(20);
				final VcgencmdBean vcgencmdBean = raspiQuery.queryVcgencmd();
				publishProgress(50);
				UptimeBean uptime = raspiQuery.queryUptime();
				publishProgress(60);
				RaspiMemoryBean memory = raspiQuery.queryMemoryInformation();
				publishProgress(70);
				String serialNo = raspiQuery.queryCpuSerial();
				publishProgress(72);
				List<ProcessBean> processes = raspiQuery
						.queryProcesses(!hideRootProcesses);
				publishProgress(80);
				final List<NetworkInterfaceInformation> networkInformation = raspiQuery
						.queryNetworkInformation();
				publishProgress(90);
				bean.setDisks(raspiQuery.queryDiskUsage());
				publishProgress(95);
				bean.setDistri(raspiQuery.queryDistributionName());
				raspiQuery.disconnect();
				publishProgress(100);
				bean.setVcgencmdInfo(vcgencmdBean);
				bean.setLastUpdate(Calendar.getInstance().getTime());
				// uptimeBean may contain messages
				if (uptime.getErrorMessage() != null) {
					bean.getErrorMessages().add(uptime.getErrorMessage());
				} else {
					bean.setStartup(uptime.getRunningPretty());
					bean.setAvgLoad(uptime.getAverageLoad());
				}
				if (memory.getErrorMessage() != null) {
					bean.getErrorMessages().add(memory.getErrorMessage());
				} else {
					bean.setFreeMem(memory.getTotalFree());
					bean.setTotalMem(memory.getTotalMemory());
				}
				bean.setSerialNo(serialNo);
				bean.setNetworkInfo(networkInformation);
				bean.setProcesses(processes);
				for (String error : bean.getErrorMessages()) {
					LOGGER.error(error);
				}
				return bean;
			} catch (RaspiQueryException e) {
				LOGGER.error("Query failed: " + e.getMessage());
				bean.setException(e);
				return bean;
			}
		}

		@Override
		protected void onPostExecute(QueryBean result) {
			// update query data
			queryData = result;
			// inform handler
			mHandler.post(mUpdateResults);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			final Integer totalProgress = values[0];
			progressBar.setProgress(totalProgress);
			super.onProgressUpdate(values);
		}

	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		LOGGER.trace("Spinner item selected: pos=" + itemPosition + ", id="
				+ itemId);
		// get device with id
		RaspberryDeviceBean read = deviceDb.read(itemId);
		this.currentDevice = read;
		if (currentDevice != null) {
			this.currentDevice.setSpinnerPosition(itemPosition);
		}
		// refresh options menu
		this.supportInvalidateOptionsMenu();
		// if current device == null (if only device was deleted), start new
		// raspi activity
		if (currentDevice == null) {
			Toast.makeText(this, R.string.please_add_a_raspberry_pi,
					Toast.LENGTH_LONG).show();
			this.startActivity(newRaspiIntent);
		}
		return true;
	}

	@Override
	public void onRefresh(PullToRefreshBase<ScrollView> refreshView) {
		LOGGER.trace("Query initiated by PullToRefresh.");
		this.doQuery(true);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// saving process and disk table
		if (queryData != null) {
			LOGGER.trace("Saving instance state (query data)");
			outState.putSerializable(QUERY_DATA, queryData);
		}
		if (currentDevice != null) {
			LOGGER.trace("Saving instance state (current device)");
			outState.putSerializable(CURRENT_DEVICE, currentDevice);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onHaltClick(DialogInterface dialog) {
		LOGGER.trace("ShutdownDialog: Halt chosen.");
		this.doHalt();
	}

	@Override
	public void onRebootClick(DialogInterface dialog) {
		LOGGER.trace("ShutdownDialog: Reboot chosen.");
		this.doReboot();
	}

}
