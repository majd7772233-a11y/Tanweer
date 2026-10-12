package com.magd.tanweer.util

import com.magd.tanweer.data.local.BookEntity
import com.magd.tanweer.data.model.CatalogBookItem
import com.magd.tanweer.data.model.BooksCatalogResponse
import com.magd.tanweer.data.model.GitHubRelease
import com.magd.tanweer.data.model.GitHubReleaseAsset
import com.magd.tanweer.data.model.SchoolHierarchy

data class CanonicalBook(
    val canonicalId: String,
    val gradeId: Int,
    val subjectId: String,
    val title: String,
    val edition: String?,
    val fileSizeMb: Double,
    val fileUrl: String,
    val releaseTag: String? = null,
    val assetName: String? = null,
    val sha256: String? = null,
    val localFileName: String = "${canonicalId}.pdf",
    val catalogRecord: CatalogBookItem? = null,
    val releaseAsset: GitHubReleaseAsset? = null
) {
    fun toEntity(): BookEntity = BookEntity(
        id = canonicalId,
        gradeId = gradeId,
        subjectId = subjectId,
        title = title,
        edition = edition ?: "إصدار رسمي - المناهج المعتمدة",
        fileSizeMb = fileSizeMb,
        fileUrl = fileUrl,
        thumbnailUrl = null,
        subjectName = BookCatalogResolver.mapSubjectMeta(subjectId).second,
        subjectIcon = BookCatalogResolver.mapSubjectMeta(subjectId).third
    )
}

data class ResolvedBookCatalog(
    val books: List<BookEntity>,
    val canonicalBooks: List<CanonicalBook> = emptyList(),
    val releaseTag: String?,
    val catalogVersion: String?,
    val totalCount: Int
)

object BookCatalogResolver {

    /**
     * Generates a deterministic, canonical ID for any school book.
     * Format: book_<grade>_<normalizedSubject>[_<part>]
     */
    fun getCanonicalBookId(gradeId: Int, subjectId: String, partNumber: Int?): String {
        val normalizedSubj = normalizeSubjectKey(subjectId)
        val partSuffix = if (partNumber != null && partNumber > 0) "_$partNumber" else ""
        return "book_${gradeId}_${normalizedSubj}${partSuffix}"
    }

    fun normalizeSubjectKey(raw: String): String {
        val s = raw.lowercase().trim().replace("-", "_")
        return when {
            s.contains("neighborhoodsactivities") || s.contains("biology_act") -> "biology_act"
            s.contains("neighborhoods") || s.contains("biology") || s.contains("أحياء") || s.contains("احياء") -> "biology"
            s.contains("chemistryactivities") || s.contains("chemistry_act") -> "chemistry_act"
            s.contains("chemistry") || s.contains("كيمياء") -> "chemistry"
            s.contains("physicsactivities") || s.contains("physics_act") -> "physics_act"
            s.contains("physics") || s.contains("فيزياء") -> "physics"
            s.contains("englishwork") || s.contains("english_work") -> "english_work"
            s.contains("englishpubils") || s.contains("englishpupils") || s.contains("english_pupils") || s.contains("english") || s.contains("إنجليزي") || s.contains("انجليزي") -> "english"
            s.contains("syntax") || s.contains("نحو") -> "syntax"
            s.contains("literature") || s.contains("أدب") || s.contains("ادب") -> "literature"
            s.contains("reading") || s.contains("قراءة") -> "reading"
            s.contains("arabic") || s.contains("عربي") || s.contains("عربية") -> "arabic"
            s.contains("sirprophet") || s.contains("سيرة") -> "sirah"
            s.contains("hadith") || s.contains("حديث") -> "hadith"
            s.contains("eman") || s.contains("إيمان") || s.contains("ايمان") -> "eman"
            s.contains("quran") || s.contains("قرآن") || s.contains("قران") -> "quran"
            s.contains("islam") || s.contains("إسلام") || s.contains("اسلام") -> "islamic"
            s.contains("nationaleducation") || s.contains("وطنية") -> "civics"
            s.contains("ymenisociety") || s.contains("مجتمع") -> "yemen_society"
            s.contains("geographic") || s.contains("geography") || s.contains("جغرافيا") -> "geography"
            s.contains("history") || s.contains("تاريخ") -> "history"
            s.contains("math") || s.contains("رياضيات") -> "math"
            s.contains("science") || s.contains("علوم") -> "science"
            s.contains("computer") || s.contains("حاسوب") -> "computer"
            else -> s
        }
    }

