package it.mithril.app.ui.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Player YouTube incorporato tramite IFrame Player API in una WebView.
 * L'origine https serve a YouTube per accettare l'incorporamento (senza referrer risponde errore 153).
 */
@Stable
class YouTubePlayerState : MediaEngine {
    /** -1 non avviato, 0 finito, 1 in riproduzione, 2 in pausa, 3 buffering, 5 pronto. */
    override var state by mutableIntStateOf(-1)
    override var position by mutableFloatStateOf(0f)
    override var duration by mutableFloatStateOf(0f)
    var error by mutableStateOf<Int?>(null)
    var ready by mutableStateOf(false)
    var endedCount by mutableIntStateOf(0)
    internal var web: WebView? = null
    override var onEnded: (() -> Unit)? = null

    override fun play() = js("cmd('play')")
    override fun pause() = js("cmd('pause')")
    override fun seek(sec: Float) = js("cmd('seek',$sec)")
    fun load(id: String) { position = 0f; duration = 0f; error = null; state = -1; js("cmd('load','$id')") }
    private fun js(code: String) { web?.evaluateJavascript(code, null) }

    companion object {
        const val ORIGIN = "https://it.mithril.app"
        /** Errori che indicano un video che il canale non permette di incorporare. */
        val NOT_EMBEDDABLE = setOf(101, 150)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
fun createYouTubeWebView(context: Context, firstId: String, state: YouTubePlayerState): WebView {
    val main = Handler(Looper.getMainLooper())
    return WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            // I link (logo YouTube, titolo) non devono navigare dentro la WebView.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
        }
        addJavascriptInterface(object {
            @JavascriptInterface fun onReady(d: Double) = main.post { state.ready = true; state.state = 5; if (d > 0) state.duration = d.toFloat() }
            @JavascriptInterface fun onState(s: Int) = main.post { state.state = s; if (s == 0) { state.endedCount++; state.onEnded?.invoke() } }
            @JavascriptInterface fun onError(e: Int) = main.post { state.error = e }
            @JavascriptInterface fun onTime(t: Double, d: Double) = main.post { state.position = t.toFloat(); if (d > 0) state.duration = d.toFloat() }
        }, "Mithril")
        state.web = this
        loadDataWithBaseURL(YouTubePlayerState.ORIGIN + "/", html(firstId), "text/html", "utf-8", null)
    }
}

private fun html(id: String) = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script>
var player=null, ready=false;
var tag=document.createElement('script'); tag.src='https://www.youtube.com/iframe_api'; document.head.appendChild(tag);
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
    playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,controls:1,fs:0,iv_load_policy:3,origin:'${YouTubePlayerState.ORIGIN}',widget_referrer:'${YouTubePlayerState.ORIGIN}'},
    events:{
      onReady:function(e){ready=true;Mithril.onReady(player.getDuration()||0);e.target.playVideo();},
      onStateChange:function(e){Mithril.onState(e.data);},
      onError:function(e){Mithril.onError(e.data);}
    }});
  setInterval(function(){ if(ready&&player&&player.getCurrentTime){ Mithril.onTime(player.getCurrentTime()||0, player.getDuration()||0); } }, 500);
}
function cmd(c,a){ if(!ready||!player) return;
  if(c=='play') player.playVideo(); else if(c=='pause') player.pauseVideo();
  else if(c=='seek') player.seekTo(a,true); else if(c=='load') player.loadVideoById(a); }
</script></body></html>
""".trimIndent()
