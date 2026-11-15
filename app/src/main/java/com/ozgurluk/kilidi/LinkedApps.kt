package com.ozgurluk.kilidi

/**
 * Süresi telefon süresine değil, etkinlik panelindeki bir çubuğa yazılan uygulamalar.
 *
 * - AnkiDroid (orijinali ve Odak çatalı) → "Ders"
 * - Perde (gitar teorisi uygulaması) → "Elektro gitar"
 *
 * Bu uygulamalar her zaman serbesttir: kilitte açık kalır, 2 saatlik sınıra sayılmaz ve
 * telefon süresine eklenmez. Çubuğun süresi bu uygulamalardan otomatik gelir; kullanıcı
 * yalnızca bunun üstüne ekleme yapabilir, altına indiremez.
 */
object LinkedApps {

    const val STUDY_ID = "study"
    const val GUITAR_ID = "guitar"

    const val PERDE = "com.enoli.perde"
    private const val ANKI = "com.ichi2.anki"

    /** Serbest listeye kalıcı olarak eklenen paketler (kilit ekranı ızgarasında da görünür). */
    val ALWAYS_ALLOWED = setOf(
        ANKI,               // AnkiDroid (Play Store)
        "$ANKI.odak",       // AnkiDroid Odak çatalı
        PERDE
    )

    /** AnkiDroid'in tüm sürümleri (orijinal, Odak çatalı, debug vb.). */
    fun isAnki(pkg: String): Boolean = pkg == ANKI || pkg.startsWith("$ANKI.")

    fun isPerde(pkg: String): Boolean = pkg == PERDE

    /** Bu paket bir etkinlik çubuğuna bağlı mı? */
    fun isLinked(pkg: String): Boolean = isAnki(pkg) || isPerde(pkg)

    /** Paketin süresinin yazıldığı çubuk (yoksa null). */
    fun categoryFor(pkg: String): String? = when {
        isAnki(pkg) -> STUDY_ID
        isPerde(pkg) -> GUITAR_ID
        else -> null
    }

    /** Çubuğa süre veren paket(ler)in denetimi (yoksa null). */
    fun matcherFor(categoryId: String): ((String) -> Boolean)? = when (categoryId) {
        STUDY_ID -> ::isAnki
        GUITAR_ID -> ::isPerde
        else -> null
    }

    fun isLinkedCategory(categoryId: String): Boolean = matcherFor(categoryId) != null

    /** Düzenleme penceresinde gösterilen kaynak adı. */
    fun sourceName(categoryId: String): String = when (categoryId) {
        STUDY_ID -> "AnkiDroid"
        GUITAR_ID -> "Perde"
        else -> ""
    }
}
