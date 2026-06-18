package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.Charset
import kotlin.math.min

/**
 * Han Xin Code (汉信码) encoder.
 *
 * Implemented from ISO/IEC 20830:2021 with extensive reference to the Zint
 * backend (BSD-3-Clause) and the AIM Han Xin Code ITS.
 */
object HanXinEncoder {

    private const val TAG = "HanXinEncoder"

    // Versions 1..84, symbol size = version * 2 + 21
    private const val VERSION_COUNT = 84
    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 84

    // Error correction levels 1..4
    private const val ECC_L1 = 1
    private const val ECC_L2 = 2
    private const val ECC_L3 = 3
    private const val ECC_L4 = 4

    // Mode indices used by the dynamic-programming optimizer
    private const val MODE_N = 0 // Numeric
    private const val MODE_T = 1 // Text
    private const val MODE_B = 2 // Binary / Byte
    private const val MODE_1 = 3 // Common Chinese Region 1
    private const val MODE_2 = 4 // Common Chinese Region 2
    private const val MODE_D = 5 // GB18030 2-byte
    private const val MODE_F = 6 // GB18030 4-byte
    private const val MODE_COUNT = 7

    private val MODE_CHARS = charArrayOf('n', 't', 'b', '1', '2', 'd', 'f')

    // Table B.1 - total codewords per version
    internal val TOTAL_CODEWORDS = intArrayOf(
        25, 37, 50, 54, 69, 84, 100, 117, 136, 155,
        161, 181, 203, 225, 249, 273, 299, 325, 353, 381,
        411, 422, 453, 485, 518, 552, 587, 623, 660, 698,
        737, 754, 794, 836, 878, 922, 966, 1011, 1058, 1105,
        1126, 1175, 1224, 1275, 1327, 1380, 1434, 1489, 1513, 1569,
        1628, 1686, 1745, 1805, 1867, 1929, 1992, 2021, 2086, 2151,
        2218, 2286, 2355, 2425, 2496, 2528, 2600, 2673, 2749, 2824,
        2900, 2977, 3056, 3135, 3171, 3252, 3334, 3416, 3500, 3585,
        3671, 3758, 3798, 3886
    )

    // Table B.1 - data codewords per ECC level [ecc-1][version-1]
    internal val DATA_CODEWORDS = arrayOf(
        intArrayOf(
            21, 31, 42, 46, 57, 70, 84, 99, 114, 131,
            135, 153, 171, 189, 209, 229, 251, 273, 297, 321,
            345, 354, 381, 407, 436, 464, 493, 523, 554, 586,
            619, 634, 666, 702, 738, 774, 812, 849, 888, 929,
            946, 987, 1028, 1071, 1115, 1160, 1204, 1251, 1271, 1317,
            1368, 1416, 1465, 1517, 1569, 1621, 1674, 1697, 1752, 1807,
            1864, 1920, 1979, 2037, 2096, 2124, 2184, 2245, 2309, 2372,
            2436, 2501, 2568, 2633, 2663, 2732, 2800, 2870, 2940, 3011,
            3083, 3156, 3190, 3264
        ),
        intArrayOf(
            17, 25, 34, 38, 49, 58, 70, 81, 96, 109,
            113, 127, 143, 157, 175, 191, 209, 227, 247, 267,
            287, 296, 317, 339, 362, 386, 411, 437, 462, 488,
            515, 528, 556, 586, 614, 646, 676, 707, 740, 773,
            788, 823, 856, 893, 929, 966, 1004, 1043, 1059, 1099,
            1140, 1180, 1221, 1263, 1307, 1351, 1394, 1415, 1460, 1505,
            1552, 1600, 1649, 1697, 1748, 1770, 1820, 1871, 1925, 1976,
            2030, 2083, 2140, 2195, 2219, 2276, 2334, 2392, 2450, 2509,
            2569, 2630, 2658, 2720
        ),
        intArrayOf(
            13, 19, 26, 30, 37, 46, 54, 63, 74, 83,
            87, 97, 109, 121, 135, 147, 161, 175, 191, 205,
            221, 228, 245, 261, 280, 298, 317, 337, 358, 376,
            397, 408, 428, 452, 474, 498, 522, 545, 572, 597,
            608, 635, 660, 689, 717, 746, 774, 805, 817, 847,
            880, 910, 943, 975, 1009, 1041, 1076, 1091, 1126, 1161,
            1198, 1234, 1271, 1309, 1348, 1366, 1404, 1443, 1485, 1524,
            1566, 1607, 1650, 1693, 1713, 1756, 1800, 1844, 1890, 1935,
            1983, 2030, 2050, 2098
        ),
        intArrayOf(
            9, 15, 20, 22, 27, 34, 40, 47, 54, 61,
            65, 73, 81, 89, 99, 109, 119, 129, 141, 153,
            165, 168, 181, 195, 208, 220, 235, 251, 264, 280,
            295, 302, 318, 334, 352, 368, 386, 405, 424, 441,
            450, 469, 490, 509, 531, 552, 574, 595, 605, 627,
            652, 674, 697, 721, 747, 771, 796, 809, 834, 861,
            892, 914, 941, 969, 998, 1012, 1040, 1069, 1099, 1130,
            1160, 1191, 1222, 1253, 1269, 1300, 1334, 1366, 1400, 1433,
            1469, 1504, 1520, 1554
        )
    )

    // Annex A - module parameters k, r, m
    internal val MODULE_K = intArrayOf(
        0, 0, 0, 14, 16, 16, 17, 18, 19, 20,
        14, 15, 16, 16, 17, 17, 18, 19, 20, 20,
        21, 16, 17, 17, 18, 18, 19, 19, 20, 20,
        21, 17, 17, 18, 18, 19, 19, 19, 20, 20,
        17, 17, 18, 18, 18, 19, 19, 19, 17, 17,
        18, 18, 18, 18, 19, 19, 19, 17, 17, 18,
        18, 18, 18, 19, 19, 17, 17, 17, 18, 18,
        18, 18, 19, 19, 17, 17, 17, 18, 18, 18,
        18, 18, 17, 17
    )

    internal val MODULE_R = intArrayOf(
        0, 0, 0, 15, 15, 17, 18, 19, 20, 21,
        15, 15, 15, 17, 17, 19, 19, 19, 19, 21,
        21, 17, 16, 18, 17, 19, 18, 20, 19, 21,
        20, 17, 19, 17, 19, 17, 19, 21, 19, 21,
        18, 20, 17, 19, 21, 18, 20, 22, 17, 19,
        15, 17, 19, 21, 17, 19, 21, 18, 20, 15,
        17, 19, 21, 16, 18, 17, 19, 21, 15, 17,
        19, 21, 15, 17, 18, 20, 22, 15, 17, 19,
        21, 23, 17, 19
    )

    internal val MODULE_M = intArrayOf(
        0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 3, 3, 3, 3, 3, 3, 3, 3, 3,
        3, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        5, 5, 5, 5, 5, 5, 5, 5, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 7, 7, 7,
        7, 7, 7, 7, 7, 8, 8, 8, 8, 8,
        8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
        9, 9, 10, 10
    )

