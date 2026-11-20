package com.jjchess.helper.core

/**
 * ============================================================================
 * 所有可调阈值集中在此文件，便于按真实截图/真机调参。
 * 每个常量均注明含义、单位与调整方向。
 * 默认值来自一张 1264x2780 的 JJ 象棋揭棋开局截图（棋盘完整、无弹窗）。
 * 坐标系：屏幕像素，左上为 (0,0)。棋盘为 9 列 x 10 行交叉点。
 * ============================================================================
 */
object Const {

    // -------------------- 一、棋盘几何（归一化比例，0..1）--------------------
    // 采用比例存储，使不同分辨率屏幕通用。运行时乘以实际截图宽高得到像素。
    // 从截图测得：9 列中心 x≈[92,227,362,498,633,769,904,1040,1175]，列距≈135px
    //              10 行中心 y≈[804,936,...,1992]，行距≈132px，图宽1264 高2780
    // 首列中心比例 = 92/1264 = 0.0728；列距比例 = 135/1264 = 0.1068
    // 首行中心比例 = 804/2780 = 0.2892；行距比例 = 132/2780 = 0.04748
    const val BOARD_FIRST_COL_X_RATIO = 0.0728f   // 第0列交叉点中心 x / 截图宽
    const val BOARD_FIRST_ROW_Y_RATIO = 0.2892f   // 第0行交叉点中心 y / 截图高
    const val BOARD_COL_STEP_RATIO = 0.10680f     // 相邻列间距 / 截图宽
    const val BOARD_ROW_STEP_RATIO = 0.04748f     // 相邻行间距 / 截图高

    // 棋盘方向：真机截图里 row0(顶排)是红方帅，row9(底排)是黑方将 => 红在上、黑在下
    // 这决定了暗子按开局位置走时的"底线"和"前进方向"。若你的截图相反，把下面几项对调即可。
    const val BOARD_RED_ON_TOP = true            // true=红方在顶部(row0)；false=红方在底部(row9)
    const val BOARD_RED_BASE_ROW = 0             // 红方底线所在行（帅的初始行）
    const val BOARD_BLACK_BASE_ROW = 9           // 黑方底线所在行（将的初始行）
    const val RED_FORWARD = 1                    // 红方兵前进时 row 的变化量（在上方=+1 向下）
    const val BLACK_FORWARD = -1                 // 黑方兵前进时 row 的变化量
    const val RED_PALACE_ROW_MIN = 0             // 红方九宫行范围
    const val RED_PALACE_ROW_MAX = 2
    const val BLACK_PALACE_ROW_MIN = 7           // 黑方九宫行范围
    const val BLACK_PALACE_ROW_MAX = 9

    // 用户校准后可覆盖上面的默认比例（保存在 SharedPreferences，见 CalibrationStore）
    const val PREF_CALIB = "jjchess_calib"
    const val KEY_CALIB_CUSTOM = "calib_custom"
    const val KEY_FIRST_COL_X = "first_col_x_ratio"
    const val KEY_FIRST_ROW_Y = "first_row_y_ratio"
    const val KEY_COL_STEP = "col_step_ratio"
    const val KEY_ROW_STEP = "row_step_ratio"

    // -------------------- 二、棋子尺寸 --------------------
    const val PIECE_DIAMETER_RATIO = 0.095f       // 棋子直径 / 截图宽（截图约120px/1264≈0.095）
    const val PIECE_SAMPLE_RADIUS_RATIO = 0.028f  // 取棋子中心样本的半径 / 截图宽（约35px）
    const val PIECE_OCCUPIED_MIN_GOLD = 0.18f     // 判定"有子"：交叉点邻域金橙色像素占比下限

    // -------------------- 三、颜色阈值（RGB）--------------------
    // 棋子外环金橙色：截图采样中心色≈(223,182,121) 外环≈(229,196,146)
    const val GOLD_R_MIN = 165          // 金色像素 R 下限
    const val GOLD_G_MIN = 95           // 金色像素 G 下限
    const val GOLD_G_MAX = 210          // 金色像素 G 上限
    const val GOLD_B_MAX = 140          // 金色像素 B 上限
    const val GOLD_RB_DIFF_MIN = 55     // 金色像素 (R-B) 下限，区分金橙与灰绿棋盘

    // 棋盘背景（灰绿石板）采样≈(172,178,155)，用于自动检测边界时的背景判定
    const val BOARD_BG_R = 172
    const val BOARD_BG_G = 178
    const val BOARD_BG_B = 155
    const val BOARD_BG_TOLERANCE = 45  // 与背景色的最大通道差，用于边界检测

    // 红字识别：明子红色汉字。顶行红方暗子因底纹也偏红，需用红字占比区分
    const val RED_TEXT_R_MIN = 110
    const val RED_TEXT_R_MAX = 215
    const val RED_TEXT_G_MAX = 110
    const val RED_TEXT_B_MAX = 110
    const val RED_TEXT_RG_DIFF_MIN = 50 // (R-G) 下限
    // 明子判据：中心区域红字或黑字像素占比高，且金色占比明显低于暗子
    const val OPEN_GOLD_RATIO_MAX = 0.64f   // 明子中心金色像素占比上限（暗子≈0.78，明子≈0.23~0.49）
    const val OPEN_TEXT_RATIO_MIN = 0.055f  // 明子中心深色文字像素占比下限（真机明兵≈0.074，故下调）

