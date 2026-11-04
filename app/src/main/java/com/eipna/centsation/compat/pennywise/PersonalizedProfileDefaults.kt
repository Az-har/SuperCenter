package com.eipna.centsation.compat.pennywise

/**
 * Hyper-personalized financial defaults seeded directly from the user's
 * PennyWise profile. Automatically active in Super Center for real-time
 * bank SMS parsing, clean display names, and auto-categorization.
 */
object PersonalizedProfileDefaults {

    val KNOWN_BANKS = listOf(
        "ICICI Bank",
        "City Union Bank",
        "Yes Bank",
        "Cashfree"
    )

    val DEFAULT_MERCHANT_ALIASES: Map<String, String> = mapOf(
        "9344002076ptyes" to "Afdhal",
        "9677236663ptyes" to "Sandha",
        "Afrar Ahamed J" to "number plate",
        "Alif Laila A" to "Mummy ka amount",
        "Annai Velankann" to "petrol",
        "Aravind" to "haircut uncle shop",
        "Atho Pal" to "Atho",
        "Ayyanar Fancy S" to "Ayanar Fancy Store",
        "B Thangaraj Sob" to "kaalaan kada",
        "BP Anna nagar" to "petrol bunk G.W",
        "City Sta" to "city star bike accessories",
        "Cmrl Sti Dparki" to "Metro parking",
        "Coco" to "CNG padi",
        "Cumta" to "local bus",
        "District by Zom" to "Movie ticket",
        "Fridays" to "Friday's fast food",
        "Frysto" to "Frysto burger",
        "Gears" to "gears India",
        "Gemini Auto Ser" to "petrol",
        "Hathwaym" to "Hathway Broadband Bill",
        "Hathway Cable a" to "Hathway Broadband Bill",
        "HungerBox" to "getham ozone",
        "Immanuel" to "petrol",
        "Inft*fi13" to "ICIC credit card bill",
        "Inft*fk14" to "ICIC credit card bill",
        "Inft*fh10" to "ICIC credit card bill",
        "Juice" to "pain balm",
        "Kalai Super Mar" to "Kalai Super Market",
        "Kalai Super Market" to "Kalai Super Market",
        "Karthikeyan S" to "fancy store",
        "Kkumar pani poo" to "pani poori",
        "Kms Hakkim Kaly" to "food shopl",
        "Krishva Enterpr" to "Fruit vendor",
        "Chick Laban" to "Chick Laban",
        "Credit card bill" to "Credit Card Bill",
        "Mr Azhar Ayyash" to "Azhar Ayyash (Self)",
        "Rajalakshmi Eng" to "Rajalakshmi Engineering",
        "aura silver" to "Aura Silver",
        "Bajaj showroom" to "Bajaj Showroom",
        "Milk Delivery" to "Milk Delivery",
        "v s automobile" to "VS Automobile",
        "Google India Di" to "Google India",
        "AuraGold" to "Aura Gold",
        "shanthosh friend" to "Shanthosh",
        "Shanthosh" to "Shanthosh",
        "Amazonpayindiap" to "Amazon Pay"
    )

