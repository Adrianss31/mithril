package it.mithril.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import it.mithril.app.BuildConfig
import it.mithril.app.data.Repo

@Serializable data class SignedRelease(val payload: String, val signature: String)

@Serializable data class ReleaseInfo(
    val versionName: String, val versionCode: Long, val minSdk: Int,
    val apkUrl: String, val size: Long, val sha256: String, val notes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: ReleaseInfo? = null) : UpdateState
}

private val json = Json { ignoreUnknownKeys = true }

/** Descrittore firmato: la chiave pubblica è incorporata nell'app, quella privata vive solo nei Secrets di GitHub. */
object UpdateVerifier {
    const val REPO_PATH = "/Adrianss31/mithril/releases/download/"

    fun verify(document: String, publicKey: String): ReleaseInfo {
        require(publicKey.isNotBlank()) { "Chiave di aggiornamento mancante in questa build." }
        require(document.toByteArray().size <= 64 * 1024) { "Descrittore troppo grande." }
        val envelope = json.decodeFromString<SignedRelease>(document)
        val data = Base64.getDecoder().decode(envelope.payload)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        val verifier = Signature.getInstance("SHA256withRSA").apply { initVerify(key); update(data) }
        require(verifier.verify(Base64.getDecoder().decode(envelope.signature))) { "Firma dell'aggiornamento non valida." }
        val release = json.decodeFromString<ReleaseInfo>(data.toString(Charsets.UTF_8))
        require(release.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.]+)?"))) { "Versione non valida." }
        require(release.versionCode > 0 && release.minSdk >= 26 && release.size in 1..150_000_000) { "Descrittore non valido." }
        require(release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Checksum non valido." }
        val uri = URI(release.apkUrl)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null) { "Indirizzo APK non valido." }
        require(uri.path.startsWith(REPO_PATH) && !uri.path.contains("..") && uri.query == null && uri.fragment == null) { "Indirizzo APK non valido." }
        return release
    }
}

/** Aggiornamenti in-app dalle GitHub Releases: controllo, download verificato e installazione. */
object Updates {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private const val AUTO_INTERVAL = 6 * 60 * 60 * 1000L

    private val ALLOWED_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    /** Controllo automatico all'avvio, al massimo ogni 6 ore e mai sulle build debug. */
    suspend fun autoCheck(context: Context) {
        if (BuildConfig.DEBUG) return
        val repo = Repo.get(context)
        val now = System.currentTimeMillis()
        if (now - repo.settings.now().lastUpdateCheck < AUTO_INTERVAL && _state.value !is UpdateState.Idle) return
        check(context, quiet = true)
    }

    suspend fun check(context: Context, quiet: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Checking) return
        if (!quiet) _state.value = UpdateState.Checking
        _state.value = try {
            require(!BuildConfig.DEBUG) { "Gli aggiornamenti funzionano solo sulla versione release." }
            val info = fetchManifest()
            Repo.get(context).settings.setLastUpdateCheck(System.currentTimeMillis())
            if (info.versionCode > BuildConfig.VERSION_CODE && info.minSdk <= Build.VERSION.SDK_INT) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: Exception) {
            if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "Controllo non riuscito.")
        }
    }

    suspend fun download(context: Context, info: ReleaseInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        _state.value = try {
            val file = downloadApk(context, info) { p -> _state.value = UpdateState.Downloading(info, p) }
            UpdateState.Ready(info, file)
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Download non riuscito.", info)
        }
    }

    /** Intent da avviare: impostazione "origini sconosciute" se manca il permesso, altrimenti l'installer. */
    fun installIntent(context: Context, file: File): Intent {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun connection(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            require(current.protocol == "https" && current.userInfo == null && current.port == -1) { "Indirizzo di aggiornamento non sicuro." }
            require(current.host in ALLOWED_HOSTS) { "Host di aggiornamento non autorizzato." }
            val c = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mithril/${BuildConfig.VERSION_NAME}")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: error("Reindirizzamento non valido.")
                c.disconnect(); current = URL(current, location)
            } else {
                if (code != 200) {
                    c.disconnect()
                    if (code == 404) error("Nessun aggiornamento pubblicato al momento.")
                    error("Server aggiornamenti non disponibile (HTTP $code).")
                }
                return c
            }
        }
        error("Troppi reindirizzamenti.")
    }

    private suspend fun fetchManifest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val c = connection(BuildConfig.UPDATE_MANIFEST_URL)
        val doc = try {
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf); if (n < 0) break
                    require(out.size() + n <= 64 * 1024) { "Descrittore troppo grande." }; out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally { c.disconnect() }
        UpdateVerifier.verify(doc, BuildConfig.UPDATE_PUBLIC_KEY)
    }

    private suspend fun downloadApk(context: Context, info: ReleaseInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(info.versionCode > BuildConfig.VERSION_CODE && info.minSdk <= Build.VERSION.SDK_INT) { "Versione non compatibile." }
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        require(folder.usableSpace > info.size + 20_000_000L) { "Spazio insufficiente per l'aggiornamento." }
        val file = File(folder, "update.apk")
        val partial = File(folder, "update.part")
        file.delete(); partial.delete()
        try {
            val c = connection(info.apkUrl)
            try {
                c.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buf = ByteArray(128 * 1024); var count = 0L; var lastReport = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            count += n; require(count <= info.size) { "Dimensione del pacchetto non valida." }
                            output.write(buf, 0, n)
                            if (count - lastReport > 256 * 1024) { lastReport = count; progress(count.toFloat() / info.size) }
                        }
                        output.fd.sync()
                    }
                }
            } finally { c.disconnect() }
            verifyApk(context, partial, info)
            require(partial.renameTo(file)) { "Impossibile salvare l'aggiornamento." }
            file
        } catch (e: Exception) { partial.delete(); file.delete(); throw e }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, file: File, info: ReleaseInfo) {
        require(file.length() == info.size) { "Aggiornamento incompleto." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(128 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        require(digest.digest().joinToString("") { "%02x".format(it) }.equals(info.sha256, true)) { "Il pacchetto non supera la verifica di integrità." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.path, flags)) { "APK non valido." }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "Il pacchetto appartiene a un'altra app." }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(code == info.versionCode && code > BuildConfig.VERSION_CODE) { "Versione del pacchetto non valida." }
        val incoming = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        require(!incoming.isNullOrEmpty() && !current.isNullOrEmpty() && incoming.map { it.toCharsString() }.toSet() == current.map { it.toCharsString() }.toSet()) {
            "Firma APK diversa da quella dell'app installata."
        }
    }
}