    // 黑字识别：底行黑方暗子中心偏黑（黑字像素≈1100），红方暗子红字≈1070
    const val BLACK_TEXT_MAX = 110     // 黑字像素各通道上限

    // 红黑方判定：揭棋开局暗子底纹，红方棋子中心偏暖（R 明显 > B），黑方偏冷/暗
    // 顶行红方暗子中心≈(223,182,121)，底行黑方暗子中心偏暗
    const val SIDE_RED_RB_DIFF_MIN = 45 // 中心 (R-B) 大于此值判为红方，否则黑方
    const val SIDE_RED_BRIGHT_MIN = 150 // 中心平均亮度下限，过低视为黑方暗子

    // -------------------- 四、棋字模板匹配 --------------------
    // assets/templates/ 下的模板图命名：R_车.png R_马.png ... B_車.png ...
    // 模板可为空（仓库不含模板），空时按"未知暗子"处理。
    // 真机字模(与识别同一crop公式提取)自匹配 NCC=1.0，错误字模≈0.13，取 0.5 可靠区分
    const val TEMPLATE_MATCH_THRESHOLD = 0.50f  // 归一化互相关匹配相似度下限，低于则视为未知
    const val TEMPLATE_DIR = "templates"

    // -------------------- 五、识别触发与防抖 --------------------
    const val CAPTURE_STABLE_MS = 220L          // 画面连续稳定多久后触发识别（毫秒）
    const val CAPTURE_POLL_INTERVAL_MS = 120L   // 轮询/事件合并的基础间隔
    const val CAPTURE_DIFF_RATIO_THRESHOLD = 0.012f // 帧间像素变化比例超过此值视为"画面在变"
    const val CAPTURE_DIFF_SAMPLE_STEP = 8      // 帧差采样步长（每隔N像素取一个，降低开销）
    const val MAX_RECOGNIZE_RETRIES = 3         // 识别中画面变化导致中断的最大重试次数

    // 截图串行控制：takeScreenshot 并发/高频会被系统限流失败，必须单一入口串行调用
    const val SHOT_MIN_INTERVAL_MS = 220L       // 两次截图之间的最小间隔（低于系统限流阈值）
    const val SHOT_TIMEOUT_MS = 1500L           // 单次截图回调等待超时
    const val SHOT_MAX_RETRY = 3                // 单帧截图失败重试次数

    // -------------------- 六、自动落子 --------------------
    const val MOVE_TAP_START_DELAY_MS = 120L    // 点起点前的延时
    const val MOVE_TAP_GAP_MS = 260L            // 起点与终点点击之间的界面响应延时
    const val MOVE_TAP_END_DELAY_MS = 120L      // 点终点后的延时
    const val MOVE_GESTURE_DURATION_MS = 60L    // dispatchGesture 单次点击持续时间
    const val MOVE_MAX_RETRY = 2                // 单步点击失败重试次数
    const val MOVE_SETTLE_WAIT_MS = 500L        // 落子后等待画面稳定的时间

    // -------------------- 七、引擎与算力 --------------------
    const val ENGINE_ASSET_PATH = "engine/pikafish"       // assets 中引擎二进制相对路径
    const val ENGINE_NNUE_ASSET_PATH = "engine/pikafish.nnue" // NNUE 权重文件（引擎必需，缺则无法工作）
    const val ENGINE_NODES_SAVE = 2000           // 省电档 nodes 上限
    const val ENGINE_NODES_STD = 8000            // 标准档 nodes 上限
    const val ENGINE_NODES_FULL = 30000          // 火力档 nodes 上限
    const val ENGINE_MOVETIME_SAVE = 100         // 省电档 movetime(ms)
    const val ENGINE_MOVETIME_STD = 400          // 标准档 movetime(ms)
    const val ENGINE_MOVETIME_FULL = 1200        // 火力档 movetime(ms)
    const val ENGINE_SAMPLES_SAVE = 4            // 省电档暗子随机分配采样次数
    const val ENGINE_SAMPLES_STD = 8             // 标准档采样次数
    const val ENGINE_SAMPLES_FULL = 16           // 火力档采样次数
    const val ENGINE_STARTUP_TIMEOUT_MS = 5000L  // 引擎启动超时
    const val ENGINE_READ_TIMEOUT_MS = 15000L    // 等待引擎 bestmove 的超时

    // -------------------- 八、悬浮窗 --------------------
    const val OVERLAY_TEXT_SIZE_SP = 13f         // 悬浮窗文字大小
    const val OVERLAY_STROKE_WIDTH = 2f          // 白字黑边描边宽度(px)
    const val OVERLAY_DEFAULT_X_RATIO = 0.02f    // 悬浮窗默认 x / 屏宽
    const val OVERLAY_DEFAULT_Y_RATIO = 0.24f    // 悬浮窗默认 y / 屏高（棋盘上方空白区）
    const val OVERLAY_COLLAPSED_SIZE_DP = 44     // 折叠小圆点直径(dp)

    // -------------------- 九、暗子池 --------------------
    // 红方池：车马炮相士兵各二 + 明帅；黑方池：車馬砲象士卒各二 + 明將
    // 注意：揭棋共32子，红黑各16。帅/将开局为明子，故暗子各15。
    const val DARK_POOL_COUNT_PER_SIDE = 15
}
