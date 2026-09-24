package cc.openxiot.matrix.ui.main

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.ui.devices.DeviceListScreen
import cc.openxiot.matrix.ui.home.HomeScreen
import cc.openxiot.matrix.ui.products.ProductListScreen
import cc.openxiot.matrix.ui.profile.ProfileScreen
import cc.openxiot.matrix.ui.project.ProjectViewModel
import cc.openxiot.matrix.ui.project.SpaceTreeContent
import cc.openxiot.matrix.util.mirrorInRtl
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
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToModbus: () -> Unit = {},
    // 告警与历史都以**当前项目根空间**为鉴权作用域（口径同设备页/首页看板），
    // 故这两个回调要带上 rootId —— 由 MainScreen 自己从 mainViewModel 取，调用方不必操心
    onNavigateToAlarm: (rootId: String) -> Unit = {},
    onNavigateToHistory: (rootId: String) -> Unit = {},
    onNavigateToModbusService: ((spaceId: String, serviceId: String) -> Unit)? = null,
    onNavigateToDashboardEdit: ((rootId: String) -> Unit)? = null,
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
                                contentDescription = stringResource(tab.labelRes),
                                modifier = Modifier.size(22.dp),
                                tint = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                stringResource(tab.labelRes),
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
                beyondViewportPageCount = 0,
                // 关掉手指左右翻页：这个手势留给各个 Tab 自己的横向操作（轮播、横滑卡片等），
                // 否则两者会抢同一个手势，横滑几下就翻到隔壁 Tab 去了。
                // 切换 Tab 只走底部点击（下面的 animateScrollToPage 仍然有效）。
                userScrollEnabled = false
            ) { page ->
                when (tabs[page]) {
                    BottomTab.Home -> HomeScreen(
                        // 看板整个项目级的数：根空间作为接口的鉴权作用域（与项目页同一个来源）
                        rootId = mainViewModel.currentRootSpaceId,
                        onEditDashboard = {
                            mainViewModel.currentRootSpaceId?.let { rootId ->
                                onNavigateToDashboardEdit?.invoke(rootId)
                            }
                        }
                    )
                    BottomTab.Projects -> {
                        val rootId = mainViewModel.currentRootSpaceId
                        if (rootId != null) {
                            val coroutineScope = rememberCoroutineScope()
                            var isRefreshing by remember { mutableStateOf(false) }
                            val treeState by projectViewModel.treeState.collectAsState()

                            Column(modifier = Modifier.fillMaxSize()) {
                                // 标题靠左，显示当前项目的名称（根空间自己不再作为一行出现在树里）；
                                // 名字优先取空间图里的（最新），图还没回来时退回登录态存的那个。
                                // 右边那颗「>」进项目选择页 —— 原来这是树下那张「当前项目」卡片上的按钮。
                                PageTitle(
                                    title = treeState.rootSpace?.name ?: currentProjectName
                                        ?: stringResource(R.string.tab_project),
                                    actions = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(48.dp)
                                                .clickable(onClick = onNavigateToProjectPicker),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = stringResource(R.string.main_switch_project),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp).mirrorInRtl()
                                            )
                                        }
                                    }
                                )

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
                                        onDeviceDetail = onNavigateToDeviceDetail,
                                        onDeviceOperation = onNavigateToDeviceOperation,
                                        onServiceClick = onNavigateToModbusService
                                    )
                                }
                            }
                        } else if (organizationEnabled && currentOrgName == null) {
                            EmptyHint(
                                message = stringResource(R.string.main_pick_org_first),
                                buttonText = stringResource(R.string.main_pick_org),
                                onClick = onNavigateToOrgPicker
                            )
                        } else {
                            EmptyHint(
                                message = stringResource(R.string.main_pick_project_first),
                                buttonText = stringResource(R.string.main_pick_project),
                                onClick = onNavigateToProjectPicker
                            )
                        }
                    }
                    BottomTab.Devices -> DeviceListScreen(
                        rootId = mainViewModel.currentRootSpaceId,
                        onDeviceDetail = onNavigateToDeviceDetail,
                        onDeviceOperation = onNavigateToDeviceOperation,
                        onServiceClick = onNavigateToModbusService
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
                        onNavigateToAbout = onNavigateToAbout,
                        onNavigateToLanguage = onNavigateToLanguage,
                        onNavigateToModbus = onNavigateToModbus,
                        onNavigateToAlarm = {
                            mainViewModel.currentRootSpaceId?.let(onNavigateToAlarm)
                        },
                        onNavigateToHistory = {
                            mainViewModel.currentRootSpaceId?.let(onNavigateToHistory)
                        }
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
            // 标题吃掉剩余宽度（过长的省略），右边 [actions] 的位置就一定留得出来 ——
            // 项目页的标题是项目名、长度不可控，不留的话会把右边的「>」挤出屏幕。
            if (onClick != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).mirrorInRtl()
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }
            actions()
        }
    }
}
