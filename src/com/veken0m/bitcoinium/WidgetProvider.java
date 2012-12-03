package com.veken0m.bitcoinium;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

import com.veken0m.bitcoinium.R;
import com.xeiam.xchange.Currencies;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.dto.marketdata.Ticker;

public class WidgetProvider extends BaseWidgetProvider {

	@Override
	public void onReceive(Context ctxt, Intent intent) {

		if (REFRESH.equals(intent.getAction())) {
			setAlarm(ctxt);
		} else {
			super.onReceive(ctxt, intent);
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		setAlarm(context);
	}

	/**
	 * This class lets us refresh the widget whenever we want to
	 */

	public static class UpdateService extends IntentService {

		public UpdateService() {

			super("WidgetProvider$UpdateService");
		}

		@Override
		public void onCreate() {
			super.onCreate();
		}

		@Override
		public int onStartCommand(Intent intent, int flags, int startId) {
			super.onStartCommand(intent, flags, startId);
			return START_STICKY;
		}

		@Override
		public void onHandleIntent(Intent intent) {
			buildUpdate(this);
		}

		public void buildUpdate(Context context) {

			readPreferences(context);

			AppWidgetManager widgetManager = AppWidgetManager
					.getInstance(context);
			ComponentName widgetComponent = new ComponentName(context,
					WidgetProvider.class);
			int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);

			// Date for widget "Refreshed" label
			final SimpleDateFormat sdf = new SimpleDateFormat("h:mm a",
					Locale.US);
			final String currentTime = sdf.format(new Date());

			final RemoteViews views = new RemoteViews(context.getPackageName(),
					R.layout.appwidget);

			final Intent intent = new Intent(context, MainActivity.class);
			final PendingIntent pendingIntent = PendingIntent.getActivity(
					context, 0, intent, 0);

			for (int appWidgetId : widgetIds) {

				String pref_widgetExchange = WidgetConfigureActivity
						.loadExchangePref(context, appWidgetId);
				String pref_currency = "NA";
				String exchange = "NA";
				int NOTIFY_ID = -1;

				if (pref_widgetExchange
						.equals("com.xeiam.xchange.virtex.VirtExExchange")) {
					pref_currency = "CAD";
					exchange = "VirtEx";
					pref_mtgoxLower = pref_virtexLower;
					pref_mtgoxUpper = pref_virtexUpper;
					NOTIFY_ID = NOTIFY_ID_VIRTEX;
				} else if (pref_widgetExchange.equals("com.xeiam.xchange.mtgox.v1.MtGoxExchange")) 
					{
					pref_currency = WidgetConfigureActivity.loadCurrencyPref(
							context, appWidgetId);
					exchange = "MtGox";
					NOTIFY_ID = NOTIFY_ID_MTGOX;
				}

				if ((pref_currency.length() == 3) && !(exchange.equals("NA"))
						) {

					views.setOnClickPendingIntent(R.id.widgetButton,
							pendingIntent);

					try {

						// Get ticker using XChange
						final Ticker ticker = ExchangeFactory.INSTANCE
								.createExchange(pref_widgetExchange)
								.getPollingMarketDataService()
								.getTicker(Currencies.BTC, pref_currency);

						// Retrieve values from ticker
						Float lastValue = ticker.getLast().getAmount()
								.floatValue();

						final String lastPrice = Utils.formatWidgetMoney(
								lastValue, pref_currency, true);
						final String highPrice = Utils.formatWidgetMoney(ticker
								.getHigh().getAmount().floatValue(),
								pref_currency, false);
						final String lowPrice = Utils.formatWidgetMoney(ticker
								.getLow().getAmount().floatValue(),
								pref_currency, false);
						final String volume = Utils.formatTwoDecimals(ticker
								.getVolume().floatValue());

						views.setTextViewText(R.id.widgetExchange, exchange);
						views.setTextViewText(R.id.widgetLowText, lowPrice);
						views.setTextViewText(R.id.widgetHighText, highPrice);
						views.setTextViewText(R.id.widgetLastText, lastPrice);
						views.setTextViewText(R.id.widgetVolText, "Volume: "
								+ volume);

						views.setTextViewText(R.id.label, "Refreshed @ "
								+ currentTime);
						views.setTextColor(R.id.label, Color.GREEN);

						if (pref_DisplayUpdates == true) {
							createTicker(context, R.drawable.bitcoin, ""
									+ exchange + " Updated!");
						}

						// if (pref_mtgoxTicker) {
						// createPermanentNotification(context,
						// R.drawable.bitcoin, "Bitcoin at "
						// + lastPrice, "Bitcoin value: "
						// + lastPrice + " on " + exchange,
						// NOTIFY_ID);
						// }

						if (pref_PriceAlarm) {

							if (exchange.equals("MtGox")
									&& pref_currency.equals(pref_mtgoxCurrency)
									&& !pref_mtgoxUpper.equals("")
									&& !pref_mtgoxLower.equals("")
									&& !Utils.isBetween(lastValue,
											Float.valueOf(pref_mtgoxLower),
											Float.valueOf(pref_mtgoxUpper))
									) {
								createNotification(context, lastPrice,
										exchange, NOTIFY_ID);
							}

							else if (exchange.equals("VirtEx")
									&& pref_currency.equals("CAD")
									&& !pref_virtexLower.equals("")
									&& !pref_virtexUpper.equals("")
									&& !Utils.isBetween(lastValue,
											Float.valueOf(pref_virtexLower),
											Float.valueOf(pref_virtexUpper))
								) {
								createNotification(context, lastPrice,
										exchange, NOTIFY_ID);
							}
						}

					} catch (Exception e) {
						e.printStackTrace();
						if (pref_DisplayUpdates == true) {
							createTicker(context, R.drawable.bitcoin, exchange
									+ " Update failed!");
						}
						views.setTextColor(R.id.label, Color.RED);

					}
					widgetManager.updateAppWidget(appWidgetId, views);
				}
			}
		}
	}

}