    // Table D.1 - RS block definitions flattened as (batchSize, dataLength, eccLength) triples
    // for each version and ECC level: index = (version - 1) * 36 + (eccLevel - 1) * 9 + group * 3
    internal val RS_TABLE_D1 = intArrayOf(
        1, 21, 4, 0, 0, 0, 0, 0, 0,
        1, 17, 8, 0, 0, 0, 0, 0, 0,
        1, 13, 12, 0, 0, 0, 0, 0, 0,
        1, 9, 16, 0, 0, 0, 0, 0, 0,
        1, 31, 6, 0, 0, 0, 0, 0, 0,
        1, 25, 12, 0, 0, 0, 0, 0, 0,
        1, 19, 18, 0, 0, 0, 0, 0, 0,
        1, 15, 22, 0, 0, 0, 0, 0, 0,
        1, 42, 8, 0, 0, 0, 0, 0, 0,
        1, 34, 16, 0, 0, 0, 0, 0, 0,
        1, 26, 24, 0, 0, 0, 0, 0, 0,
        1, 20, 30, 0, 0, 0, 0, 0, 0,
        1, 46, 8, 0, 0, 0, 0, 0, 0,
        1, 38, 16, 0, 0, 0, 0, 0, 0,
        1, 30, 24, 0, 0, 0, 0, 0, 0,
        1, 22, 32, 0, 0, 0, 0, 0, 0,
        1, 57, 12, 0, 0, 0, 0, 0, 0,
        1, 49, 20, 0, 0, 0, 0, 0, 0,
        1, 37, 32, 0, 0, 0, 0, 0, 0,
        1, 14, 20, 1, 13, 22, 0, 0, 0,
        1, 70, 14, 0, 0, 0, 0, 0, 0,
        1, 58, 26, 0, 0, 0, 0, 0, 0,
        1, 24, 20, 1, 22, 18, 0, 0, 0,
        1, 16, 24, 1, 18, 26, 0, 0, 0,
        1, 84, 16, 0, 0, 0, 0, 0, 0,
        1, 70, 30, 0, 0, 0, 0, 0, 0,
        1, 26, 22, 1, 28, 24, 0, 0, 0,
        2, 14, 20, 1, 12, 20, 0, 0, 0,
        1, 99, 18, 0, 0, 0, 0, 0, 0,
        1, 40, 18, 1, 41, 18, 0, 0, 0,
        1, 31, 26, 1, 32, 28, 0, 0, 0,
        2, 16, 24, 1, 15, 22, 0, 0, 0,
        1, 114, 22, 0, 0, 0, 0, 0, 0,
        2, 48, 20, 0, 0, 0, 0, 0, 0,
        2, 24, 20, 1, 26, 22, 0, 0, 0,
        2, 18, 28, 1, 18, 26, 0, 0, 0,
        1, 131, 24, 0, 0, 0, 0, 0, 0,
        1, 52, 22, 1, 57, 24, 0, 0, 0,
        2, 27, 24, 1, 29, 24, 0, 0, 0,
        2, 21, 32, 1, 19, 30, 0, 0, 0,
        1, 135, 26, 0, 0, 0, 0, 0, 0,
        1, 56, 24, 1, 57, 24, 0, 0, 0,
        2, 28, 24, 1, 31, 26, 0, 0, 0,
        2, 22, 32, 1, 21, 32, 0, 0, 0,
        1, 153, 28, 0, 0, 0, 0, 0, 0,
        1, 62, 26, 1, 65, 28, 0, 0, 0,
        2, 32, 28, 1, 33, 28, 0, 0, 0,
        3, 17, 26, 1, 22, 30, 0, 0, 0,
        1, 86, 16, 1, 85, 16, 0, 0, 0,
        1, 71, 30, 1, 72, 30, 0, 0, 0,
        2, 37, 32, 1, 35, 30, 0, 0, 0,
        3, 20, 30, 1, 21, 32, 0, 0, 0,
        1, 94, 18, 1, 95, 18, 0, 0, 0,
        2, 51, 22, 1, 55, 24, 0, 0, 0,
        3, 30, 26, 1, 31, 26, 0, 0, 0,
        4, 18, 28, 1, 17, 24, 0, 0, 0,
        1, 104, 20, 1, 105, 20, 0, 0, 0,
        2, 57, 24, 1, 61, 26, 0, 0, 0,
        3, 33, 28, 1, 36, 30, 0, 0, 0,
        4, 20, 30, 1, 19, 30, 0, 0, 0,
        1, 115, 22, 1, 114, 22, 0, 0, 0,
        2, 65, 28, 1, 61, 26, 0, 0, 0,
        3, 38, 32, 1, 33, 30, 0, 0, 0,
        5, 19, 28, 1, 14, 24, 0, 0, 0,
        1, 126, 24, 1, 125, 24, 0, 0, 0,
        2, 70, 30, 1, 69, 30, 0, 0, 0,
        4, 33, 28, 1, 29, 26, 0, 0, 0,
        5, 20, 30, 1, 19, 30, 0, 0, 0,
        1, 136, 26, 1, 137, 26, 0, 0, 0,
        3, 56, 24, 1, 59, 26, 0, 0, 0,
        5, 35, 30, 0, 0, 0, 0, 0, 0,
        6, 18, 28, 1, 21, 28, 0, 0, 0,
        1, 148, 28, 1, 149, 28, 0, 0, 0,
        3, 61, 26, 1, 64, 28, 0, 0, 0,
        7, 24, 20, 1, 23, 22, 0, 0, 0,
        6, 20, 30, 1, 21, 32, 0, 0, 0,
        3, 107, 20, 0, 0, 0, 0, 0, 0,
        3, 65, 28, 1, 72, 30, 0, 0, 0,
        7, 26, 22, 1, 23, 22, 0, 0, 0,
        7, 19, 28, 1, 20, 32, 0, 0, 0,
        3, 115, 22, 0, 0, 0, 0, 0, 0,
        4, 56, 24, 1, 63, 28, 0, 0, 0,
        7, 28, 24, 1, 25, 22, 0, 0, 0,
        8, 18, 28, 1, 21, 22, 0, 0, 0,
        2, 116, 22, 1, 122, 24, 0, 0, 0,
        4, 56, 24, 1, 72, 30, 0, 0, 0,
        7, 28, 24, 1, 32, 26, 0, 0, 0,
        8, 18, 28, 1, 24, 30, 0, 0, 0,
        3, 127, 24, 0, 0, 0, 0, 0, 0,
        5, 51, 22, 1, 62, 26, 0, 0, 0,
        7, 30, 26, 1, 35, 26, 0, 0, 0,
        8, 20, 30, 1, 21, 32, 0, 0, 0,
        2, 135, 26, 1, 137, 26, 0, 0, 0,
        5, 56, 24, 1, 59, 26, 0, 0, 0,
        7, 33, 28, 1, 30, 28, 0, 0, 0,
        11, 16, 24, 1, 19, 26, 0, 0, 0,
        3, 105, 20, 1, 121, 22, 0, 0, 0,
        5, 61, 26, 1, 57, 26, 0, 0, 0,
        9, 28, 24, 1, 28, 22, 0, 0, 0,
        10, 19, 28, 1, 18, 30, 0, 0, 0,
        2, 157, 30, 1, 150, 28, 0, 0, 0,
        5, 65, 28, 1, 61, 26, 0, 0, 0,
        8, 33, 28, 1, 34, 30, 0, 0, 0,
        10, 19, 28, 2, 15, 26, 0, 0, 0,
        3, 126, 24, 1, 115, 22, 0, 0, 0,
        7, 51, 22, 1, 54, 22, 0, 0, 0,
        8, 35, 30, 1, 37, 30, 0, 0, 0,
        15, 15, 22, 1, 10, 22, 0, 0, 0,
        4, 105, 20, 1, 103, 20, 0, 0, 0,
        7, 56, 24, 1, 45, 18, 0, 0, 0,
        10, 31, 26, 1, 27, 26, 0, 0, 0,
        10, 17, 26, 3, 20, 28, 1, 21, 28,
        3, 139, 26, 1, 137, 28, 0, 0, 0,
        6, 66, 28, 1, 66, 30, 0, 0, 0,
        9, 36, 30, 1, 34, 32, 0, 0, 0,
        13, 19, 28, 1, 17, 32, 0, 0, 0,
        6, 84, 16, 1, 82, 16, 0, 0, 0,
        6, 70, 30, 1, 68, 30, 0, 0, 0,
        7, 35, 30, 3, 33, 28, 1, 32, 28,
        13, 20, 30, 1, 20, 28, 0, 0, 0,
        5, 105, 20, 1, 94, 18, 0, 0, 0,
        6, 74, 32, 1, 71, 30, 0, 0, 0,
        11, 33, 28, 1, 34, 32, 0, 0, 0,
        13, 19, 28, 3, 16, 26, 0, 0, 0,
        4, 127, 24, 1, 126, 24, 0, 0, 0,
        7, 66, 28, 1, 66, 30, 0, 0, 0,
        12, 30, 24, 1, 24, 28, 1, 24, 30,
        15, 19, 28, 1, 17, 32, 0, 0, 0,
        7, 84, 16, 1, 78, 16, 0, 0, 0,
        7, 70, 30, 1, 66, 28, 0, 0, 0,
        12, 33, 28, 1, 32, 30, 0, 0, 0,
        14, 21, 32, 1, 24, 28, 0, 0, 0,
        5, 117, 22, 1, 117, 24, 0, 0, 0,
        8, 66, 28, 1, 58, 26, 0, 0, 0,
        11, 38, 32, 1, 34, 32, 0, 0, 0,
        15, 20, 30, 2, 17, 26, 0, 0, 0,
        4, 148, 28, 1, 146, 28, 0, 0, 0,
        8, 68, 30, 1, 70, 24, 0, 0, 0,
        10, 36, 32, 3, 38, 28, 0, 0, 0,
        16, 19, 28, 3, 16, 26, 0, 0, 0,
        4, 126, 24, 2, 135, 26, 0, 0, 0,
        8, 70, 28, 2, 43, 26, 0, 0, 0,
        13, 32, 28, 2, 41, 30, 0, 0, 0,
        17, 19, 28, 3, 15, 26, 0, 0, 0,
        5, 136, 26, 1, 132, 24, 0, 0, 0,
        5, 67, 30, 4, 68, 28, 1, 69, 28,
        14, 35, 30, 1, 32, 24, 0, 0, 0,
        18, 18, 26, 3, 16, 28, 1, 14, 28,
        3, 142, 26, 3, 141, 28, 0, 0, 0,
        8, 70, 30, 1, 73, 32, 1, 74, 32,
        12, 34, 30, 3, 34, 26, 1, 35, 28,
        18, 21, 32, 1, 27, 30, 0, 0, 0,
        5, 116, 22, 2, 103, 20, 1, 102, 20,
        9, 74, 32, 1, 74, 30, 0, 0, 0,
        14, 34, 28, 2, 32, 32, 1, 32, 30,
        19, 21, 32, 1, 25, 26, 0, 0, 0,
        7, 116, 22, 1, 117, 22, 0, 0, 0,
        11, 65, 28, 1, 58, 24, 0, 0, 0,
        15, 38, 32, 1, 27, 28, 0, 0, 0,
        20, 20, 30, 1, 20, 32, 1, 21, 32,
        6, 136, 26, 1, 130, 24, 0, 0, 0,
        11, 66, 28, 1, 62, 30, 0, 0, 0,
        14, 34, 28, 3, 34, 32, 1, 30, 30,
        18, 20, 30, 3, 20, 28, 2, 15, 26,
        5, 105, 20, 2, 115, 22, 2, 116, 22,
        10, 75, 32, 1, 73, 32, 0, 0, 0,
        16, 38, 32, 1, 27, 28, 0, 0, 0,
        22, 19, 28, 2, 16, 30, 1, 19, 30,
        6, 147, 28, 1, 146, 28, 0, 0, 0,
        11, 66, 28, 2, 65, 30, 0, 0, 0,
        18, 33, 28, 2, 33, 30, 0, 0, 0,
        22, 21, 32, 1, 28, 30, 0, 0, 0,
        6, 116, 22, 3, 125, 24, 0, 0, 0,
        11, 75, 32, 1, 68, 30, 0, 0, 0,
        13, 35, 28, 6, 34, 32, 1, 30, 30,
        23, 21, 32, 1, 26, 30, 0, 0, 0,
        7, 105, 20, 4, 95, 18, 0, 0, 0,
        12, 67, 28, 1, 63, 30, 1, 62, 32,
        21, 31, 26, 2, 33, 32, 0, 0, 0,
        23, 21, 32, 2, 24, 30, 0, 0, 0,
        10, 116, 22, 0, 0, 0, 0, 0, 0,
        12, 74, 32, 1, 78, 30, 0, 0, 0,
        18, 37, 32, 1, 39, 30, 1, 41, 28,
        25, 21, 32, 1, 27, 28, 0, 0, 0,
        5, 126, 24, 4, 115, 22, 1, 114, 22,
        12, 67, 28, 2, 66, 32, 1, 68, 30,
        21, 35, 30, 1, 39, 30, 0, 0, 0,
        26, 21, 32, 1, 28, 28, 0, 0, 0,
        9, 126, 24, 1, 117, 22, 0, 0, 0,
        13, 75, 32, 1, 68, 30, 0, 0, 0,
        20, 35, 30, 3, 35, 28, 0, 0, 0,
        27, 21, 32, 1, 28, 30, 0, 0, 0,
        9, 126, 24, 1, 137, 26, 0, 0, 0,
        13, 71, 30, 2, 68, 32, 0, 0, 0,
        20, 37, 32, 1, 39, 28, 1, 38, 28,
        24, 20, 32, 5, 25, 28, 0, 0, 0,
        8, 147, 28, 1, 141, 28, 0, 0, 0,
        10, 73, 32, 4, 74, 30, 1, 73, 30,
        16, 36, 32, 6, 39, 30, 1, 37, 30,
        27, 21, 32, 3, 20, 26, 0, 0, 0,
        9, 137, 26, 1, 135, 26, 0, 0, 0,
        12, 70, 30, 4, 75, 32, 0, 0, 0,
        24, 35, 30, 1, 40, 28, 0, 0, 0,
        23, 20, 32, 8, 24, 30, 0, 0, 0,
        14, 95, 18, 1, 86, 18, 0, 0, 0,
        13, 73, 32, 3, 77, 30, 0, 0, 0,
        24, 35, 30, 2, 35, 28, 0, 0, 0,
        26, 21, 32, 5, 21, 30, 1, 23, 30,
        9, 147, 28, 1, 142, 28, 0, 0, 0,
        10, 73, 30, 6, 70, 32, 1, 71, 32,
        25, 35, 30, 2, 34, 26, 0, 0, 0,
        29, 21, 32, 4, 22, 30, 0, 0, 0,
        11, 126, 24, 1, 131, 24, 0, 0, 0,
        16, 74, 32, 1, 79, 30, 0, 0, 0,
        25, 38, 32, 1, 25, 30, 0, 0, 0,
        33, 21, 32, 1, 28, 28, 0, 0, 0,
        14, 105, 20, 1, 99, 18, 0, 0, 0,
        19, 65, 28, 1, 72, 28, 0, 0, 0,
        24, 37, 32, 2, 40, 30, 1, 41, 30,
        31, 21, 32, 4, 24, 32, 0, 0, 0,
        10, 147, 28, 1, 151, 28, 0, 0, 0,
        15, 71, 30, 3, 71, 32, 1, 73, 32,
        24, 37, 32, 3, 38, 30, 1, 39, 30,
        36, 19, 30, 3, 29, 26, 0, 0, 0,
        15, 105, 20, 1, 99, 18, 0, 0, 0,
        19, 70, 30, 1, 64, 28, 0, 0, 0,
        27, 38, 32, 2, 25, 26, 0, 0, 0,
        38, 20, 30, 2, 18, 28, 0, 0, 0,
        14, 105, 20, 1, 113, 22, 1, 114, 22,
        17, 67, 30, 3, 92, 32, 0, 0, 0,
        30, 35, 30, 1, 41, 30, 0, 0, 0,
        36, 21, 32, 1, 26, 30, 1, 27, 30,
        11, 146, 28, 1, 146, 26, 0, 0, 0,
        20, 70, 30, 1, 60, 26, 0, 0, 0,
        29, 38, 32, 1, 24, 32, 0, 0, 0,
        40, 20, 30, 2, 17, 26, 0, 0, 0,
        3, 137, 26, 1, 136, 26, 10, 126, 24,
        22, 65, 28, 1, 75, 30, 0, 0, 0,
        30, 37, 32, 1, 51, 30, 0, 0, 0,
        42, 20, 30, 1, 21, 30, 0, 0, 0,
        12, 126, 24, 2, 118, 22, 1, 116, 22,
        19, 74, 32, 1, 74, 30, 1, 72, 28,
        30, 38, 32, 2, 29, 30, 0, 0, 0,
        39, 20, 32, 2, 37, 26, 1, 38, 26,
        12, 126, 24, 3, 136, 26, 0, 0, 0,
        21, 70, 30, 2, 65, 28, 0, 0, 0,
        34, 35, 30, 1, 44, 32, 0, 0, 0,
        42, 20, 30, 2, 19, 28, 2, 18, 28,
        12, 126, 24, 3, 117, 22, 1, 116, 22,
        25, 61, 26, 2, 62, 28, 0, 0, 0,
        34, 35, 30, 1, 40, 32, 1, 41, 32,
        45, 20, 30, 1, 20, 32, 1, 21, 32,
        15, 105, 20, 2, 115, 22, 2, 116, 22,
        25, 65, 28, 1, 72, 28, 0, 0, 0,
        18, 35, 30, 17, 37, 32, 1, 50, 32,
        42, 20, 30, 6, 19, 28, 1, 15, 28,
        19, 105, 20, 1, 101, 20, 0, 0, 0,
        33, 51, 22, 1, 65, 22, 0, 0, 0,
        40, 33, 28, 1, 28, 28, 0, 0, 0,
        49, 20, 30, 1, 18, 28, 0, 0, 0,
        18, 105, 20, 2, 117, 22, 0, 0, 0,
        26, 65, 28, 1, 80, 30, 0, 0, 0,
        35, 35, 30, 3, 35, 28, 1, 36, 28,
        52, 18, 28, 2, 38, 30, 0, 0, 0,
        26, 84, 16, 0, 0, 0, 0, 0, 0,
        26, 70, 30, 0, 0, 0, 0, 0, 0,
        45, 31, 26, 1, 9, 26, 0, 0, 0,
        52, 20, 30, 0, 0, 0, 0, 0, 0,
        16, 126, 24, 1, 114, 22, 1, 115, 22,
        23, 70, 30, 3, 65, 28, 1, 66, 28,
        40, 35, 30, 1, 43, 30, 0, 0, 0,
        46, 20, 30, 7, 19, 28, 1, 16, 28,
        19, 116, 22, 1, 105, 22, 0, 0, 0,
        20, 70, 30, 7, 66, 28, 1, 63, 28,
        40, 35, 30, 1, 42, 32, 1, 43, 32,
        54, 20, 30, 1, 19, 30, 0, 0, 0,
        17, 126, 24, 2, 115, 22, 0, 0, 0,
        24, 70, 30, 4, 74, 32, 0, 0, 0,
        48, 31, 26, 2, 18, 26, 0, 0, 0,
        54, 19, 28, 6, 15, 26, 1, 14, 26,
        29, 84, 16, 0, 0, 0, 0, 0, 0,
        29, 70, 30, 0, 0, 0, 0, 0, 0,
        6, 34, 30, 3, 36, 30, 38, 33, 28,
        58, 20, 30, 0, 0, 0, 0, 0, 0,
        16, 147, 28, 1, 149, 28, 0, 0, 0,
        31, 66, 28, 1, 37, 26, 0, 0, 0,
        48, 33, 28, 1, 23, 26, 0, 0, 0,
        53, 20, 30, 6, 19, 28, 1, 17, 28,
        20, 115, 22, 2, 134, 24, 0, 0, 0,
        29, 66, 28, 2, 56, 26, 2, 57, 26,
        45, 36, 30, 2, 15, 28, 0, 0, 0,
        59, 20, 30, 2, 21, 32, 0, 0, 0,
        17, 147, 28, 1, 134, 26, 0, 0, 0,
        26, 70, 30, 5, 75, 32, 0, 0, 0,
        47, 35, 30, 1, 48, 32, 0, 0, 0,
        64, 18, 28, 2, 33, 30, 1, 35, 30,
        22, 115, 22, 1, 133, 24, 0, 0, 0,
        33, 65, 28, 1, 74, 28, 0, 0, 0,
        43, 36, 30, 5, 27, 28, 1, 30, 28,
        57, 20, 30, 5, 21, 32, 1, 24, 32,
        18, 136, 26, 2, 142, 26, 0, 0, 0,
        33, 66, 28, 2, 49, 26, 0, 0, 0,
        48, 35, 30, 2, 38, 28, 0, 0, 0,
        64, 20, 30, 1, 20, 32, 0, 0, 0,
        19, 126, 24, 2, 135, 26, 1, 136, 26,
        32, 66, 28, 2, 55, 26, 2, 56, 26,
        49, 36, 30, 2, 18, 32, 0, 0, 0,
        65, 18, 28, 5, 27, 30, 1, 29, 30,
        20, 137, 26, 1, 130, 26, 0, 0, 0,
        30, 75, 32, 2, 71, 32, 0, 0, 0,
        46, 35, 30, 6, 39, 32, 0, 0, 0,
        3, 12, 30, 70, 19, 28, 0, 0, 0,
        20, 147, 28, 0, 0, 0, 0, 0, 0,
        35, 70, 30, 0, 0, 0, 0, 0, 0,
        49, 35, 30, 5, 35, 28, 0, 0, 0,
        70, 20, 30, 0, 0, 0, 0, 0, 0,
        21, 136, 26, 1, 155, 28, 0, 0, 0,
        34, 70, 30, 1, 64, 28, 1, 65, 28,
        54, 35, 30, 1, 45, 30, 0, 0, 0,
        68, 20, 30, 3, 18, 28, 1, 19, 28,
        19, 126, 24, 5, 115, 22, 1, 114, 22,
        33, 70, 30, 3, 65, 28, 1, 64, 28,
        52, 35, 30, 3, 41, 32, 1, 40, 32,
        67, 20, 30, 5, 21, 32, 1, 24, 32,
        2, 150, 28, 21, 136, 26, 0, 0, 0,
        32, 70, 30, 6, 65, 28, 0, 0, 0,
        52, 38, 32, 2, 27, 32, 0, 0, 0,
        73, 20, 30, 2, 22, 32, 0, 0, 0,
        21, 126, 24, 4, 136, 26, 0, 0, 0,
        30, 74, 32, 6, 73, 30, 0, 0, 0,
        54, 35, 30, 4, 40, 32, 0, 0, 0,
        75, 20, 30, 1, 20, 28, 0, 0, 0,
        30, 105, 20, 1, 114, 22, 0, 0, 0,
        3, 45, 22, 55, 47, 20, 0, 0, 0,
        2, 26, 26, 62, 33, 28, 0, 0, 0,
        79, 18, 28, 4, 33, 30, 0, 0, 0
    )

