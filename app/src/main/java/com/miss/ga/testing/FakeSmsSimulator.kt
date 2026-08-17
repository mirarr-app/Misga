package com.miss.ga.testing

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.engine.NotificationHelper
import com.miss.ga.engine.SmsFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FakeSmsScenario(
    val title: String,
    val sender: String,
    val body: String,
    val expectedAction: FilterAction,
    val description: String
)

data class SimulationResult(
    val sender: String,
    val body: String,
    val action: FilterAction,
    val matchedRule: String?,
    val notificationSent: Boolean,
    val messageId: Long,
    val threadId: Long
)

object FakeSmsSimulator {

    fun getPredefinedScenarios(): List<FakeSmsScenario> = listOf(
        FakeSmsScenario(
            title = "Commercial 1000 Shortcode Spam",
            sender = "10009988",
            body = "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت دریافت روی لینک کلیک کنید.",
            expectedAction = FilterAction.SPAM,
            description = "Iranian commercial mass-SMS prefix (1000) with promo text"
        ),
        FakeSmsScenario(
            title = "Monthly Discount (تخفیف ماهیانه)",
            sender = "09351112233",
            body = "بسته تخفیف ماهیانه اینترنت همراه با ۲۰ گیگابایت هدیه فعال شد. پیشنهاد ماهانه را از دست ندهید.",
            expectedAction = FilterAction.SPAM,
            description = "Promotional keyword matching 'تخفیف ماهیانه / ماهانه'"
        ),
        FakeSmsScenario(
            title = "Opt-out Subscription (لغو ۱۱)",
            sender = "+989120000000",
            body = "برنده ۱۰۰ میلیون تومان وجه نقد شدید! برای انصراف و عدم دریافت پیامک عدد ۱۱ را به سامانه ارسال کنید.",
            expectedAction = FilterAction.SPAM,
            description = "Matches Iranian promotional subscription opt-out (لغو ۱۱ / ارسال عدد)"
        ),
        FakeSmsScenario(
            title = "Phishing Link (سامانه ثنا)",
            sender = "+989901234567",
            body = "ابلاغیه قضایی: پرونده شکایت علیه شما در سامانه ثنا ثبت شد. جهت مشاهده پیوست: https://adliran-sana.com/view.apk",
            expectedAction = FilterAction.SPAM,
            description = "Malicious phishing scam mimicking official court notices"
        ),
        FakeSmsScenario(
            title = "Unlicensed Loan Scam (وام فوری)",
            sender = "02188776655",
            body = "وام فوری یکروزه بدون ضامن و بدون چک تا سقف ۵۰۰ میلیون تومان. پرداخت فوری وام در محل.",
            expectedAction = FilterAction.SPAM,
            description = "Landline 021 commercial SMS advertising instant financial loans"
        ),
        FakeSmsScenario(
            title = "Legitimate Personal Family Chat",
            sender = "+989123456789",
            body = "سلام علی جان، امشب ساعت ۸ بیا دور هم باشیم. مادر هم منتظره.",
            expectedAction = FilterAction.NORMAL,
            description = "Authentic personal Persian message — MUST alert with sound and heads-up"
        ),
        FakeSmsScenario(
            title = "Work Meeting Notification",
            sender = "+989198765432",
            body = "جلسه بررسی پروژه فردا ساعت ۱۰ صبح در اتاق کنفرانس برگزار می‌شود. لطفا گزارشات را آماده کنید.",
            expectedAction = FilterAction.NORMAL,
            description = "Normal professional SMS — MUST notify with full alert"
        ),
        FakeSmsScenario(
            title = "Banking OTP / Verification Code",
            sender = "BankMellat",
            body = "رمز یکبار مصرف شما: ۷۸۴۵۱۲\nمبلغ: ۲۵۰,۰۰۰ ریال\nخرید اینترنتی\nبانک ملت",
            expectedAction = FilterAction.NORMAL,
            description = "Transactional banking two-factor SMS"
        )
    )

    suspend fun simulateIncomingSms(
        context: Context,
        sender: String,
        body: String
    ): SimulationResult = withContext(Dispatchers.IO) {
        val dbHelper = MisgaDatabaseHelper.getInstance(context)
        val filterEngine = SmsFilterEngine(dbHelper)
        val notificationHelper = NotificationHelper(context)
        val repository = SmsRepository(context)

        val normalizedSender = sender.trim()
        val now = System.currentTimeMillis()

        // 1. Run Filter Engine Evaluation
        val filterResult = filterEngine.evaluateMessage(normalizedSender, body)

        // 2. Insert into Android Telephony Content Provider
        var messageId = now
        var threadId = 0L
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, normalizedSender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val insertedUri: Uri? = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            if (insertedUri != null) {
                messageId = ContentUris.parseId(insertedUri)
            }
            threadId = repository.getOrCreateThreadId(normalizedSender)
        } catch (e: Exception) {
            Log.e("FakeSmsSimulator", "Could not write to Telephony provider (app might not be default SMS app yet)", e)
            threadId = (normalizedSender.hashCode().toLong() and 0x7FFFFFFF)
        }

        // 3. Save Spam / Action Metadata in Local Database
        if (filterResult.action == FilterAction.SPAM) {
            dbHelper.markMessageSpam(
                messageId = messageId,
                address = normalizedSender,
                matchedRuleName = filterResult.matchedRuleName,
                action = filterResult.action
            )
        }

        // 4. Trigger Notification if Action is not SPAM
        val contactName = repository.resolveContactName(normalizedSender)
        val willNotify = filterResult.action != FilterAction.SPAM

        if (willNotify) {
            notificationHelper.showSmsNotification(
                threadId = threadId,
                sender = normalizedSender,
                contactName = contactName,
                body = body,
                action = filterResult.action,
                messageId = messageId
            )
        }

        SimulationResult(
            sender = normalizedSender,
            body = body,
            action = filterResult.action,
            matchedRule = filterResult.matchedRuleName,
            notificationSent = willNotify,
            messageId = messageId,
            threadId = threadId
        )
    }

    suspend fun clearSimulatedTestData(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val scenarios = getPredefinedScenarios().map { it.sender }
            for (sender in scenarios) {
                val deleted = context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} = ?",
                    arrayOf(sender)
                )
                count += deleted
            }
        } catch (e: Exception) {
            Log.e("FakeSmsSimulator", "Error clearing test data", e)
        }
        count
    }
}
