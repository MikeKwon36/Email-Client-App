package com.example.gmailquickstart;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

import com.google.api.services.gmail.model.*;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Toolbar mToolbar;
    private ActionBar mActionbar;
    private EmailAdapter mEmailAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mRecyclerViewLayoutManager;

    //scopes updated for Read/write permissions
    private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS,GmailScopes.GMAIL_READONLY,GmailScopes.GMAIL_COMPOSE };
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    private static final String PREF_ACCOUNT_NAME = "stringKey";
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;

    //Replace AsyncTask DB call to retrieve all emails, transform into Email objects, then store in Singleton for easy access (will need to limit number of emails retrieved...
    //Update onClick listener in RecyclerViewHolder to open email messages
    //Add FAB to launch compose email screen

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_phone);

        // Initialize credentials and service object.
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));

        //Create Layout elements
        mOutputText = (TextView)findViewById(R.id.xmlTextView);
        mToolbar = (Toolbar)findViewById(R.id.xmlActionBar);
        setSupportActionBar(mToolbar);
        mActionbar = getSupportActionBar();
        mActionbar.setTitle("KwonMail");
        mActionbar.setDisplayHomeAsUpEnabled(true);
        mActionbar.setHomeButtonEnabled(true);

        //Instantiate List of Email Objects
        mEmailAdapter = new EmailAdapter(EmailList.getInstance().getAllEmails());
        mRecyclerView = (RecyclerView) findViewById(R.id.xmlRecyclerView);
        mRecyclerViewLayoutManager = new LinearLayoutManager(MainActivity.this);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mRecyclerViewLayoutManager);
        mRecyclerView.setAdapter(mEmailAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isGooglePlayServicesAvailable()) {refreshResults();}
        else {mOutputText.setText("Google Play Services required: " + "after installing, close and relaunch this app.");}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {isGooglePlayServicesAvailable();}
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        mCredential.setSelectedAccountName(accountName);
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();}
                } else if (resultCode == RESULT_CANCELED) {mOutputText.setText("Account unspecified.");}
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {chooseAccount();}
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Attempt to get a set of data from the Gmail API to display. If the
     * email address isn't known yet, then call chooseAccount() method so the
     * user can pick an account.
     */
    private void refreshResults() {
        if (mCredential.getSelectedAccountName() == null) {chooseAccount();}
        else {
            if (isDeviceOnline()) {new MakeRequestTask(mCredential).execute();}
            else {mOutputText.setText("No network connection available.");}
        }
    }

    //Starts an activity in Google Play Services so the user can pick an account
    private void chooseAccount() {
        startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**Check that Google Play services APK is installed and up to date. Will launch an error dialog
     * for the user to update Google Play Services if possible.*/
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    //Display an error dialog showing that Google Play Services is missing or out of date.
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(connectionStatusCode,
                MainActivity.this, REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    //An asynchronous task to handle Gmail API call
    private class MakeRequestTask extends AsyncTask<Void, Void, ArrayList<Email>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        //constructor
        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart").build();
        }

        //Background task to call Gmail API
        @Override
        protected ArrayList<Email> doInBackground(Void... params) {
            try {return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        //Fetch a list of Gmail message data attached to the specified account
        private ArrayList<Email> getDataFromApi() throws IOException {
            //List<String> strings = new ArrayList<String>();
            ListMessagesResponse list = mService.users().messages().list("me").execute();
            int subjectIndex = 0;

            for (int i = 0; i < list.getMessages().size(); i++) {

                Message message = list.getMessages().get(i);
                for (int x = 0; x < message.getPayload().getHeaders().size(); x++) {
                    if(message.getPayload().getHeaders().get(x).getName().equals("Subject")){subjectIndex=x;}
                }

                Email email = new Email();
                email.setmEmailID(message.getId());
                email.setmSnippet(message.get("snippet").toString());
                email.setmPayloadHeadersSubject(message.getPayload().getHeaders().get(subjectIndex).toString());
                EmailList.getInstance().addEmail(i, email);
            }
            return EmailList.getInstance().getAllEmails();
        }

        @Override
        protected void onPostExecute(ArrayList<Email> output) {
            mEmailAdapter.notifyDataSetChanged();
            try{
                ListMessagesResponse list = mService.users().messages().list("me").execute();
                Message message = list.getMessages().get(0);
                mOutputText.setText(message.getId());
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError).getConnectionStatusCode());}
                else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(((UserRecoverableAuthIOException) mLastError).getIntent(),MainActivity.REQUEST_AUTHORIZATION);}
                else {mOutputText.setText("The following error occurred:\n" + mLastError.getMessage());}}

            else {mOutputText.setText("Request cancelled.");}
        }
    }
}