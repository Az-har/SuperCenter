package com.eipna.centsation.sms

import com.eipna.centsation.compat.pennywise.PersonalizedProfileDefaults
import com.eipna.centsation.sms.parser.BankSmsParserFactory
import com.eipna.centsation.sms.parser.CityUnionBankParser
import com.eipna.centsation.sms.parser.GenericIndianBankParser
import com.eipna.centsation.sms.parser.ICICIBankParser
import com.eipna.centsation.sms.parser.MerchantCleaner
import com.eipna.centsation.sms.parser.YesBankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsParserTest {

    @Test
    fun testICICIDebitUPI() {
        val parser = ICICIBankParser()
        val sms = "Acct XX1234 is debited for Rs 450.00 on 04-Nov-26. Info: UPI/529482910482/BP Anna nagar. Avl Bal Rs 15,200.00"
        val parsed = parser.parse("VK-ICICIB", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isExpense)
        assertEquals("1234", parsed.accountLast4)
        assertEquals(15200.0, parsed.balanceAfter ?: 0.0, 0.001)
        assertEquals("BP Anna nagar", parsed.rawMerchant)

        // Verify hyper-personalized alias and category
        val resolvedAlias = PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES[parsed.rawMerchant]
        assertEquals("petrol bunk G.W", resolvedAlias)
        val resolvedCategory = PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS[resolvedAlias]
        assertEquals("petrol", resolvedCategory)
    }

    @Test
    fun testICICICardSpend() {
        val parser = ICICIBankParser()
        val sms = "INR 1,250.00 spent using ICICI Bank Card XX9012 on 04-Nov-26 at Fridays. Avl Bal Rs 45,000.00"
        val parsed = parser.parse("ICICIBANK", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(1250.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isExpense)
        assertEquals("9012", parsed.accountLast4)
        assertEquals("Fridays", parsed.rawMerchant)

        val alias = PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES[parsed.rawMerchant]
        assertEquals("Friday's fast food", alias)
        val category = PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS[alias]
        assertEquals("Food & Dining", category)
    }

    @Test
    fun testICICICredit() {
        val parser = ICICIBankParser()
        val sms = "Acct XX5521 is credited with Rs 25,000.00 on 01-Nov-26. Info: NEFT/Salary. Avl Bal Rs 65,000.00"
        val parsed = parser.parse("VK-ICICIB", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(25000.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isExpense)
        assertEquals("5521", parsed.accountLast4)
    }

    @Test
    fun testCityUnionBankDebit() {
        val parser = CityUnionBankParser()
        val sms = "Your a/c no. XXXXXXXXXXX5501 is debited for Rs.850.00 on 04-11-2026 and credited to a/c no. YYYYYYYYYYY4577 (UPI Ref no 998877665544)"
        val parsed = parser.parse("VM-CUBANK", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(850.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isExpense)
        assertEquals("5501", parsed.accountLast4)
        assertEquals("998877665544", parsed.reference)
    }

    @Test
    fun testCityUnionBankNEFT() {
        val parser = CityUnionBankParser()
        val sms = "Savings No XXXXXXXXXXX4321 credited with INR 10,000.00 towards BY NEFT TRF:RAJALAKSHMI ENG: on 02-NOV-2026. Avl Bal 12,500.00"
        val parsed = parser.parse("JK-CUBLTD", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(10000.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isExpense)
        assertEquals("4321", parsed.accountLast4)
        assertEquals(12500.0, parsed.balanceAfter ?: 0.0, 0.001)
    }

    @Test
    fun testYesBankUPI() {
        val parser = YesBankParser()
        val sms = "Paid Rs 350.00 from A/c XX8899 to 9344002076ptyes on 04-11-2026. UPI Ref 11223344"
        val parsed = parser.parse("YESB", sms, 1000L)

        assertNotNull(parsed)
        assertEquals(350.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isExpense)
        assertEquals("8899", parsed.accountLast4)
        assertEquals("9344002076ptyes", parsed.rawMerchant)

        val alias = PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES[parsed.rawMerchant]
        assertEquals("Afdhal", alias)
        val category = PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS[alias]
        assertEquals("Family spend", category)
    }

    @Test
    fun testParserFactoryAutoRouting() {
        val iciciSms = "Acct XX1111 debited for Rs 120.00 at HungerBox. Avl Bal Rs 5,000.00"
        val parsed = BankSmsParserFactory.parse("VK-ICICIB", iciciSms)

        assertNotNull(parsed)
        assertEquals("ICICI Bank", parsed!!.bankName)
        assertEquals(120.0, parsed.amount, 0.001)

        val alias = PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES[parsed.rawMerchant]
        assertEquals("getham ozone", alias)
        val category = PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS[alias]
        assertEquals("Food & Dining", category)
    }

    @Test
    fun testPersonalizedProfileMappings() {
        // Test Mom's Bills
        assertEquals("Mummy ka amount", PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES["Alif Laila A"])
        assertEquals("Mom's Bills", PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS["Mummy ka amount"])

        // Test Groceries
        assertEquals("Kalai Super Market", PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES["Kalai Super Mar"])
        assertEquals("Groceries", PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS["Kalai Super Market"])

        // Test Investments
        assertEquals("Sandha", PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES["9677236663ptyes"])
        assertEquals("Investments", PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS["Sandha"])

        // Test Keyword fallback
        assertEquals("petrol", PersonalizedProfileDefaults.inferCategoryByKeyword("Annai Velankanni bunk"))
        assertEquals("Food & Dining", PersonalizedProfileDefaults.inferCategoryByKeyword("Tasty Burger Point"))
    }

    @Test
    fun testFastRejectOtpAndSpam() {
        val parser = ICICIBankParser()
        val otpSms = "Do not share this OTP. 849201 is your secret code to login to ICICI Bank NetBanking."
        assertNull(parser.parse("VK-ICICIB", otpSms, 1000L))

        val spamSms = "Congratulations! You have a pre-approved personal loan of Rs 5,00,000. Apply now."
        assertNull(parser.parse("VK-ICICIB", spamSms, 1000L))

        val ccBillPayment = "Payment of Rs 12,000.00 received on your ICICI Bank Credit Card ending 4001."
        assertNull(parser.parse("VK-ICICIB", ccBillPayment, 1000L))
    }

    @Test
    fun testMerchantCleaner() {
        assertEquals("Swiggy", MerchantCleaner.clean("UPI/Swiggy Pvt Ltd"))
        assertEquals("Zomato", MerchantCleaner.clean("Zomato Ltd (Ref: 12345)"))
        assertEquals("BP Anna nagar", MerchantCleaner.clean("BP Anna nagar on 04/11"))
        assertEquals("Friday's", MerchantCleaner.clean("Friday's -"))
        assertFalse(MerchantCleaner.isValid("TO BLOCK UPI"))
        assertFalse(MerchantCleaner.isValid("VIA"))
        assertTrue(MerchantCleaner.isValid("HungerBox"))
    }

    @Test
    fun testGooglePayNotificationParsing() {
        val parser = GenericIndianBankParser()
        val notif = "Google Pay: Paid ₹150.00 to Friday's fast food using ICICI Bank account"
        val parsed = parser.parse("Google Pay", notif, 1000L)

        assertNotNull(parsed)
        assertEquals(150.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isExpense)
        assertEquals("Friday's fast food", parsed.rawMerchant)
    }
}
