package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.BookItem
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.data.pdf.PdfBookManager
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LibraryViewMode(val title: String, val icon: String, val desc: String) {
    WOODEN_SHELF("رفوف خشبية", "🪵", "عرض كعوب الكتب على رفوف واقعية"),
    CARDS_3D("كروت 3D", "✨", "بطاقات مجسمة بألوان المواد"),
    LIST_VIEW("قائمة سريعة", "📋", "قائمة مضغوطة مع أحجام الملفات"),
    SCATTERED_DESK("طاولة المذاكرة", "📐", "توزيع طبيعي على طاولة الدراسة"),
    COVER_FLOW("كاروسيل الغلاف", "🎞️", "استعراض أفقي للأغلفة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingBooks.collectAsStateWithLifecycle()
    val pdfManager = remember { PdfBookManager(context) }

    var selectedGradeId by remember { mutableIntStateOf(currentUser?.gradeId ?: 10) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubjectFilter by remember { mutableStateOf<String?>(null) }
    // DEFAULT VIEW MODE IS WOODEN BOOKSHELF AS REQUESTED
    var currentViewMode by remember { mutableStateOf(LibraryViewMode.WOODEN_SHELF) }
    var isViewModeSheetOpen by remember { mutableStateOf(false) }
    var selectedBookForDetail by remember { mutableStateOf<BookItem?>(null) }

    // Fetch all books
    val allBooks by viewModel.repository.getAllBooks().collectAsStateWithLifecycle(initialValue = emptyList())

    // Track downloaded books dynamically
    var downloadedBooksMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(allBooks) {
        withContext(Dispatchers.IO) {
            val map = allBooks.associate { it.id to pdfManager.isBookDownloaded(it.id) }
            downloadedBooksMap = map
        }
    }

    val gradesList = remember {
        listOf(
            7 to "الصف السابع",
            8 to "الصف الثامن",
            9 to "الصف التاسع",
            10 to "الأول الثانوي",
            11 to "الثاني الثانوي",
            12 to "الثالث الثانوي",
            6 to "الصف السادس",
            5 to "الصف الخامس"
        )
    }

    val gradeBooks = remember(allBooks, selectedGradeId) {
        allBooks.filter { it.gradeId == selectedGradeId }
    }

    val filteredBooks = remember(gradeBooks, searchQuery, selectedSubjectFilter) {
        gradeBooks.filter { book ->
            val matchesSearch = searchQuery.isBlank() ||
                    book.title.contains(searchQuery, ignoreCase = true) ||
                    book.subjectName.contains(searchQuery, ignoreCase = true)
            val matchesSubject = selectedSubjectFilter == null || book.subjectId.contains(selectedSubjectFilter!!, ignoreCase = true)
            matchesSearch && matchesSubject
        }
    }

    val distinctSubjects = remember(gradeBooks) {
        gradeBooks.map { it.subjectId to it.subjectName }.distinctBy { it.first }
    }

    val currentGradeName = SchoolHierarchy.getGradeName(selectedGradeId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Compact Mode Switcher Icon Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📚 مكتبة الكتب والمناهج",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "${allBooks.size} كتاباً دراسياً معتمداً",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Compact View Mode Selector Button (Saves screen real estate)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyanGlow)
                                .border(1.dp, CyanAccent, RoundedCornerShape(12.dp))
                                .clickable { isViewModeSheetOpen = true }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = currentViewMode.icon, fontSize = 14.sp)
                                Text(
                                    text = currentViewMode.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                                Icon(Icons.Default.Tune, contentDescription = "تغيير النمط", tint = CyanAccent, modifier = Modifier.size(14.dp))
                            }
                        }

                        // Refresh/Sync Button
                        IconButton(
                            onClick = {
                                viewModel.syncBooks(selectedGradeId) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = if (isSyncing) TextMuted else TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Grade Selector Scrollable Row
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر الصف الدراسي لتصفح كتبه:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(gradesList) { (gid, gName) ->
                            val isSelected = gid == selectedGradeId
                            val count = allBooks.count { it.gradeId == gid }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) CyanGlow else MidnightSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) CyanAccent else GlassBorderSubtle,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        selectedGradeId = gid
                                        selectedSubjectFilter = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = gName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) CyanAccent else TextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) CyanAccent else GlassSurfaceLight)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) TextOnAccent else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar & Subject Filter
            if (gradeBooks.isNotEmpty()) {
                item {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "بحث في كتب $currentGradeName",
                        placeholder = "ابحث باسم الكتاب أو المادة...",
                        leadingIcon = Icons.Default.Search
                    )
                }

                if (distinctSubjects.size > 1) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedSubjectFilter == null,
                                    onClick = { selectedSubjectFilter = null },
                                    label = { Text("جميع المواد (${gradeBooks.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent,
                                        selectedLabelColor = TextOnAccent
                                    )
                                )
                            }
                            items(distinctSubjects) { (subId, subName) ->
                                val count = gradeBooks.count { it.subjectId == subId }
                                FilterChip(
                                    selected = selectedSubjectFilter == subId,
                                    onClick = {
                                        selectedSubjectFilter = if (selectedSubjectFilter == subId) null else subId
                                    },
                                    label = { Text("$subName ($count)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent,
                                        selectedLabelColor = TextOnAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Syncing Indicator
            if (isSyncing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CyanAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "جاري الاتصال بـ GitHub Releases وفحص ملفات المناهج والكتب...",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            // Empty State for Grade without Books
            else if (gradeBooks.isEmpty()) {
                item {
                    EmptyGradeBooksView(
                        gradeName = currentGradeName,
                        onSyncClick = {
                            viewModel.syncBooks(selectedGradeId) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            // Render Books based on Current View Mode (Default: Wooden Shelf)
            else {
                when (currentViewMode) {
                    LibraryViewMode.WOODEN_SHELF -> {
                        item {
                            WoodenBookshelfView(
                                books = filteredBooks,
                                downloadedMap = downloadedBooksMap,
                                onSelectBook = { book ->
                                    selectedBookForDetail = book
                                },
                                onOpenBook = { book ->
                                    viewModel.openBookInPdfReader(book)
                                }
                            )
                        }
                    }

                    LibraryViewMode.CARDS_3D -> {
                        items(filteredBooks, key = { it.id }) { book ->
                            Canvas3DBookCard(
                                book = book,
                                isDownloaded = downloadedBooksMap[book.id] == true,
                                onDownload = {
                                    viewModel.openBookInPdfReader(book)
                                }
                            )
                        }
                    }

                    LibraryViewMode.LIST_VIEW -> {
                        items(filteredBooks, key = { it.id }) { book ->
                            ListViewBookItem(
                                book = book,
                                isDownloaded = downloadedBooksMap[book.id] == true,
                                onOpen = { viewModel.openBookInPdfReader(book) }
                            )
                        }
                    }

                    LibraryViewMode.SCATTERED_DESK -> {
                        item {
                            ScatteredDeskView(
                                books = filteredBooks,
                                downloadedMap = downloadedBooksMap,
                                onSelectBook = { book ->
                                    selectedBookForDetail = book
                                },
                                onOpenBook = { book ->
                                    viewModel.openBookInPdfReader(book)
                                }
                            )
                        }
                    }

                    LibraryViewMode.COVER_FLOW -> {
                        item {
                            CoverFlow3DView(
                                books = filteredBooks,
                                downloadedMap = downloadedBooksMap,
                                onOpenBook = { book ->
                                    viewModel.openBookInPdfReader(book)
                                }
                            )
                        }
                    }
                }
            }
        }

        // View Mode Selector Bottom Sheet (Clean & Elegant)
        if (isViewModeSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isViewModeSheetOpen = false },
                containerColor = MidnightSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎨 طريقة عرض المكتبة:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LibraryViewMode.values().forEach { mode ->
                        val isSelected = currentViewMode == mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) CyanGlow else GlassSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) CyanAccent else GlassBorderSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    currentViewMode = mode
                                    isViewModeSheetOpen = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = mode.icon, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = mode.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) CyanAccent else TextPrimary
                                        )
                                        Text(
                                            text = mode.desc,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 2
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = CyanAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Selected Book Quick Detail Modal / Bottom Sheet
        if (selectedBookForDetail != null) {
            val book = selectedBookForDetail!!
            val isDownloaded = downloadedBooksMap[book.id] == true

            ModalBottomSheet(
                onDismissRequest = { selectedBookForDetail = null },
                containerColor = MidnightSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas3DBookCard(
                        book = book,
                        isDownloaded = isDownloaded,
                        onDownload = {
                            selectedBookForDetail = null
                            viewModel.openBookInPdfReader(book)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassButton(
                            text = if (isDownloaded) "فتح ومطالعة الكتاب 📖" else "تحميل وقراءة الآن ⬇️",
                            icon = if (isDownloaded) Icons.Default.MenuBook else Icons.Default.CloudDownload,
                            color = if (isDownloaded) EmeraldGreen else CyanAccent,
                            textColor = TextOnAccent,
                            onClick = {
                                selectedBookForDetail = null
                                viewModel.openBookInPdfReader(book)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. LIST VIEW MODE
// -------------------------------------------------------------
@Composable
fun ListViewBookItem(
    book: BookItem,
    isDownloaded: Boolean,
    onOpen: () -> Unit
) {
    val (primaryColor) = remember(book.subjectId) { getBookThemeColors(book.subjectId) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        backgroundColor = MidnightSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryColor.copy(alpha = 0.2f))
                    .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = book.subjectIcon.ifEmpty { "📕" }, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = book.subjectName, fontSize = 12.sp, color = primaryColor)
                    Text(text = "•", fontSize = 12.sp, color = TextMuted)
                    Text(
                        text = if (isDownloaded) "محمل أوفلاين ✅" else "${book.fileSizeMb} MB",
                        fontSize = 11.sp,
                        color = if (isDownloaded) EmeraldGreen else TextSecondary,
                        fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            GlassButton(
                text = if (isDownloaded) "فتح 📖" else "تحميل ⬇️",
                color = if (isDownloaded) EmeraldGreen else primaryColor,
                textColor = TextOnAccent,
                onClick = onOpen,
                modifier = Modifier.height(34.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 2. REALISTIC WOODEN BOOKSHELF VIEW
// -------------------------------------------------------------
@Composable
fun WoodenBookshelfView(
    books: List<BookItem>,
    downloadedMap: Map<String, Boolean>,
    onSelectBook: (BookItem) -> Unit,
    onOpenBook: (BookItem) -> Unit
) {
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    val chunkedBooks = remember(books) { books.chunked(6) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF140F0A))
            .border(2.dp, Color(0xFF422C1A), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        chunkedBooks.forEach { shelfBooks ->
            Column(modifier = Modifier.fillMaxWidth()) {
                // Books on shelf
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(shelfBooks, key = { it.id }) { book ->
                        val isSelected = selectedBookId == book.id
                        CanvasBookshelfSpine(
                            book = book,
                            isDownloaded = downloadedMap[book.id] == true,
                            isSelected = isSelected,
                            onClick = {
                                selectedBookId = book.id
                                onSelectBook(book)
                            }
                        )
                    }
                }

                // Realistic Oak Shelf Plank (Canvas)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Top shelf surface with wood grain highlight
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF8D5B33),
                                Color(0xFF5C381E),
                                Color(0xFF331E10)
                            )
                        ),
                        size = androidx.compose.ui.geometry.Size(w, h - 4f)
                    )

                    // Front shelf edge highlight
                    drawLine(
                        color = Color(0xFFA67145),
                        start = androidx.compose.ui.geometry.Offset(0f, 1f),
                        end = androidx.compose.ui.geometry.Offset(w, 1f),
                        strokeWidth = 2f
                    )

                    // Bottom cast shadow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x99000000), Color.Transparent)
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, h - 4f),
                        size = androidx.compose.ui.geometry.Size(w, 4f)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. SCATTERED DESK VIEW
// -------------------------------------------------------------
@Composable
fun ScatteredDeskView(
    books: List<BookItem>,
    downloadedMap: Map<String, Boolean>,
    onSelectBook: (BookItem) -> Unit,
    onOpenBook: (BookItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131822))
            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📐 طاولة المذاكرة الحرة (انقر على أي كتاب لفتحه مباشرة):",
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )

        books.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEachIndexed { idx, book ->
                    val angle = if (idx % 2 == 0) -4f else 4f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .rotate(angle)
                            .clickable { onSelectBook(book) }
                    ) {
                        Canvas3DBookCard(
                            book = book,
                            isDownloaded = downloadedMap[book.id] == true,
                            onDownload = { onOpenBook(book) }
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. 3D COVER FLOW VIEW
// -------------------------------------------------------------
@Composable
fun CoverFlow3DView(
    books: List<BookItem>,
    downloadedMap: Map<String, Boolean>,
    onOpenBook: (BookItem) -> Unit
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MidnightSurface)
            .border(1.dp, CyanGlow, RoundedCornerShape(20.dp))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (books.isNotEmpty()) {
            val currentBook = books[activeIndex.coerceIn(0, books.size - 1)]

            // Center Spotlight Book
            Canvas3DBookCard(
                book = currentBook,
                isDownloaded = downloadedMap[currentBook.id] == true,
                onDownload = { onOpenBook(currentBook) },
                modifier = Modifier.width(300.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = { if (activeIndex > 0) activeIndex-- },
                    enabled = activeIndex > 0
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "السابق", tint = CyanAccent)
                }

                Text(
                    text = "${activeIndex + 1} / ${books.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                IconButton(
                    onClick = { if (activeIndex < books.size - 1) activeIndex++ },
                    enabled = activeIndex < books.size - 1
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "التالي", tint = CyanAccent)
                }
            }
        }
    }
}

@Composable
fun EmptyGradeBooksView(
    gradeName: String,
    onSyncClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        backgroundColor = GlassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AmberGlow)
                    .border(1.5.dp, WarmAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📖", fontSize = 42.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "لا توجد كتب لـ $gradeName حالياً",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WarmAmber,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            GlassButton(
                text = "فحص التحديثات والمزامنة 🔄",
                icon = Icons.Default.Refresh,
                color = WarmAmber,
                textColor = TextOnAccent,
                onClick = onSyncClick
            )
        }
    }
}
