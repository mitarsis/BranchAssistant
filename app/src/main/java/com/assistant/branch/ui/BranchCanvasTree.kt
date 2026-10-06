package com.assistant.branch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private data class NodePos(val id: String, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Дерево беседы в стиле Obsidian Canvas: pan всего холста + drag отдельных
 * узлов. Узлы — настоящие Compose-карточки с текстом, линии — Canvas.
 */
@Composable
fun BranchCanvasTree(
    nodes: List<BranchNodeInfo>,
    activePath: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
    onLongPress: (BranchNodeInfo) -> Unit = {},
    forkParentId: String? = null,
) {
    if (nodes.isEmpty()) return

    // Двойной тап по ноде разворачивает её на весь экран с прокруткой. null =
    // ничего не развёрнуто. Состояние живёт в BranchCanvasTree, потому что
    // overlay должен рендериться поверх всех нод (zIndex выше).
    var expandedNodeId by remember { mutableStateOf<String?>(null) }
    val expandedNode = expandedNodeId?.let { id -> nodes.firstOrNull { it.id == id } }

    val childMap = remember(nodes) { nodes.groupBy { it.parentId } }
    // Roots — это parentId == null. Если таких нет, ищем orphan'ы: узлы, чей
    // parentId указывает на несуществующий message. Показываем их как
    // самостоятельные roots — лучше видеть «потерянную» ветку, чем маскировать
    // её под нормальный корень.
    val roots = remember(nodes) {
        val explicitRoots = nodes.filter { it.parentId == null }
        if (explicitRoots.isNotEmpty()) {
            explicitRoots
        } else {
            val allIds = nodes.map { it.id }.toHashSet()
            nodes.filter { it.parentId != null && it.parentId !in allIds }
                .ifEmpty { listOf(nodes.first()) }
        }
    }

    val activeSet = activePath.toHashSet()
    val leafId = activePath.lastOrNull()

    val nodeW = 480f
    val nodeH = 320f
    val hGap = 28f
    val vGap = 96f
    val pad = 48f
    val minNodeW = 200f
    val minNodeH = 80f
    // Шахматное смещение по X для каждого уровня глубины. Чётный depth — +
    // stagger, нечётный — 0. Получается «лесенка со сдвигом».
    val xStagger = 120f

    // Позиции узлов (в пикселях канваса). Можно двигать drag'ом.
    val positions = remember { mutableStateMapOf<String, NodePos>() }

    // Авто-раскладка при смене набора узлов. Вся мутация positions — внутри
    // эффекта, а не в composable body. Старые позиции стираются, нодам
    // выдаются начальные координаты.
    androidx.compose.runtime.LaunchedEffect(nodes) {
        positions.clear()
        if (nodes.isEmpty()) return@LaunchedEffect
        fun measureWidth(id: String): Float {
            val kids = childMap[id].orEmpty()
            val total = if (kids.isEmpty()) nodeW
            else kids.map { measureWidth(it.id) }.sum() + (kids.size - 1) * hGap
            return maxOf(nodeW, total)
        }
        fun place(id: String, x: Float, y: Float, depth: Int = 0) {
            val w = measureWidth(id)
            // Стабильный jitter (seed по hashCode) — одинаковый между прогонами.
            val rng = kotlin.random.Random(id.hashCode())
            val jx = (rng.nextFloat() - 0.5f) * 32f
            val jy = (rng.nextFloat() - 0.5f) * 32f
            positions[id] = NodePos(id, x + jx, y + jy, w, nodeH)
            val kids = childMap[id].orEmpty()
            if (kids.isEmpty()) return
            val totalKidsW = kids.map { measureWidth(it.id) }.sum() + (kids.size - 1) * hGap
            // Шахматный сдвиг по X: дети каждого уровня смещены относительно
            // родителя, чтобы раскладка шла «ступенькой», а не строго столбиком.
            val childXShift = (depth % 2) * xStagger
            var curX = x + childXShift + (w - totalKidsW) / 2f
            val childY = y + nodeH + vGap + jy
            for (kid in kids) {
                place(kid.id, curX, childY, depth + 1)
                curX += measureWidth(kid.id) + hGap
            }
        }
        var colX = pad
        for (root in roots) {
            place(root.id, colX, pad)
            colX += measureWidth(root.id) + hGap
        }
    }

    // Push-apart: при resize/drag ноды сдвигаем пересекающиеся ноды
    // (и их поддеревья) вниз, чтобы не было наложений.
    fun shiftSubtree(rootId: String, dy: Float) {
        if (dy == 0f) return
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val p = positions[id] ?: continue
            positions[id] = p.copy(y = p.y + dy)
            childMap[id].orEmpty().forEach { queue.add(it.id) }
        }
    }

    fun rectsOverlap(a: NodePos, b: NodePos): Boolean =
        a.x < b.x + b.w && a.x + a.w > b.x &&
        a.y < b.y + b.h && a.y + a.h > b.y

    fun pushApart(movedId: String) {
        val moved = positions[movedId] ?: return
        val movedRect = moved
        // Один проход (не repeat), и только для нод ниже moved.
        // Иначе при больших resize может «всё разлететься».
        for (m in nodes) {
            if (m.id == movedId) continue
            val p = positions[m.id] ?: continue
            if (p.y < movedRect.y) continue  // толкаем только тех, кто ниже
            if (!rectsOverlap(p, movedRect)) continue
            val dy = (movedRect.y + movedRect.h + vGap - p.y).coerceIn(0f, 2000f)
            if (dy > 0f) shiftSubtree(m.id, dy)
        }
    }

    // Pan всего холста.
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .pointerInput(Unit) {
                detectDragGestures(
                    ) { change, drag ->
                    val dx = drag.x.roundToInt().toFloat()
                    val dy = drag.y.roundToInt().toFloat()
                    if (dx != 0f || dy != 0f) {
                        pan += Offset(dx, dy)
                    }
                }
            },
    ) {
        // Линии.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edge = Color(0xFF323744)
            for (n in nodes) {
                val parentId = n.parentId ?: continue
                val from = positions[parentId] ?: continue
                val to = positions[n.id] ?: continue
                val sx = from.x + from.w + pan.x
                val sy = from.y + from.h / 2f + pan.y
                val ex = to.x + pan.x
                val ey = to.y + to.h / 2f + pan.y
                val midX = (sx + ex) / 2f
                drawPath(
                    Path().apply {
                        moveTo(sx, sy)
                        cubicTo(midX, sy, midX, ey, ex, ey)
                    },
                    color = edge,
                    style = Stroke(width = 2f),
                )
            }
        }

        // Узлы.
        for (n in nodes) {
            val p = positions[n.id] ?: continue
            val isActive = n.id in activeSet
            val isLeaf = n.id == leafId
            NodeCard(
                node = n,
                posX = p.x + pan.x,
                posY = p.y + pan.y,
                widthPx = p.w,
                heightPx = p.h,
                isActive = isActive,
                isLeaf = isLeaf,
                isForkParent = n.id == forkParentId,
                onDragDelta = { dx, dy ->
                    val cur = positions[n.id] ?: return@NodeCard
                    positions[n.id] = cur.copy(x = cur.x + dx, y = cur.y + dy)
                    // pushApart при drag убираем: иначе при drag вниз
                    // нода постоянно сдвигает других, и всё уезжает
                    // бесконечно. Resize сам толкает соседей.
                },
                onResize = { dw, dh ->
                    val cur = positions[n.id] ?: return@NodeCard
                    val newW = (cur.w + dw).coerceAtLeast(80f)
                    val newH = (cur.h + dh).coerceAtLeast(60f)
                    positions[n.id] = cur.copy(w = newW, h = newH)
                    pushApart(n.id)
                },
                onClick = { onSelect(n.id) },
                onLongPress = { onLongPress(n) },
                onDoubleTap = { expandedNodeId = n.id },
                modifier = Modifier.zIndex(1f),
            )
        }
        // Overlay для развёрнутой ноды. Рендерится поверх всех нод (zIndex
        // выше) и блокирует pan холста (есть свой pointerInput на onClose).
        if (expandedNode != null) {
            ExpandedNodeOverlay(
                node = expandedNode,
                onClose = { expandedNodeId = null },
                modifier = Modifier.zIndex(10f),
            )
        }
    }
}

