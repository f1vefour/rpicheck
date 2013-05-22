package de.eidottermihi.rpicheck.fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.eidottermihi.rpicheck.R;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author Michael
 * 
 */
public class QueryDataFragment extends Fragment {
	public static final String TAG = "QueryDataFragment";

	private static final Logger LOGGER = LoggerFactory
			.getLogger(QueryDataFragment.class);

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		LOGGER.debug("Creating fragment {}.", TAG);
		return inflater.inflate(R.layout.fragment_querydata, container);
	}

}