    private val FINDER_TOP_LEFT = intArrayOf(0x7F, 0x40, 0x5F, 0x50, 0x57, 0x57, 0x57)
    private val FINDER_NORMAL = intArrayOf(0x7F, 0x01, 0x7D, 0x05, 0x75, 0x75, 0x75)
    private val FINDER_BOTTOM_RIGHT = intArrayOf(0x75, 0x75, 0x75, 0x05, 0x7D, 0x01, 0x7F)

    private val GB18030 = Charset.forName("GB18030")

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    data class EncodeResult(
        val bitmap: Bitmap,
        val version: Int,
        val eccLevel: Int,
        val mask: Int
    )

    /**
     * Encode [content] into a Han Xin Code bitmap.
     *
     * @param requestedEccLevel 1..4, or 0 for automatic.
     * @param requestedVersion 1..84, or 0 for automatic.
     * @param requestedMask 0..3, or -1 for automatic.
     */
    fun encode(
        content: String,
        width: Int = 600,
        height: Int = 600,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
        requestedEccLevel: Int = 0,
        requestedVersion: Int = 0,
        requestedMask: Int = -1
    ): EncodeResult? {
        return try {
            val (ddata, eci) = convertInput(content)
            val modes = chooseModes(ddata)
            val estBinLen = calculateBinaryLength(modes, ddata, eci)
            val binary = BitWriter(estBinLen + 8)
            writeBinary(binary, modes, ddata, eci)

            var binLen = binary.size
            var codewords = binLen shr 3
            if ((binLen and 0x07) != 0) codewords++

            var eccLevel = when (requestedEccLevel) {
                in ECC_L1..ECC_L4 -> requestedEccLevel
                else -> ECC_L1
            }

            var version = findVersion(codewords, eccLevel, requestedVersion)
                ?: throw IllegalArgumentException("Input too long for Han Xin Code")

            var dataCodewords = DATA_CODEWORDS[eccLevel - 1][version - 1]

            // Promote ECC level automatically if there is spare capacity
            if (requestedEccLevel == 0 || requestedEccLevel != eccLevel) {
                if (eccLevel == ECC_L1 && codewords <= DATA_CODEWORDS[ECC_L2 - 1][version - 1]) {
                    eccLevel = ECC_L2
                    dataCodewords = DATA_CODEWORDS[eccLevel - 1][version - 1]
                }
                if (eccLevel == ECC_L2 && codewords <= DATA_CODEWORDS[ECC_L3 - 1][version - 1]) {
                    eccLevel = ECC_L3
                    dataCodewords = DATA_CODEWORDS[eccLevel - 1][version - 1]
                }
                if (eccLevel == ECC_L3 && codewords <= DATA_CODEWORDS[ECC_L4 - 1][version - 1]) {
                    eccLevel = ECC_L4
                    dataCodewords = DATA_CODEWORDS[eccLevel - 1][version - 1]
                }
            }

            val size = version * 2 + 21
            val dataStream = IntArray(dataCodewords) { 0 }
            binary.toByteArray(dataCodewords).copyInto(dataStream, 0, 0, min(binary.byteSize(), dataCodewords))

            val totalCodewords = TOTAL_CODEWORDS[version - 1]
            val fullStream = IntArray(totalCodewords)
            addErrorCorrection(fullStream, dataStream, dataCodewords, version, eccLevel)

            val picketFence = IntArray(totalCodewords)
            makePicketFence(fullStream, picketFence)

            val grid = IntArray(size * size) { 0 }
            setupGrid(grid, size, version)
            populateGrid(grid, size, picketFence)

            val mask = applyBitmask(grid, size, version, eccLevel, requestedMask)

            val bitmap = renderBitmap(grid, size, width, height, foreground, background)
            EncodeResult(bitmap, version, eccLevel, mask)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // -------------------------------------------------------------------------
    // Input conversion
    // -------------------------------------------------------------------------

    private fun convertInput(content: String): Pair<IntArray, Int> {
        // Try GB18030 first; if the whole string is representable, use ECI 32.
        val bytes = try {
            content.toByteArray(GB18030)
        } catch (e: Exception) {
            content.toByteArray(Charsets.UTF_8)
        }

        // Check whether the byte sequence is valid GB18030.
        var validGb = true
        val result = mutableListOf<Int>()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b <= 0x7F) {
                result.add(b)
                i++
            } else if (b in 0x81..0xFE && i + 1 < bytes.size) {
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 in 0x30..0x39) {
                    // 4-byte sequence
                    if (i + 3 < bytes.size) {
                        val b3 = bytes[i + 2].toInt() and 0xFF
                        val b4 = bytes[i + 3].toInt() and 0xFF
                        if (b3 in 0x81..0xFE && b4 in 0x30..0x39) {
                            result.add((b shl 8) or b2)
                            result.add((b3 shl 8) or b4)
                            i += 4
                        } else {
                            validGb = false
                            break
                        }
                    } else {
                        validGb = false
                        break
                    }
                } else if ((b2 in 0x40..0x7E) || (b2 in 0x80..0xFE)) {
                    result.add((b shl 8) or b2)
                    i += 2
                } else {
                    validGb = false
                    break
                }
            } else {
                validGb = false
                break
            }
        }

