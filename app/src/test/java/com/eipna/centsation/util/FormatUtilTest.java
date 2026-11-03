package com.eipna.centsation.util;

import org.junit.Assert;
import org.junit.Test;

public class FormatUtilTest {

    @Test
    public void testIndianNumberFormatting() {
        Assert.assertEquals("0", FormatUtil.formatIndianSystem(0.0));
        Assert.assertEquals("99", FormatUtil.formatIndianSystem(99.0));
        Assert.assertEquals("999", FormatUtil.formatIndianSystem(999.0));
        Assert.assertEquals("1,000", FormatUtil.formatIndianSystem(1000.0));
        Assert.assertEquals("10,000", FormatUtil.formatIndianSystem(10000.0));
        Assert.assertEquals("1,00,000", FormatUtil.formatIndianSystem(100000.0));
        Assert.assertEquals("10,00,000", FormatUtil.formatIndianSystem(1000000.0));
        Assert.assertEquals("1,00,00,000", FormatUtil.formatIndianSystem(10000000.0));
        Assert.assertEquals("12,34,56,789", FormatUtil.formatIndianSystem(123456789.0));
    }

    @Test
    public void testIndianNumberFormattingWithDecimals() {
        Assert.assertEquals("1,00,000.50", FormatUtil.formatIndianSystem(100000.5));
        Assert.assertEquals("1,23,456.78", FormatUtil.formatIndianSystem(123456.78));
    }

    @Test
    public void testIndianNumberFormattingNegative() {
        Assert.assertEquals("-1,00,000", FormatUtil.formatIndianSystem(-100000.0));
        Assert.assertEquals("-5,000.50", FormatUtil.formatIndianSystem(-5000.5));
    }

    @Test
    public void testWesternNumberFormatting() {
        Assert.assertEquals("100,000", FormatUtil.formatWesternSystem(100000.0));
        Assert.assertEquals("1,000,000", FormatUtil.formatWesternSystem(1000000.0));
        Assert.assertEquals("-1,000,000", FormatUtil.formatWesternSystem(-1000000.0));
    }

    @Test
    public void testCurrencyBasedFormattingAuto() {
        // INR should format with Indian commas
        Assert.assertEquals("1,00,000", FormatUtil.formatAmount(100000.0, "INR"));
        // USD should format with Western commas
        Assert.assertEquals("100,000", FormatUtil.formatAmount(100000.0, "USD"));
        // EUR should format with Western commas
        Assert.assertEquals("1,000,000", FormatUtil.formatAmount(1000000.0, "EUR"));
        // NPR (Nepalese Rupee) uses South Asian system
        Assert.assertEquals("5,00,000", FormatUtil.formatAmount(500000.0, "NPR"));
    }

    @Test
    public void testParseDoubleWithCommasAndSymbols() {
        Assert.assertEquals(100000.0, FormatUtil.parseDouble("1,00,000"), 0.001);
        Assert.assertEquals(100000.50, FormatUtil.parseDouble("100,000.50"), 0.001);
        Assert.assertEquals(50000.0, FormatUtil.parseDouble("₹ 50,000"), 0.001);
        Assert.assertEquals(1234.56, FormatUtil.parseDouble("$1,234.56"), 0.001);
        Assert.assertEquals(25000.0, FormatUtil.parseDouble("  25,000  "), 0.001);
    }

    @Test(expected = NumberFormatException.class)
    public void testParseDoubleEmptyThrows() {
        FormatUtil.parseDouble("");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseDoubleNullThrows() {
        FormatUtil.parseDouble(null);
    }

    @Test(expected = NumberFormatException.class)
    public void testParseDoubleInvalidThrows() {
        FormatUtil.parseDouble("abc");
    }

    @Test
    public void testParseDoubleOrDefault() {
        Assert.assertEquals(100.0, FormatUtil.parseDouble("100", 0.0), 0.001);
        Assert.assertEquals(0.0, FormatUtil.parseDouble("", 0.0), 0.001);
        Assert.assertEquals(0.0, FormatUtil.parseDouble(null, 0.0), 0.001);
        Assert.assertEquals(5.0, FormatUtil.parseDouble("invalid", 5.0), 0.001);
    }
}
