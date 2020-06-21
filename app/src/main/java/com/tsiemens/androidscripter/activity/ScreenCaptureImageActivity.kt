package com.tsiemens.androidscripter.activity

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.graphics.Bitmap
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.widget.Toolbar
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.tsiemens.androidscripter.DebugOverlayManager
import com.tsiemens.androidscripter.OverlayManager
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.TesseractHelper
import com.tsiemens.androidscripter.script.ScreenUtil
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException


class ScreenCaptureImageActivity : ScreenCaptureActivityBase() {
    private var mImgView : ImageView? = null
    lateinit private var serverLocationText : TextView

    private var lastImgText: String? = null

    // Set here for testing only
    val overlayManager = OverlayManager(this)

    val debugOverlayManager = DebugOverlayManager(this)

    lateinit var tessHelper: TesseractHelper

    lateinit var screenCapClient: ScreenCaptureClient

    var httpd: ImgHttpd? = null

    companion object {
        private val TAG = ScreenCaptureImageActivity::class.java.simpleName
        private val PREPARE_TESS_PERMISSION_REQUEST_CODE =
            MIN_REQUEST_CODE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imgcap)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        mImgView = findViewById(R.id.bitmap_imgview)
        serverLocationText = findViewById(R.id.url_textview)

        // start projection
        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener { startProjection() }

        // stop projection
        val stopButton = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener { stopProjection() }

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap, imgId: Long) {
                    val imgText = tessHelper.extractText(bm)
                    lastImgText = imgText
                    Log.i(TAG, "Image text: \"$imgText\"")

                    val action = {
                        if (imgText != null) {
                            overlayManager.updateOcrText(imgText)
                        }
                        mImgView!!.setImageBitmap(bm)
                    }
                    val newBm = ScreenUtil.toGray(bm)
                    val xs = ScreenUtil.findXs(bm)
                    val lines = ScreenUtil.getLinesFromXs(xs.xs)
                    ScreenUtil.drawLinesToBm(lines, newBm)
                    httpd?.bitmap = newBm

                    if (Looper.myLooper() == Looper.getMainLooper()) {
                       action()
                    } else {
                        runOnUiThread(action)
                    }
                }
            }
        setScreenCaptureClient(screenCapClient)

        tessHelper = TesseractHelper(
            this,
            PREPARE_TESS_PERMISSION_REQUEST_CODE
        )
        tessHelper.prepareTesseract(true)

        try {
            httpd = ImgHttpd(this)
            httpd!!.doStart()
            val url = "http://${httpd!!.getIpAddress()}:${httpd!!.listeningPort}"
            Log.i(TAG, "Started httpd at $url")
            serverLocationText.text = url
        } catch (e: IOException) {
            Log.e(TAG, "Could not start httpd: $e")
            Toast.makeText(this, "Unaable to start http server", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == PREPARE_TESS_PERMISSION_REQUEST_CODE) {
            tessHelper.prepareTesseract(false)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                Log.i(TAG, "Capture in overlay pressed")
                // The next image cap will return a response in the client
                screenCapClient.requestScreenCap()
            })
        }

        if (debugOverlayManager.permittedToShow() && !debugOverlayManager.started()) {
            debugOverlayManager.showOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
        httpd?.stop()
    }
}

class HtmlContent {
    companion object {
        val jsScript = """
var tooltip = $( '<div id="tooltip">' ).appendTo( 'body' )[0];

$( '.coords' ).
    each(function () {
        var showTooltip = function( e ) {
            var pos = $( this ).position(),
                top = pos.top,
                left = pos.left,
                width = $( this ).width(),
                height = $( this ).height();
            
            var x = ( e.clientX - left ).toFixed( 1 ),
                y = ( e.clientY - top ).toFixed( 1 );
            
            $( tooltip ).text( x + ', ' + y ).css({
                left: e.clientX - 60,
                top: e.clientY - 60
            }).show();
        };
        
        $( this ).
            mousemove(showTooltip).
            mouseenter(showTooltip).
            mouseleave(function () {
                $( tooltip ).hide();
            }); 
    });
"""

        val css = """
body { font:13px/1.4 Arial, sans-serif; margin:50px; background:gray; }
#tooltip { font-size: 30px; text-align:center; background:black; color:white; padding:3px 0; width:200px; position:fixed; display:none; white-space:nowrap; }
"""

        val mainHeader = """<script type="text/javascript" src="https://code.jquery.com/jquery-3.4.1.slim.min.js"></script>
<link rel="stylesheet" href="/styles.css"/>"""
    }
}

class ImgHttpd
@Throws(IOException::class)
constructor(val context: Context) : NanoHTTPD(8080) {
    var bitmap: Bitmap?  = null

    companion object {
        val TAG = ImgHttpd::class.java.simpleName

        fun pathRegex(path: String): Regex {
            return Regex("^${Regex.escape(path)}(#.*)?$")
        }

        val rootPathRe = pathRegex("/")

        val screenshotPath = "/screenshot.png"
        val screenshotPathRe = pathRegex(screenshotPath)

        val stylePath = "/styles.css"
        val stylePathRe = pathRegex(stylePath)
    }

    fun doStart() {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Running! Point your browsers to http://localhost:8080/ \n")
    }

    private fun htmlBody(content: String, head: String? = null): String {
        val headActual = if(head != null) "<head>\n$head\n</head>\n" else ""
        return "<html>\n$headActual<body>\n$content\n</body></html>\n"
    }

    fun serveMain(): Response {
        var msg = ""
        var head: String? = null
        if (bitmap != null) {
            head = HtmlContent.mainHeader
            msg += "<img class=\"coords\" src=\"$screenshotPath\"/><script type=\"text/javascript\">${HtmlContent.jsScript}</script>"
        } else {
            msg += "<p>No image available</p>"
        }

        return NanoHTTPD.newFixedLengthResponse(htmlBody(msg, head))

    }

    private fun serveScreenshot(): Response {
        val bmTmp = bitmap ?: return serve404()

        val ops = ByteArrayOutputStream()
        bmTmp.compress(Bitmap.CompressFormat.PNG, 100, ops)
        val arr = ops.toByteArray()
        return newChunkedResponse(Response.Status.ACCEPTED, "text/png",
            ByteArrayInputStream(arr))
    }

    private fun serveStyles(): Response {
        return newFixedLengthResponse(Response.Status.ACCEPTED, "text/css", HtmlContent.css)
    }

    private fun serve404(): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", htmlBody("404 Not Found"))
    }

    override fun serve(session: IHTTPSession): NanoHTTPD.Response {
        Log.e(TAG, "Serving request: '${session.uri}'")
        if (session.uri.matches(screenshotPathRe)) {
            return serveScreenshot()
        } else if (session.uri.matches(rootPathRe)) {
            return serveMain()
        } else if (session.uri.matches(stylePathRe)) {
            return serveStyles()
        }
        Log.w(TAG, "Found no matching page for '${session.uri}'")
        return serve404()
    }

    fun getIpAddress(): String {
        val addrInt = (context.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(addrInt)
    }
}