    val DEFAULT_CATEGORY_MAPPINGS: Map<String, String> = mapOf(
        // Aliases & Raw Merchants to Categories
        "9344002076ptyes" to "Family spend",
        "Afdhal" to "Family spend",
        "9677236663ptyes" to "Investments",
        "Sandha" to "Investments",
        "Alif Laila A" to "Mom's Bills",
        "Mummy ka amount" to "Mom's Bills",
        "Aravind" to "Personal Care",
        "haircut uncle shop" to "Personal Care",
        "Atho Pal" to "Food & Dining",
        "Atho" to "Food & Dining",
        "Ayyanar Fancy S" to "Bills & Utilities",
        "Ayanar Fancy Store" to "Bills & Utilities",
        "B Thangaraj Sob" to "Food & Dining",
        "kaalaan kada" to "Food & Dining",
        "BP Anna nagar" to "petrol",
        "petrol bunk G.W" to "petrol",
        "Chick Laban" to "Food & Dining",
        "Credit card bill" to "Bills & Utilities",
        "Cumta" to "Transportation",
        "local bus" to "Transportation",
        "Hathway Cable a" to "Mom's Bills",
        "Hathwaym" to "Mom's Bills",
        "Hathway Broadband Bill" to "Mom's Bills",
        "HungerBox" to "Food & Dining",
        "getham ozone" to "Food & Dining",
        "Immanuel" to "petrol",
        "Inft*fi13" to "Bills & Utilities",
        "Inft*fk14" to "Bills & Utilities",
        "Inft*fh10" to "Bills & Utilities",
        "ICIC credit card bill" to "Bills & Utilities",
        "Kalai Super Mar" to "Groceries",
        "Kalai Super Market" to "Groceries",
        "Karthikeyan S" to "Family spend",
        "fancy store" to "Family spend",
        "Kkumar pani poo" to "Food & Dining",
        "pani poori" to "Food & Dining",
        "Kms Hakkim Kaly" to "Food & Dining",
        "food shopl" to "Food & Dining",
        "Krishva Enterpr" to "Groceries",
        "Fruit vendor" to "Groceries",
        "District by Zom" to "Entertainment",
        "Movie ticket" to "Entertainment",
        "Fridays" to "Food & Dining",
        "Friday's fast food" to "Food & Dining",
        "Frysto" to "Food & Dining",
        "Frysto burger" to "Food & Dining",
        "Gears" to "Automobile",
        "gears India" to "Automobile",
        "Gemini Auto Ser" to "petrol",
        "Juice" to "Health & Medical",
        "pain balm" to "Health & Medical",
        "City Sta" to "Automobile",
        "city star bike accessories" to "Automobile",
        "Cmrl Sti Dparki" to "Transportation",
        "Metro parking" to "Transportation",
        "Coco" to "petrol",
        "CNG padi" to "petrol",
        "number plate" to "Automobile",
        "Afrar Ahamed J" to "Automobile",
        "aura silver" to "Investments",
        "AuraGold" to "Investments",
        "Aura Gold" to "Investments",
        "Rajalakshmi Eng" to "Education",
        "Rajalakshmi Engineering" to "Education",
        "Bajaj showroom" to "Automobile",
        "Bajaj Showroom" to "Automobile",
        "Milk Delivery" to "Groceries",
        "v s automobile" to "Automobile",
        "VS Automobile" to "Automobile",
        "Google India Di" to "Bills & Utilities",
        "Google India" to "Bills & Utilities",
        "shanthosh friend" to "Friends & Social",
        "Shanthosh" to "Friends & Social",
        "Amazonpayindiap" to "Shopping",
        "Amazon Pay" to "Shopping"
    )

    /**
     * Fallback heuristic keyword categorization if merchant isn't explicitly mapped.
     */
    fun inferCategoryByKeyword(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("petrol") || lower.contains("fuel") || lower.contains("bunk") ||
                    lower.contains("cng") || lower.contains("diesel") -> "petrol"
            lower.contains("bus") || lower.contains("metro") || lower.contains("train") ||
                    lower.contains("toll") || lower.contains("parking") || lower.contains("auto") -> "Transportation"
            lower.contains("food") || lower.contains("dine") || lower.contains("dining") ||
                    lower.contains("restaurant") || lower.contains("burger") || lower.contains("pizza") ||
                    lower.contains("swiggy") || lower.contains("zomato") || lower.contains("hotel") ||
                    lower.contains("poori") || lower.contains("kada") || lower.contains("tea") -> "Food & Dining"
            lower.contains("super market") || lower.contains("mart") || lower.contains("grocery") ||
                    lower.contains("groceries") || lower.contains("milk") || lower.contains("fruit") ||
                    lower.contains("vegetable") -> "Groceries"
            lower.contains("bill") || lower.contains("broadband") || lower.contains("hathway") ||
                    lower.contains("airtel") || lower.contains("jio") || lower.contains("recharge") ||
                    lower.contains("electricity") || lower.contains("eb ") || lower.contains("bescom") -> "Bills & Utilities"
            lower.contains("mummy") || lower.contains("mom") || lower.contains("family") ||
                    lower.contains("home") -> "Mom's Bills"
            lower.contains("gold") || lower.contains("silver") || lower.contains("sandha") ||
                    lower.contains("mutual") || lower.contains("zerodha") || lower.contains("groww") -> "Investments"
            lower.contains("movie") || lower.contains("cinema") || lower.contains("theatre") ||
                    lower.contains("ticket") -> "Entertainment"
            lower.contains("haircut") || lower.contains("salon") || lower.contains("spa") ||
                    lower.contains("grooming") -> "Personal Care"
            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") ||
                    lower.contains("clothing") || lower.contains("store") || lower.contains("fancy") -> "Shopping"
            lower.contains("bike") || lower.contains("car") || lower.contains("automobile") ||
                    lower.contains("gears") -> "Automobile"
            else -> "General"
        }
    }
}
