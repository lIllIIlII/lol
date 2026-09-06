/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.data.network

/** 网盘平台 */
enum class SharePlatform { QUARK, UC, XUNLEI, BAIDU, C139, PAN123, LANZOU, ILANZOU, COWTRANSFER, FEIJI, CTFILE, WENSHUSHU }

/**
 * 解析结果：share_id + 提取码 + 平台。
 */
data class ParsedShare(
    val shareId: String,
    val pwd: String?,
    val platform: SharePlatform
)

/**
 * 从分享链接或整段分享文案中提取 share_id 与提取码。
 * 支持：pan.quark.cn/s/xxx（夸克）、drive.uc.cn/s/xxx（UC）、pan.xunlei.com/s/xxx（迅雷）
 */
object ShareLinkParser {

    private val urlRegex = Regex("""https?://[^\s]+""")
    private val quarkShareIdRegex = Regex("""pan\.quark\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ucShareIdRegex = Regex("""drive\.uc\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val xunleiShareIdRegex = Regex("""pan\.xunlei\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val baiduShareIdRegex = Regex("""pan\.baidu\.com/s/(1[A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val c139ShareIdRegex = Regex("""yun\.139\.com/shareweb/.*?/w/i/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    // 123 云盘分享链接（抓包 + alist 实践综合，文档 §4.1）：
    // - https://www.123pan.com/s/<ShareKey> / https://www.123865.com/s/<ShareKey>
    // - https://<UID>.share.123pan.cn/123pan/<ShareKey>
    // - https://www.123pan.cn/api/srr?sk=<ShareKey>&st=s
    // ShareKey 形态：含一个中划线、两端为字母数字，如 2785Vv-T4Ded
    private val pan123ShareIdRegex = Regex("""123(?:865|pan)\.(?:com|cn)/s/([A-Za-z0-9]+-[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareSubRegex = Regex("""share\.123pan\.cn/123pan/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan123SrrRegex = Regex("""api/srr\?sk=([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    // 蓝奏云优享版（www.ilanzou.com）：SPA 新站，走 apix.ilanzou.com 接口（与经典蓝奏云不同源）
    // 必须优先于经典蓝奏云正则匹配，否则会被 lanzou 家族正则吞掉走错解析器
    private val ilanzouShareIdRegex = Regex(
        """ilanzou[a-z]{0,2}\.(?:com|cn|net)/(?:s/)?([A-Za-z][0-9A-Za-z_-]{5,})""",
        RegexOption.IGNORE_CASE
    )
    // 蓝奏云：域名家族 lanzou[a-z]?.com/.cn/.net（wwbll.lanzoul.com / wwapg.lanzoub.com / wwm.lanzouv.com 等）
    // 分享 ID 一般以 b/i/a 开头（b0188jmrwj、iJPKr0dzhtni）；ilanzou 已单列，不在此匹配
    private val lanzouShareIdRegex = Regex(
        """(?:[0-9A-Za-z]+\.)?(?:lanzo[u]?[a-z]{0,2})\.(?:com|cn|net)/(?:s/)?([A-Za-z][0-9A-Za-z_-]{4,})""",
        RegexOption.IGNORE_CASE
    )
    // 奶牛快传：https://cowtransfer.com/s/2f1b183a5ed548（14 位 hex，新链接也可能是更长 UUID 形态）
    private val cowShareIdRegex = Regex("""cowtransfer\.com/s/([0-9A-Za-z-]{10,})""", RegexOption.IGNORE_CASE)
    // 小飞机网盘：https://share.feijipan.com/s/LaWUHmt5?code=ywzt
    private val feijiShareIdRegex = Regex("""feijipan\.com/s/([0-9A-Za-z-]{4,})""", RegexOption.IGNORE_CASE)
    // 城通网盘：域名家族（ctfile.com 及镜像 545c.com / pipipan.com 等）；
    // 文件分享 /f/<uid-fid-chk>（3 段）或 /file/<uid-fid>（2 段），文件夹 /dir/<id>（暂不支持）
    private val ctfileShareIdRegex = Regex(
        """(?:[a-z0-9]+\.)?(?:ctfile|pipipan|545c)\.com/(f|file|dir)/([a-zA-Z0-9-]{5,})""",
        RegexOption.IGNORE_CASE
    )
    // 文叔叔：https://f.wenshushu.cn/f/<code>（尾段 11/12 位 tid 或 16 位 token）
    private val wssShareIdRegex = Regex(
        """(?:[a-z]+\.)?wenshushu\.cn/f/([a-zA-Z0-9]{8,20})""",
        RegexOption.IGNORE_CASE
    )
    private val pwdInUrlRegex = Regex("""[?&]pwd=([A-Za-z0-9]+)""")
    private val codeInUrlRegex = Regex("""[?&]code=([A-Za-z0-9]+)""")
    // 城通访问密码常在 URL：?p=xxxx
    private val pInUrlRegex = Regex("""[?&]p=([A-Za-z0-9]{2,12})""")
    private val pwdInTextRegex = Regex("""(?:提取码|访问码|访问密码|密码)[：:]\s*([A-Za-z0-9]{3,12})""")

    fun parse(text: String): ParsedShare? {
        val url = urlRegex.find(text.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: return null
        // 夸克链接
        quarkShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.QUARK)
        }
        // UC 链接
        ucShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.UC)
        }
        // 迅雷链接
        xunleiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.XUNLEI)
        }
        // 百度链接：https://pan.baidu.com/s/1xxxxx?pwd=xxxx
        baiduShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            // 百度 surl 不包含开头的 "1"（verify/list 接口用 1 后面的部分）
            val surl = sid.removePrefix("1")
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = surl, pwd = pwd, platform = SharePlatform.BAIDU)
        }
        // 139（和彩云）链接：https://yun.139.com/shareweb/#/w/i/{linkID} 提取码 xxxx
        c139ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.C139)
        }
        // 123 云盘链接（3 种形态，按优先级匹配）
        pan123ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123ShareSubRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123SrrRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        // 蓝奏云优享版链接（先于经典蓝奏云匹配；?pwd= / ?code= 或文案提取码均支持）
        ilanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 6) return@let
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: codeInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.ILANZOU)
        }
        // 蓝奏云链接（域名家族多，见 lanzouShareIdRegex 注释；?pwd= 或文案提取码均支持）
        lanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 6) return@let
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.LANZOU)
        }
        // 奶牛快传链接
        cowShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.COWTRANSFER)
        }
        // 小飞机网盘链接（code 参数即提取码）
        feijiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 5) return@let
            val pwd = codeInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.FEIJI)
        }
        // 城通网盘链接（group1=f/file/dir 形态，group2=分享 ID；?p= 或文案访问密码均支持）
        ctfileShareIdRegex.find(url)?.groupValues?.let { g ->
            val kind = g.getOrNull(1).orEmpty()
            val sid = g.getOrNull(2).orEmpty()
            if (sid.length >= 6) {
                val pwd = pInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
                // dir 文件夹分享暂不支持：归一化为 f 形态交由仓库层提示
                return ParsedShare(shareId = "$kind:$sid", pwd = pwd, platform = SharePlatform.CTFILE)
            }
        }
        // 文叔叔链接
        wssShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length in 8..20) {
                val pwd = pInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
                return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.WENSHUSHU)
            }
        }
        return null
    }
}
