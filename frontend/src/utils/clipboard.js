/**
 * 复制文本到剪贴板。
 * 优先使用 Clipboard API（需要页面处于聚焦状态且为安全上下文），
 * 失败时降级到临时 textarea + execCommand，兼容窗口未聚焦等场景。
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyText(text) {
  if (text == null || text === '') return false
  try {
    if (navigator.clipboard && document.hasFocus()) {
      await navigator.clipboard.writeText(String(text))
      return true
    }
  } catch {
    /* 继续走降级方案 */
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = String(text)
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.top = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, ta.value.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
