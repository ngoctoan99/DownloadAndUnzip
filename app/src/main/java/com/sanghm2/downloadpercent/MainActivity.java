package com.sanghm2.downloadpercent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.sanghm2.downloadpercent.databinding.ActivityMainBinding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {
    ProgressDialog mProgressDialog, mProgressDialog1;
    ActivityMainBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage("Download");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);


        mProgressDialog1 = new ProgressDialog(MainActivity.this);
        mProgressDialog1.setMessage("Unzip");
        mProgressDialog1.setIndeterminate(true);
        mProgressDialog1.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog1.setCancelable(true);




        // execute this when the downloader must be fired
        binding.btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File directory = new File(Environment.getExternalStorageDirectory()+"/sample.zip");
                if (!directory.exists()) {
                    DownloadTask downloadTask = new DownloadTask(MainActivity.this);
                    downloadTask.execute("https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-large-zip-file.zip");
                }else{
                    Toast.makeText(MainActivity.this, "Downloaded", Toast.LENGTH_SHORT).show();
                }
            }
        });
        binding.btnUnzipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File directory1 = new File(Environment.getExternalStorageDirectory()+"/unzip/sample-mpg-file.mpg");
                if (!directory1.exists()) {
                    UnzipFileTask unzipFileTask = new UnzipFileTask(Environment.getExternalStorageDirectory()+"/sample.zip",Environment.getExternalStorageDirectory()+"/unzip/");
                    unzipFileTask.execute();
                }else{
                    Toast.makeText(MainActivity.this, "Unzipped", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setVideo(){
        File directory1 = new File(Environment.getExternalStorageDirectory()+"/unzip/sample-mpg-file.mpg");
        if (directory1.exists()) {
            binding.videoView.setKeepScreenOn(true);
            binding.videoView.setVideoPath(Environment.getExternalStorageDirectory()+"/unzip/sample-mpg-file.mpg");
            binding.videoView.start();
        }else{
            Toast.makeText(MainActivity.this, "Error LinkVideo", Toast.LENGTH_SHORT).show();
        }
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;

        public DownloadTask(Context context) {
            this.context = context;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
            mProgressDialog.show();
        }
        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream( Environment.getExternalStorageDirectory().toString()+"/sample.zip");

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }
        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }
        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            mProgressDialog.dismiss();
            if (result != null)
                Toast.makeText(context,"Download error: "+result, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(context,"File downloaded", Toast.LENGTH_SHORT).show();
        }
    }

    private class UnzipFileTask extends  AsyncTask<Void, Integer , Integer>{
        private String _zipFile;
        private String _location;
        private int per = 0;

        public UnzipFileTask(String zipFile, String location){
            _zipFile = zipFile;
            _location = location;
            _dirChecker("");
        }
        private void _dirChecker(String dir){
            File f = new File(_location + dir);
            if(!f.isDirectory()){
                f.mkdirs();
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog1.show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            try {
                ZipFile zip = new ZipFile(_zipFile);
                mProgressDialog1.setMax(zip.size());
                FileInputStream fin = new FileInputStream(_zipFile);
                ZipInputStream zin = new ZipInputStream(new BufferedInputStream(fin));
                ZipEntry ze = null;
                while ((ze = zin.getNextEntry()) != null) {
                    Log.v("Decompress", "Unzipping " + ze.getName());
                    if (ze.isDirectory()) {
                        _dirChecker(ze.getName());
                    } else {
                        // Here I am doing the update of my progress bar
                        per++;
                        publishProgress(per / zip.size() * 100);

                        FileOutputStream fout = new FileOutputStream(_location + ze.getName());
                        for (int c = zin.read(); c != -1; c = zin.read()) {
                            fout.write(c);
                        }
                        zin.closeEntry();
                        fout.close();
                    }
                }
                zin.close();
            } catch (Exception e) {
                Log.e("Decompress", "unzip", e);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mProgressDialog1.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            mProgressDialog1.dismiss();
            setVideo();
        }
    }
}
