package com.veken0m.bitcoinium;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.veken0m.bitcoinium.R;
import com.veken0m.graphing.GraphViewer;
import com.veken0m.graphing.LineGraphView;
import com.veken0m.graphing.GraphView.GraphViewData;
import com.veken0m.graphing.GraphView.GraphViewSeries;
import com.xeiam.xchange.Currencies;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;

public class GraphActivity extends SherlockActivity {

	private GraphViewer g_graphView = null;
	private ProgressDialog graphProgressDialog;
	private static final Handler mOrderHandler = new Handler();
	public static final String VIRTEX = "com.veken0m.bitcoinium.VIRTEX";
	public static final String MTGOX = "com.veken0m.bitcoinium.MTGOX";
	public static String exchangeName = "";
	public static String currency = "";
	public String exchange = VIRTEX;
	public String xchangeExchange = null;
	static String pref_mtgoxCurrency;

	/**
	 * Variables required for LineGraphView
	 */
	LineGraphView graphView = null;
	static Boolean pref_graphMode;
	static Boolean pref_scaleMode;
	static int pref_mtgoxWindowSize;
	static int pref_virtexWindowSize;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.graph);
		readPreferences(getApplicationContext());

		ActionBar actionbar = getSupportActionBar();
		actionbar.show();

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			exchange = extras.getString("exchange");
		}

		if (exchange.equalsIgnoreCase(MTGOX)) {
			exchangeName = "MtGox";
			xchangeExchange = "com.xeiam.xchange.mtgox.v1.MtGoxExchange";
			currency = pref_mtgoxCurrency;
		}
		if (exchange.equalsIgnoreCase(VIRTEX)) {
			exchangeName = "VirtEx";
			xchangeExchange = "com.xeiam.xchange.virtex.VirtExExchange";
			currency = Currencies.CAD;
		}

		viewGraph();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.preferences) {
			startActivity(new Intent(this, PreferencesActivity.class));
		}
		return super.onOptionsItemSelected(item);
	}

	public class GraphThread extends Thread {

		@Override
		public void run() {
			generatePreviousPriceGraph();
			mOrderHandler.post(mGraphView);
		}
	}

	/**
	 * mGraphView run() is called when our GraphThread is finished
	 */
	final Runnable mGraphView = new Runnable() {
		@Override
		public void run() {
			safelyDismiss(graphProgressDialog);
			if (g_graphView != null || graphView != null) {
				if (!pref_graphMode) {
					setContentView(graphView);
				} else {
					setContentView(g_graphView);
				}
			} else {
				createPopup("Unable to retrieve transactions from "
						+ exchangeName + ", check your 3G or WiFi connection");
			}
		}
	};

	/**
	 * generatePreviousPriceGraph prepares price graph of all the values
	 * available from the API It connects to exchange, reads the JSON, and plots
	 * a GraphView of it
	 */
	private void generatePreviousPriceGraph() {

		g_graphView = null;

		try {

			final Trades trades = ExchangeFactory.INSTANCE
					.createExchange(xchangeExchange)
					.getPollingMarketDataService()
					.getTrades(Currencies.BTC, currency);

			List<Trade> tradesList = trades.getTrades();

			float[] values = new float[tradesList.size()];
			float[] dates = new float[tradesList.size()];
			final GraphViewData[] data = new GraphViewData[values.length];

			final Format formatter = new SimpleDateFormat("MMM dd @ HH:mm");

			float largest = Integer.MIN_VALUE;
			float smallest = Integer.MAX_VALUE;

			final int tradesListSize = tradesList.size();
			for (int i = 0; i < tradesListSize; i++) {
				final Trade trade = tradesList.get(i);
				values[i] = trade.getPrice().getAmount().floatValue();
				dates[i] = Float.valueOf(trade.getTimestamp().getMillis());

				if (values[i] > largest) {
					largest = values[i];
				}
				if (values[i] < smallest) {
					smallest = values[i];
				}
			}

			if (pref_graphMode) {

				final String sOldestDate = formatter.format(dates[0]);
				final String sMidDate = formatter.format(dates[dates.length / 2 - 1]);
				final String sNewestDate = formatter.format(dates[dates.length - 1]);

				// min, max, steps, pre string, post string, number of decimal
				// places
				final String[] verlabels = GraphViewer.createLabels(smallest,
						largest, 10, "$", "", 4);

				final String[] horlabels = new String[] { sOldestDate, "", "",
						sMidDate, "", "", sNewestDate };

				g_graphView = new GraphViewer(this, values, currency
						+ "/BTC since " + sOldestDate, // title
						horlabels, // horizontal labels
						verlabels, // vertical labels
						GraphViewer.LINE, // type of graph
						smallest, // min
						largest); // max
			} else {

				for (int i = 0; i < tradesListSize; i++) {
					data[i] = new GraphViewData(dates[i], values[i]);
				}

				graphView = new LineGraphView(this, exchangeName + ": "
						+ currency + "/BTC") {
					@Override
					protected String formatLabel(double value, boolean isValueX) {
						if (isValueX) {
							return formatter.format(value);
						} else
							return super.formatLabel(value, isValueX);
					}
				};

				double windowSize;
				if (exchangeName.equalsIgnoreCase("mtgox")) {
					windowSize = pref_mtgoxWindowSize * 3600000;
				} else {
					windowSize = pref_virtexWindowSize * 3600000;
				}
				// startValue enables graph window to be aligned with latest
				// trades
				final double startValue = dates[dates.length - 1] - windowSize;
				graphView.addSeries(new GraphViewSeries(data));
				graphView.setViewPort(startValue, windowSize);
				graphView.setScrollable(true);
				graphView.setScalable(true);
				if (!pref_scaleMode) {
					graphView.setManualYAxisBounds(largest, smallest);
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void createPopup(String pMessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(pMessage);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setContentView(R.layout.graph);
		if (g_graphView != null || graphView != null) {
			if (!pref_graphMode) {
				setContentView(graphView);
			} else {
				setContentView(g_graphView);
			}
		} else {
			viewGraph();
		}
	}

	private void safelyDismiss(ProgressDialog dialog) {
		if (dialog != null && dialog.isShowing()) {
			dialog.dismiss();
		}
	}

	private void viewGraph() {
		if (graphProgressDialog != null && graphProgressDialog.isShowing()) {
			return;
		}
		graphProgressDialog = ProgressDialog
				.show(this,
						"Working...",
						"Retrieving trades... \n\nNote: May take a while on large exchanges.",
						true, true);
		GraphThread gt = new GraphThread();
		gt.start();
	}

	protected static void readPreferences(Context context) {
		// Get the xml/preferences.xml preferences
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);

		pref_graphMode = prefs.getBoolean("graphmodePref", false);
		pref_scaleMode = prefs.getBoolean("graphscalePref", false);
		pref_mtgoxWindowSize = Integer.parseInt(prefs.getString(
				"mtgoxWindowSize", "4"));
		pref_virtexWindowSize = Integer.parseInt(prefs.getString(
				"virtexWindowSize", "36"));
		pref_mtgoxCurrency = prefs.getString("mtgoxCurrencyPref", "USD");
	}

}