@Composable
private fun NodeCard(
    node: BranchNodeInfo,
    posX: Float,
    posY: Float,
    widthPx: Float,
    heightPx: Float,
    isActive: Boolean,
    isLeaf: Boolean,
    isForkParent: Boolean = false,
    onDragDelta: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val offsetXPx = posX.toInt()
    val offsetYPx = posY.toInt()

    val isSuggestion = node.source == "agent-suggestion"
Box(
    modifier = modifier
        .size(width = widthDp, height = heightDp)
        .offset { IntOffset(offsetXPx, offsetYPx) }
        .clip(RoundedCornerShape(12.dp))
        .background(
            when {
                isSuggestion -> Color(0xFF1B1F29).copy(alpha = 0.7f)
                node.isUser -> Color(0xFF2C3242)
                else -> Color(0xFF1B1F29)
            }
        )
        .border(
            width = if (isLeaf) 2.5.dp else if (isForkParent) 2.5.dp else if (isActive) 2.dp else 1.dp,
            color = when {
                isLeaf -> Color(0xFFB6A8FF)
                // forkParent — оранжевый: чёткий визуальный сигнал «от
                // этой ноды сейчас создаётся новая ветка».
                isForkParent -> Color(0xFFFFB74D)
                // suggestion от агента — пунктирная обводка.
                isSuggestion -> Color(0xFF9E9E9E)
                isActive -> Color(0xFF7C5CFF)
                else -> Color(0xFF323744)
            },
            shape = RoundedCornerShape(12.dp),
        )
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    val dx = drag.x.roundToInt().toFloat()
                    val dy = drag.y.roundToInt().toFloat()
                    if (dx != 0f || dy != 0f) {
                        onDragDelta(dx, dy)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onDoubleTap() },
                )
            },
    ) {
// Header. Эмодзи вместо текстовых меток — быстрее считывается на глаз.
    Text(
        text = when {
            node.source == "agent-suggestion" -> "✨ suggestion"
            node.source == "agent" -> "✨ agent"
            node.isUser -> "👤"
            else -> "🤖"
        },
        color = Color(0xFF9AA1B1),
        fontSize = 11.sp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 14.dp, top = 8.dp),
    )
        // Скроллируемый body: текст не обрезается, длинные сообщения можно
        // прочитать свайпом внутри карточки. На resize handle это не влияет
        // (он в BottomEnd, отдельный pointerInput).
        val scrollState = rememberScrollState()
        val textBg = if (node.isUser) Color(0xFF2C3242) else Color(0xFF1B1F29)
        val isScrollable = scrollState.canScrollForward || scrollState.canScrollBackward
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(heightDp - 34.dp)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = node.fullText.ifBlank { node.preview }.ifBlank { "…" },
                color = Color(0xFFE6E8EE),
                fontSize = 14.sp,
            )
        }
        // Fade-out снизу: подсказка, что текст прокручивается. Появляется
        // только если есть что скроллить.
        if (isScrollable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, textBg),
                        )
                    ),
            )
        }
        // Resize handle (правый нижний угол). Тянем — меняем размер ноды.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .background(Color(0xFF7C5CFF).copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        val dw = drag.x.roundToInt().toFloat()
                        val dh = drag.y.roundToInt().toFloat()
                        if (dw != 0f || dh != 0f) {
                            onResize(dw, dh)
                        }
                    }
                },
        )
    }
}