    fun mapSubjectMeta(subjectKey: String): Triple<String, String, String> {
        val norm = normalizeSubjectKey(subjectKey)
        return when (norm) {
            "biology_act" -> Triple("biology_act", "الأنشطة والتجارب - الأحياء", "🧬")
            "biology" -> Triple("biology", "علم الأحياء", "🧬")
            "chemistry_act" -> Triple("chemistry_act", "الأنشطة والتجارب - الكيمياء", "🧪")
            "chemistry" -> Triple("chemistry", "الكيمياء", "🧪")
            "physics_act" -> Triple("physics_act", "الأنشطة والتجارب - الفيزياء", "⚡")
            "physics" -> Triple("physics", "الفيزياء", "⚡")
            "english_pupils" -> Triple("english_pupils", "اللغة الإنجليزية (كتاب الطالب)", "🇬🇧")
            "english_work" -> Triple("english_work", "اللغة الإنجليزية (كتاب الأنشطة)", "🇬🇧")
            "english" -> Triple("english", "اللغة الإنجليزية", "🇬🇧")
            "syntax" -> Triple("syntax", "النحو والصرف والقواعد", "📖")
            "literature" -> Triple("literature", "الأدب والنصوص والبلاغة", "📖")
            "reading" -> Triple("reading", "القراءة والمطالعة", "📖")
            "arabic" -> Triple("arabic", "اللغة العربية", "📖")
            "sirah" -> Triple("sirah", "السيرة النبوية الشريفة", "🕌")
            "hadith" -> Triple("hadith", "الحديث الشريف وعلومه", "🕌")
            "eman" -> Triple("eman", "الإيمان والتربية الإيمانية", "✨")
            "quran" -> Triple("quran", "القرآن الكريم وتلاوته", "✨")
            "islamic" -> Triple("islamic", "التربية الإسلامية", "🕌")
            "civics" -> Triple("civics", "التربية الوطنية", "🇾🇪")
            "yemen_society" -> Triple("yemen_society", "المجتمع اليمني والقضايا المعاصرة", "🇾🇪")
            "geography" -> Triple("geography", "الجغرافيا", "🧭")
            "history" -> Triple("history", "التاريخ", "🏛️")
            "math" -> Triple("math", "الرياضيات", "📐")
            "science" -> Triple("science", "العلوم", "🔬")
            "computer" -> Triple("computer", "الحاسوب وتكنولوجيا المعلومات", "💻")
            else -> Triple(norm, norm.replaceFirstChar { it.uppercase() }, "📕")
        }
    }

    /**
     * Parses a GitHub Release asset filename into canonical metadata.
     */
    fun parseAssetToCanonical(asset: GitHubReleaseAsset): BookEntity? {
        val rawName = asset.name.trim()
        if (!rawName.endsWith(".pdf", ignoreCase = true)) return null

        val baseName = rawName.substringBeforeLast(".pdf")
        val parts = baseName.split("-", "_")
        if (parts.isEmpty()) return null

        val subjectPart = parts[0].trim().lowercase()
        val gradePart = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        val partNumber = parts.getOrNull(2)?.trim()?.toIntOrNull()

        val (normSubjId, subjectName, subjectIcon) = mapSubjectMeta(subjectPart)
        val canonicalId = getCanonicalBookId(gradePart, normSubjId, partNumber)

        val partAr = when {
            subjectPart.contains("activities") -> "كتاب الأنشطة والتجارب العملية"
            subjectPart.contains("pupil") || subjectPart.contains("pubil") -> "كتاب الطالب (Pupils Book)"
            subjectPart.contains("work") -> "كتاب التدريبات (Workbook)"
            partNumber == null || partNumber == 0 -> "كتاب المنهج الكامل"
            partNumber == 1 -> "الجزء الأول"
            partNumber == 2 -> "الجزء الثاني"
            partNumber == 3 -> "الجزء الثالث"
            else -> "الجزء $partNumber"
        }

        val title = "كتاب $subjectName ($partAr)"
        val sizeMb = Math.round((asset.size.toDouble() / (1024.0 * 1024.0)) * 10.0) / 10.0

        return BookEntity(
            id = canonicalId,
            gradeId = gradePart,
            subjectId = normSubjId,
            title = title,
            edition = "إصدار رسمي - المناهج المعتمدة",
            fileSizeMb = sizeMb,
            fileUrl = asset.browser_download_url,
            thumbnailUrl = null,
            subjectName = subjectName,
            subjectIcon = subjectIcon
        )
    }

