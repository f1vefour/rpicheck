package de.eidottermihi.rpicheck.fragment;

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
public class ChooserFragment extends Fragment {
	public static final String TAG = "ChooserFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chooser, container);
	}

}
