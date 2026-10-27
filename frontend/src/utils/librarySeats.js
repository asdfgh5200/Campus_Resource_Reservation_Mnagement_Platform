// 图书馆多媒体自习室座位排布工具
// 6F / 7F 各 60 个座位，采用「多种形态分区组合」的平面排布：
//   · 长桌区、四人连排、双人组、靠窗单人位 —— 横向排布的条形座位
//   · 研讨卡座（田字格）—— 2×2 座位环绕共享桌面，上下两排面对面
// 座位号 01-60 与后端资源 roomNo（如 "6F-01"）一一对应。

const CELL = 58 // 座位格边长（正方形，便于 90° 旋转）
const GROUP_AISLE = 46 // 组间 / 区间过道宽度
const ROW_PITCH = 98 // 区块内相邻两排的纵向间距
const BLOCK_PAD = 30 // 区块内边距
const BLOCK_TITLE_H = 36 // 区块标题高度
const BLOCK_GAP = 44 // 区块之间的纵向间距
const PAGE_PAD = 40 // 画布四周留白

/** 横向一排座位的宽度 */
function rowWidth(count, groupSize) {
  const aisles = groupSize > 0 ? Math.floor((count - 1) / groupSize) : 0
  return count * CELL + aisles * GROUP_AISLE
}
/** 横向排列若干 2×2 研讨卡座的总宽度 */
function podsWidth(count) {
  return count * (2 * CELL) + (count - 1) * GROUP_AISLE
}

function newLayout() {
  return { seats: [], dividers: [], blocks: [] }
}
function makeCounter() {
  let i = 0
  return () => ++i
}

/**
 * 横向一排座位
 * @param groupSize 每多少座之后插入过道（0 表示全部紧邻）
 */
function addRow(L, { x, y, count, rot = 0, groupSize = 0, next }) {
  let sx = x
  for (let i = 0; i < count; i++) {
    L.seats.push({ index: next(), x: sx, y, size: CELL, rot })
    if (i < count - 1) {
      if (groupSize > 0 && (i + 1) % groupSize === 0) {
        sx += CELL + GROUP_AISLE
      } else {
        // 相邻座位之间的隔板
        L.dividers.push({ x: sx + CELL - 1.5, y: y + 2, w: 3, h: CELL - 4 })
        sx += CELL
      }
    }
  }
}

/** 2×2 研讨卡座：上排座位面朝下、下排座位面朝上，中间拼出共享桌面 */
function addPods(L, { x, y, count, next }) {
  let sx = x
  for (let p = 0; p < count; p++) {
    const bx = sx
    const by = y
    L.seats.push({ index: next(), x: bx, y: by, size: CELL, rot: 180 })
    L.seats.push({ index: next(), x: bx + CELL, y: by, size: CELL, rot: 180 })
    L.seats.push({ index: next(), x: bx, y: by + CELL, size: CELL, rot: 0 })
    L.seats.push({ index: next(), x: bx + CELL, y: by + CELL, size: CELL, rot: 0 })
    // 卡座左右外侧隔板
    L.dividers.push({ x: bx - 3, y: by + 2, w: 3, h: CELL * 2 - 4 })
    L.dividers.push({ x: bx + CELL * 2, y: by + 2, w: 3, h: CELL * 2 - 4 })
    // 上下两排之间的共享桌隔板
    L.dividers.push({ x: bx + 2, y: by + CELL - 1.5, w: CELL * 2 - 4, h: 3 })
    sx += CELL * 2 + GROUP_AISLE
  }
}

/** 统一计算区块位置、整体画布尺寸 */
function finalize(L, specs) {
  const maxInner = Math.max(...specs.map((s) => s.innerW))
  const width = PAGE_PAD * 2 + maxInner + BLOCK_PAD * 2
  let y = PAGE_PAD
  for (const sp of specs) {
    const boxX = PAGE_PAD + (maxInner - sp.innerW) / 2
    const boxW = sp.innerW + BLOCK_PAD * 2
    const boxH = BLOCK_TITLE_H + sp.contentH + BLOCK_PAD
    L.blocks.push({ label: sp.label, x: boxX, y, w: boxW, h: boxH })
    sp.draw(boxX + BLOCK_PAD, y + BLOCK_TITLE_H)
    y += boxH + BLOCK_GAP
  }
  return {
    seats: L.seats,
    dividers: L.dividers,
    blocks: L.blocks,
    width,
    height: y - BLOCK_GAP + PAGE_PAD
  }
}

// 6F：横向长桌 + 研讨卡座 + 双人组
function build6F() {
  const L = newLayout()
  const next = makeCounter()
  const specs = [
    {
      label: '长桌自习区 · 30 座',
      innerW: rowWidth(10, 5),
      contentH: 2 * ROW_PITCH + CELL,
      draw: (x, top) => {
        for (let r = 0; r < 3; r++) {
          addRow(L, { x, y: top + r * ROW_PITCH, count: 10, groupSize: 5, next })
        }
      }
    },
    {
      label: '研讨卡座区 · 16 座',
      innerW: podsWidth(4),
      contentH: CELL * 2,
      draw: (x, top) => addPods(L, { x, y: top, count: 4, next })
    },
    {
      label: '双人自习区 · 14 座',
      innerW: rowWidth(8, 2),
      contentH: ROW_PITCH + CELL,
      draw: (x, top) => {
        addRow(L, { x, y: top, count: 8, groupSize: 2, next })
        addRow(L, { x, y: top + ROW_PITCH, count: 6, groupSize: 2, next })
      }
    }
  ]
  return finalize(L, specs)
}

// 7F：四人连排 + 研讨卡座 + 靠窗单人位（与 6F 排布明显不同）
function build7F() {
  const L = newLayout()
  const next = makeCounter()
  const specs = [
    {
      label: '四人连排区 · 16 座',
      innerW: rowWidth(8, 4),
      contentH: ROW_PITCH + CELL,
      draw: (x, top) => {
        addRow(L, { x, y: top, count: 8, groupSize: 4, next })
        addRow(L, { x, y: top + ROW_PITCH, count: 8, groupSize: 4, next })
      }
    },
    {
      label: '研讨卡座区 · 24 座',
      innerW: podsWidth(3),
      contentH: ROW_PITCH + CELL * 2,
      draw: (x, top) => {
        addPods(L, { x, y: top, count: 3, next })
        addPods(L, { x, y: top + ROW_PITCH, count: 3, next })
      }
    },
    {
      label: '靠窗单人区 · 20 座',
      innerW: rowWidth(10, 5),
      contentH: ROW_PITCH + CELL,
      draw: (x, top) => {
        addRow(L, { x, y: top, count: 10, groupSize: 5, next })
        addRow(L, { x, y: top + ROW_PITCH, count: 10, groupSize: 5, next })
      }
    }
  ]
  return finalize(L, specs)
}

/**
 * 生成某楼层座位排布
 * @param {string} floor '6F' | '7F'
 */
export function buildSeatLayout(floor) {
  return floor === '7F' ? build7F() : build6F()
}

/** 座位序号 → 座位号文本（与后端 roomNo 一致，如 1 → "6F-01"） */
export function seatRoomNo(floor, index) {
  return `${floor}-${String(index).padStart(2, '0')}`
}
