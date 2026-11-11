package com.socialshop.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.ui.account.AccountScreen
import com.socialshop.app.ui.account.ChangePasswordScreen
import com.socialshop.app.ui.account.EditProfileScreen
import com.socialshop.app.ui.auth.ForgotPasswordScreen
import com.socialshop.app.ui.auth.LoginScreen
import com.socialshop.app.ui.auth.RegisterScreen
import com.socialshop.app.ui.cart.CartScreen
import com.socialshop.app.ui.checkout.CheckoutScreen
import com.socialshop.app.ui.home.HomeScreen
import com.socialshop.app.ui.orders.OrderDetailScreen
import com.socialshop.app.ui.orders.OrdersScreen
import com.socialshop.app.ui.payment.PaymentScreen
import com.socialshop.app.ui.product.ProductDetailScreen
import com.socialshop.app.ui.product.ReviewsScreen
import com.socialshop.app.ui.products.ProductListScreen
import com.socialshop.app.ui.wishlist.WishlistScreen
import com.socialshop.app.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlin.reflect.KClass

/** Snackbar dùng chung để mọi màn hình báo kết quả thao tác. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState chưa được cung cấp") }

@Composable
fun repository(): ShopRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { EntryPointAccessors.fromApplication(context, RepositoryEntryPoint::class.java).shopRepository() }
}

private data class TopTab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopTab(HomeRoute, HomeRoute::class, "Trang chủ", Icons.Filled.Home),
    TopTab(ProductsRoute(), ProductsRoute::class, "Sản phẩm", Icons.Filled.Search),
    TopTab(CartRoute, CartRoute::class, "Giỏ hàng", Icons.Filled.ShoppingCart),
    TopTab(WishlistRoute, WishlistRoute::class, "Yêu thích", Icons.Filled.Favorite),
    TopTab(AccountRoute, AccountRoute::class, "Tài khoản", Icons.Filled.Person),
)

fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun SocialShopRoot() {
    val nav = rememberNavController()
    val repo = repository()
    val snackbar = remember { SnackbarHostState() }
    val token by repo.token.collectAsStateWithLifecycle()
    val cartCount by repo.cartCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { repo.restoreSession() }
    // Token bị xóa (đăng xuất hoặc server trả 401) → dọn trạng thái người dùng
    LaunchedEffect(token) { if (token == null) repo.onSessionExpired() }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.openTab(tab.route) },
                                label = { Text(tab.label) },
                                icon = {
                                    if (tab.route == CartRoute && cartCount > 0) {
                                        BadgedBox(badge = { Badge { Text(if (cartCount > 99) "99+" else "$cartCount") } }) {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable<HomeRoute> {
                    HomeScreen(
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                        onCategory = { id, name -> nav.navigate(ProductsRoute(categoryId = id, title = name)) },
                        onSearch = { nav.navigate(ProductsRoute(search = it)) },
                    )
                }
                composable<ProductsRoute> { entry ->
                    val route = entry.toRoute<ProductsRoute>()
                    ProductListScreen(
                        initialSearch = route.search,
                        categoryId = route.categoryId,
                        title = route.title,
                        canGoBack = nav.previousBackStackEntry != null && (route.categoryId != null || route.search != null),
                        onBack = { nav.popBackStack() },
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                    )
                }
                composable<CartRoute> {
                    CartScreen(
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                        onCheckout = { nav.navigate(CheckoutRoute) },
                        onLogin = { nav.navigate(LoginRoute) },
                        onShop = { nav.openTab(HomeRoute) },
                    )
                }
                composable<WishlistRoute> {
                    WishlistScreen(
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                        onLogin = { nav.navigate(LoginRoute) },
                    )
                }
                composable<AccountRoute> {
                    AccountScreen(
                        onLogin = { nav.navigate(LoginRoute) },
                        onRegister = { nav.navigate(RegisterRoute) },
                        onOrders = { nav.navigate(OrdersRoute) },
                        onEditProfile = { nav.navigate(EditProfileRoute) },
                        onChangePassword = { nav.navigate(ChangePasswordRoute) },
                    )
                }
                composable<ProductDetailRoute> { entry ->
                    val route = entry.toRoute<ProductDetailRoute>()
                    ProductDetailScreen(
                        slug = route.slug,
                        onBack = { nav.popBackStack() },
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                        onLogin = { nav.navigate(LoginRoute) },
                        onCart = { nav.openTab(CartRoute) },
                        onCheckout = { nav.navigate(CheckoutRoute) },
                        onReviews = { name, canReview -> nav.navigate(ReviewsRoute(route.slug, name, canReview)) },
                    )
                }
                composable<ReviewsRoute> { entry ->
                    val route = entry.toRoute<ReviewsRoute>()
                    ReviewsScreen(route.slug, route.name, route.canReview, onBack = { nav.popBackStack() })
                }
                composable<CheckoutRoute> {
                    CheckoutScreen(
                        onBack = { nav.popBackStack() },
                        onPlaced = { orderId, paymentUrl ->
                            val next: Any = if (paymentUrl != null) PaymentRoute(paymentUrl, orderId) else OrderDetailRoute(orderId, justPlaced = true)
                            // Bỏ màn Thanh toán khỏi back stack (giỏ đã trống sau khi đặt)
                            nav.navigate(next) { popUpTo<CheckoutRoute> { inclusive = true } }
                        },
                    )
                }
                composable<PaymentRoute> { entry ->
                    val route = entry.toRoute<PaymentRoute>()
                    PaymentScreen(route.url, onFinished = {
                        nav.navigate(OrderDetailRoute(route.orderId, justPlaced = true)) {
                            popUpTo<PaymentRoute> { inclusive = true }
                        }
                    })
                }
                composable<OrdersRoute> {
                    OrdersScreen(onBack = { nav.popBackStack() }, onOrder = { nav.navigate(OrderDetailRoute(it)) })
                }
                composable<OrderDetailRoute> { entry ->
                    val route = entry.toRoute<OrderDetailRoute>()
                    OrderDetailScreen(
                        orderId = route.id,
                        justPlaced = route.justPlaced,
                        onBack = { nav.popBackStack() },
                        onProduct = { nav.navigate(ProductDetailRoute(it)) },
                    )
                }
                composable<LoginRoute> {
                    LoginScreen(
                        onBack = { nav.popBackStack() },
                        onSuccess = { nav.popBackStack() },
                        onRegister = { nav.navigate(RegisterRoute) { popUpTo<LoginRoute> { inclusive = true } } },
                        onForgot = { nav.navigate(ForgotPasswordRoute) },
                    )
                }
                composable<RegisterRoute> {
                    RegisterScreen(
                        onBack = { nav.popBackStack() },
                        onSuccess = { nav.popBackStack() },
                        onLogin = { nav.navigate(LoginRoute) { popUpTo<RegisterRoute> { inclusive = true } } },
                    )
                }
                composable<ForgotPasswordRoute> { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }
                composable<EditProfileRoute> { EditProfileScreen(onBack = { nav.popBackStack() }) }
                composable<ChangePasswordRoute> { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
