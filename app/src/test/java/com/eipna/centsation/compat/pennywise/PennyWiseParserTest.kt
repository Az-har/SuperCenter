package com.eipna.centsation.compat.pennywise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PennyWiseParserTest {

    private val samplePennyWiseJson = """
    {
      "_format": "PennyWise Backup v1.3",
      "metadata": {
        "app_version": "2.1.0",
        "database_version": 66
      },
      "database": {
        "accounts": [
          {
            "id": 1,
            "bankName": "HDFC",
            "accountLast4": "4321",
            "balance": 85000.50,
            "accountType": "SAVINGS",
            "isCreditCard": false,
            "alias": "Primary Salary Account"
          },
          {
            "id": 2,
            "bankName": "ICICI",
            "accountLast4": "9876",
            "balance": 15000.00,
            "accountType": "CREDIT",
            "isCreditCard": true,
            "alias": "Amazon Pay ICICI Card"
          }
        ],
        "merchant_aliases": [
          {
            "merchantName": "INF*SWIGGY*4938",
            "alias": "Swiggy"
          },
          {
            "merchantName": "VPA-RAHUL@OKAXIS",
            "alias": "Rahul Sharma"
          }
        ],
        "merchant_mappings": [
          {
            "merchantName": "Swiggy",
            "category": "Food & Dining"
          }
        ],
        "transactions": [
          {
            "id": 101,
            "amount": 450.00,
            "merchantName": "INF*SWIGGY*4938",
            "category": "General",
            "transactionType": "EXPENSE",
            "dateTime": "2026-11-01T19:30:00",
            "bankName": "HDFC",
            "accountNumber": "4321",
            "smsBody": "Rs 450 spent on HDFC card 4321 at INF*SWIGGY*4938"
          },
          {
            "id": 102,
            "amount": 120000.00,
            "merchantName": "EMPLOYER SALARY",
            "category": "Salary",
            "transactionType": "INCOME",
            "dateTime": "2026-11-01T09:00:00",
            "bankName": "HDFC",
            "accountNumber": "4321"
          }
        ],
        "loans": [
          {
            "id": 1,
            "personName": "Rahul Sharma",
            "initialAmount": 5000.00,
            "remainingAmount": 2500.00,
            "loanType": "LENT",
            "notes": "Trip flight tickets share",
            "dueDate": "2026-11-15T00:00:00"
          }
        ]
      }
    }
    """.trimIndent()

    @Test
    fun parse_extractsAccountsWithAliases() {
        val result = PennyWiseParser.parse(samplePennyWiseJson)
        assertEquals(2, result.accountsImported)
        val hdfc = result.accounts.first { it.bankName == "HDFC" }
        assertEquals("Primary Salary Account", hdfc.alias)
        assertEquals("Primary Salary Account", hdfc.displayLabel)

        val icici = result.accounts.first { it.bankName == "ICICI" }
        assertEquals("Amazon Pay ICICI Card", icici.alias)
        assertTrue(icici.isCreditCard)
    }

    @Test
    fun parse_extractsMerchantAliasesAndResolvesThemInTransactions() {
        val pkg = PennyWiseParser.parsePackage(samplePennyWiseJson)
        assertEquals(2, pkg.merchantAliases.size)

        val swiggyTx = pkg.transactions.first { it.id == 101L }
        assertEquals("INF*SWIGGY*4938", swiggyTx.rawMerchant)
        assertEquals("Swiggy", swiggyTx.resolvedMerchant)
        assertEquals("Food & Dining", swiggyTx.category)
    }

    @Test
    fun parse_extractsLoansAndCalculatesDirection() {
        val pkg = PennyWiseParser.parsePackage(samplePennyWiseJson)
        assertEquals(1, pkg.loans.size)
        val loan = pkg.loans.first()
        assertEquals("Rahul Sharma", loan.personName)
        assertEquals(5000.00, loan.initialAmount, 0.01)
        assertEquals(2500.00, loan.remainingAmount, 0.01)
        assertTrue(loan.isLent)
    }

    @Test
    fun parse_generatesMarkdownProfileForSharing() {
        val result = PennyWiseParser.parse(samplePennyWiseJson)
        assertNotNull(result.markdownSummary)
        assertTrue(result.markdownSummary.contains("Primary Salary Account"))
        assertTrue(result.markdownSummary.contains("Amazon Pay ICICI Card"))
        assertTrue(result.markdownSummary.contains("Swiggy"))
        assertTrue(result.markdownSummary.contains("Rahul Sharma"))
    }
}
