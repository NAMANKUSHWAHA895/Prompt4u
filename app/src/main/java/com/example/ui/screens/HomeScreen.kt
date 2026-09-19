package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AdminPanelDialog
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.AudienceSelectionDialog
import com.example.ui.components.PostCard
import com.example.ui.components.PostDetailDialog
import com.example.ui.viewmodel.PromptGalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PromptGalleryViewModel) {
    val context = LocalContext.current

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val favoritePosts by viewModel.favoritePosts.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedPost by viewModel.selectedPost.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val targetAudience by viewModel.targetAudience.collectAsStateWithLifecycle()
    val showAudienceOnboarding by viewModel.showAudienceOnboarding.collectAsStateWithLifecycle()
    val isAdminDialogOpen by viewModel.isAdminDialogOpen.collectAsStateWithLifecycle()
    val isAdminAccessPinOpen by viewModel.isAdminAccessPinOpen.collectAsStateWithLifecycle()
    val isAdminAuthenticated by viewModel.isAdminAuthenticated.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    val categories = listOf(
        "All",
        "Cyberpunk",
        "Photorealistic",
        "Fantasy",
        "Anime",
        "3D Render",
        "Architecture",
        "Favorites"
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            viewModel.openAdminAccess()
                                        },
                                        onTap = {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime > 1500L) {
                                                titleTapCount = 1
                                            } else {
                                                titleTapCount++
                                            }
                                            lastTapTime = now
                                            if (titleTapCount >= 5) {
                                                titleTapCount = 0
                                                viewModel.openAdminAccess()
                                            }
                                        }
                                    )
                                }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_prompt4u_logo),
                                contentDescription = "Prompt4u Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Prompt4u",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    },
                    actions = {
                        // Target Audience Pill Selector
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable { viewModel.openAudienceSelector() }
                                .testTag("audience_filter_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = when (targetAudience) {
                                        "Girls" -> "👧 Girls"
                                        "Boys" -> "👦 Boys"
                                        else -> "👥 All"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (currentTab == 0 || currentTab == 2) {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                    if (!isSearchExpanded) viewModel.setSearchQuery("")
                                },
                                modifier = Modifier.testTag("toggle_search_button")
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                                    contentDescription = "Search Prompts"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search Bar Expandable
                AnimatedVisibility(
                    visible = isSearchExpanded && (currentTab == 0 || currentTab == 2),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                val trimmed = query.trim().lowercase()
                                if (trimmed in listOf("#admin", "//admin", "admin#", "/admin", "admin@root")) {
                                    viewModel.setSearchQuery("")
                                    isSearchExpanded = false
                                    viewModel.openAdminAccess()
                                } else {
                                    viewModel.setSearchQuery(query)
                                }
                            },
                            placeholder = { Text("Search prompts, keywords, styles...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field")
                        )
                    }
                }

                // Category Chips (Horizontal Scroll) on Feed tab
                if (currentTab == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSelectedCategory(category) },
                                label = {
                                    Text(
                                        text = category,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_$category")
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DynamicFeed,
                            contentDescription = "Prompt Feed"
                        )
                    },
                    label = { Text("Feed", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_feed")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.setCurrentTab(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Get AI Prompt"
                        )
                    },
                    label = { Text("Get Prompt", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_reverse_prompt")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setCurrentTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Saved Prompts"
                        )
                    },
                    label = { Text("Saved", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_saved")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.setCurrentTab(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null
                        )
                    },
                    text = { Text("Get AI Prompt", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_get_prompt")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    // Community / Featured Feed
                    if (posts.isEmpty()) {
                        EmptyState(
                            title = if (searchQuery.isNotEmpty()) "No Prompts Found" else if (selectedCategory != "All") "No Prompts in $selectedCategory" else "Welcome to Prompt4u",
                            subtitle = if (searchQuery.isNotEmpty()) {
                                "No prompts matched \"$searchQuery\". Try a different search."
                            } else if (selectedCategory != "All") {
                                "No prompts found under $selectedCategory."
                            } else {
                                "New prompts published by admins will appear here and are visible to all users immediately!"
                            }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("feed_list")
                        ) {
                            items(posts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                                    onLikeClick = { viewModel.likePost(it) },
                                    onCardClick = { viewModel.setSelectedPost(post) },
                                    onTryPromptClick = null
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Reverse Prompt (Upload Image -> Get Prompt with 6-core blueprint)
                    ReversePromptScreen(viewModel = viewModel)
                }
                2 -> {
                    // Saved / Favorites using Cookies
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Cookie Storage Status Banner (User requested: "use cookies for saved prompts")
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "🍪", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Cookie-Based Persistence Active",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "${favoritePosts.size} prompts saved in client cookies",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                if (favoritePosts.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearAllCookies() }) {
                                        Text(
                                            text = "Clear Cookies",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (favoritePosts.isEmpty()) {
                            EmptyState(
                                title = "No Saved Prompts in Cookies",
                                subtitle = "Tap the heart or bookmark icon on any post to store prompts into cookies for instant offline recall."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 80.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(favoritePosts, key = { it.id }) { post ->
                                    PostCard(
                                        post = post,
                                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                                        onLikeClick = { viewModel.likePost(it) },
                                        onCardClick = { viewModel.setSelectedPost(post) },
                                        onTryPromptClick = null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Post Detail Dialog
            selectedPost?.let { post ->
                PostDetailDialog(
                    post = post,
                    onDismiss = { viewModel.setSelectedPost(null) },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    onLikeClick = { viewModel.likePost(it) }
                )
            }

            // Audience Selection Dialog (Asked when app starts: "for whom you need prompt girls boys or both")
            if (showAudienceOnboarding) {
                AudienceSelectionDialog(
                    currentAudience = targetAudience,
                    onSelectAudience = { viewModel.selectTargetAudience(it) },
                    onDismiss = { viewModel.dismissAudienceSelector() }
                )
            }

            // Admin Access PIN Dialog (User asked: "how do i access admin panel in app")
            if (isAdminAccessPinOpen) {
                AdminPinDialog(
                    onDismiss = { viewModel.closeAdminPinDialog() },
                    onVerifyPin = { viewModel.verifyAdminPin(it) }
                )
            }

            // Admin Panel Dialog (User asked: "add admin panel to add post that include image and prompt only and the post should go live")
            if (isAdminDialogOpen) {
                AdminPanelDialog(
                    onDismiss = { viewModel.closeAdminDialog() },
                    onPublishPost = { uri, prompt, aud, cat -> viewModel.publishAdminPost(uri, prompt, aud, cat) },
                    onLogout = { viewModel.logoutAdmin() },
                    onChangePasskey = { currentKey, newKey -> viewModel.changeAdminPasskey(currentKey, newKey) }
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
