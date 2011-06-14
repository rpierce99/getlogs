package com.GetLogs;

import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Process;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class GetLogsActivity extends Activity implements Runnable {
    /** Called when the activity is first created. */
	private ArrayList<String> maskList = new ArrayList<String>();
	private ArrayAdapter<String> maskAdapter;
	private ProgressDialog dlg;
	private String result_string;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ListView list = (ListView)findViewById(R.id.maskList);
    	maskAdapter = new ArrayAdapter<String>(this, R.layout.simple_list_item, maskList);
        list.setAdapter(maskAdapter);
        
        SharedPreferences settings = getSharedPreferences(getString(R.string.app_name), 0);
        String masks = settings.getString("maskList", "");
        String[] arrMasks = masks.split(getString(R.string.LIST_SEPERATOR));
        for (String mask : arrMasks)
        {
        	if (mask != "")
        	{
        		maskAdapter.add(mask);
        	}
        }
        list.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				TextView clicked = (TextView)view;
				String clickedText = clicked.getText().toString();
				maskAdapter.remove(clickedText);
			}
        	
        });
        
        Button addButton = (Button)findViewById(R.id.maskAddButton);
        addButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				EditText maskAddBox = (EditText)findViewById(R.id.maskAddText);
				String maskString = maskAddBox.getText().toString().trim();
				if (maskString == "")
				{
					// warn about empty strings?
					return;
				}
				
				if (maskList.contains(maskString))
				{
					// warn about list already contains this
					return;
				}
				
				maskAdapter.add(maskString);
				maskAddBox.setText("");
			}

        });
        
        Button uploadButton = (Button)findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		try
        		{
        			StringBuilder list = new StringBuilder();
            		for (String strMask : maskList)
            		{
            			list.append(strMask);
            			list.append(getString(R.string.LIST_SEPERATOR));
            		}
            		
            		String strList = list.toString();
            		if (strList.length() > 0)
            		{
            			strList = strList.substring(0, strList.length() - getString(R.string.LIST_SEPERATOR).length());
            		
            		}
            		
        			SharedPreferences settings = getSharedPreferences(getString(R.string.app_name), 0);
	                SharedPreferences.Editor editor = settings.edit();
	                editor.putString("maskList", strList);
	                editor.commit();
            		
        		}
        		catch(Exception ex)
        		{
        			// couldn't save settings, meh.
        		}
        		
        		dlg = ProgressDialog.show(GetLogsActivity.this, "", "Gathering and uploading logs...");
        		Thread thread = new Thread(GetLogsActivity.this);
        		thread.start();       		
        	}
        });
        
    }

    private Handler handler = new Handler()
    {
    	@Override
    	public void handleMessage(Message msg)
    	{
    		try
    		{
    			dlg.dismiss();
    		}
    		catch (IllegalArgumentException ex)
    		{
    			dlg.hide();
    		}
    		
			TextView text = (TextView)findViewById(R.id.resultText);
			text.setText(result_string);
    	}
    };
    
    private String getMyPhoneNumber(){
    	TelephonyManager mTelephonyMgr;
    	mTelephonyMgr = (TelephonyManager)
    		getSystemService(Context.TELEPHONY_SERVICE); 
    	return mTelephonyMgr.getLine1Number();
	}
    
    private String getDeviceID(){
    	TelephonyManager phonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
    	 String id = phonyManager.getDeviceId();
    	 if (id == null){
    	  id = "";
    	 }
    	 return id;    	 
    }
    
	public void run() {
		try
		{
			Process p = Runtime.getRuntime().exec("su");
			DataOutputStream writer =  new DataOutputStream(p.getOutputStream());
			String line;
			File file = new File(getExternalFilesDir(null), "data.txt");
			if (file.exists())
			{
				file.delete();
			}
			String fileName = file.getAbsolutePath();
			writer.writeBytes("echo ====================== SYSTEM INFORMATION =================== >> " + fileName + "\n");
			writer.writeBytes("uname -a >> " + fileName + "\n");
			writer.writeBytes("getprop >> " + fileName + "\n");
			
			CheckBox logcatBox = (CheckBox)findViewById(R.id.logcatBox);
			if (logcatBox.isChecked())
			{
				
				writer.writeBytes("echo ======================= SYSTEM LOG =================== >> " + fileName + "\n");
				writer.flush();
				writer.writeBytes("logcat -v time -d >> " + fileName + "\n");
				writer.flush();
			}
			
			CheckBox radioBox = (CheckBox)findViewById(R.id.radioBox);
			if (radioBox.isChecked())
			{
				writer.writeBytes("echo ======================= RADIO LOG =================== >> " + fileName + "\n");
				writer.flush();
				writer.writeBytes("logcat -v time -b radio -d >> " + fileName + "\n");
				writer.flush();
			}
			
			CheckBox kernelBox = (CheckBox)findViewById(R.id.dmesgBox);
			if (kernelBox.isChecked())
			{
				writer.writeBytes("echo ======================= KERNEL LOG =================== >> " + fileName + "\n");
				
				writer.writeBytes("dmesg >> " + fileName + "\n");
				writer.flush();
			}
			
			writer.writeBytes("exit\n");
			writer.flush();
			p.waitFor();
			
			FileReader fileStream = new FileReader(fileName);
			BufferedReader fileReader = new BufferedReader(fileStream);
			StringBuilder postText = new StringBuilder();
			while((line = fileReader.readLine()) != null)
			{
				postText.append(line + getString(R.string.LINE_SEPERATOR));
			}
			fileReader.close();
			
			String pasteCode = postText.toString();
			for(String word : maskList)
			{
				pasteCode = pasteCode.replaceAll(word, "###PRIVATE###");
			}
			
			String phn = getMyPhoneNumber();
			pasteCode = pasteCode.replaceAll(phn, "###MDN##");
			
			String devid = getDeviceID();
			pasteCode = pasteCode.replaceAll(devid, "###IMEI/MEID/ESN###");
			
			Account[] accounts = AccountManager.get(this).getAccounts();
			for (Account account : accounts) {
			  String possibleEmail = account.name;
			  pasteCode = pasteCode.replaceAll(possibleEmail, "###ACCT###");
			}
			
			HttpClient webClient = new DefaultHttpClient();
			HttpPost post = new HttpPost("http://pastebin.com/api/api_post.php");
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("api_option","paste"));
			params.add(new BasicNameValuePair("api_dev_key", "4c7377b174b8d262dca80eb6662f92ae"));
			params.add(new BasicNameValuePair("api_paste_code", pasteCode));
			params.add(new BasicNameValuePair("api_paste_private", "1"));
			post.setEntity(new UrlEncodedFormEntity(params));
			HttpResponse resp = webClient.execute(post);
			int status = resp.getStatusLine().getStatusCode();
			if (status == HttpStatus.SC_OK)
			{
				InputStream respStream = resp.getEntity().getContent();
				BufferedReader read = new BufferedReader(new InputStreamReader(respStream));
				String url = read.readLine();
				result_string = "Pasted To: " +url;
				handler.sendEmptyMessage(0);
			}
			else
			{
				result_string = "Paste Failed: Error " + status;
				handler.sendEmptyMessage(0);
			}
		}
		catch(Exception ex)
		{
			result_string = ex.getMessage();
			handler.sendEmptyMessage(0);
		}
		
	}
}