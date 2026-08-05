/**
 * 中文 strings.
 *
 * The machinery ships from the first commit; the Chinese voice pass is its own task (spec §3), so
 * these are a working first cut rather than final copy. Anything missing falls back to English
 * rather than showing a key, which is why `fallbackLocale` is set.
 */
export default {
  common: {
    all: '全部',
    cancel: '取消',
    done: '暂时完成',
    save: '保存',
    settled: '已结清',
  },
  money: {
    youAreOwed: '待收',
    youOwe: '待付',
    allSquare: '已结清',
  },
  settle: {
    pay: '支付',
    remind: '提醒',
    sentForConfirmation: '已发送给{name}确认',
    owesYou: '{name}欠你',
    youOweThem: '你欠{name}',
  },
  payback: {
    pending: '等待{name}确认',
    approved: '已确认',
    rejected: '需要重新确认',
  },
} as const