        if (validGb) {
            return Pair(result.toIntArray(), 32)
        }

        // Fallback: encode as UTF-8 bytes and use ECI 26 (UTF-8).
        val utf8 = content.toByteArray(Charsets.UTF_8)
        return Pair(utf8.map { it.toInt() and 0xFF }.toIntArray(), 26)
    }

    // -------------------------------------------------------------------------
    // Mode selection
    // -------------------------------------------------------------------------

    private fun chooseModes(ddata: IntArray): CharArray {
        val length = ddata.size
        if (length == 0) return CharArray(0)

        val charModes = Array(length) { CharArray(MODE_COUNT) { '\u0000' } }
        val prevCosts = IntArray(MODE_COUNT)
        val curCosts = IntArray(MODE_COUNT)

        // Initial mode costs (head costs) in 1/6 bits
        val headCosts = intArrayOf(24, 24, 102, 24, 24, 24, 0)
        headCosts.copyInto(prevCosts)

        var numericEnd = 0
        var numericCost = 0
        var textSubmode = 1
        var fourByteEnd = 0
        var fourByteCost = 0

        for (i in 0 until length) {
            curCosts.fill(0)
            charModes[i].fill('\u0000')

            val inNumeric = isInNumeric(ddata, length, i, numericEnd, numericCost)
            if (inNumeric.first) {
                numericEnd = inNumeric.second
                numericCost = inNumeric.third
                curCosts[MODE_N] = prevCosts[MODE_N] + numericCost
                charModes[i][MODE_N] = 'n'
            }

            val text1 = lookupText1(ddata[i]) != -1
            val text2 = lookupText2(ddata[i]) != -1
            if (text1 || text2) {
                if ((textSubmode == 1 && text2) || (textSubmode == 2 && text1)) {
                    curCosts[MODE_T] = prevCosts[MODE_T] + 72 // submode switch + char
                    textSubmode = if (text2) 2 else 1
                } else {
                    curCosts[MODE_T] = prevCosts[MODE_T] + 36 // char only
                }
                charModes[i][MODE_T] = 't'
            } else {
                textSubmode = 1
            }

            // Binary mode can encode anything
            curCosts[MODE_B] = prevCosts[MODE_B] + if (ddata[i] > 0xFF) 96 else 48
            charModes[i][MODE_B] = 'b'

            val inFourByte = isInFourByte(ddata, length, i, fourByteEnd, fourByteCost)
            if (inFourByte.first) {
                fourByteEnd = inFourByte.second
                fourByteCost = inFourByte.third
                curCosts[MODE_F] = prevCosts[MODE_F] + fourByteCost
                charModes[i][MODE_F] = 'f'
            } else if (isDoubleByte(ddata[i])) {
                curCosts[MODE_D] = prevCosts[MODE_D] + 90
                charModes[i][MODE_D] = 'd'
                if (isRegion1(ddata[i])) {
                    curCosts[MODE_1] = prevCosts[MODE_1] + 72
                    charModes[i][MODE_1] = '1'
                } else if (isRegion2(ddata[i])) {
                    curCosts[MODE_2] = prevCosts[MODE_2] + 72
                    charModes[i][MODE_2] = '2'
                }
            }

            // End-of-data costs on the last character
            if (i == length - 1) {
                val eodCosts = intArrayOf(60, 36, 0, 72, 72, 90, 0)
                for (j in 0 until MODE_COUNT) {
                    if (charModes[i][j] != '\u0000') {
                        curCosts[j] += eodCosts[j]
                    }
                }
            }

            // Consider switching to a new mode at this position
            for (j in 0 until MODE_COUNT) {
                for (k in 0 until MODE_COUNT) {
                    if (j != k && charModes[i][k] != '\u0000') {
                        val newCost = curCosts[k] + switchCost(k, j)
                        if (charModes[i][j] == '\u0000' || newCost < curCosts[j]) {
                            curCosts[j] = newCost
                            charModes[i][j] = MODE_CHARS[k]
                        }
                    }
                }
            }

            curCosts.copyInto(prevCosts)
        }

        // Pick the cheapest final mode
        var minCost = prevCosts[0]
        var curMode = MODE_CHARS[0]
        for (i in 1 until MODE_COUNT) {
            if (prevCosts[i] < minCost) {
                minCost = prevCosts[i]
                curMode = MODE_CHARS[i]
            }
        }

        // Backtrack
        val modes = CharArray(length)
        for (i in length - 1 downTo 0) {
            val idx = MODE_CHARS.indexOf(curMode)
            curMode = charModes[i][idx]
            modes[i] = curMode
        }
        return modes
    }

    private fun switchCost(from: Int, to: Int): Int {
        // Costs are in 1/6 bits, including terminator of previous mode + mode indicator of new mode.
        val table = arrayOf(
            intArrayOf(0, 84, 156, 84, 84, 84, 60),
            intArrayOf(60, 0, 132, 60, 60, 60, 36),
            intArrayOf(24, 24, 0, 24, 24, 24, 0),
            intArrayOf(96, 96, 168, 0, 72, 96, 72),
            intArrayOf(96, 96, 168, 72, 0, 96, 72),
            intArrayOf(114, 114, 186, 114, 114, 0, 90),
            intArrayOf(24, 24, 102, 24, 24, 24, 0)
        )
        return table[from][to]
    }

    private fun isInNumeric(
        ddata: IntArray,
        length: Int,
        pos: Int,
        prevEnd: Int,
        prevCost: Int
    ): Triple<Boolean, Int, Int> {
        if (pos < prevEnd) return Triple(true, prevEnd, prevCost)

        var i = pos
        while (i < length && i < pos + 3 && ddata[i] in 0x30..0x39) i++
        val digitCount = i - pos
        if (digitCount == 0) return Triple(false, 0, 0)

        val cost = when (digitCount) {
            1 -> 60
            2 -> 30
            else -> 20
        }
        return Triple(true, i, cost)
    }

    private fun isInFourByte(
        ddata: IntArray,
        length: Int,
        pos: Int,
        prevEnd: Int,
        prevCost: Int
    ): Triple<Boolean, Int, Int> {
        if (pos < prevEnd) return Triple(true, prevEnd, prevCost)
        if (pos >= length - 1 || !isFourByte(ddata[pos], ddata[pos + 1])) {
            return Triple(false, 0, 0)
        }
        return Triple(true, pos + 2, 75)
    }

    private fun isRegion1(glyph: Int): Boolean {
        val high = glyph shr 8
        val low = glyph and 0xFF
        if (high in 0xB0..0xD7 && low in 0xA1..0xFE) return true
        if (high in 0xA1..0xA3 && low in 0xA1..0xFE) return true
        if (glyph in 0xA8A1..0xA8C0) return true
        return false
    }

    private fun isRegion2(glyph: Int): Boolean {
        val high = glyph shr 8
        val low = glyph and 0xFF
        return high in 0xD8..0xF7 && low in 0xA1..0xFE
    }

    private fun isDoubleByte(glyph: Int): Boolean {
        val high = glyph shr 8
        val low = glyph and 0xFF
        if (high !in 0x81..0xFE) return false
        return low in 0x40..0x7E || low in 0x80..0xFE
    }

    private fun isFourByte(glyph1: Int, glyph2: Int): Boolean {
        val h1 = glyph1 shr 8
        val l1 = glyph1 and 0xFF
        if (h1 !in 0x81..0xFE || l1 !in 0x30..0x39) return false
        val h2 = glyph2 shr 8
        val l2 = glyph2 and 0xFF
        return h2 in 0x81..0xFE && l2 in 0x30..0x39
    }

    private fun lookupText1(input: Int): Int {
        return when {
            input in 0x30..0x39 -> input - 0x30
            input in 0x41..0x5A -> input - 0x41 + 10
            input in 0x61..0x7A -> input - 0x61 + 36
            else -> -1
        }
    }

    private fun lookupText2(input: Int): Int {
        return when {
            input <= 27 -> input
            input in 0x20..0x2F -> input - 0x20 + 28
            input in 0x3A..0x40 -> input - 0x3A + 44
            input in 0x5B..0x60 -> input - 0x5B + 51
            input in 0x7B..0x7F -> input - 0x7B + 57
            else -> -1
        }
    }

    private fun getTextSubmode(input: Int): Int {
        return if (lookupText1(input) != -1) 1 else 2
    }

    // -------------------------------------------------------------------------
    // Binary length estimation
    // -------------------------------------------------------------------------

    private fun calculateBinaryLength(modes: CharArray, ddata: IntArray, eci: Int): Int {
        var est = 0
        if (eci != 0) {
            est += 4
            est += when {
                eci <= 127 -> 8
                eci <= 16383 -> 16
                else -> 24
            }
        }

        var lastMode = '\u0000'
        var submode = 1
        var numericRun = 0
        var i = 0
        while (i < ddata.size) {
            if (modes[i] != lastMode) {
                if (i > 0) est += terminatorLength(lastMode)
                if (modes[i] != 'f' || (modes[i] == '1' && lastMode == '2') || (modes[i] == '2' && lastMode == '1')) {
                    est += 4
                }
                if (modes[i] == 'b') est += 13
                lastMode = modes[i]
                submode = 1
                numericRun = 0
            }
            when (modes[i]) {
                'n' -> {
                    if (numericRun % 3 == 0) est += 10
                    numericRun++
                }
                't' -> {
                    if (getTextSubmode(ddata[i]) != submode) {
                        est += 6
                        submode = getTextSubmode(ddata[i])
                    }
                    est += 6
                }
                'b' -> est += if (ddata[i] > 0xFF) 16 else 8
                '1', '2' -> est += 12
                'd' -> est += 15
                'f' -> {
                    est += 25
                    i++
                }
            }
            i++
        }
        est += terminatorLength(lastMode)
        return est
    }

    private fun terminatorLength(mode: Char): Int {
        return when (mode) {
            'n' -> 10
            't' -> 6
            '1', '2' -> 12
            'd' -> 15
            else -> 0
        }
    }

    // -------------------------------------------------------------------------
    // Binary stream generation
    // -------------------------------------------------------------------------

    private fun writeBinary(binary: BitWriter, modes: CharArray, ddata: IntArray, eci: Int) {
        var position = 0
        val length = ddata.size

        if (eci != 0) {
            binary.append(8, 4) // ECI mode indicator
            when {
                eci <= 127 -> binary.append(eci, 8)
                eci <= 16383 -> {
                    binary.append(2, 2)
                    binary.append(eci, 14)
                }
                else -> {
                    binary.append(6, 3)
                    binary.append(eci, 21)
                }
            }
        }

        while (position < length) {
            var blockLength = 1
            var doubleByteCount = 0
            if (modes[position] == 'b' && ddata[position] > 0xFF) doubleByteCount++
            while (position + blockLength < length && modes[position + blockLength] == modes[position]) {
                if (modes[position] == 'b' && ddata[position + blockLength] > 0xFF) doubleByteCount++
                blockLength++
            }

            when (modes[position]) {
                'n' -> writeNumeric(binary, ddata, position, blockLength)
                't' -> writeText(binary, ddata, position, blockLength)
                'b' -> writeBinary(binary, ddata, position, blockLength, doubleByteCount)
                '1' -> writeRegion1(binary, ddata, modes, position, blockLength, length)
                '2' -> writeRegion2(binary, ddata, modes, position, blockLength, length)
                'd' -> writeDoubleByte(binary, ddata, position, blockLength)
                'f' -> writeFourByte(binary, ddata, position, blockLength)
            }
            position += blockLength
        }
    }

    private fun writeNumeric(binary: BitWriter, ddata: IntArray, position: Int, blockLength: Int) {
        binary.append(1, 4)
        var i = 0
        var count = 0
        while (i < blockLength) {
            val first = ddata[position + i] - 0x30
            count = 1
            var encodingValue = first
            if (i + 1 < blockLength) {
                val second = ddata[position + i + 1] - 0x30
                count = 2
                encodingValue = encodingValue * 10 + second
                if (i + 2 < blockLength) {
                    val third = ddata[position + i + 2] - 0x30
                    count = 3
                    encodingValue = encodingValue * 10 + third
                }
            }
            binary.append(encodingValue, 10)
            i += count
        }
        val terminator = when (count) {
            1 -> 1021
            2 -> 1022
            else -> 1023
        }
        binary.append(terminator, 10)
    }

    private fun writeText(binary: BitWriter, ddata: IntArray, position: Int, blockLength: Int) {
        binary.append(2, 4)
        var submode = 1
        var i = 0
        while (i < blockLength) {
            val ch = ddata[position + i]
            if (getTextSubmode(ch) != submode) {
                binary.append(62, 6)
                submode = getTextSubmode(ch)
            }
            val value = if (submode == 1) lookupText1(ch) else lookupText2(ch)
            binary.append(value, 6)
            i++
        }
        binary.append(63, 6)
    }

    private fun writeBinary(
        binary: BitWriter,
        ddata: IntArray,
        position: Int,
        blockLength: Int,
        doubleByteCount: Int
    ) {
        binary.append(3, 4)
        binary.append(blockLength + doubleByteCount, 13)
        for (i in 0 until blockLength) {
            val ch = ddata[position + i]
            binary.append(ch, if (ch > 0xFF) 16 else 8)
        }
    }

    private fun writeRegion1(
        binary: BitWriter,
        ddata: IntArray,
        modes: CharArray,
        position: Int,
        blockLength: Int,
        length: Int
    ) {
        if (position == 0 || modes[position - 1] != '2') {
            binary.append(4, 4)
        }
        for (i in 0 until blockLength) {
            val glyphValue = ddata[position + i]
            val firstByte = (glyphValue shr 8) and 0xFF
            val secondByte = glyphValue and 0xFF
            var glyph = (0x5E * (firstByte - 0xB0)) + (secondByte - 0xA1)
            if (firstByte in 0xA1..0xA3 && secondByte in 0xA1..0xFE) {
                glyph = (0x5E * (firstByte - 0xA1)) + (secondByte - 0xA1) + 0xEB0
            }
            if (glyphValue in 0xA8A1..0xA8C0) {
                glyph = (secondByte - 0xA1) + 0xFCA
            }
            binary.append(glyph, 12)
        }
        val term = if (position + blockLength == length || modes.getOrNull(position + blockLength) != '2') {
            4095
        } else {
            4094
        }
        binary.append(term, 12)
    }

    private fun writeRegion2(
        binary: BitWriter,
        ddata: IntArray,
        modes: CharArray,
        position: Int,
        blockLength: Int,
        length: Int
    ) {
        if (position == 0 || modes[position - 1] != '1') {
            binary.append(5, 4)
        }
        for (i in 0 until blockLength) {
            val glyphValue = ddata[position + i]
            val firstByte = (glyphValue shr 8) and 0xFF
            val secondByte = glyphValue and 0xFF
            val glyph = (0x5E * (firstByte - 0xD8)) + (secondByte - 0xA1)
            binary.append(glyph, 12)
        }
        val term = if (position + blockLength == length || modes.getOrNull(position + blockLength) != '1') {
            4095
        } else {
            4094
        }
        binary.append(term, 12)
    }

    private fun writeDoubleByte(binary: BitWriter, ddata: IntArray, position: Int, blockLength: Int) {
        binary.append(6, 4)
        for (i in 0 until blockLength) {
            val glyphValue = ddata[position + i]
            val firstByte = (glyphValue shr 8) and 0xFF
            val secondByte = glyphValue and 0xFF
            val glyph = if (secondByte <= 0x7E) {
                (0xBE * (firstByte - 0x81)) + (secondByte - 0x40)
            } else {
                (0xBE * (firstByte - 0x81)) + (secondByte - 0x41)
            }
            binary.append(glyph, 15)
        }
        binary.append(32767, 15)
    }

    private fun writeFourByte(binary: BitWriter, ddata: IntArray, position: Int, blockLength: Int) {
        var i = 0
        while (i < blockLength) {
            binary.append(7, 4)
            val first = ddata[position + i]
            val second = ddata[position + i + 1]
            val firstByte = (first shr 8) and 0xFF
            val secondByte = first and 0xFF
            val thirdByte = (second shr 8) and 0xFF
            val fourthByte = second and 0xFF
            val glyph = (0x3138 * (firstByte - 0x81)) +
                    (0x04EC * (secondByte - 0x30)) +
                    (0x0A * (thirdByte - 0x81)) +
                    (fourthByte - 0x30)
            binary.append(glyph, 21)
            i += 2
        }
    }

    // -------------------------------------------------------------------------
    // Version / capacity helpers
    // -------------------------------------------------------------------------

    private fun findVersion(codewords: Int, eccLevel: Int, requestedVersion: Int): Int? {
        var version = MAX_VERSION + 1
        for (i in MAX_VERSION downTo MIN_VERSION) {
            if (DATA_CODEWORDS[eccLevel - 1][i - 1] >= codewords) {
                version = i
            }
        }
        if (version == MAX_VERSION + 1) return null

        if (requestedVersion in MIN_VERSION..MAX_VERSION) {
            if (requestedVersion < version) return null
            version = requestedVersion
        }
        return version
    }

    // -------------------------------------------------------------------------
    // Reed-Solomon error correction
    // -------------------------------------------------------------------------

    private fun addErrorCorrection(
        fullStream: IntArray,
        dataStream: IntArray,
        dataCodewords: Int,
        version: Int,
        eccLevel: Int
    ) {
        val rs8 = ReedSolomon(0x163, 8)
        val tablePos = (version - 1) * 36 + (eccLevel - 1) * 9
        var inputPos = 0
        var outputPos = 0

        for (group in 0 until 3) {
            val batchSize = RS_TABLE_D1[tablePos + group * 3]
            val dataLength = RS_TABLE_D1[tablePos + group * 3 + 1]
            val eccLength = RS_TABLE_D1[tablePos + group * 3 + 2]
            if (batchSize == 0) continue

            rs8.initCode(eccLength, 1)
            val dataBlock = IntArray(dataLength)
            val eccBlock = IntArray(eccLength)

            repeat(batchSize) {
                for (j in 0 until dataLength) {
                    dataBlock[j] = if (inputPos < dataCodewords) dataStream[inputPos] else 0
                    fullStream[outputPos++] = dataBlock[j]
                    inputPos++
                }
                rs8.encode(dataBlock, dataLength, eccBlock)
                for (j in 0 until eccLength) {
                    fullStream[outputPos++] = eccBlock[j]
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Picket fence interleaving
    // -------------------------------------------------------------------------

    private fun makePicketFence(fullStream: IntArray, picketFence: IntArray) {
        val streamSize = fullStream.size
        var outputPos = 0
        for (start in 0 until 13) {
            var i = start
            while (i < streamSize) {
                picketFence[outputPos++] = fullStream[i]
                i += 13
            }
        }
    }

    // -------------------------------------------------------------------------
    // Grid construction
    // -------------------------------------------------------------------------

    private fun setupGrid(grid: IntArray, size: Int, version: Int) {
        grid.fill(0)

        placeFinderTopLeft(grid, size)
        placeFinder(grid, size, 0, size - 7)
        placeFinder(grid, size, size - 7, 0)
        placeFinderBottomRight(grid, size)

        // Finder separators
        for (i in 0 until 8) {
            grid[7 * size + i] = 0x10
            grid[i * size + 7] = 0x10
            grid[7 * size + (size - i - 1)] = 0x10
            grid[(size - i - 1) * size + 7] = 0x10
            grid[i * size + (size - 8)] = 0x10
            grid[(size - 8) * size + i] = 0x10
            grid[(size - 8) * size + (size - i - 1)] = 0x10
            grid[(size - i - 1) * size + (size - 8)] = 0x10
        }

        // Function information region
        for (i in 0 until 9) {
            grid[8 * size + i] = 0x10
            grid[i * size + 8] = 0x10
            grid[8 * size + (size - i - 1)] = 0x10
            grid[(size - i - 1) * size + 8] = 0x10
            grid[i * size + (size - 9)] = 0x10
            grid[(size - 9) * size + i] = 0x10
            grid[(size - 9) * size + (size - i - 1)] = 0x10
            grid[(size - i - 1) * size + (size - 9)] = 0x10
        }

        if (version > 3) {
            val k = MODULE_K[version - 1]
            val r = MODULE_R[version - 1]
            val m = MODULE_M[version - 1]

            // Assistant alignment patterns on left/right edges
            var y = 0
            var modY = 0
            while (y < size) {
                val moduleHeight = if (modY < m) k else r - 1
                if ((modY and 1) == 0) {
                    if ((m and 1) == 1) plotAssistant(grid, size, 0, y)
                } else {
                    if ((m and 1) == 0) plotAssistant(grid, size, 0, y)
                    plotAssistant(grid, size, size - 1, y)
                }
                modY++
                y += moduleHeight
            }

            // Assistant alignment patterns on top/bottom edges
            var x = size - 1
            var modX = 0
            while (x >= 0) {
                val moduleWidth = if (modX < m) k else r - 1
                if ((modX and 1) == 0) {
                    if ((m and 1) == 1) plotAssistant(grid, size, x, size - 1)
                } else {
                    if ((m and 1) == 0) plotAssistant(grid, size, x, size - 1)
                    plotAssistant(grid, size, x, 0)
                }
                modX++
                x -= moduleWidth
            }

            // Main alignment patterns
            var columnSwitch = 1
            y = 0
            modY = 0
            while (y < size) {
                val moduleHeight = if (modY < m) k else r - 1
                val rowSwitch = if (columnSwitch == 1) {
                    columnSwitch = 0
                    1
                } else {
                    columnSwitch = 1
                    0
                }

                x = size - 1
                modX = 0
                var currentRowSwitch = rowSwitch
                while (x >= 0) {
                    val moduleWidth = if (modX < m) k else r - 1
                    if (currentRowSwitch == 1) {
                        if (!(y == 0 && x == size - 1)) {
                            plotAlignment(grid, size, x, y, moduleWidth, moduleHeight)
                        }
                        currentRowSwitch = 0
                    } else {
                        currentRowSwitch = 1
                    }
                    modX++
                    x -= moduleWidth
                }
                modY++
                y += moduleHeight
            }
        }
    }

    private fun placeFinderTopLeft(grid: IntArray, size: Int) {
        for (xp in 0 until 7) {
            for (yp in 0 until 7) {
                val bit = (FINDER_TOP_LEFT[yp] shr (6 - xp)) and 1
                grid[yp * size + xp] = if (bit == 1) 0x11 else 0x10
            }
        }
    }

    private fun placeFinder(grid: IntArray, size: Int, x: Int, y: Int) {
        for (xp in 0 until 7) {
            for (yp in 0 until 7) {
                val bit = (FINDER_NORMAL[yp] shr (6 - xp)) and 1
                grid[(yp + y) * size + (xp + x)] = if (bit == 1) 0x11 else 0x10
            }
        }
    }

    private fun placeFinderBottomRight(grid: IntArray, size: Int) {
        val x = size - 7
        val y = x
        for (xp in 0 until 7) {
            for (yp in 0 until 7) {
                val bit = (FINDER_BOTTOM_RIGHT[yp] shr (6 - xp)) and 1
                grid[(yp + y) * size + (xp + x)] = if (bit == 1) 0x11 else 0x10
            }
        }
    }

    private fun safePlot(grid: IntArray, size: Int, x: Int, y: Int, value: Int) {
        if (x in 0 until size && y in 0 until size && grid[y * size + x] == 0) {
            grid[y * size + x] = value
        }
    }

    private fun plotAlignment(grid: IntArray, size: Int, x: Int, y: Int, w: Int, h: Int) {
        safePlot(grid, size, x, y, 0x11)
        safePlot(grid, size, x - 1, y + 1, 0x10)
        for (i in 1..w) {
            safePlot(grid, size, x - i, y, 0x11)
            safePlot(grid, size, x - i - 1, y + 1, 0x10)
        }
        for (i in 1 until h) {
            safePlot(grid, size, x, y + i, 0x11)
            safePlot(grid, size, x - 1, y + i + 1, 0x10)
        }
    }

    private fun plotAssistant(grid: IntArray, size: Int, x: Int, y: Int) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                safePlot(grid, size, x + dx, y + dy, if (dx == 0 && dy == 0) 0x11 else 0x10)
            }
        }
    }

    private fun populateGrid(grid: IntArray, size: Int, picketFence: IntArray) {
        val jMax = picketFence.size * 8
        var j = 0
        for (i in grid.indices) {
            if (grid[i] == 0 && j < jMax) {
                if ((picketFence[j shr 3] shr (7 - (j and 0x07))) and 1 == 1) {
                    grid[i] = 0x01
                }
                j++
            }
        }
    }

    // -------------------------------------------------------------------------
    // Masking
    // -------------------------------------------------------------------------

    private fun applyBitmask(
        grid: IntArray,
        size: Int,
        version: Int,
        eccLevel: Int,
        requestedMask: Int
    ): Int {
        val sizeSquared = size * size
        val mask = IntArray(sizeSquared)
        val local = IntArray(sizeSquared)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val k = y * size + x
                if ((grid[k] and 0xF0) == 0) {
                    val j = x + 1
                    val i = y + 1
                    if (((i + j) and 1) == 0) mask[k] = mask[k] or 0x02
                    if ((((i + j) % 3 + j % 3) and 1) == 0) mask[k] = mask[k] or 0x04
                    if (((i % j + j % i + i % 3 + j % 3) and 1) == 0) mask[k] = mask[k] or 0x08
                }
            }
        }

        val bestPattern: Int
        if (requestedMask in 0..3) {
            bestPattern = requestedMask
        } else {
            // Pattern 0
            for (k in 0 until sizeSquared) local[k] = grid[k] and 0x0F
            setFunctionInfo(local, size, version, eccLevel, 0)
            val penalties = IntArray(4)
            penalties[0] = evaluate(local, size)
            bestPattern = (0 until 4).minByOrNull { pattern ->
                if (pattern == 0) penalties[0] else {
                    val bit = 1 shl pattern
                    for (k in 0 until sizeSquared) {
                        local[k] = if ((mask[k] and bit) != 0) grid[k] xor 0x01 else grid[k] and 0x0F
                    }
                    setFunctionInfo(local, size, version, eccLevel, pattern)
                    penalties[pattern] = evaluate(local, size)
                    penalties[pattern]
                }
            } ?: 0
        }

        if (bestPattern != 0) {
            val bit = 1 shl bestPattern
            for (k in 0 until sizeSquared) {
                if ((mask[k] and bit) != 0) {
                    grid[k] = grid[k] xor 0x01
                }
            }
        }
        setFunctionInfo(grid, size, version, eccLevel, bestPattern)
        return bestPattern
    }

    private fun setFunctionInfo(grid: IntArray, size: Int, version: Int, eccLevel: Int, bitmask: Int) {
        val fi = BitWriter(40)
        fi.append(version + 20, 8)
        fi.append(eccLevel - 1, 2)
        fi.append(bitmask, 2)

        val fiCw = IntArray(3)
        for (i in 0 until 3) {
            var cw = 0
            for (j in 0 until 4) {
                if (fi[i * 4 + j] == 1) cw = cw or (0x08 shr j)
            }
            fiCw[i] = cw
        }

        val rs4 = ReedSolomon(0x13, 4)
        rs4.initCode(4, 1)
        val fiEcc = IntArray(4)
        rs4.encode(fiCw, 3, fiEcc)
        for (v in fiEcc) fi.append(v, 4)
        for (i in 28 until 34) fi[i] = 0

        for (i in 0 until 9) {
            if (fi[i] == 1) {
                grid[8 * size + i] = 0x01
                grid[(size - 8 - 1) * size + (size - i - 1)] = 0x01
            }
            if (fi[i + 8] == 1) {
                grid[(8 - i) * size + 8] = 0x01
                grid[(size - 8 - 1 + i) * size + (size - 8 - 1)] = 0x01
            }
            if (fi[i + 17] == 1) {
                grid[i * size + (size - 1 - 8)] = 0x01
                grid[(size - 1 - i) * size + 8] = 0x01
            }
            if (fi[i + 25] == 1) {
                grid[8 * size + (size - 1 - 8 + i)] = 0x01
                grid[(size - 1 - 8) * size + (8 - i)] = 0x01
            }
        }
    }

    private fun evaluate(local: IntArray, size: Int): Int {
        val h1010111 = intArrayOf(1, 0, 1, 0, 1, 1, 1)
        val h1110101 = intArrayOf(1, 1, 1, 0, 1, 0, 1)
        var result = 0

        // Test 1: 1:1:1:1:3 or 3:1:1:1:1 ratio pattern
        for (x in 0 until size) {
            var y = 0
            while (y <= size - 7) {
                if (local[y * size + x] == 1 &&
                    local[(y + 1) * size + x] != local[(y + 5) * size + x] &&
                    local[(y + 2) * size + x] == 1 &&
                    local[(y + 3) * size + x] == 0 &&
                    local[(y + 4) * size + x] == 1 &&
                    local[(y + 6) * size + x] == 1
                ) {
                    var beforeCount = 0
                    var b = y - 1
                    while (b >= y - 3) {
                        if (b < 0) {
                            beforeCount = 3
                            break
                        }
                        if (local[b * size + x] == 1) break
                        beforeCount++
                        b--
                    }
                    if (beforeCount == 3) {
                        result += 50
                    } else {
                        var afterCount = 0
                        var a = y + 7
                        while (a <= y + 9) {
                            if (a >= size) {
                                afterCount = 3
                                break
                            }
                            if (local[a * size + x] == 1) break
                            afterCount++
                            a++
                        }
                        if (afterCount == 3) result += 50
                    }
                    y++
                }
                y++
            }
        }

        for (y in 0 until size) {
            val r = y * size
            var x = 0
            while (x <= size - 7) {
                val slice = local.copyOfRange(r + x, r + x + 7)
                if (slice.contentEquals(h1010111) || slice.contentEquals(h1110101)) {
                    var beforeCount = 0
                    var b = x - 1
                    while (b >= x - 3) {
                        if (b < 0) {
                            beforeCount = 3
                            break
                        }
                        if (local[r + b] == 1) break
                        beforeCount++
                        b--
                    }
                    if (beforeCount == 3) {
                        result += 50
                    } else {
                        var afterCount = 0
                        var a = x + 7
                        while (a <= x + 9) {
                            if (a >= size) {
                                afterCount = 3
                                break
                            }
                            if (local[r + a] == 1) break
                            afterCount++
                            a++
                        }
                        if (afterCount == 3) result += 50
                    }
                    x++
                }
                x++
            }
        }

        // Test 2: runs of same colour
        for (x in 0 until size) {
            var block = 0
            var state = 0
            for (y in 0 until size) {
                if (local[y * size + x] == state) {
                    block++
                } else {
                    if (block >= 3) result += block * 4
                    block = 1
                    state = local[y * size + x]
                }
            }
            if (block >= 3) result += block * 4
        }

        for (y in 0 until size) {
            val r = y * size
            var block = 0
            var state = 0
            for (x in 0 until size) {
                if (local[r + x] == state) {
                    block++
                } else {
                    if (block >= 3) result += block * 4
                    block = 1
                    state = local[r + x]
                }
            }
            if (block >= 3) result += block * 4
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Bitmap rendering
    // -------------------------------------------------------------------------

    private fun renderBitmap(
        grid: IntArray,
        size: Int,
        width: Int,
        height: Int,
        foreground: Int,
        background: Int
    ): Bitmap {
        val moduleWidth = width / size
        val moduleHeight = height / size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val mx = x / moduleWidth
                val my = y / moduleHeight
                val dark = if (mx in 0 until size && my in 0 until size) {
                    (grid[my * size + mx] and 1) == 1
                } else false
                bitmap.setPixel(x, y, if (dark) foreground else background)
            }
        }
        return bitmap
    }

    // -------------------------------------------------------------------------
    // BitWriter helper
    // -------------------------------------------------------------------------

    private class BitWriter(initialCapacity: Int) {
        private val bits = IntArray(initialCapacity) { 0 }
        private var pos = 0

        val size: Int get() = pos

        operator fun get(index: Int): Int = bits[index]

        operator fun set(index: Int, value: Int) {
            bits[index] = value and 1
        }

        fun append(value: Int, bitCount: Int) {
            var v = value
            for (i in bitCount - 1 downTo 0) {
                bits[pos + i] = v and 1
                v = v shr 1
            }
            pos += bitCount
        }

        fun byteSize(): Int = (pos + 7) shr 3

        fun toByteArray(maxBytes: Int): IntArray {
            val result = IntArray(maxBytes) { 0 }
            for (i in 0 until pos) {
                if (bits[i] == 1) {
                    result[i shr 3] = result[i shr 3] or (0x80 shr (i and 0x07))
                }
            }
            return result
        }
    }

    // -------------------------------------------------------------------------
    // Reed-Solomon helper
    // -------------------------------------------------------------------------

    internal class ReedSolomon(private val primitive: Int, private val symbolSize: Int) {
        private val fieldSize = 1 shl symbolSize
        private val alphaTo = IntArray(fieldSize)
        private val indexOf = IntArray(fieldSize)
        internal var generator: IntArray = intArrayOf()
            private set
        private var generatorRev: IntArray = intArrayOf()

        init {
            initField()
        }

        private fun initField() {
            var mask = 1
            alphaTo[symbolSize] = 0
            for (i in 0 until symbolSize) {
                alphaTo[i] = mask
                indexOf[alphaTo[i]] = i
                if ((primitive and (1 shl i)) != 0) {
                    alphaTo[symbolSize] = alphaTo[symbolSize] or mask
                }
                mask = mask shl 1
            }
            indexOf[alphaTo[symbolSize]] = symbolSize
            mask = mask shr 1
            for (i in symbolSize + 1 until fieldSize - 1) {
                alphaTo[i] = if (alphaTo[i - 1] >= mask) {
                    alphaTo[symbolSize] xor ((alphaTo[i - 1] shl 1) and (fieldSize - 1))
                } else {
                    alphaTo[i - 1] shl 1
                }
                indexOf[alphaTo[i]] = i
            }
            indexOf[0] = -1
        }

        fun initCode(numEcc: Int, firstRoot: Int) {
            generator = IntArray(numEcc + 1)
            generator[0] = 1
            for (i in firstRoot until firstRoot + numEcc) {
                val old = generator.copyOf()
                val degree = i - firstRoot
                // g(x) <- g(x) * (x + alpha^i)
                // new_g[0] = alpha^i * old_g[0]
                // new_g[j] = old_g[j-1] + alpha^i * old_g[j]  for 1 <= j <= d+1
                for (j in 0..degree) {
                    val shifted = old[j]
                    val scaled = if (old[j + 1] != 0) {
                        alphaTo[(indexOf[old[j + 1]] + i) % (fieldSize - 1)]
                    } else 0
                    generator[j + 1] = shifted xor scaled
                }
                generator[0] = if (old[0] != 0) {
                    alphaTo[(indexOf[old[0]] + i) % (fieldSize - 1)]
                } else 0
            }
            generatorRev = IntArray(numEcc + 1) { generator[numEcc - it] }
        }

        fun encode(data: IntArray, dataLength: Int, ecc: IntArray) {
            val numEcc = ecc.size
            // Build dividend = reverse(data) * x^numEcc so that when we divide by
            // the reciprocal generator g_rev(x) = x^numEcc * g(1/x) we obtain a
            // remainder whose reverse is the ECC for the [data, ecc] codeword.
            val dividend = IntArray(dataLength + numEcc)
            for (i in 0 until dataLength) {
                dividend[i + numEcc] = data[dataLength - 1 - i]
            }
            val remainder = polyMod(dividend, generatorRev)
            for (i in 0 until numEcc) {
                ecc[i] = remainder[numEcc - 1 - i]
            }
        }

        /**
         * Compute the remainder of [dividend] divided by [divisor] over GF(2^symbolSize).
         * Coefficients are stored in ascending power order (index i is x^i).
         * The returned array contains the low-degree remainder coefficients.
         */
        private fun polyMod(dividend: IntArray, divisor: IntArray): IntArray {
            val result = dividend.copyOf()
            val degD = divisor.size - 1
            val leadLog = indexOf[divisor[degD]]
            for (i in result.size - 1 downTo degD) {
                if (result[i] == 0) continue
                val factorLog = (indexOf[result[i]] - leadLog).mod(fieldSize - 1)
                for (j in 0..degD) {
                    if (divisor[j] == 0) continue
                    val exp = (factorLog + indexOf[divisor[j]]).mod(fieldSize - 1)
                    result[i - degD + j] = result[i - degD + j] xor alphaTo[exp]
                }
            }
            return result.copyOf(degD)
        }

        /**
         * Calculate the syndromes of [data] using roots alpha^1 .. alpha^numEcc.
         */
        fun calculateSyndromes(data: IntArray, totalLength: Int, numEcc: Int): IntArray {
            val syndromes = IntArray(numEcc)
            for (i in 0 until numEcc) {
                val rootExp = i + 1
                var sum = 0
                for (j in 0 until totalLength) {
                    if (data[j] == 0) continue
                    val exp = (indexOf[data[j]] + rootExp * j) % (fieldSize - 1)
                    sum = sum xor alphaTo[exp]
                }
                syndromes[i] = sum
            }
            return syndromes
        }

        /**
         * Berlekamp-Massey algorithm. Returns the error-locator polynomial sigma
         * and error-evaluator polynomial omega.
         */
        fun berlekampMassey(syndromes: IntArray, numEcc: Int): Pair<IntArray, IntArray> {
            val sigma = IntArray(numEcc + 1) { 0 }
            var b = IntArray(numEcc + 1) { 0 }
            sigma[0] = 1
            b[0] = 1
            var l = 0
            var m = 1
            var bLog = 0

            for (n in 0 until numEcc) {
                var discrepancy = syndromes[n]
                for (i in 1..l) {
                    if (sigma[i] == 0) continue
                    val exp = (indexOf[sigma[i]] + indexOf[syndromes[n - i]]) % (fieldSize - 1)
                    discrepancy = discrepancy xor alphaTo[exp]
                }
                if (discrepancy == 0) {
                    m++
                } else if (2 * l <= n) {
                    val t = sigma.copyOf()
                    val discLog = indexOf[discrepancy]
                    for (i in 0 until numEcc + 1) {
                        if (b[i] == 0) continue
                        val exp = (discLog + (fieldSize - 1) - bLog + indexOf[b[i]]) % (fieldSize - 1)
                        sigma[i + m] = sigma[i + m] xor alphaTo[exp]
                    }
                    l = n + 1 - l
                    b = t
                    bLog = discLog
                    m = 1
                } else {
                    val discLog = indexOf[discrepancy]
                    for (i in 0 until numEcc + 1) {
                        if (b[i] == 0) continue
                        val exp = (discLog + (fieldSize - 1) - bLog + indexOf[b[i]]) % (fieldSize - 1)
                        sigma[i + m] = sigma[i + m] xor alphaTo[exp]
                    }
                    m++
                }
            }

            // Compute omega = sigma * syndromes mod x^numEcc
            val omega = IntArray(numEcc)
            for (i in 0 until numEcc) {
                var sum = 0
                for (j in 0..i) {
                    if (sigma[j] == 0 || syndromes[i - j] == 0) continue
                    val exp = (indexOf[sigma[j]] + indexOf[syndromes[i - j]]) % (fieldSize - 1)
                    sum = sum xor alphaTo[exp]
                }
                omega[i] = sum
            }
            return sigma to omega
        }

        /**
         * Find the roots of the error-locator polynomial [sigma] by evaluating it
         * at alpha^0 .. alpha^(totalLength-1). Returns the logarithmic error
         * locations (powers of alpha) or an empty list if the polynomial degree
         * does not match the number of roots found.
         */
        fun chienSearch(sigma: IntArray, totalLength: Int): List<Int> {
            val roots = mutableListOf<Int>()
            val degree = sigma.indexOfLast { it != 0 }
            for (i in 0 until totalLength) {
                var sum = sigma[0]
                for (j in 1 until sigma.size) {
                    if (sigma[j] == 0) continue
                    val exp = (indexOf[sigma[j]] + j * i) % (fieldSize - 1)
                    sum = sum xor alphaTo[exp]
                }
                if (sum == 0) {
                    roots.add(i)
                }
            }
            if (roots.size != degree) return emptyList()
            return roots
        }

        /**
         * Compute the error magnitude at logarithmic location [loc] using
         * Forney's formula.
         */
        fun forney(sigma: IntArray, omega: IntArray, loc: Int): Int {
            val xiInv = alphaTo[(fieldSize - 1 - loc) % (fieldSize - 1)]
            var omegaVal = 0
            for (i in 0 until omega.size) {
                if (omega[i] == 0) continue
                val exp = (indexOf[omega[i]] + i * loc) % (fieldSize - 1)
                omegaVal = omegaVal xor alphaTo[exp]
            }
            var sigmaDerivative = 0
            for (i in 1 until sigma.size step 2) {
                if (sigma[i] == 0) continue
                val exp = (indexOf[sigma[i]] + (i - 1) * loc) % (fieldSize - 1)
                sigmaDerivative = sigmaDerivative xor alphaTo[exp]
            }
            if (sigmaDerivative == 0) return 0
            val errLog = (indexOf[omegaVal] + indexOf[xiInv] + (fieldSize - 1) - indexOf[sigmaDerivative]) % (fieldSize - 1)
            return alphaTo[errLog]
        }

        /**
         * Verify that the Reed-Solomon syndromes of [data] are all zero.
         *
         * @param data array of data + ecc codewords
         * @param dataLength number of data codewords
         * @param numEcc number of ecc codewords
         * @return true if no errors are detected
         */
        fun checkSyndromes(data: IntArray, dataLength: Int, numEcc: Int): Boolean {
            val totalLength = dataLength + numEcc
            return calculateSyndromes(data, totalLength, numEcc).all { it == 0 }
        }
    }
}
