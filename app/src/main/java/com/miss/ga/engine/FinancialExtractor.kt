package com.miss.ga.engine

import java.util.regex.Pattern

data class BankCard(
    val raw: String,
    val number: String,
    val formatted: String,
    val bankName: String?
)

data class ShebaNumber(
    val raw: String,
    val iban: String,
    val formatted: String,
    val bankName: String?
)

data class FinancialDetails(
    val bankCard: BankCard? = null,
    val sheba: ShebaNumber? = null
)

object FinancialExtractor {

    private val BANK_CARD_PATTERN = Pattern.compile(
        "(?:(?:\\b|(?<=[^\\d]))(\\d{4}[-\\s\\u200c]?\\d{4}[-\\s\\u200c]?\\d{4}[-\\s\\u200c]?\\d{4})(?:\\b|(?=[^\\d])))"
    )

    private val SHEBA_PATTERN = Pattern.compile(
        "(?:\\b|(?<=[^a-zA-Z0-9]))(?:IR|ir)\\s*([0-9]{2}[-\\s\\u200c]?[0-9]{4}[-\\s\\u200c]?[0-9]{4}[-\\s\\u200c]?[0-9]{4}[-\\s\\u200c]?[0-9]{4}[-\\s\\u200c]?[0-9]{4}[-\\s\\u200c]?[0-9]{2})(?:\\b|(?=[^0-9]))",
        Pattern.CASE_INSENSITIVE
    )

    private val BANK_BINS = mapOf(
        "603799" to "بانک ملی",
        "589210" to "بانک سپه",
        "627648" to "بانک صادرات",
        "627961" to "بانک صنعت و معدن",
        "603770" to "بانک کشاورزی",
        "628023" to "بانک مسکن",
        "627760" to "پست بانک",
        "502908" to "بانک توسعه تعاون",
        "627412" to "بانک اقتصاد نوین",
        "622106" to "بانک پارسیان",
        "639194" to "بانک پارسیان",
        "627884" to "بانک پارسیان",
        "502229" to "بانک پاسارگاد",
        "639607" to "بانک پاسارگاد",
        "627488" to "بانک کارآفرین",
        "502910" to "بانک کارآفرین",
        "610433" to "بانک ملت",
        "991975" to "بانک ملت",
        "627353" to "بانک تجارت",
        "585983" to "بانک تجارت",
        "589463" to "بانک رفاه کارگران",
        "621986" to "بانک سامان",
        "823610" to "بلو بانک",
        "639346" to "بانک سینا",
        "504706" to "بانک شهر",
        "502806" to "بانک شهر",
        "502938" to "بانک دی",
        "606373" to "بانک قرض‌الحسنه مهر ایران",
        "606256" to "مؤسسه اعتباری ملل",
        "636214" to "بانک آینده",
        "505785" to "بانک ایران زمین",
        "505416" to "بانک گردشگری",
        "504172" to "بانک رسالت",
        "505801" to "مؤسسه کوثر",
        "636795" to "بانک مرکزی",
        "639599" to "بانک قوامین",
        "507677" to "مؤسسه نور",
        "636949" to "بانک حکمت ایرانیان",
        "627381" to "بانک انصار",
        "628157" to "مؤسسه اعتباری توسعه"
    )

    private val SHEBA_BANK_CODES = mapOf(
        "010" to "بانک مرکزی",
        "011" to "بانک صنعت و معدن",
        "012" to "بانک ملت",
        "013" to "بانک رفاه کارگران",
        "014" to "بانک مسکن",
        "015" to "بانک سپه",
        "016" to "بانک کشاورزی",
        "017" to "بانک ملی",
        "018" to "بانک تجارت",
        "019" to "بانک صادرات",
        "020" to "بانک توسعه صادرات",
        "021" to "پست بانک",
        "022" to "بانک توسعه تعاون",
        "051" to "بانک اقتصاد نوین",
        "053" to "بانک کارآفرین",
        "054" to "بانک پارسیان",
        "055" to "بانک اقتصاد نوین",
        "056" to "بانک سامان",
        "057" to "بانک پاسارگاد",
        "058" to "بانک سرمایه",
        "059" to "بانک سینا",
        "060" to "بانک مهر ایران",
        "061" to "بانک شهر",
        "062" to "بانک آینده",
        "064" to "بانک گردشگری",
        "065" to "بانک حکمت ایرانیان",
        "066" to "بانک دی",
        "069" to "بانک ایران زمین",
        "070" to "بانک قرض‌الحسنه رسالت",
        "078" to "بانک خاورمیانه"
    )