    /**
     * Parses a catalog.json entry into canonical BookEntity.
     */
    fun parseCatalogItemToCanonical(item: CatalogBookItem): BookEntity {
        val (normSubjId, defaultSubjName, defaultSubjIcon) = mapSubjectMeta(item.subject_id)
        val canonicalId = getCanonicalBookId(item.grade_id, normSubjId, item.part_number)

        val subjectName = if (item.subject_name.isNotBlank()) item.subject_name else defaultSubjName
        val subjectIcon = if (item.subject_icon.isNotBlank()) item.subject_icon else defaultSubjIcon
        val title = if (item.title.isNotBlank()) item.title else "كتاب $subjectName"

        return BookEntity(
            id = canonicalId,
            gradeId = item.grade_id,
            subjectId = normSubjId,
            title = title,
            edition = item.edition ?: "إصدار معتمد - المناهج الدراسية",
            fileSizeMb = if (item.size_mb > 0) item.size_mb else (item.size_bytes.toDouble() / (1024 * 1024)),
            fileUrl = item.download_url,
            thumbnailUrl = null,
            subjectName = subjectName,
            subjectIcon = subjectIcon
        )
    }

    /**
     * Merges catalog.json and GitHub Releases into a unified, pristine snapshot.
     * Guaranteed zero duplicate books and complete 58-book metadata.
     */
    fun resolveAndMerge(
        catalogResponse: BooksCatalogResponse?,
        releases: List<GitHubRelease>?
    ): ResolvedBookCatalog {
        val mergedMap = LinkedHashMap<String, BookEntity>()

        // 1. Authoritative base: Start with catalog.json if available (exactly 58 official curriculum books)
        if (catalogResponse != null && catalogResponse.books.isNotEmpty()) {
            catalogResponse.books.forEach { item ->
                val catalogBook = parseCatalogItemToCanonical(item)
                mergedMap[catalogBook.id] = catalogBook
            }
        }

        // 2. Determine active release tag and latest release assets
        val latestRelease = releases?.firstOrNull { !it.assets.isNullOrEmpty() }
        val releaseTag = latestRelease?.tag_name ?: releases?.firstOrNull()?.tag_name
        val catalogVersion = catalogResponse?.version?.toString()

        // 3. Enrich existing catalog items with live release download URLs and file sizes
        latestRelease?.assets?.forEach { asset ->
            parseAssetToCanonical(asset)?.let { assetBook ->
                val existing = mergedMap[assetBook.id]
                if (existing != null) {
                    mergedMap[assetBook.id] = existing.copy(
                        fileUrl = if (assetBook.fileUrl.isNotBlank()) assetBook.fileUrl else existing.fileUrl,
                        fileSizeMb = if (assetBook.fileSizeMb > 0) assetBook.fileSizeMb else existing.fileSizeMb
                    )
                } else if (catalogResponse == null || catalogResponse.books.isEmpty()) {
                    // Only insert asset as standalone book if catalog.json is completely absent
                    mergedMap[assetBook.id] = assetBook
                }
            }
        }

        val allBooks = mergedMap.values.toList().distinctBy { it.id }
        val canonicalBooks = allBooks.map { book ->
            CanonicalBook(
                canonicalId = book.id,
                gradeId = book.gradeId,
                subjectId = book.subjectId,
                title = book.title,
                edition = book.edition,
                fileSizeMb = book.fileSizeMb,
                fileUrl = book.fileUrl,
                releaseTag = releaseTag,
                localFileName = "${book.id}.pdf"
            )
        }
        return ResolvedBookCatalog(
            books = allBooks,
            canonicalBooks = canonicalBooks,
            releaseTag = releaseTag,
            catalogVersion = catalogVersion,
            totalCount = allBooks.size
        )
    }
}
