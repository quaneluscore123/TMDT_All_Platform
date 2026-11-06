package com.socialshop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.socialshop.app.core.resolveBackendUrl
import com.socialshop.app.data.remote.Product

@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    AsyncImage(
        model = url.resolveBackendUrl(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    Card(
        onClick = onClick,
        modifier = if (width != null) modifier.width(width) else modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            ProductImage(
                product.imageUrl,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium),
            )
            if (product.isOnSale && product.discountPercent > 0) {
                DiscountBadge(product.discountPercent, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            product.brand?.let {
                Text(
                    it.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            PriceRow(product.price, product.originalPrice, product.isOnSale)
            Spacer(Modifier.height(2.dp))
            RatingRow(product.rating, product.reviewsCount)
            if (product.stock <= 0 && !product.hasVariants) {
                Text("Hết hàng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Dùng khi một hàng cần chỗ bấm toàn vùng mà không phải Card. */
fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
