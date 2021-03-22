package com.anychart

import android.widget.FrameLayout
import android.webkit.WebView
import android.os.Parcelable
import android.os.Bundle
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.core.content.getSystemService
import com.anychart.chart.common.listener.ListenersInterface
import com.anychart.core.Chart
import java.lang.StringBuilder

open class AnyChartView: FrameLayout {
	var jsListener: JsListener? = null
	var onRenderedListener: OnRenderedListener? = null
	private var webView: WebView? = null
	private var chart: Chart? = null
	private var isRestored = false
	private var isRendered = false
	private var isDebug = false
	private val scripts = StringBuilder()
	private val fonts = StringBuilder()
	protected var js = StringBuilder()
	private var licenceKey = ""
	private var progressBar: View? = null
	private var backgroundColor: String? = null
	
	interface JsListener {
		fun onJsLineAdd(jsLine: String)
	}
	
	interface OnRenderedListener {
		fun onRendered()
	}
	
	constructor(context: Context?): super(context!!) {
		init()
	}
	
	constructor(context: Context?, attrs: AttributeSet?): super(
		context!!, attrs
	) {
		init()
	}
	
	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int): super(
		context!!, attrs, defStyleAttr
	) {
		init()
	}
	
	public override fun onSaveInstanceState(): Parcelable? {
		val bundle = Bundle()
		bundle.putParcelable("superState", super.onSaveInstanceState())
		bundle.putString("js", js.toString())
		return bundle
	}
	
	public override fun onRestoreInstanceState(state: Parcelable) {
		var state: Parcelable? = state
		if (state is Bundle) {
			val bundle = state
			js.append(bundle.getString("js"))
			state = bundle.getParcelable("superState")
		}
		isRestored = true
		super.onRestoreInstanceState(state)
	}
	
	@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
	private fun init() {
		val view =
			context.getSystemService<LayoutInflater>()?.inflate(R.layout.view_anychart, this, true)
		
		APIlib.getInstance().setActiveAnyChartView(this)
		if (progressBar != null)
			progressBar?.visibility = VISIBLE
		webView = view?.findViewById(R.id.web_view)
		webView?.settings?.loadsImagesAutomatically = true
		webView?.settings?.javaScriptEnabled = true
		webView?.settings?.loadWithOverviewMode = true
		webView?.isLongClickable = true
		webView?.setOnLongClickListener { true }
		webView?.webChromeClient =
			object: WebChromeClient() {
				override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
					if (isDebug)
						Log.e("AnyChart", consoleMessage.message())
					webView?.isEnabled = true
					return true
				}
			}
		isRendered = false
		JsObject.variableIndex = 0
		jsListener =
			object: JsListener {
				override fun onJsLineAdd(jsLine: String) {
					webView?.post runnable@{
						if (isRestored)
							return@runnable
						if (isRendered) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
								webView?.evaluateJavascript(jsLine, null)
							else
								webView?.loadUrl("javascript:$jsLine")
						}
						js.append(jsLine)
					}
				}
			}
		webView?.webViewClient =
			object: WebViewClient() {
				override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest):
					Boolean = true
			
			override fun onPageFinished(view: WebView, url: String) {
				val resultJs =
					if (isRestored)
						js.toString()
					else
						js
							.append(chart!!.getJsBase())
							.append(".container(\"container\");")
							.append(chart!!.getJsBase()).append(".draw();")
							.toString()
				webView?.evaluateJavascript("""
					anychart.licenseKey("$licenceKey");
					anychart.onDocumentReady(function() { $resultJs });
				""") {
					if (onRenderedListener != null) onRenderedListener!!.onRendered()
					if (progressBar != null) progressBar!!.visibility = GONE
				}
				isRestored = false
				isRendered = true
			}
		}
		webView?.addJavascriptInterface(ListenersInterface.getInstance(), "android")
	}
	
	private fun loadHtml() {
		val htmlData = """
			<html>
			<head>
			    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
			    <style type="text/css">
			        html, body, #container {
			            width: 100%;
			            height: 100%;
			            margin: 0;
			            padding: 0;
						${if (backgroundColor != null) "background-color: $backgroundColor;" else ""}
					}
					$fonts
				</style>
			</head>
			<body>
				<script src="file:///android_asset/anychart-bundle.min.js"></script>
				$scripts
				<link rel="stylesheet" href="file:///android_asset/anychart-ui.min.css"/>
				<div id="container"></div>
			</body>
			</html>
		"""
		webView!!.loadDataWithBaseURL("", htmlData, "text/html", "UTF-8", null)
	}
	
	fun addScript(url: String?) {
		scripts.append("<script src=\"")
			.append(url)
			.append("\"></script>\n")
	}
	
	fun addCss(url: String?) {
		scripts.append("<link rel=\"stylesheet\" href=\"")
			.append(url)
			.append("\"/>\n")
	}
	
	fun addFont(fontFamily: String?, url: String?) {
		fonts.append("@font-face {\n")
			.append("font-family: ").append(fontFamily).append(";\n")
			.append("src: url(").append(url).append(");\n")
			.append("}\n")
	}
	
	fun setLicenceKey(key: String) {
		licenceKey = key
	}
	
	fun setZoomEnabled(value: Boolean?) {
		webView!!.settings.builtInZoomControls = value!!
		webView!!.settings.displayZoomControls = !value
	}
	
	fun clear() {
		webView!!.loadUrl("about:blank")
		isRendered = false
		if (progressBar != null) {
			progressBar!!.visibility = VISIBLE
		}
	}
	
	fun setChart(chart: Chart?) {
		isRestored = false
		this.chart = chart
		loadHtml()
	}
	
	fun setProgressBar(progressBar: View) {
		this.progressBar = progressBar
		progressBar.visibility = VISIBLE
	}
	
	fun setBackgroundColor(color: String?) {
		backgroundColor = color
		webView!!.setBackgroundColor(Color.parseColor(color))
	}
	
	fun setDebug(value: Boolean) {
		isDebug = value
	}
}