/**
 * Full-screen overlay с развёрнутым текстом ноды. Тот же стиль карточки,
 * но во весь экран с прокруткой и кнопкой закрытия (×) в правом верхнем углу.
 * Тап по затемнённому фону тоже закрывает overlay.
 */
@Composable
private fun ExpandedNodeOverlay(
    node: BranchNodeInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textBg = if (node.isUser) Color(0xFF2C3242) else Color(0xFF1B1F29)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) {
                // Тап по затемнению — закрыть. Сам контент перехватывает тапы.
                detectTapGestures(onTap = { onClose() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(textBg)
                .border(
                    width = 2.dp,
                    color = if (node.isActive) Color(0xFF7C5CFF) else Color(0xFF5C5C70),
                    shape = RoundedCornerShape(16.dp),
                )
                .pointerInput(Unit) {
                    // Перехватываем тапы внутри карточки, чтобы они не
                    // закрывали overlay.
                    detectTapGestures { /* swallow */ }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when {
                            node.source == "agent-suggestion" -> "✨ suggestion"
                            node.source == "agent" -> "✨ agent"
                            node.isUser -> "👤"
                            else -> "🤖"
                        },
                        color = Color(0xFF9AA1B1),
                        fontSize = 12.sp,
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Закрыть",
                            tint = Color(0xFF9AA1B1),
                        )
                    }
                }
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    Text(
                        text = node.fullText.ifBlank { node.preview }.ifBlank { "…" },
                        color = Color(0xFFE6E8EE),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