    /**
     * Extracts financial card and Sheba details from a message string.
     */
    fun extract(rawText: String?): FinancialDetails {
        if (rawText.isNullOrBlank()) return FinancialDetails()
        // Runs per chat bubble during composition: skip the regexes for the vast majority
        // of messages that can't contain a 16-digit card number.
        if (rawText.count { it.isDigit() } < 16) return FinancialDetails()
        val text = OtpExtractor.normalizeDigits(rawText)

        val card = extractBankCard(text)
        val sheba = extractSheba(text)

        return FinancialDetails(bankCard = card, sheba = sheba)
    }

    fun extractBankCard(text: String): BankCard? {
        val matcher = BANK_CARD_PATTERN.matcher(text)
        while (matcher.find()) {
            val candidateGroup = matcher.group(1) ?: continue
            val digits = candidateGroup.filter { it.isDigit() }
            if (digits.length == 16 && isValidLuhn(digits)) {
                val bin = digits.substring(0, 6)
                val bankName = BANK_BINS[bin]
                val formatted = "${digits.substring(0, 4)} ${digits.substring(4, 8)} ${digits.substring(8, 12)} ${digits.substring(12, 16)}"
                return BankCard(
                    raw = candidateGroup,
                    number = digits,
                    formatted = formatted,
                    bankName = bankName
                )
            }
        }
        return null
    }

    fun extractSheba(text: String): ShebaNumber? {
        val matcher = SHEBA_PATTERN.matcher(text)
        while (matcher.find()) {
            val digitsPart = matcher.group(1) ?: continue
            val digits = digitsPart.filter { it.isDigit() }
            if (digits.length == 24) {
                val fullIban = "IR$digits"
                if (isValidShebaChecksum(fullIban)) {
                    val bankCode = digits.substring(2, 5)
                    val bankName = SHEBA_BANK_CODES[bankCode]
                    val formatted = "IR${digits.substring(0, 2)} ${digits.substring(2, 6)} ${digits.substring(6, 10)} ${digits.substring(10, 14)} ${digits.substring(14, 18)} ${digits.substring(18, 22)} ${digits.substring(22, 24)}"
                    return ShebaNumber(
                        raw = matcher.group(0) ?: fullIban,
                        iban = fullIban,
                        formatted = formatted,
                        bankName = bankName
                    )
                }
            }
        }
        return null
    }

    /**
     * Standard Luhn (mod 10) algorithm.
     */
    fun isValidLuhn(number: String): Boolean {
        if (number.length != 16) return false
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            val digit = number[i].digitToIntOrNull() ?: return false
            var d = digit
            if (alternate) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    /**
     * ISO 7064 Modulo 97-10 check for IBAN (IR = country code).
     * Modulo result must equal 1.
     */
    fun isValidShebaChecksum(iban: String): Boolean {
        if (iban.length != 26) return false
        if (!iban.startsWith("IR", ignoreCase = true)) return false

        // Move first 4 characters (IR + 2 check digits) to the end.
        // IR -> I=18, R=27 -> "1827"
        val checkDigits = iban.substring(2, 4)
        val bban = iban.substring(4)
        val rearranged = bban + "1827" + checkDigits

        var remainder = 0
        for (ch in rearranged) {
            val digit = ch.digitToIntOrNull() ?: return false
            remainder = (remainder * 10 + digit) % 97
        }
        return remainder == 1
    }
}
