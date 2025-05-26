package com.digitalbiology.audio;

import java.util.Locale;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class HelpActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_help);
		Toast.makeText(this, R.string.loading_help, Toast.LENGTH_LONG).show();

		WebView webview = (WebView) findViewById(R.id.webview);
		// By default, redirects cause jump from WebView to default
		// system browser. Overriding url loading allows the WebView
		// to load the redirect into this screen.
		webview.setWebViewClient(new WebViewClient() {
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				view.loadUrl(url);
				return false;
			}
		});

		String url = "https://docs.google.com/document/d/";
	    String language = Locale.getDefault().getLanguage();
		if (language.equals("de"))
			url += "1StfsJEqK-wgDBSe-yzhFVj0zn2ynVhGmpTIQcud0aOY/edit?usp=sharing";
		else if (language.equals("fr"))
			url += "1a24ncvGBQXkXMpwsFXwuZTmc7pc-pOsGVkZ2XkT2sMQ/edit?usp=sharing";
		else if (language.equals("es"))
			url += "1uDy_oHpbPMReseqMLJPNvsdDhKC7HTW-vSdze5MK8zE/edit?usp=sharing";
		else if (language.equals("it"))
			url += "1AliriWPEklWC4Nz5tNZqCsxHyFEhk390dSBpw4QPudw/edit?usp=sharing";
	    else if (language.equals("sv"))
			url += "1xyQDF3J-jhr-MwfyucNjYh_SJ7IqpCeKYL2Qt_69Ccs/edit?usp=sharing";
	    else if (language.equals("nb") || language.equals("no"))
			url += "1y88J2w9PVTz04Xu4OImIwTe6jZ73uirfrHUDvTbIQLk/edit?usp=sharing";
		else if (language.equals("nl"))
			url += "1ZQXIy1yB6oCjMsdhq572T5tAEaVULbnyTxZFYPx820Q/edit?usp=sharing";
	    else
			url += "12cdJF85LHix200T9jAfyC7-XAa0fMnJYQOIMuASr8Xk/edit?usp=sharing";

//		if (language.equals("de"))
//			webview.loadUrl("file:///android_asset/help-de.html");
//		else if (language.equals("fr"))
//			webview.loadUrl("file:///android_asset/help-fr.html");
//		else if (language.equals("it"))
//			webview.loadUrl("file:///android_asset/help-it.html");
//		else if (language.equals("sv"))
//			webview.loadUrl("file:///android_asset/help-sv.html");
//		else if (language.equals("nb") || language.equals("no"))
//			webview.loadUrl("file:///android_asset/help-nb.html");
//		else
//			webview.loadUrl("file:///android_asset/help-en.html");

		webview.loadUrl(url);
	}
}
