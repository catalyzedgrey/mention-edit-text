package com.example.android.mentiontext.presentation

import com.example.android.mentiontext.data.Mention
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.*
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import timber.log.Timber
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.res.ResourcesCompat
import com.example.android.mentiontext.R
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList


const val POST_TEXT_EMS = 2200

class MentionEditText : AppCompatEditText {
    private var mPattern: Pattern? = null
    private var mAction: Runnable? = null
    private var mMentionTextColor = 0
    private var mIsSelected = false
    private var mLastSelectedRange: Range? = null
    private var mRangeArrayList: MutableList<Range?>? = null
    private var mOnMentionInputListener: OnMentionInputListener? = null
    private var isMentioned = false
    private var position = 0
    private var mentionList: ArrayList<Mention> = ArrayList<Mention>()
    private val hashtagPattern =
        Pattern.compile("(#[A-Za-z0-9-_\\u0600-\\u06FF]+)(?:#[A-Za-z0-9-_\\u0600-\\u06FF]+)*\\b")
    private val hashtagColor = Color.parseColor("#FF3F3FD1")


    private var usersMap: MutableMap<Pattern, Mention>? = mutableStateMapOf()

    private lateinit var semiBold: Typeface
    private var from = 0
    private var to = 0
    private var mLayout: Layout? = null

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        init()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        return HackInputConnection(super.onCreateInputConnection(outAttrs), true, this)
    }

    override fun setText(text: CharSequence, type: BufferType) {
        super.setText(text, type)
        if (mAction == null) {
            mAction = Runnable {
                try {
                    this@MentionEditText.setSelection(
                        if (position > 0) position else Objects.requireNonNull(getText())?.length
                            ?: 0
                    )
                    position = 0
                } catch (e: IndexOutOfBoundsException) {
                    e.printStackTrace()
                }

            }
        }
        postDelayed(mAction, 100)
    }

    override fun onTextChanged(
        text: CharSequence,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        colorMentionString()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        try {
            if (mLastSelectedRange == null || !mLastSelectedRange!!.isEqual(selStart, selEnd)) {
                val closestRange = getRangeOfClosestMentionString(selStart, selEnd)
                if (closestRange != null && closestRange.to == selEnd) {
                    mIsSelected = false
                }
                val nearbyRange = getRangeOfNearbyMentionString(selStart, selEnd)
                if (nearbyRange != null) {
                    if (selStart == selEnd) {
                        this.setSelection(nearbyRange.getAnchorPosition(selStart))
                    } else {
                        if (selEnd < nearbyRange.to) {
                            this.setSelection(selStart, nearbyRange.to)
                        }
                        if (selStart > nearbyRange.from) {
                            this.setSelection(nearbyRange.from, selEnd)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("IndexOutOfBoundsException", "${e.message}")
        }
    }


    fun setIsMentioned(isMentioned: Boolean) {
        this.isMentioned = isMentioned
    }

    fun setMentionTextColor(color: Int) {
        mMentionTextColor = color
    }

    fun getMentionList(): ArrayList<Mention> {
        return mentionList
    }

    val currentCursorLine: Float
        get() {
            val pos = selectionStart
            if (layout != null) {
                val line = layout!!.getLineForOffset(pos)
                return line * 19f
            }
            return lineCount.toFloat()
        }

    override fun onPreDraw(): Boolean {
        mLayout = layout
        if (layout != null) {
            viewTreeObserver.removeOnPreDrawListener(this)
        }
        return true
    }

    fun setOnMentionInputListener(onMentionInputListener: OnMentionInputListener) {
        this.mOnMentionInputListener = onMentionInputListener
    }

    fun setCursorPosition(position: Int) {
        this.position = position
    }

    private fun init() {

        typeface = ResourcesCompat.getFont(
            context,
            R.font.merged_regular,
        )

        semiBold = ResourcesCompat.getFont(
            context,
            R.font.merged_medium,
        )!!
        viewTreeObserver.addOnPreDrawListener(this)
        mRangeArrayList = ArrayList<Range?>(5)
        mPattern = Pattern.compile("@[\\u4e00-\\u9fa5\\w\\-]+")
        mMentionTextColor = -65536
        addTextChangedListener(MentionTextWatcher())
        filters = arrayOf(filter, InputFilter.LengthFilter(POST_TEXT_EMS))
    }


    private fun colorMentionString() {
        mIsSelected = false
        if (mRangeArrayList != null) {
            mRangeArrayList!!.clear()
        }
        val spannableText = this.text
        if (spannableText != null && !TextUtils.isEmpty(spannableText.toString())) {
            val oldSpans = spannableText.getSpans(
                0, spannableText.length,
                ForegroundColorSpan::class.java
            ) as Array<ForegroundColorSpan?>
            val var4 = oldSpans.size
            for (var5 in 0 until var4) {
                val oldSpan = oldSpans[var5]
                spannableText.removeSpan(oldSpan)
            }

            val text = spannableText.toString()

            for (pattern in usersMap?.keys!!) {
                for (user in getMentionList()) {
                    val name = user.getFullName()
                    val matcher = pattern.matcher(name)
                    if (matcher.find()) {
                        var start = -1
                        while (text.indexOf(name, start + 1).also { start = it } != -1) {
                            val end = start + name.length
                            if (end == -1) continue
                            //change color for mentioned span
                            spannableText?.setSpan(
                                ForegroundColorSpan(mMentionTextColor),
                                start,
                                end,
                                33
                            )

                            //change font for mentioned span
                            spannableText?.setSpan(
                                StyleSpan(semiBold.style),
                                start,
                                end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            mRangeArrayList!!.add(Range(start, end))
                        }
                    }
                }
            }
            val matcher = hashtagPattern.matcher(text)
            while (matcher.find()) {
                spannableText?.setSpan(
                    ForegroundColorSpan(hashtagColor),
                    matcher.start(),
                    matcher.end(),
                    0
                )

            }

        }
    }

    fun colorEditedMentionString() {
        mIsSelected = false
        if (mRangeArrayList != null) {
            mRangeArrayList!!.clear()
        }
        val spannableText = this.text
        if (spannableText != null && !TextUtils.isEmpty(spannableText.toString())) {
            val oldSpans = spannableText.getSpans(
                0, spannableText.length,
                ForegroundColorSpan::class.java
            ) as Array<ForegroundColorSpan?>
            val var4 = oldSpans.size
            for (var5 in 0 until var4) {
                val oldSpan = oldSpans[var5]
                spannableText.removeSpan(oldSpan)
            }

            val text = spannableText.toString()

            for (pattern in usersMap?.keys!!) {
                val name = usersMap!![pattern]?.getFullName()
                name?.let {
                    val matcher = pattern.matcher(name)
                    if (matcher.find()) {
                        var start = -1
                        while (text.indexOf(name, start + 1).also { start = it } != -1) {
                            val end = start + name.length
                            if (end == -1) continue
                            //change color for mentioned span
                            spannableText?.setSpan(
                                ForegroundColorSpan(mMentionTextColor),
                                start,
                                end,
                                33
                            )

                            //change font for mentioned span
                            spannableText?.setSpan(
                                StyleSpan(semiBold.style),
                                start,
                                end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            mRangeArrayList!!.add(Range(start, end))
                        }
                    }
                }
            }
            val matcher = hashtagPattern.matcher(text)
            while (matcher.find()) {
                spannableText?.setSpan(
                    ForegroundColorSpan(hashtagColor),
                    matcher.start(),
                    matcher.end(),
                    0
                )

            }

        }
    }


    private fun getRangeOfClosestMentionString(selStart: Int, selEnd: Int): Range? {
        return if (mRangeArrayList == null) {
            null
        } else {
            val var3: Iterator<*> = mRangeArrayList!!.iterator()
            var range: Range
            do {
                if (!var3.hasNext()) {
                    return null
                }
                range = var3.next() as Range
            } while (!range.contains(selStart, selEnd))
            range
        }
    }

    private fun getRangeOfNearbyMentionString(selStart: Int, selEnd: Int): Range? {
        return if (mRangeArrayList == null) {
            null
        } else {
            val var3: Iterator<*> = mRangeArrayList!!.iterator()
            var range: Range
            do {
                if (!var3.hasNext()) {
                    return null
                }
                range = var3.next() as Range
            } while (!range.isWrappedBy(selStart, selEnd))
            range
        }
    }

    //    public void setMentionName(ArrayList<String> mentionName) {
    //        this.mentionName = mentionName;
    //    }
    interface OnMentionInputListener {
        fun onMentionCharacterInput(isMention: Boolean)
    }

    private inner class Range(var from: Int, var to: Int) {
        fun isWrappedBy(start: Int, end: Int): Boolean {
            return start > this.from && start < this.to || end > this.from && end < this.to
        }

        fun contains(start: Int, end: Int): Boolean {
            return this.from <= start && this.to >= end
        }

        fun isEqual(start: Int, end: Int): Boolean {
            return this.from == start && this.to == end || this.from == end && this.to == start
        }

        fun getAnchorPosition(value: Int): Int {
            return if (value - this.from - (this.to - value) >= 0) this.to else this.from
        }
    }

    private inner class HackInputConnection(
        target: InputConnection?,
        mutable: Boolean,
        editText: MentionEditText
    ) :
        InputConnectionWrapper(target, mutable) {
        private val editText: EditText
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            return if (event.action == 0 && event.keyCode == 67) {
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                val closestRange = getRangeOfClosestMentionString(selectionStart, selectionEnd)
                if (closestRange == null) {
                    mIsSelected = false
                    super.sendKeyEvent(event)
                } else if (!mIsSelected && selectionStart != closestRange.from) {
                    mIsSelected = true
                    mLastSelectedRange = closestRange
                    from = closestRange.from
                    to = closestRange.to
                    this.setSelection(closestRange.to, closestRange.from)
                    true
                } else {
                    mIsSelected = false
                    super.sendKeyEvent(event)
                }
            } else {
                super.sendKeyEvent(event)
            }
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            return if (beforeLength == 1 && afterLength == 0) {
                sendKeyEvent(KeyEvent(0, 67)) && sendKeyEvent(KeyEvent(1, 67))
            } else {
                deleteFromPatter()
                super.deleteSurroundingText(beforeLength, afterLength)
            }
        }

        init {
            this.editText = editText
        }
    }

    private fun deleteFromPatter() {
        try {
            val test = Objects.requireNonNull(text).toString().substring(from, to)
            for (userMp in usersMap?.keys!!) {
                if (userMp.matcher(test).find())
                    usersMap?.remove(userMp)
            }

            from = 0
            to = 0
        } catch (e: java.lang.Exception) {
            Timber.e("deleteFromPattern ${e.stackTraceToString()}")
        }
    }

    private inner class MentionTextWatcher : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(charSequence: CharSequence, index: Int, i1: Int, count: Int) {

            val list = charSequence.toString().split("((?<=[\\s\\n])|(?=[\\s\\n]))".toRegex())
            val topLevelCondition =
                list[list.lastIndex].startsWith("@") || list[list.lastIndex].startsWith("\n@")

            if (count == 1) {
                val mentionChar = charSequence.toString()[index]
                if (mOnMentionInputListener != null) {
                    val x = true//charSequence[charSequence.lastIndex] != ' '
                    val y = '@' == mentionChar
                    val z = (charSequence.indexOf("@ ") == -1)
                    val exp = x && topLevelCondition && (y || z)
                    mOnMentionInputListener!!.onMentionCharacterInput(
                        exp
//                        '@' == mentionChar || (isAfterMention.isNotEmpty() && charSequence.indexOf("@ ") == -1)
                    )
                }
            } else {

                mOnMentionInputListener?.onMentionCharacterInput(
                    topLevelCondition
                            && (charSequence[charSequence.lastIndex] == '@'
                            || charSequence.indexOf("@ ") == -1)
                )

            }
        }

        override fun afterTextChanged(editable: Editable) {
        }
    }

    fun setPatterns(usersMap: MutableMap<Pattern, Mention>?) {
        this.usersMap = usersMap
    }

    fun showKeyboard() {
        this.requestFocus()
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private var filter =
        InputFilter { _, start, end, _, _, _ ->
            for (i in start until end) {
                if (end == POST_TEXT_EMS) {
                    return@InputFilter text
                }
            }
            null
        }
}