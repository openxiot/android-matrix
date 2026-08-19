package cc.openxiot.wematrix.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.ui.devices.DeviceListScreen
import cc.openxiot.wematrix.ui.home.HomeScreen
import cc.openxiot.wematrix.ui.products.ProductListScreen
import cc.openxiot.wematrix.ui.profile.ProfileScreen
import cc.openxiot.wematrix.ui.project.ProjectViewModel
import cc.openxiot.wematrix.ui.project.SpaceTreeContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentOrgName: String?,
    currentProjectName: String?,
    organizationEnabled: Boolean = true,
    onNavigateToOrgPicker: () -> Unit = {},
    onNavigateToProjectPicker: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDeviceDetail: ((String) -> Unit)? = null,
    onNavigateToDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
    onNavigateToProductDetail: ((String) -> Unit)? = null,
    mainViewModel: MainViewModel = viewModel(),
    projectViewModel: ProjectViewModel = viewModel()
) {
    val tabs = BottomTab.entries

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(56.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                tabs.forEach { tab ->
                    val selected = mainViewModel.currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { mainViewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        val pagerState = rememberPagerState(
            initialPage = mainViewModel.currentTab.ordinal,
            pageCount = { tabs.size }
        )

        // 手指左右滑动 → 更新底部选中 Tab
        LaunchedEffect(pagerState, tabs) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    mainViewModel.selectTab(tabs[page])
                }
        }

        // 点击底部 Tab → 分页器动画滚动到对应页
        LaunchedEffect(mainViewModel.currentTab) {
            if (pagerState.settledPage != mainViewModel.currentTab.ordinal) {
                pagerState.animateScrollToPage(mainViewModel.currentTab.ordinal)
            }
        }

        Box(modifier = Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                when (tabs[page]) {
                    BottomTab.Home -> HomeScreen()
                    BottomTab.Projects -> {
                        val rootId = mainViewModel.currentRootSpaceId
                        if (rootId != null) {
                            val coroutineScope = rememberCoroutineScope()
                            var isRefreshing by remember { mutableStateOf(false) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Centered app name title bar
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "矩阵",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Pull-to-refresh space tree
                                PullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = {
                                        coroutineScope.launch {
                                            isRefreshing = true
                                            projectViewModel.loadSpaceGraphInternal(rootId)
                                            isRefreshing = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    SpaceTreeContent(
                                        rootId = rootId,
                                        viewModel = projectViewModel,
                                        showActions = false,
                                        onRootSpaceClick = onNavigateToProjectPicker,
                                        onDeviceDetail = onNavigateToDeviceDetail,
                                        onDeviceOperation = onNavigateToDeviceOperation
                                    )
                                }
                            }
                        } else if (organizationEnabled && currentOrgName == null) {
                            EmptyHint(
                                message = "请先选择当前组织",
                                buttonText = "选择组织",
                                onClick = onNavigateToOrgPicker
                            )
                        } else {
                            EmptyHint(
                                message = "请先选择当前项目",
                                buttonText = "选择项目",
                                onClick = onNavigateToProjectPicker
                            )
                        }
                    }
                    BottomTab.Devices -> DeviceListScreen(
                        rootId = mainViewModel.currentRootSpaceId,
                        onDeviceDetail = onNavigateToDeviceDetail,
                        onDeviceOperation = onNavigateToDeviceOperation
                    )
                    BottomTab.Products -> ProductListScreen(
                        onProductDetail = onNavigateToProductDetail
                    )
                    BottomTab.Profile -> ProfileScreen(
                        currentOrgName = currentOrgName,
                        currentProjectName = currentProjectName,
                        organizationEnabled = organizationEnabled,
                        onNavigateToOrgPicker = onNavigateToOrgPicker,
                        onNavigateToProjectPicker = onNavigateToProjectPicker,
                        onNavigateToAccount = onNavigateToAccount,
                        onNavigateToAbout = onNavigateToAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun PageTitle(
    title: String,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            actions()
        }
    }
}
