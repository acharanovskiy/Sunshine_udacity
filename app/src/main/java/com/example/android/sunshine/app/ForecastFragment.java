package com.example.android.sunshine.app;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.example.android.sunshine.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> weatherData;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        List<String> dummyData = new ArrayList<>(asList(getResources().getStringArray(R.array.dummyStrings)));

        weatherData = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast,
                R.id.list_item_forecast_textview, dummyData);

        ListView view = (ListView) rootView.findViewById(R.id.list_forecast);
        view.setAdapter(weatherData);

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            new FetchWeatherTask().execute("31-436");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        @Override
        protected void onPostExecute(String[] strings) {
            if( strings != null) {
                weatherData.clear();
                for (int i = 0; i < strings.length; i++) {
                    weatherData.add(strings[i]);
                }
            }
        }

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected String[] doInBackground(String... strings) {
            if (strings.length == 0) {
                return getData("31-436");
            }
            String zip = strings[0];
            return getData(zip);
        }

        private String[] getData(String zipcode) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;

            String response = null;
            String[] weatherData = null;
            try {
                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("api.openweathermap.org")
                        .path("data/2.5/forecast/daily")
                        .appendQueryParameter("q", zipcode)
                        .appendQueryParameter("mode", "json")
                        .appendQueryParameter("cnt", "5")
                        .appendQueryParameter("units", "metric")
                        .appendQueryParameter("APPID", "84e95b0fb91ecd4688c0195b299e9af9");
                URL url = new URL(builder.build().toString());

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                InputStream stream = conn.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (stream == null)
                    return null;

                reader = new BufferedReader(new InputStreamReader(stream));

                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0)
                    return null;

                response = buffer.toString();

                weatherData = getWeatherData(response, 5);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                e.printStackTrace();
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            return weatherData;
        }

        private String[] getWeatherData(String response, int numDays) {
            //"Sun, Jul 9 - Clear - 24/14"

            String out[] = new String[numDays];
            Time dayTime = new Time();
            dayTime.setToNow();
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
            dayTime = new Time();


            try {
                JSONObject allData = new JSONObject(response);
                JSONArray days = allData.getJSONArray("list");
                for (int i = 0; i < days.length(); i++) {
                    JSONObject obj = days.getJSONObject(i);
                    String cloud, day;
                    double max = obj.getJSONObject("temp").getDouble("max");
                    double min = obj.getJSONObject("temp").getDouble("min");
                    cloud = obj.getJSONArray("weather").getJSONObject(0).getString("main");
                    long dateTime;
                    // Cheating to convert this to UTC time, which is what we want anyhow
                    dateTime = dayTime.setJulianDay(julianStartDay + i);
                    day = getReadableDateString(dateTime);
                    out[i] = day + " - " + cloud + " - " + formatHighLows(max, min);
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }


            return out;
        }

        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }
    }
}