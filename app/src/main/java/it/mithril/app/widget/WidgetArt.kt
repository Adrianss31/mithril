package it.mithril.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import it.mithril.app.R

/**
 * Forme che Glance non sa disegnare (anelli, icone Material Symbols). Sono maschere bianche:
 * il widget le colora con `ColorFilter.tint` e i colori chiaro/scuro, così seguono il tema da sole.
 */
object WidgetArt {
    private const val RING_PX = 256

    /** Icona Material Symbols Rounded disegnata dal font dell'app (ligatura). */
    fun glyph(context: Context, name: String, filled: Boolean, px: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val tf = ResourcesCompat.getFont(context, if (filled) R.font.symbols_fill1 else R.font.symbols_fill0)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = px.toFloat(); color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val fm = p.fontMetrics
        Canvas(bmp).drawText(name, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p)
        return bmp
    }

    /** Anello pieno (traccia) o arco di avanzamento da ore 12 in senso orario, estremi arrotondati. Diametro 64 su 78, tratto 6. */
    fun ring(fraction: Float, full: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(RING_PX, RING_PX, Bitmap.Config.ARGB_8888)
        val unit = RING_PX / 78f
        val stroke = 6f * unit
        val r = 32f * unit - stroke / 2
        val c = RING_PX / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
        val rect = RectF(c - r, c - r, c + r, c + r)
        if (full) Canvas(bmp).drawCircle(c, c, r, p)
        else if (fraction > 0f) Canvas(bmp).drawArc(rect, -90f, 360f * fraction.coerceAtMost(1f), false, p)
        return bmp
    }

    /** Bordo tratteggiato 1,5 del cerchio 78 (stato "senza permesso"). */
    fun dashedCircle(): Bitmap {
        val bmp = Bitmap.createBitmap(RING_PX, RING_PX, Bitmap.Config.ARGB_8888)
        val unit = RING_PX / 78f
        val w = 1.5f * unit
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w; color = Color.WHITE
            pathEffect = DashPathEffect(floatArrayOf(5f * unit, 4f * unit), 0f)
        }
        Canvas(bmp).drawCircle(RING_PX / 2f, RING_PX / 2f, RING_PX / 2f - w, p)
        return bmp
    }

    /** Cerchio pieno (per badge e pallini, perché `cornerRadius` di Glance funziona solo da Android 12). */
    fun disc(): Bitmap {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawCircle(48f, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bmp
    }
}
