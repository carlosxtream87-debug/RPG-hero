package com.example.game

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class TilePoint(val col: Int, val row: Int)

private data class AStarNode(
  val col: Int,
  val row: Int,
  val g: Float,
  val f: Float,
) : Comparable<AStarNode> {
  override fun compareTo(other: AStarNode): Int = f.compareTo(other.f)
}

object GridPathfinder {
  private val cardinalDirs = arrayOf(
    1 to 0,
    -1 to 0,
    0 to 1,
    0 to -1,
  )

  private val allDirs = arrayOf(
    1 to 0,
    -1 to 0,
    0 to 1,
    0 to -1,
    1 to 1,
    1 to -1,
    -1 to 1,
    -1 to -1,
  )

  fun worldToTile(
    x: Float,
    y: Float,
    zone: ZoneMapData,
    tileSize: Float = RpgCatalog.TILE_SIZE,
  ): TilePoint {
    val c = (x / tileSize).toInt().coerceIn(0, zone.cols - 1)
    val r = (y / tileSize).toInt().coerceIn(0, zone.rows - 1)
    return TilePoint(c, r)
  }

  fun tileCenter(
    col: Int,
    row: Int,
    tileSize: Float = RpgCatalog.TILE_SIZE,
  ): Pair<Float, Float> {
    return (col + 0.5f) * tileSize to (row + 0.5f) * tileSize
  }

  fun isBlocked(zone: ZoneMapData, col: Int, row: Int): Boolean {
    if (col !in 0 until zone.cols || row !in 0 until zone.rows) return true
    return zone.tiles[row][col].solid
  }

  fun collidesWithWorld(
    zone: ZoneMapData,
    cx: Float,
    cy: Float,
    radius: Float,
    tileSize: Float = RpgCatalog.TILE_SIZE,
  ): Boolean {
    val minC = ((cx - radius) / tileSize).toInt()
    val maxC = ((cx + radius) / tileSize).toInt()
    val minR = ((cy - radius) / tileSize).toInt()
    val maxR = ((cy + radius) / tileSize).toInt()

    for (r in minR..maxR) {
      for (c in minC..maxC) {
        if (r !in 0 until zone.rows || c !in 0 until zone.cols) return true
        if (zone.tiles[r][c].solid) return true
      }
    }
    return false
  }

  fun hasLineOfSight(
    zone: ZoneMapData,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    clearanceRadius: Float,
    tileSize: Float = RpgCatalog.TILE_SIZE,
  ): Boolean {
    val dist = hypot(x2 - x1, y2 - y1)
    if (dist <= 1f) return !collidesWithWorld(zone, x2, y2, clearanceRadius, tileSize)
    val step = max(6f, tileSize * 0.25f)
    val steps = (dist / step).toInt().coerceAtLeast(1)
    for (i in 1..steps) {
      val t = i.toFloat() / steps.toFloat()
      val px = x1 + (x2 - x1) * t
      val py = y1 + (y2 - y1) * t
      if (collidesWithWorld(zone, px, py, clearanceRadius, tileSize)) {
        return false
      }
    }
    return true
  }

  fun findNearestPassable(zone: ZoneMapData, col: Int, row: Int): TilePoint? {
    val clampedC = col.coerceIn(0, zone.cols - 1)
    val clampedR = row.coerceIn(0, zone.rows - 1)
    if (!isBlocked(zone, clampedC, clampedR)) return TilePoint(clampedC, clampedR)

    val visited = BooleanArray(zone.cols * zone.rows)
    val queue = ArrayDeque<TilePoint>()
    queue.addLast(TilePoint(clampedC, clampedR))
    visited[clampedR * zone.cols + clampedC] = true

    while (queue.isNotEmpty()) {
      val cur = queue.removeFirst()
      for ((dc, dr) in cardinalDirs) {
        val nc = cur.col + dc
        val nr = cur.row + dr
        if (nc !in 0 until zone.cols || nr !in 0 until zone.rows) continue
        val idx = nr * zone.cols + nc
        if (visited[idx]) continue
        if (!isBlocked(zone, nc, nr)) return TilePoint(nc, nr)
        visited[idx] = true
        queue.addLast(TilePoint(nc, nr))
      }
    }
    return null
  }

  fun findPath(
    zone: ZoneMapData,
    startCol: Int,
    startRow: Int,
    targetCol: Int,
    targetRow: Int,
    ignoreSolid: Boolean = false,
  ): List<TilePoint> {
    val sc = startCol.coerceIn(0, zone.cols - 1)
    val sr = startRow.coerceIn(0, zone.rows - 1)
    val resolvedGoal = if (ignoreSolid) {
      TilePoint(targetCol.coerceIn(0, zone.cols - 1), targetRow.coerceIn(0, zone.rows - 1))
    } else {
      findNearestPassable(zone, targetCol, targetRow) ?: return emptyList()
    }

    val gc = resolvedGoal.col
    val gr = resolvedGoal.row
    if (sc == gc && sr == gr) return emptyList()

    val total = zone.cols * zone.rows
    val gScore = FloatArray(total) { Float.POSITIVE_INFINITY }
    val parent = IntArray(total) { -1 }
    val closed = BooleanArray(total)

    val startIdx = sr * zone.cols + sc
    val goalIdx = gr * zone.cols + gc
    gScore[startIdx] = 0f

    val open = PriorityQueue<AStarNode>()
    open.add(AStarNode(sc, sr, 0f, octileHeuristic(sc, sr, gc, gr)))

    while (open.isNotEmpty()) {
      val cur = open.poll() ?: break
      val curIdx = cur.row * zone.cols + cur.col
      if (curIdx == goalIdx) {
        return reconstructPath(parent, startIdx, goalIdx, zone.cols)
      }
      if (closed[curIdx]) continue
      closed[curIdx] = true

      for ((dc, dr) in allDirs) {
        val nc = cur.col + dc
        val nr = cur.row + dr
        if (nc !in 0 until zone.cols || nr !in 0 until zone.rows) continue

        if (!ignoreSolid) {
          if (isBlocked(zone, nc, nr)) continue
          if (dc != 0 && dr != 0) {
            // Prevent diagonal corner clipping across adjacent solid tiles
            if (isBlocked(zone, cur.col + dc, cur.row) || isBlocked(zone, cur.col, cur.row + dr)) {
              continue
            }
          }
        }

        val nIdx = nr * zone.cols + nc
        if (closed[nIdx]) continue

        val stepCost = if (dc != 0 && dr != 0) 1.4142135f else 1f
        val tentativeG = gScore[curIdx] + stepCost
        if (tentativeG < gScore[nIdx]) {
          gScore[nIdx] = tentativeG
          parent[nIdx] = curIdx
          val f = tentativeG + octileHeuristic(nc, nr, gc, gr)
          open.add(AStarNode(nc, nr, tentativeG, f))
        }
      }
    }

    return emptyList()
  }

  private fun octileHeuristic(c1: Int, r1: Int, c2: Int, r2: Int): Float {
    val dx = abs(c2 - c1)
    val dy = abs(r2 - r1)
    return max(dx, dy) + 0.4142135f * min(dx, dy)
  }

  private fun reconstructPath(
    parent: IntArray,
    startIdx: Int,
    goalIdx: Int,
    cols: Int,
  ): List<TilePoint> {
    val reversed = mutableListOf<TilePoint>()
    var cur = goalIdx
    while (cur != -1 && cur != startIdx) {
      reversed += TilePoint(col = cur % cols, row = cur / cols)
      cur = parent[cur]
    }
    reversed.reverse()
    return reversed
  }
}
